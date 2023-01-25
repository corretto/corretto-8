/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_INLINE_HPP

#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1ConcurrentMarkObjArrayProcessor.inline.hpp"

// Utility routine to set an exclusive range of cards on the given
// card liveness bitmap
inline void ConcurrentMark::set_card_bitmap_range(BitMap* card_bm,
                                                  BitMap::idx_t start_idx,
                                                  BitMap::idx_t end_idx,
                                                  bool is_par) {

  // Set the exclusive bit range [start_idx, end_idx).
  assert((end_idx - start_idx) > 0, "at least one card");
  assert(end_idx <= card_bm->size(), "sanity");

  // Silently clip the end index
  end_idx = MIN2(end_idx, card_bm->size());

  // For small ranges use a simple loop; otherwise use set_range or
  // use par_at_put_range (if parallel). The range is made up of the
  // cards that are spanned by an object/mem region so 8 cards will
  // allow up to object sizes up to 4K to be handled using the loop.
  if ((end_idx - start_idx) <= 8) {
    for (BitMap::idx_t i = start_idx; i < end_idx; i += 1) {
      if (is_par) {
        card_bm->par_set_bit(i);
      } else {
        card_bm->set_bit(i);
      }
    }
  } else {
    // Note BitMap::par_at_put_range() and BitMap::set_range() are exclusive.
    if (is_par) {
      card_bm->par_at_put_range(start_idx, end_idx, true);
    } else {
      card_bm->set_range(start_idx, end_idx);
    }
  }
}

// Returns the index in the liveness accounting card bitmap
// for the given address
inline BitMap::idx_t ConcurrentMark::card_bitmap_index_for(HeapWord* addr) {
  // Below, the term "card num" means the result of shifting an address
  // by the card shift -- address 0 corresponds to card number 0.  One
  // must subtract the card num of the bottom of the heap to obtain a
  // card table index.
  intptr_t card_num = intptr_t(uintptr_t(addr) >> CardTableModRefBS::card_shift);
  return card_num - heap_bottom_card_num();
}

// Counts the given memory region in the given task/worker
// counting data structures.
inline void ConcurrentMark::count_region(MemRegion mr, HeapRegion* hr,
                                         size_t* marked_bytes_array,
                                         BitMap* task_card_bm) {
  G1CollectedHeap* g1h = _g1h;
  CardTableModRefBS* ct_bs = g1h->g1_barrier_set();

  HeapWord* start = mr.start();
  HeapWord* end = mr.end();
  size_t region_size_bytes = mr.byte_size();
  uint index = hr->hrm_index();

  assert(!hr->continuesHumongous(), "should not be HC region");
  assert(hr == g1h->heap_region_containing(start), "sanity");
  assert(hr == g1h->heap_region_containing(mr.last()), "sanity");
  assert(marked_bytes_array != NULL, "pre-condition");
  assert(task_card_bm != NULL, "pre-condition");

  // Add to the task local marked bytes for this region.
  marked_bytes_array[index] += region_size_bytes;

  BitMap::idx_t start_idx = card_bitmap_index_for(start);
  BitMap::idx_t end_idx = card_bitmap_index_for(end);

  // Note: if we're looking at the last region in heap - end
  // could be actually just beyond the end of the heap; end_idx
  // will then correspond to a (non-existent) card that is also
  // just beyond the heap.
  if (g1h->is_in_g1_reserved(end) && !ct_bs->is_card_aligned(end)) {
    // end of region is not card aligned - incremement to cover
    // all the cards spanned by the region.
    end_idx += 1;
  }
  // The card bitmap is task/worker specific => no need to use
  // the 'par' BitMap routines.
  // Set bits in the exclusive bit range [start_idx, end_idx).
  set_card_bitmap_range(task_card_bm, start_idx, end_idx, false /* is_par */);
}

