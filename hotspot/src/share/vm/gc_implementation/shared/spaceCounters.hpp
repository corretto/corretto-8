/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_SHARED_SPACECOUNTERS_HPP
#define SHARE_VM_GC_IMPLEMENTATION_SHARED_SPACECOUNTERS_HPP

#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/shared/generationCounters.hpp"
#include "gc_implementation/shared/immutableSpace.hpp"
#include "gc_implementation/shared/mutableSpace.hpp"
#include "runtime/perfData.hpp"
#endif // INCLUDE_ALL_GCS

// A SpaceCounter is a holder class for performance counters
// that track a space;

class SpaceCounters: public CHeapObj<mtGC> {
  friend class VMStructs;

 private:
  PerfVariable*      _capacity;
  PerfVariable*      _used;

  // Constant PerfData types don't need to retain a reference.
  // However, it's a good idea to document them here.
  // PerfConstant*     _size;

  MutableSpace*     _object_space;
  char*             _name_space;

 public:

  SpaceCounters(const char* name, int ordinal, size_t max_size,
                MutableSpace* m, GenerationCounters* gc);

  ~SpaceCounters() {
    if (_name_space != NULL) FREE_C_HEAP_ARRAY(char, _name_space, mtGC);
  }

  inline void update_capacity() {
    _capacity->set_value(_object_space->capacity_in_bytes());
  }

  inline void update_used() {
    _used->set_value(_object_space->used_in_bytes());
  }

  inline void update_all() {
    update_used();
    update_capacity();
  }

  const char* name_space() const        { return _name_space; }
};

class MutableSpaceUsedHelper: public PerfLongSampleHelper {
  private:
    MutableSpace* _m;

  public:
    MutableSpaceUsedHelper(MutableSpace* m) : _m(m) { }

    inline jlong take_sample() {
      return _m->used_in_bytes();
    }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_SHARED_SPACECOUNTERS_HPP
