/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_SPACE_HPP
#define SHARE_VM_MEMORY_SPACE_HPP

#include "memory/allocation.hpp"
#include "memory/blockOffsetTable.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "memory/iterator.hpp"
#include "memory/memRegion.hpp"
#include "memory/watermark.hpp"
#include "oops/markOop.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/macros.hpp"
#include "utilities/workgroup.hpp"

// A space is an abstraction for the "storage units" backing
// up the generation abstraction. It includes specific
// implementations for keeping track of free and used space,
// for iterating over objects and free blocks, etc.

// Here's the Space hierarchy:
//
// - Space               -- an asbtract base class describing a heap area
//   - CompactibleSpace  -- a space supporting compaction
//     - CompactibleFreeListSpace -- (used for CMS generation)
//     - ContiguousSpace -- a compactible space in which all free space
//                          is contiguous
//       - EdenSpace     -- contiguous space used as nursery
//         - ConcEdenSpace -- contiguous space with a 'soft end safe' allocation
//       - OffsetTableContigSpace -- contiguous space with a block offset array
//                          that allows "fast" block_start calls
//         - TenuredSpace -- (used for TenuredGeneration)

// Forward decls.
class Space;
class BlockOffsetArray;
class BlockOffsetArrayContigSpace;
class Generation;
class CompactibleSpace;
class BlockOffsetTable;
class GenRemSet;
class CardTableRS;
class DirtyCardToOopClosure;

// A Space describes a heap area. Class Space is an abstract
// base class.
//
// Space supports allocation, size computation and GC support is provided.
//
// Invariant: bottom() and end() are on page_size boundaries and
// bottom() <= top() <= end()
// top() is inclusive and end() is exclusive.

class Space: public CHeapObj<mtGC> {
  friend class VMStructs;
 protected:
  HeapWord* _bottom;
  HeapWord* _end;

  // Used in support of save_marks()
  HeapWord* _saved_mark_word;

  MemRegionClosure* _preconsumptionDirtyCardClosure;

  // A sequential tasks done structure. This supports
  // parallel GC, where we have threads dynamically
  // claiming sub-tasks from a larger parallel task.
  SequentialSubTasksDone _par_seq_tasks;

  Space():
    _bottom(NULL), _end(NULL), _preconsumptionDirtyCardClosure(NULL) { }

 public:
  // Accessors
  HeapWord* bottom() const         { return _bottom; }
  HeapWord* end() const            { return _end;    }
  virtual void set_bottom(HeapWord* value) { _bottom = value; }
  virtual void set_end(HeapWord* value)    { _end = value; }

  virtual HeapWord* saved_mark_word() const  { return _saved_mark_word; }

  void set_saved_mark_word(HeapWord* p) { _saved_mark_word = p; }

  // Returns true if this object has been allocated since a
  // generation's "save_marks" call.
  virtual bool obj_allocated_since_save_marks(const oop obj) const {
    return (HeapWord*)obj >= saved_mark_word();
  }

  MemRegionClosure* preconsumptionDirtyCardClosure() const {
    return _preconsumptionDirtyCardClosure;
  }
  void setPreconsumptionDirtyCardClosure(MemRegionClosure* cl) {
    _preconsumptionDirtyCardClosure = cl;
  }

  // Returns a subregion of the space containing only the allocated objects in
  // the space.
  virtual MemRegion used_region() const = 0;

  // Returns a region that is guaranteed to contain (at least) all objects
  // allocated at the time of the last call to "save_marks".  If the space
  // initializes its DirtyCardToOopClosure's specifying the "contig" option
  // (that is, if the space is contiguous), then this region must contain only
  // such objects: the memregion will be from the bottom of the region to the
  // saved mark.  Otherwise, the "obj_allocated_since_save_marks" method of
  // the space must distiguish between objects in the region allocated before
  // and after the call to save marks.
  MemRegion used_region_at_save_marks() const {
    return MemRegion(bottom(), saved_mark_word());
  }

  // Initialization.
  // "initialize" should be called once on a space, before it is used for
  // any purpose.  The "mr" arguments gives the bounds of the space, and
  // the "clear_space" argument should be true unless the memory in "mr" is
  // known to be zeroed.
  virtual void initialize(MemRegion mr, bool clear_space, bool mangle_space);