// Counts the given memory region in the task/worker counting
// data structures for the given worker id.
inline void ConcurrentMark::count_region(MemRegion mr,
                                         HeapRegion* hr,
                                         uint worker_id) {
  size_t* marked_bytes_array = count_marked_bytes_array_for(worker_id);
  BitMap* task_card_bm = count_card_bitmap_for(worker_id);
  count_region(mr, hr, marked_bytes_array, task_card_bm);
}

// Counts the given object in the given task/worker counting data structures.
inline void ConcurrentMark::count_object(oop obj,
                                         HeapRegion* hr,
                                         size_t* marked_bytes_array,
                                         BitMap* task_card_bm) {
  MemRegion mr((HeapWord*)obj, obj->size());
  count_region(mr, hr, marked_bytes_array, task_card_bm);
}

// Attempts to mark the given object and, if successful, counts
// the object in the given task/worker counting structures.
inline bool ConcurrentMark::par_mark_and_count(oop obj,
                                               HeapRegion* hr,
                                               size_t* marked_bytes_array,
                                               BitMap* task_card_bm) {
  HeapWord* addr = (HeapWord*)obj;
  if (_nextMarkBitMap->parMark(addr)) {
    // Update the task specific count data for the object.
    count_object(obj, hr, marked_bytes_array, task_card_bm);
    return true;
  }
  return false;
}

// Attempts to mark the given object and, if successful, counts
// the object in the task/worker counting structures for the
// given worker id.
inline bool ConcurrentMark::par_mark_and_count(oop obj,
                                               size_t word_size,
                                               HeapRegion* hr,
                                               uint worker_id) {
  HeapWord* addr = (HeapWord*)obj;
  if (_nextMarkBitMap->parMark(addr)) {
    MemRegion mr(addr, word_size);
    count_region(mr, hr, worker_id);
    return true;
  }
  return false;
}

inline bool CMBitMapRO::iterate(BitMapClosure* cl, MemRegion mr) {
  HeapWord* start_addr = MAX2(startWord(), mr.start());
  HeapWord* end_addr = MIN2(endWord(), mr.end());

  if (end_addr > start_addr) {
    // Right-open interval [start-offset, end-offset).
    BitMap::idx_t start_offset = heapWordToOffset(start_addr);
    BitMap::idx_t end_offset = heapWordToOffset(end_addr);

    start_offset = _bm.get_next_one_offset(start_offset, end_offset);
    while (start_offset < end_offset) {
      if (!cl->do_bit(start_offset)) {
        return false;
      }
      HeapWord* next_addr = MIN2(nextObject(offsetToHeapWord(start_offset)), end_addr);
      BitMap::idx_t next_offset = heapWordToOffset(next_addr);
      start_offset = _bm.get_next_one_offset(next_offset, end_offset);
    }
  }
  return true;
}

inline bool CMBitMapRO::iterate(BitMapClosure* cl) {
  MemRegion mr(startWord(), sizeInWords());
  return iterate(cl, mr);
}

#define check_mark(addr)                                                       \
  assert(_bmStartWord <= (addr) && (addr) < (_bmStartWord + _bmWordSize),      \
         "outside underlying space?");                                         \
  assert(G1CollectedHeap::heap()->is_in_exact(addr),                           \
         err_msg("Trying to access not available bitmap " PTR_FORMAT           \
                 " corresponding to " PTR_FORMAT " (%u)",                      \
                 p2i(this), p2i(addr), G1CollectedHeap::heap()->addr_to_region(addr)));

inline void CMBitMap::mark(HeapWord* addr) {
  check_mark(addr);
  _bm.set_bit(heapWordToOffset(addr));
}

inline void CMBitMap::clear(HeapWord* addr) {
  check_mark(addr);
  _bm.clear_bit(heapWordToOffset(addr));
}

inline bool CMBitMap::parMark(HeapWord* addr) {
  check_mark(addr);
  return _bm.par_set_bit(heapWordToOffset(addr));
}

