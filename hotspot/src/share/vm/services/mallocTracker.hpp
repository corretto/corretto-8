/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_VM_SERVICES_MALLOC_TRACKER_HPP
#define SHARE_VM_SERVICES_MALLOC_TRACKER_HPP

#if INCLUDE_NMT

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "services/nmtCommon.hpp"
#include "utilities/nativeCallStack.hpp"

/*
 * This counter class counts memory allocation and deallocation,
 * records total memory allocation size and number of allocations.
 * The counters are updated atomically.
 */
class MemoryCounter VALUE_OBJ_CLASS_SPEC {
 private:
  size_t   _count;
  size_t   _size;

  DEBUG_ONLY(size_t   _peak_count;)
  DEBUG_ONLY(size_t   _peak_size; )

 public:
  MemoryCounter() : _count(0), _size(0) {
    DEBUG_ONLY(_peak_count = 0;)
    DEBUG_ONLY(_peak_size  = 0;)
  }

  inline void allocate(size_t sz) {
    Atomic::add(1, (volatile MemoryCounterType*)&_count);
    if (sz > 0) {
      Atomic::add((MemoryCounterType)sz, (volatile MemoryCounterType*)&_size);
      DEBUG_ONLY(_peak_size = MAX2(_peak_size, _size));
    }
    DEBUG_ONLY(_peak_count = MAX2(_peak_count, _count);)
  }

  inline void deallocate(size_t sz) {
    assert(_count > 0, "Negative counter");
    assert(_size >= sz, "Negative size");
    Atomic::add(-1, (volatile MemoryCounterType*)&_count);
    if (sz > 0) {
      Atomic::add(-(MemoryCounterType)sz, (volatile MemoryCounterType*)&_size);
    }
  }

  inline void resize(ssize_t sz) {
    if (sz != 0) {
      assert(sz >= 0 || _size >= size_t(-sz), "Must be");
      Atomic::add((MemoryCounterType)sz, (volatile MemoryCounterType*)&_size);
      DEBUG_ONLY(_peak_size = MAX2(_size, _peak_size);)
    }
  }

  inline size_t count() const { return _count; }
  inline size_t size()  const { return _size;  }
  DEBUG_ONLY(inline size_t peak_count() const { return _peak_count; })
  DEBUG_ONLY(inline size_t peak_size()  const { return _peak_size; })

};

/*
 * Malloc memory used by a particular subsystem.
 * It includes the memory acquired through os::malloc()
 * call and arena's backing memory.
 */
class MallocMemory VALUE_OBJ_CLASS_SPEC {
 private:
  MemoryCounter _malloc;
  MemoryCounter _arena;

 public:
  MallocMemory() { }

  inline void record_malloc(size_t sz) {
    _malloc.allocate(sz);
  }

  inline void record_free(size_t sz) {
    _malloc.deallocate(sz);
  }

  inline void record_new_arena() {
    _arena.allocate(0);
  }

  inline void record_arena_free() {
    _arena.deallocate(0);
  }

  inline void record_arena_size_change(ssize_t sz) {
    _arena.resize(sz);
  }

  inline size_t malloc_size()  const { return _malloc.size(); }
  inline size_t malloc_count() const { return _malloc.count();}
  inline size_t arena_size()   const { return _arena.size();  }
  inline size_t arena_count()  const { return _arena.count(); }

  DEBUG_ONLY(inline const MemoryCounter& malloc_counter() const { return _malloc; })
  DEBUG_ONLY(inline const MemoryCounter& arena_counter()  const { return _arena;  })
};

class MallocMemorySummary;

// A snapshot of malloc'd memory, includes malloc memory
// usage by types and memory used by tracking itself.
class MallocMemorySnapshot : public ResourceObj {
  friend class MallocMemorySummary;

 private:
  MallocMemory      _malloc[mt_number_of_types];
  MemoryCounter     _tracking_header;


 public:
  inline MallocMemory*  by_type(MEMFLAGS flags) {
    int index = NMTUtil::flag_to_index(flags);
    return &_malloc[index];
  }

  inline MallocMemory* by_index(int index) {
    assert(index >= 0, "Index out of bound");
    assert(index < mt_number_of_types, "Index out of bound");
    return &_malloc[index];
  }

  inline MemoryCounter* malloc_overhead() {
    return &_tracking_header;
  }

  // Total malloc'd memory amount
  size_t total() const;
  // Total malloc'd memory used by arenas
  size_t total_arena() const;

  inline size_t thread_count() const {
    MallocMemorySnapshot* s = const_cast<MallocMemorySnapshot*>(this);
    return s->by_type(mtThreadStack)->malloc_count();
  }

  void copy_to(MallocMemorySnapshot* s) {
    s->_tracking_header = _tracking_header;
    for (int index = 0; index < mt_number_of_types; index ++) {
      s->_malloc[index] = _malloc[index];
    }
  }

  // Make adjustment by subtracting chunks used by arenas
  // from total chunks to get total free chunk size
  void make_adjustment();
};

/*
 * This class is for collecting malloc statistics at summary level
 */
class MallocMemorySummary : AllStatic {
 private:
  // Reserve memory for placement of MallocMemorySnapshot object
  static size_t _snapshot[CALC_OBJ_SIZE_IN_TYPE(MallocMemorySnapshot, size_t)];

 public:
   static void initialize();

   static inline void record_malloc(size_t size, MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_malloc(size);
   }

   static inline void record_free(size_t size, MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_free(size);
   }

   static inline void record_new_arena(MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_new_arena();
   }

   static inline void record_arena_free(MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_arena_free();
   }

   static inline void record_arena_size_change(ssize_t size, MEMFLAGS flag) {
     as_snapshot()->by_type(flag)->record_arena_size_change(size);
   }