  // The "clear" method must be called on a region that may have
  // had allocation performed in it, but is now to be considered empty.
  virtual void clear(bool mangle_space);

  // For detecting GC bugs.  Should only be called at GC boundaries, since
  // some unused space may be used as scratch space during GC's.
  // Default implementation does nothing. We also call this when expanding
  // a space to satisfy an allocation request. See bug #4668531
  virtual void mangle_unused_area() {}
  virtual void mangle_unused_area_complete() {}
  virtual void mangle_region(MemRegion mr) {}

  // Testers
  bool is_empty() const              { return used() == 0; }
  bool not_empty() const             { return used() > 0; }

  // Returns true iff the given the space contains the
  // given address as part of an allocated object. For
  // ceratin kinds of spaces, this might be a potentially
  // expensive operation. To prevent performance problems
  // on account of its inadvertent use in product jvm's,
  // we restrict its use to assertion checks only.
  bool is_in(const void* p) const {
    return used_region().contains(p);
  }

  // Returns true iff the given reserved memory of the space contains the
  // given address.
  bool is_in_reserved(const void* p) const { return _bottom <= p && p < _end; }

  // Returns true iff the given block is not allocated.
  virtual bool is_free_block(const HeapWord* p) const = 0;

  // Test whether p is double-aligned
  static bool is_aligned(void* p) {
    return ((intptr_t)p & (sizeof(double)-1)) == 0;
  }

  // Size computations.  Sizes are in bytes.
  size_t capacity()     const { return byte_size(bottom(), end()); }
  virtual size_t used() const = 0;
  virtual size_t free() const = 0;

  // Iterate over all the ref-containing fields of all objects in the
  // space, calling "cl.do_oop" on each.  Fields in objects allocated by
  // applications of the closure are not included in the iteration.
  virtual void oop_iterate(ExtendedOopClosure* cl);

  // Iterate over all objects in the space, calling "cl.do_object" on
  // each.  Objects allocated by applications of the closure are not
  // included in the iteration.
  virtual void object_iterate(ObjectClosure* blk) = 0;
  // Similar to object_iterate() except only iterates over
  // objects whose internal references point to objects in the space.
  virtual void safe_object_iterate(ObjectClosure* blk) = 0;

  // Create and return a new dirty card to oop closure. Can be
  // overriden to return the appropriate type of closure
  // depending on the type of space in which the closure will
  // operate. ResourceArea allocated.
  virtual DirtyCardToOopClosure* new_dcto_cl(ExtendedOopClosure* cl,
                                             CardTableModRefBS::PrecisionStyle precision,
                                             HeapWord* boundary = NULL);

  // If "p" is in the space, returns the address of the start of the
  // "block" that contains "p".  We say "block" instead of "object" since
  // some heaps may not pack objects densely; a chunk may either be an
  // object or a non-object.  If "p" is not in the space, return NULL.
  virtual HeapWord* block_start_const(const void* p) const = 0;

  // The non-const version may have benevolent side effects on the data
  // structure supporting these calls, possibly speeding up future calls.
  // The default implementation, however, is simply to call the const
  // version.
  inline virtual HeapWord* block_start(const void* p);

  // Requires "addr" to be the start of a chunk, and returns its size.
  // "addr + size" is required to be the start of a new chunk, or the end
  // of the active area of the heap.
  virtual size_t block_size(const HeapWord* addr) const = 0;

  // Requires "addr" to be the start of a block, and returns "TRUE" iff
  // the block is an object.
  virtual bool block_is_obj(const HeapWord* addr) const = 0;

  // Requires "addr" to be the start of a block, and returns "TRUE" iff
  // the block is an object and the object is alive.
  virtual bool obj_is_alive(const HeapWord* addr) const;

  // Allocation (return NULL if full).  Assumes the caller has established
  // mutually exclusive access to the space.
  virtual HeapWord* allocate(size_t word_size) = 0;

  // Allocation (return NULL if full).  Enforces mutual exclusion internally.
  virtual HeapWord* par_allocate(size_t word_size) = 0;

  // Mark-sweep-compact support: all spaces can update pointers to objects
  // moving as a part of compaction.
  virtual void adjust_pointers();