inline bool CMBitMap::parClear(HeapWord* addr) {
  check_mark(addr);
  return _bm.par_clear_bit(heapWordToOffset(addr));
}

#undef check_mark

inline void CMTask::push(oop obj) {
  HeapWord* objAddr = (HeapWord*) obj;
  assert(G1CMObjArrayProcessor::is_array_slice(obj) || _g1h->is_in_g1_reserved(objAddr), "invariant");
  assert(G1CMObjArrayProcessor::is_array_slice(obj) || !_g1h->is_on_master_free_list(
              _g1h->heap_region_containing((HeapWord*) objAddr)), "invariant");
  assert(G1CMObjArrayProcessor::is_array_slice(obj) || !_g1h->is_obj_ill(obj), "invariant");
  assert(G1CMObjArrayProcessor::is_array_slice(obj) || _nextMarkBitMap->isMarked(objAddr), "invariant");

  if (_cm->verbose_high()) {
    gclog_or_tty->print_cr("[%u] pushing " PTR_FORMAT, _worker_id, p2i((void*) obj));
  }

  if (!_task_queue->push(obj)) {
    // The local task queue looks full. We need to push some entries
    // to the global stack.

    if (_cm->verbose_medium()) {
      gclog_or_tty->print_cr("[%u] task queue overflow, "
                             "moving entries to the global stack",
                             _worker_id);
    }
    move_entries_to_global_stack();

    // this should succeed since, even if we overflow the global
    // stack, we should have definitely removed some entries from the
    // local queue. So, there must be space on it.
    bool success = _task_queue->push(obj);
    assert(success, "invariant");
  }

  statsOnly( int tmp_size = _task_queue->size();
             if (tmp_size > _local_max_size) {
               _local_max_size = tmp_size;
             }
             ++_local_pushes );
}

inline bool CMTask::is_below_finger(oop obj, HeapWord* global_finger) const {
  // If obj is above the global finger, then the mark bitmap scan
  // will find it later, and no push is needed.  Similarly, if we have
  // a current region and obj is between the local finger and the
  // end of the current region, then no push is needed.  The tradeoff
  // of checking both vs only checking the global finger is that the
  // local check will be more accurate and so result in fewer pushes,
  // but may also be a little slower.
  HeapWord* objAddr = (HeapWord*)obj;
  if (_finger != NULL) {
    // We have a current region.

    // Finger and region values are all NULL or all non-NULL.  We
    // use _finger to check since we immediately use its value.
    assert(_curr_region != NULL, "invariant");
    assert(_region_limit != NULL, "invariant");
    assert(_region_limit <= global_finger, "invariant");

    // True if obj is less than the local finger, or is between
    // the region limit and the global finger.
    if (objAddr < _finger) {
      return true;
    } else if (objAddr < _region_limit) {
      return false;
    } // Else check global finger.
  }
  // Check global finger.
  return objAddr < global_finger;
}