   static void snapshot(MallocMemorySnapshot* s) {
     as_snapshot()->copy_to(s);
     s->make_adjustment();
   }

   // Record memory used by malloc tracking header
   static inline void record_new_malloc_header(size_t sz) {
     as_snapshot()->malloc_overhead()->allocate(sz);
   }

   static inline void record_free_malloc_header(size_t sz) {
     as_snapshot()->malloc_overhead()->deallocate(sz);
   }

   // The memory used by malloc tracking headers
   static inline size_t tracking_overhead() {
     return as_snapshot()->malloc_overhead()->size();
   }

  static MallocMemorySnapshot* as_snapshot() {
    return (MallocMemorySnapshot*)_snapshot;
  }
};


/*
 * Malloc tracking header.
 * To satisfy malloc alignment requirement, NMT uses 2 machine words for tracking purpose,
 * which ensures 8-bytes alignment on 32-bit systems and 16-bytes on 64-bit systems (Product build).
 */

class MallocHeader VALUE_OBJ_CLASS_SPEC {
#ifdef _LP64
  size_t           _size      : 64;
  size_t           _flags     : 8;
  size_t           _pos_idx   : 16;
  size_t           _bucket_idx: 40;
#define MAX_MALLOCSITE_TABLE_SIZE right_n_bits(40)
#define MAX_BUCKET_LENGTH         right_n_bits(16)
#else
  size_t           _size      : 32;
  size_t           _flags     : 8;
  size_t           _pos_idx   : 8;
  size_t           _bucket_idx: 16;
#define MAX_MALLOCSITE_TABLE_SIZE  right_n_bits(16)
#define MAX_BUCKET_LENGTH          right_n_bits(8)
#endif  // _LP64

 public:
  MallocHeader(size_t size, MEMFLAGS flags, const NativeCallStack& stack, NMT_TrackingLevel level) {
    assert(sizeof(MallocHeader) == sizeof(void*) * 2,
      "Wrong header size");

    if (level == NMT_minimal) {
      return;
    }

    _flags = flags;
    set_size(size);
    if (level == NMT_detail) {
      size_t bucket_idx;
      size_t pos_idx;
      if (record_malloc_site(stack, size, &bucket_idx, &pos_idx, flags)) {
        assert(bucket_idx <= MAX_MALLOCSITE_TABLE_SIZE, "Overflow bucket index");
        assert(pos_idx <= MAX_BUCKET_LENGTH, "Overflow bucket position index");
        _bucket_idx = bucket_idx;
        _pos_idx = pos_idx;
      }
    }

    MallocMemorySummary::record_malloc(size, flags);
    MallocMemorySummary::record_new_malloc_header(sizeof(MallocHeader));
  }

  inline size_t   size()  const { return _size; }
  inline MEMFLAGS flags() const { return (MEMFLAGS)_flags; }
  bool get_stack(NativeCallStack& stack) const;

  // Cleanup tracking information before the memory is released.
  void release() const;

 private:
  inline void set_size(size_t size) {
    _size = size;
  }
  bool record_malloc_site(const NativeCallStack& stack, size_t size,
    size_t* bucket_idx, size_t* pos_idx, MEMFLAGS flags) const;
};


// Main class called from MemTracker to track malloc activities
class MallocTracker : AllStatic {
 public:
  // Initialize malloc tracker for specific tracking level
  static bool initialize(NMT_TrackingLevel level);

  static bool transition(NMT_TrackingLevel from, NMT_TrackingLevel to);

  // malloc tracking header size for specific tracking level
  static inline size_t malloc_header_size(NMT_TrackingLevel level) {
    return (level == NMT_off) ? 0 : sizeof(MallocHeader);
  }

  // Parameter name convention:
  // memblock :   the beginning address for user data
  // malloc_base: the beginning address that includes malloc tracking header
  //
  // The relationship:
  // memblock = (char*)malloc_base + sizeof(nmt header)
  //

  // Record  malloc on specified memory block
  static void* record_malloc(void* malloc_base, size_t size, MEMFLAGS flags,
    const NativeCallStack& stack, NMT_TrackingLevel level);

  // Record free on specified memory block
  static void* record_free(void* memblock);

  // Offset memory address to header address
  static inline void* get_base(void* memblock);
  static inline void* get_base(void* memblock, NMT_TrackingLevel level) {
    if (memblock == NULL || level == NMT_off) return memblock;
    return (char*)memblock - malloc_header_size(level);
  }

  // Get memory size
  static inline size_t get_size(void* memblock) {
    MallocHeader* header = malloc_header(memblock);
    return header->size();
  }

  // Get memory type
  static inline MEMFLAGS get_flags(void* memblock) {
    MallocHeader* header = malloc_header(memblock);
    return header->flags();
  }

  // Get header size
  static inline size_t get_header_size(void* memblock) {
    return (memblock == NULL) ? 0 : sizeof(MallocHeader);
  }

  static inline void record_new_arena(MEMFLAGS flags) {
    MallocMemorySummary::record_new_arena(flags);
  }

  static inline void record_arena_free(MEMFLAGS flags) {
    MallocMemorySummary::record_arena_free(flags);
  }

  static inline void record_arena_size_change(ssize_t size, MEMFLAGS flags) {
    MallocMemorySummary::record_arena_size_change(size, flags);
  }
 private:
  static inline MallocHeader* malloc_header(void *memblock) {
    assert(memblock != NULL, "NULL pointer");
    MallocHeader* header = (MallocHeader*)((char*)memblock - sizeof(MallocHeader));
    return header;
  }
};

#endif // INCLUDE_NMT


#endif //SHARE_VM_SERVICES_MALLOC_TRACKER_HPP