  // PrintHeapAtGC support
  virtual void print() const;
  virtual void print_on(outputStream* st) const;
  virtual void print_short() const;
  virtual void print_short_on(outputStream* st) const;


  // Accessor for parallel sequential tasks.
  SequentialSubTasksDone* par_seq_tasks() { return &_par_seq_tasks; }

  // IF "this" is a ContiguousSpace, return it, else return NULL.
  virtual ContiguousSpace* toContiguousSpace() {
    return NULL;
  }

  // Debugging
  virtual void verify() const = 0;
};

// A MemRegionClosure (ResourceObj) whose "do_MemRegion" function applies an
// OopClosure to (the addresses of) all the ref-containing fields that could
// be modified by virtue of the given MemRegion being dirty. (Note that
// because of the imprecise nature of the write barrier, this may iterate
// over oops beyond the region.)
// This base type for dirty card to oop closures handles memory regions
// in non-contiguous spaces with no boundaries, and should be sub-classed
// to support other space types. See ContiguousDCTOC for a sub-class
// that works with ContiguousSpaces.

class DirtyCardToOopClosure: public MemRegionClosureRO {
protected:
  ExtendedOopClosure* _cl;
  Space* _sp;
  CardTableModRefBS::PrecisionStyle _precision;
  HeapWord* _boundary;          // If non-NULL, process only non-NULL oops
                                // pointing below boundary.
  HeapWord* _min_done;          // ObjHeadPreciseArray precision requires
                                // a downwards traversal; this is the
                                // lowest location already done (or,
                                // alternatively, the lowest address that
                                // shouldn't be done again.  NULL means infinity.)
  NOT_PRODUCT(HeapWord* _last_bottom;)
  NOT_PRODUCT(HeapWord* _last_explicit_min_done;)

  // Get the actual top of the area on which the closure will
  // operate, given where the top is assumed to be (the end of the
  // memory region passed to do_MemRegion) and where the object
  // at the top is assumed to start. For example, an object may
  // start at the top but actually extend past the assumed top,
  // in which case the top becomes the end of the object.
  virtual HeapWord* get_actual_top(HeapWord* top, HeapWord* top_obj);

  // Walk the given memory region from bottom to (actual) top
  // looking for objects and applying the oop closure (_cl) to
  // them. The base implementation of this treats the area as
  // blocks, where a block may or may not be an object. Sub-
  // classes should override this to provide more accurate
  // or possibly more efficient walking.
  virtual void walk_mem_region(MemRegion mr, HeapWord* bottom, HeapWord* top);

public:
  DirtyCardToOopClosure(Space* sp, ExtendedOopClosure* cl,
                        CardTableModRefBS::PrecisionStyle precision,
                        HeapWord* boundary) :
    _sp(sp), _cl(cl), _precision(precision), _boundary(boundary),
    _min_done(NULL) {
    NOT_PRODUCT(_last_bottom = NULL);
    NOT_PRODUCT(_last_explicit_min_done = NULL);
  }

  void do_MemRegion(MemRegion mr);

  void set_min_done(HeapWord* min_done) {
    _min_done = min_done;
    NOT_PRODUCT(_last_explicit_min_done = _min_done);
  }
#ifndef PRODUCT
  void set_last_bottom(HeapWord* last_bottom) {
    _last_bottom = last_bottom;
  }
#endif
};

// A structure to represent a point at which objects are being copied
// during compaction.
class CompactPoint : public StackObj {
public:
  Generation* gen;
  CompactibleSpace* space;
  HeapWord* threshold;

  CompactPoint(Generation* g = NULL) :
    gen(g), space(NULL), threshold(0) {}
};

// A space that supports compaction operations.  This is usually, but not
// necessarily, a space that is normally contiguous.  But, for example, a
// free-list-based space whose normal collection is a mark-sweep without
// compaction could still support compaction in full GC's.

class CompactibleSpace: public Space {
  friend class VMStructs;
  friend class CompactibleFreeListSpace;
private:
  HeapWord* _compaction_top;
  CompactibleSpace* _next_compaction_space;

public:
  CompactibleSpace() :
   _compaction_top(NULL), _next_compaction_space(NULL) {}