inline void CMTask::make_reference_grey(oop obj, HeapRegion* hr) {
  if (_cm->par_mark_and_count(obj, hr, _marked_bytes_array, _card_bm)) {

    if (_cm->verbose_high()) {
      gclog_or_tty->print_cr("[%u] marked object " PTR_FORMAT,
                             _worker_id, p2i(obj));
    }

    // No OrderAccess:store_load() is needed. It is implicit in the
    // CAS done in CMBitMap::parMark() call in the routine above.
    HeapWord* global_finger = _cm->finger();

    // We only need to push a newly grey object on the mark
    // stack if it is in a section of memory the mark bitmap
    // scan has already examined.  Mark bitmap scanning
    // maintains progress "fingers" for determining that.
    //
    // Notice that the global finger might be moving forward
    // concurrently. This is not a problem. In the worst case, we
    // mark the object while it is above the global finger and, by
    // the time we read the global finger, it has moved forward
    // past this object. In this case, the object will probably
    // be visited when a task is scanning the region and will also
    // be pushed on the stack. So, some duplicate work, but no
    // correctness problems.
    if (is_below_finger(obj, global_finger)) {
      if (obj->is_typeArray()) {
        // Immediately process arrays of primitive types, rather
        // than pushing on the mark stack.  This keeps us from
        // adding humongous objects to the mark stack that might
        // be reclaimed before the entry is processed - see
        // selection of candidates for eager reclaim of humongous
        // objects.  The cost of the additional type test is
        // mitigated by avoiding a trip through the mark stack,
        // by only doing a bookkeeping update and avoiding the
        // actual scan of the object - a typeArray contains no
        // references, and the metadata is built-in.
        process_grey_object<false>(obj);
      } else {
        if (_cm->verbose_high()) {
          gclog_or_tty->print_cr("[%u] below a finger (local: " PTR_FORMAT
                                 ", global: " PTR_FORMAT ") pushing "
                                 PTR_FORMAT " on mark stack",
                                 _worker_id, p2i(_finger),
                                 p2i(global_finger), p2i(obj));
        }
        push(obj);
      }
    }
  }
}

inline void CMTask::deal_with_reference(oop obj) {
  if (_cm->verbose_high()) {
    gclog_or_tty->print_cr("[%u] we're dealing with reference = " PTR_FORMAT,
                           _worker_id, p2i((void*) obj));
  }

  increment_refs_reached();

  HeapWord* objAddr = (HeapWord*) obj;
  assert(obj->is_oop_or_null(true /* ignore mark word */), "Error");
  if (_g1h->is_in_g1_reserved(objAddr)) {
    assert(obj != NULL, "null check is implicit");
    if (!_nextMarkBitMap->isMarked(objAddr)) {
      // Only get the containing region if the object is not marked on the
      // bitmap (otherwise, it's a waste of time since we won't do
      // anything with it).
      HeapRegion* hr = _g1h->heap_region_containing_raw(obj);
      if (!hr->obj_allocated_since_next_marking(obj)) {
        make_reference_grey(obj, hr);
      }
    }
  }
}

inline size_t CMTask::scan_objArray(objArrayOop obj, MemRegion mr) {
  obj->oop_iterate(_cm_oop_closure, mr);
  return mr.word_size();
}

inline void ConcurrentMark::markPrev(oop p) {
  assert(!_prevMarkBitMap->isMarked((HeapWord*) p), "sanity");
  // Note we are overriding the read-only view of the prev map here, via
  // the cast.
  ((CMBitMap*)_prevMarkBitMap)->mark((HeapWord*) p);
}

inline void ConcurrentMark::grayRoot(oop obj, size_t word_size,
                                     uint worker_id, HeapRegion* hr) {
  assert(obj != NULL, "pre-condition");
  HeapWord* addr = (HeapWord*) obj;
  if (hr == NULL) {
    hr = _g1h->heap_region_containing_raw(addr);
  } else {
    assert(hr->is_in(addr), "pre-condition");
  }
  assert(hr != NULL, "sanity");
  // Given that we're looking for a region that contains an object
  // header it's impossible to get back a HC region.
  assert(!hr->continuesHumongous(), "sanity");

  // We cannot assert that word_size == obj->size() given that obj
  // might not be in a consistent state (another thread might be in
  // the process of copying it). So the best thing we can do is to
  // assert that word_size is under an upper bound which is its
  // containing region's capacity.
  assert(word_size * HeapWordSize <= hr->capacity(),
         err_msg("size: " SIZE_FORMAT " capacity: " SIZE_FORMAT " " HR_FORMAT,
                 word_size * HeapWordSize, hr->capacity(),
                 HR_FORMAT_PARAMS(hr)));

  if (addr < hr->next_top_at_mark_start()) {
    if (!_nextMarkBitMap->isMarked(addr)) {
      par_mark_and_count(obj, word_size, hr, worker_id);
    }
  }
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_INLINE_HPP