  virtual void initialize(MemRegion mr, bool clear_space, bool mangle_space);
  virtual void clear(bool mangle_space);

  // Used temporarily during a compaction phase to hold the value
  // top should have when compaction is complete.
  HeapWord* compaction_top() const { return _compaction_top;    }

  void set_compaction_top(HeapWord* value) {
    assert(value == NULL || (value >= bottom() && value <= end()),
      "should point inside space");
    _compaction_top = value;
  }

  // Perform operations on the space needed after a compaction
  // has been performed.
  virtual void reset_after_compaction() = 0;

  // Returns the next space (in the current generation) to be compacted in
  // the global compaction order.  Also is used to select the next
  // space into which to compact.

  virtual CompactibleSpace* next_compaction_space() const {
    return _next_compaction_space;
  }

  void set_next_compaction_space(CompactibleSpace* csp) {
    _next_compaction_space = csp;
  }

  // MarkSweep support phase2

  // Start the process of compaction of the current space: compute
  // post-compaction addresses, and insert forwarding pointers.  The fields
  // "cp->gen" and "cp->compaction_space" are the generation and space into
  // which we are currently compacting.  This call updates "cp" as necessary,
  // and leaves the "compaction_top" of the final value of
  // "cp->compaction_space" up-to-date.  Offset tables may be updated in
  // this phase as if the final copy had occurred; if so, "cp->threshold"
  // indicates when the next such action should be taken.
  virtual void prepare_for_compaction(CompactPoint* cp);
  // MarkSweep support phase3
  virtual void adjust_pointers();
  // MarkSweep support phase4
  virtual void compact();

  // The maximum percentage of objects that can be dead in the compacted
  // live part of a compacted space ("deadwood" support.)
  virtual size_t allowed_dead_ratio() const { return 0; };

  // Some contiguous spaces may maintain some data structures that should
  // be updated whenever an allocation crosses a boundary.  This function
  // returns the first such boundary.
  // (The default implementation returns the end of the space, so the
  // boundary is never crossed.)
  virtual HeapWord* initialize_threshold() { return end(); }

  // "q" is an object of the given "size" that should be forwarded;
  // "cp" names the generation ("gen") and containing "this" (which must
  // also equal "cp->space").  "compact_top" is where in "this" the
  // next object should be forwarded to.  If there is room in "this" for
  // the object, insert an appropriate forwarding pointer in "q".
  // If not, go to the next compaction space (there must
  // be one, since compaction must succeed -- we go to the first space of
  // the previous generation if necessary, updating "cp"), reset compact_top
  // and then forward.  In either case, returns the new value of "compact_top".
  // If the forwarding crosses "cp->threshold", invokes the "cross_threhold"
  // function of the then-current compaction space, and updates "cp->threshold
  // accordingly".
  virtual HeapWord* forward(oop q, size_t size, CompactPoint* cp,
                    HeapWord* compact_top);

  // Return a size with adjusments as required of the space.
  virtual size_t adjust_object_size_v(size_t size) const { return size; }

protected:
  // Used during compaction.
  HeapWord* _first_dead;
  HeapWord* _end_of_live;

  // Minimum size of a free block.
  virtual size_t minimum_free_block_size() const { return 0; }

  // This the function is invoked when an allocation of an object covering
  // "start" to "end occurs crosses the threshold; returns the next
  // threshold.  (The default implementation does nothing.)
  virtual HeapWord* cross_threshold(HeapWord* start, HeapWord* the_end) {
    return end();
  }

  // Requires "allowed_deadspace_words > 0", that "q" is the start of a
  // free block of the given "word_len", and that "q", were it an object,
  // would not move if forwared.  If the size allows, fill the free
  // block with an object, to prevent excessive compaction.  Returns "true"
  // iff the free region was made deadspace, and modifies
  // "allowed_deadspace_words" to reflect the number of available deadspace
  // words remaining after this operation.
  bool insert_deadspace(size_t& allowed_deadspace_words, HeapWord* q,
                        size_t word_len);
};

class GenSpaceMangler;

// A space in which the free area is contiguous.  It therefore supports
// faster allocation, and compaction.
class ContiguousSpace: public CompactibleSpace {
  friend class OneContigSpaceCardGeneration;
  friend class VMStructs;
 protected:
  HeapWord* _top;
  HeapWord* _concurrent_iteration_safe_limit;
  // A helper for mangling the unused area of the space in debug builds.
  GenSpaceMangler* _mangler;

  GenSpaceMangler* mangler() { return _mangler; }

  // Allocation helpers (return NULL if full).
  inline HeapWord* allocate_impl(size_t word_size, HeapWord* end_value);
  inline HeapWord* par_allocate_impl(size_t word_size, HeapWord* end_value);

 public:
  ContiguousSpace();
  ~ContiguousSpace();

  virtual void initialize(MemRegion mr, bool clear_space, bool mangle_space);
  virtual void clear(bool mangle_space);

  // Accessors
  HeapWord* top() const            { return _top;    }
  void set_top(HeapWord* value)    { _top = value; }

  void set_saved_mark()            { _saved_mark_word = top();    }
  void reset_saved_mark()          { _saved_mark_word = bottom(); }

  WaterMark bottom_mark()     { return WaterMark(this, bottom()); }
  WaterMark top_mark()        { return WaterMark(this, top()); }
  WaterMark saved_mark()      { return WaterMark(this, saved_mark_word()); }
  bool saved_mark_at_top() const { return saved_mark_word() == top(); }

  // In debug mode mangle (write it with a particular bit
  // pattern) the unused part of a space.

  // Used to save the an address in a space for later use during mangling.
  void set_top_for_allocations(HeapWord* v) PRODUCT_RETURN;
  // Used to save the space's current top for later use during mangling.
  void set_top_for_allocations() PRODUCT_RETURN;

  // Mangle regions in the space from the current top up to the
  // previously mangled part of the space.
  void mangle_unused_area() PRODUCT_RETURN;
  // Mangle [top, end)
  void mangle_unused_area_complete() PRODUCT_RETURN;
  // Mangle the given MemRegion.
  void mangle_region(MemRegion mr) PRODUCT_RETURN;

  // Do some sparse checking on the area that should have been mangled.
  void check_mangled_unused_area(HeapWord* limit) PRODUCT_RETURN;
  // Check the complete area that should have been mangled.
  // This code may be NULL depending on the macro DEBUG_MANGLING.
  void check_mangled_unused_area_complete() PRODUCT_RETURN;

  // Size computations: sizes in bytes.
  size_t capacity() const        { return byte_size(bottom(), end()); }
  size_t used() const            { return byte_size(bottom(), top()); }
  size_t free() const            { return byte_size(top(),    end()); }

  virtual bool is_free_block(const HeapWord* p) const;

  // In a contiguous space we have a more obvious bound on what parts
  // contain objects.
  MemRegion used_region() const { return MemRegion(bottom(), top()); }

  // Allocation (return NULL if full)
  virtual HeapWord* allocate(size_t word_size);
  virtual HeapWord* par_allocate(size_t word_size);
  HeapWord* allocate_aligned(size_t word_size);

  // Iteration
  void oop_iterate(ExtendedOopClosure* cl);
  void object_iterate(ObjectClosure* blk);
  // For contiguous spaces this method will iterate safely over objects
  // in the space (i.e., between bottom and top) when at a safepoint.
  void safe_object_iterate(ObjectClosure* blk);

  // Iterate over as many initialized objects in the space as possible,
  // calling "cl.do_object_careful" on each. Return NULL if all objects
  // in the space (at the start of the iteration) were iterated over.
  // Return an address indicating the extent of the iteration in the
  // event that the iteration had to return because of finding an
  // uninitialized object in the space, or if the closure "cl"
  // signaled early termination.
  HeapWord* object_iterate_careful(ObjectClosureCareful* cl);
  HeapWord* concurrent_iteration_safe_limit() {
    assert(_concurrent_iteration_safe_limit <= top(),
           "_concurrent_iteration_safe_limit update missed");
    return _concurrent_iteration_safe_limit;
  }
  // changes the safe limit, all objects from bottom() to the new
  // limit should be properly initialized
  void set_concurrent_iteration_safe_limit(HeapWord* new_limit) {
    assert(new_limit <= top(), "uninitialized objects in the safe range");
    _concurrent_iteration_safe_limit = new_limit;
  }


#if INCLUDE_ALL_GCS
  // In support of parallel oop_iterate.
  #define ContigSpace_PAR_OOP_ITERATE_DECL(OopClosureType, nv_suffix)  \
    void par_oop_iterate(MemRegion mr, OopClosureType* blk);

    ALL_PAR_OOP_ITERATE_CLOSURES(ContigSpace_PAR_OOP_ITERATE_DECL)
  #undef ContigSpace_PAR_OOP_ITERATE_DECL
#endif // INCLUDE_ALL_GCS

  // Compaction support
  virtual void reset_after_compaction() {
    assert(compaction_top() >= bottom() && compaction_top() <= end(), "should point inside space");
    set_top(compaction_top());
    // set new iteration safe limit
    set_concurrent_iteration_safe_limit(compaction_top());
  }

  // Override.
  DirtyCardToOopClosure* new_dcto_cl(ExtendedOopClosure* cl,
                                     CardTableModRefBS::PrecisionStyle precision,
                                     HeapWord* boundary = NULL);

  // Apply "blk->do_oop" to the addresses of all reference fields in objects
  // starting with the _saved_mark_word, which was noted during a generation's
  // save_marks and is required to denote the head of an object.
  // Fields in objects allocated by applications of the closure
  // *are* included in the iteration.
  // Updates _saved_mark_word to point to just after the last object
  // iterated over.
#define ContigSpace_OOP_SINCE_SAVE_MARKS_DECL(OopClosureType, nv_suffix)  \
  void oop_since_save_marks_iterate##nv_suffix(OopClosureType* blk);

  ALL_SINCE_SAVE_MARKS_CLOSURES(ContigSpace_OOP_SINCE_SAVE_MARKS_DECL)
#undef ContigSpace_OOP_SINCE_SAVE_MARKS_DECL

  // Same as object_iterate, but starting from "mark", which is required
  // to denote the start of an object.  Objects allocated by
  // applications of the closure *are* included in the iteration.
  virtual void object_iterate_from(WaterMark mark, ObjectClosure* blk);

  // Very inefficient implementation.
  virtual HeapWord* block_start_const(const void* p) const;
  size_t block_size(const HeapWord* p) const;
  // If a block is in the allocated area, it is an object.
  bool block_is_obj(const HeapWord* p) const { return p < top(); }

  // Addresses for inlined allocation
  HeapWord** top_addr() { return &_top; }
  HeapWord** end_addr() { return &_end; }

  // Overrides for more efficient compaction support.
  void prepare_for_compaction(CompactPoint* cp);

  // PrintHeapAtGC support.
  virtual void print_on(outputStream* st) const;

  // Checked dynamic downcasts.
  virtual ContiguousSpace* toContiguousSpace() {
    return this;
  }

  // Debugging
  virtual void verify() const;

  // Used to increase collection frequency.  "factor" of 0 means entire
  // space.
  void allocate_temporary_filler(int factor);

};


// A dirty card to oop closure that does filtering.
// It knows how to filter out objects that are outside of the _boundary.
class Filtering_DCTOC : public DirtyCardToOopClosure {
protected:
  // Override.
  void walk_mem_region(MemRegion mr,
                       HeapWord* bottom, HeapWord* top);

  // Walk the given memory region, from bottom to top, applying
  // the given oop closure to (possibly) all objects found. The
  // given oop closure may or may not be the same as the oop
  // closure with which this closure was created, as it may
  // be a filtering closure which makes use of the _boundary.
  // We offer two signatures, so the FilteringClosure static type is
  // apparent.
  virtual void walk_mem_region_with_cl(MemRegion mr,
                                       HeapWord* bottom, HeapWord* top,
                                       ExtendedOopClosure* cl) = 0;
  virtual void walk_mem_region_with_cl(MemRegion mr,
                                       HeapWord* bottom, HeapWord* top,
                                       FilteringClosure* cl) = 0;

public:
  Filtering_DCTOC(Space* sp, ExtendedOopClosure* cl,
                  CardTableModRefBS::PrecisionStyle precision,
                  HeapWord* boundary) :
    DirtyCardToOopClosure(sp, cl, precision, boundary) {}
};

// A dirty card to oop closure for contiguous spaces
// (ContiguousSpace and sub-classes).
// It is a FilteringClosure, as defined above, and it knows:
//
// 1. That the actual top of any area in a memory region
//    contained by the space is bounded by the end of the contiguous
//    region of the space.
// 2. That the space is really made up of objects and not just
//    blocks.

class ContiguousSpaceDCTOC : public Filtering_DCTOC {
protected:
  // Overrides.
  HeapWord* get_actual_top(HeapWord* top, HeapWord* top_obj);

  virtual void walk_mem_region_with_cl(MemRegion mr,
                                       HeapWord* bottom, HeapWord* top,
                                       ExtendedOopClosure* cl);
  virtual void walk_mem_region_with_cl(MemRegion mr,
                                       HeapWord* bottom, HeapWord* top,
                                       FilteringClosure* cl);

public:
  ContiguousSpaceDCTOC(ContiguousSpace* sp, ExtendedOopClosure* cl,
                       CardTableModRefBS::PrecisionStyle precision,
                       HeapWord* boundary) :
    Filtering_DCTOC(sp, cl, precision, boundary)
  {}
};


// Class EdenSpace describes eden-space in new generation.

class DefNewGeneration;

class EdenSpace : public ContiguousSpace {
  friend class VMStructs;
 private:
  DefNewGeneration* _gen;

  // _soft_end is used as a soft limit on allocation.  As soft limits are
  // reached, the slow-path allocation code can invoke other actions and then
  // adjust _soft_end up to a new soft limit or to end().
  HeapWord* _soft_end;

 public:
  EdenSpace(DefNewGeneration* gen) :
   _gen(gen), _soft_end(NULL) {}

  // Get/set just the 'soft' limit.
  HeapWord* soft_end()               { return _soft_end; }
  HeapWord** soft_end_addr()         { return &_soft_end; }
  void set_soft_end(HeapWord* value) { _soft_end = value; }

  // Override.
  void clear(bool mangle_space);

  // Set both the 'hard' and 'soft' limits (_end and _soft_end).
  void set_end(HeapWord* value) {
    set_soft_end(value);
    ContiguousSpace::set_end(value);
  }

  // Allocation (return NULL if full)
  HeapWord* allocate(size_t word_size);
  HeapWord* par_allocate(size_t word_size);
};

// Class ConcEdenSpace extends EdenSpace for the sake of safe
// allocation while soft-end is being modified concurrently

class ConcEdenSpace : public EdenSpace {
 public:
  ConcEdenSpace(DefNewGeneration* gen) : EdenSpace(gen) { }

  // Allocation (return NULL if full)
  HeapWord* par_allocate(size_t word_size);
};


// A ContigSpace that Supports an efficient "block_start" operation via
// a BlockOffsetArray (whose BlockOffsetSharedArray may be shared with
// other spaces.)  This is the abstract base class for old generation
// (tenured) spaces.

class OffsetTableContigSpace: public ContiguousSpace {
  friend class VMStructs;
 protected:
  BlockOffsetArrayContigSpace _offsets;
  Mutex _par_alloc_lock;

 public:
  // Constructor
  OffsetTableContigSpace(BlockOffsetSharedArray* sharedOffsetArray,
                         MemRegion mr);

  void set_bottom(HeapWord* value);
  void set_end(HeapWord* value);

  void clear(bool mangle_space);

  inline HeapWord* block_start_const(const void* p) const;

  // Add offset table update.
  virtual inline HeapWord* allocate(size_t word_size);
  inline HeapWord* par_allocate(size_t word_size);

  // MarkSweep support phase3
  virtual HeapWord* initialize_threshold();
  virtual HeapWord* cross_threshold(HeapWord* start, HeapWord* end);

  virtual void print_on(outputStream* st) const;

  // Debugging
  void verify() const;
};


// Class TenuredSpace is used by TenuredGeneration

class TenuredSpace: public OffsetTableContigSpace {
  friend class VMStructs;
 protected:
  // Mark sweep support
  size_t allowed_dead_ratio() const;
 public:
  // Constructor
  TenuredSpace(BlockOffsetSharedArray* sharedOffsetArray,
               MemRegion mr) :
    OffsetTableContigSpace(sharedOffsetArray, mr) {}
};
#endif // SHARE_VM_MEMORY_SPACE_HPP
