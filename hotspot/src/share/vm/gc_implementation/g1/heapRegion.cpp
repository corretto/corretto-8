/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "code/nmethod.hpp"
#include "gc_implementation/g1/g1BlockOffsetTable.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/heapRegion.inline.hpp"
#include "gc_implementation/g1/heapRegionBounds.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "gc_implementation/g1/heapRegionManager.inline.hpp"
#include "gc_implementation/shared/liveRange.hpp"
#include "memory/genOopClosures.inline.hpp"
#include "memory/iterator.hpp"
#include "memory/space.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "gc_implementation/g1/heapRegionTracer.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

int    HeapRegion::LogOfHRGrainBytes = 0;
int    HeapRegion::LogOfHRGrainWords = 0;
size_t HeapRegion::GrainBytes        = 0;
size_t HeapRegion::GrainWords        = 0;
size_t HeapRegion::CardsPerRegion    = 0;

HeapRegionDCTOC::HeapRegionDCTOC(G1CollectedHeap* g1,
                                 HeapRegion* hr,
                                 G1ParPushHeapRSClosure* cl,
                                 CardTableModRefBS::PrecisionStyle precision) :
  DirtyCardToOopClosure(hr, cl, precision, NULL),
  _hr(hr), _rs_scan(cl), _g1(g1) { }

FilterOutOfRegionClosure::FilterOutOfRegionClosure(HeapRegion* r,
                                                   OopClosure* oc) :
  _r_bottom(r->bottom()), _r_end(r->end()), _oc(oc) { }

void HeapRegionDCTOC::walk_mem_region(MemRegion mr,
                                      HeapWord* bottom,
                                      HeapWord* top) {
  G1CollectedHeap* g1h = _g1;
  size_t oop_size;
  HeapWord* cur = bottom;

  // Start filtering what we add to the remembered set. If the object is
  // not considered dead, either because it is marked (in the mark bitmap)
  // or it was allocated after marking finished, then we add it. Otherwise
  // we can safely ignore the object.
  if (!g1h->is_obj_dead(oop(cur), _hr)) {
    oop_size = oop(cur)->oop_iterate(_rs_scan, mr);
  } else {
    oop_size = _hr->block_size(cur);
  }

  cur += oop_size;

  if (cur < top) {
    oop cur_oop = oop(cur);
    oop_size = _hr->block_size(cur);
    HeapWord* next_obj = cur + oop_size;
    while (next_obj < top) {
      // Keep filtering the remembered set.
      if (!g1h->is_obj_dead(cur_oop, _hr)) {
        // Bottom lies entirely below top, so we can call the
        // non-memRegion version of oop_iterate below.
        cur_oop->oop_iterate(_rs_scan);
      }
      cur = next_obj;
      cur_oop = oop(cur);
      oop_size = _hr->block_size(cur);
      next_obj = cur + oop_size;
    }

    // Last object. Need to do dead-obj filtering here too.
    if (!g1h->is_obj_dead(oop(cur), _hr)) {
      oop(cur)->oop_iterate(_rs_scan, mr);
    }
  }
}

size_t HeapRegion::max_region_size() {
  return HeapRegionBounds::max_size();
}

void HeapRegion::setup_heap_region_size(size_t initial_heap_size, size_t max_heap_size) {
  uintx region_size = G1HeapRegionSize;
  if (FLAG_IS_DEFAULT(G1HeapRegionSize)) {
    size_t average_heap_size = (initial_heap_size + max_heap_size) / 2;
    region_size = MAX2(average_heap_size / HeapRegionBounds::target_number(),
                       (uintx) HeapRegionBounds::min_size());
  }

  int region_size_log = log2_long((jlong) region_size);
  // Recalculate the region size to make sure it's a power of
  // 2. This means that region_size is the largest power of 2 that's
  // <= what we've calculated so far.
  region_size = ((uintx)1 << region_size_log);

  // Now make sure that we don't go over or under our limits.
  if (region_size < HeapRegionBounds::min_size()) {
    region_size = HeapRegionBounds::min_size();
  } else if (region_size > HeapRegionBounds::max_size()) {
    region_size = HeapRegionBounds::max_size();
  }

  // And recalculate the log.
  region_size_log = log2_long((jlong) region_size);

  // Now, set up the globals.
  guarantee(LogOfHRGrainBytes == 0, "we should only set it once");
  LogOfHRGrainBytes = region_size_log;

  guarantee(LogOfHRGrainWords == 0, "we should only set it once");
  LogOfHRGrainWords = LogOfHRGrainBytes - LogHeapWordSize;

  guarantee(GrainBytes == 0, "we should only set it once");
  // The cast to int is safe, given that we've bounded region_size by
  // MIN_REGION_SIZE and MAX_REGION_SIZE.
  GrainBytes = (size_t)region_size;

  guarantee(GrainWords == 0, "we should only set it once");
  GrainWords = GrainBytes >> LogHeapWordSize;
  guarantee((size_t) 1 << LogOfHRGrainWords == GrainWords, "sanity");

  guarantee(CardsPerRegion == 0, "we should only set it once");
  CardsPerRegion = GrainBytes >> CardTableModRefBS::card_shift;
}

void HeapRegion::reset_after_compaction() {
  G1OffsetTableContigSpace::reset_after_compaction();
  // After a compaction the mark bitmap is invalid, so we must
  // treat all objects as being inside the unmarked area.
  zero_marked_bytes();
  init_top_at_mark_start();
}

void HeapRegion::hr_clear(bool par, bool clear_space, bool locked) {
  assert(_humongous_start_region == NULL,
         "we should have already filtered out humongous regions");
  assert(_end == _orig_end,
         "we should have already filtered out humongous regions");

  _in_collection_set = false;

  set_allocation_context(AllocationContext::system());
  set_young_index_in_cset(-1);
  uninstall_surv_rate_group();
  set_free();
  reset_pre_dummy_top();

  if (!par) {
    // If this is parallel, this will be done later.
    HeapRegionRemSet* hrrs = rem_set();
    if (locked) {
      hrrs->clear_locked();
    } else {
      hrrs->clear();
    }
    _claimed = InitialClaimValue;
  }
  zero_marked_bytes();

  _offsets.resize(HeapRegion::GrainWords);
  init_top_at_mark_start();
  if (clear_space) clear(SpaceDecorator::Mangle);
}

void HeapRegion::par_clear() {
  assert(used() == 0, "the region should have been already cleared");
  assert(capacity() == HeapRegion::GrainBytes, "should be back to normal");
  HeapRegionRemSet* hrrs = rem_set();
  hrrs->clear();
  CardTableModRefBS* ct_bs =
                   (CardTableModRefBS*)G1CollectedHeap::heap()->barrier_set();
  ct_bs->clear(MemRegion(bottom(), end()));
}

void HeapRegion::calc_gc_efficiency() {
  // GC efficiency is the ratio of how much space would be
  // reclaimed over how long we predict it would take to reclaim it.
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1CollectorPolicy* g1p = g1h->g1_policy();

  // Retrieve a prediction of the elapsed time for this region for
  // a mixed gc because the region will only be evacuated during a
  // mixed gc.
  double region_elapsed_time_ms =
    g1p->predict_region_elapsed_time_ms(this, false /* for_young_gc */);
  _gc_efficiency = (double) reclaimable_bytes() / region_elapsed_time_ms;
}

void HeapRegion::set_free() {
  report_region_type_change(G1HeapRegionTraceType::Free);
  _type.set_free();
}

void HeapRegion::set_eden() {
  report_region_type_change(G1HeapRegionTraceType::Eden);
  _type.set_eden();
}

void HeapRegion::set_eden_pre_gc() {
  report_region_type_change(G1HeapRegionTraceType::Eden);
  _type.set_eden_pre_gc();
}

void HeapRegion::set_survivor() {
  report_region_type_change(G1HeapRegionTraceType::Survivor);
  _type.set_survivor();
}

void HeapRegion::set_old() {
  report_region_type_change(G1HeapRegionTraceType::Old);
  _type.set_old();
}

void HeapRegion::set_startsHumongous(HeapWord* new_top, HeapWord* new_end) {
  assert(!isHumongous(), "sanity / pre-condition");
  assert(end() == _orig_end,
         "Should be normal before the humongous object allocation");
  assert(top() == bottom(), "should be empty");
  assert(bottom() <= new_top && new_top <= new_end, "pre-condition");

  report_region_type_change(G1HeapRegionTraceType::StartsHumongous);
  _type.set_starts_humongous();
  _humongous_start_region = this;

  set_end(new_end);
  _offsets.set_for_starts_humongous(new_top);
}

void HeapRegion::set_continuesHumongous(HeapRegion* first_hr) {
  assert(!isHumongous(), "sanity / pre-condition");
  assert(end() == _orig_end,
         "Should be normal before the humongous object allocation");
  assert(top() == bottom(), "should be empty");
  assert(first_hr->startsHumongous(), "pre-condition");

  report_region_type_change(G1HeapRegionTraceType::ContinuesHumongous);
  _type.set_continues_humongous();
  _humongous_start_region = first_hr;
}

void HeapRegion::clear_humongous() {
  assert(isHumongous(), "pre-condition");

  if (startsHumongous()) {
    assert(top() <= end(), "pre-condition");
    set_end(_orig_end);
    if (top() > end()) {
      // at least one "continues humongous" region after it
      set_top(end());
    }
  } else {
    // continues humongous
    assert(end() == _orig_end, "sanity");
  }

  assert(capacity() == HeapRegion::GrainBytes, "pre-condition");
  _humongous_start_region = NULL;
}

bool HeapRegion::claimHeapRegion(jint claimValue) {
  jint current = _claimed;
  if (current != claimValue) {
    jint res = Atomic::cmpxchg(claimValue, &_claimed, current);
    if (res == current) {
      return true;
    }
  }
  return false;
}

HeapRegion::HeapRegion(uint hrm_index,
                       G1BlockOffsetSharedArray* sharedOffsetArray,
                       MemRegion mr) :
    G1OffsetTableContigSpace(sharedOffsetArray, mr),
    _hrm_index(hrm_index),
    _allocation_context(AllocationContext::system()),
    _humongous_start_region(NULL),
    _in_collection_set(false),
    _next_in_special_set(NULL), _orig_end(NULL),
    _claimed(InitialClaimValue), _evacuation_failed(false),
    _prev_marked_bytes(0), _next_marked_bytes(0), _gc_efficiency(0.0),
    _next_young_region(NULL),
    _next_dirty_cards_region(NULL), _next(NULL), _prev(NULL),
#ifdef ASSERT
    _containing_set(NULL),
#endif // ASSERT
     _young_index_in_cset(-1), _surv_rate_group(NULL), _age_index(-1),
    _rem_set(NULL), _recorded_rs_length(0), _predicted_elapsed_time_ms(0),
    _predicted_bytes_to_copy(0)
{
  _rem_set = new HeapRegionRemSet(sharedOffsetArray, this);
  assert(HeapRegionRemSet::num_par_rem_sets() > 0, "Invariant.");

  initialize(mr);
}

void HeapRegion::initialize(MemRegion mr, bool clear_space, bool mangle_space) {
  assert(_rem_set->is_empty(), "Remembered set must be empty");

  G1OffsetTableContigSpace::initialize(mr, clear_space, mangle_space);

  _orig_end = mr.end();
  hr_clear(false /*par*/, false /*clear_space*/);
  set_top(bottom());
  record_timestamp();
}

void HeapRegion::report_region_type_change(G1HeapRegionTraceType::Type to) {
  HeapRegionTracer::send_region_type_change(_hrm_index,
                                            get_trace_type(),
                                            to,
                                            (uintptr_t)bottom(),
                                            used());
}

CompactibleSpace* HeapRegion::next_compaction_space() const {
  return G1CollectedHeap::heap()->next_compaction_region(this);
}

void HeapRegion::note_self_forwarding_removal_start(bool during_initial_mark,
                                                    bool during_conc_mark) {
  // We always recreate the prev marking info and we'll explicitly
  // mark all objects we find to be self-forwarded on the prev
  // bitmap. So all objects need to be below PTAMS.
  _prev_marked_bytes = 0;

  if (during_initial_mark) {
    // During initial-mark, we'll also explicitly mark all objects
    // we find to be self-forwarded on the next bitmap. So all
    // objects need to be below NTAMS.
    _next_top_at_mark_start = top();
    _next_marked_bytes = 0;
  } else if (during_conc_mark) {
    // During concurrent mark, all objects in the CSet (including
    // the ones we find to be self-forwarded) are implicitly live.
    // So all objects need to be above NTAMS.
    _next_top_at_mark_start = bottom();
    _next_marked_bytes = 0;
  }
}

void HeapRegion::note_self_forwarding_removal_end(bool during_initial_mark,
                                                  bool during_conc_mark,
                                                  size_t marked_bytes) {
  assert(0 <= marked_bytes && marked_bytes <= used(),
         err_msg("marked: " SIZE_FORMAT " used: " SIZE_FORMAT,
                 marked_bytes, used()));
  _prev_top_at_mark_start = top();
  _prev_marked_bytes = marked_bytes;
}

HeapWord*
HeapRegion::object_iterate_mem_careful(MemRegion mr,
                                                 ObjectClosure* cl) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  // We used to use "block_start_careful" here.  But we're actually happy
  // to update the BOT while we do this...
  HeapWord* cur = block_start(mr.start());
  mr = mr.intersection(used_region());
  if (mr.is_empty()) return NULL;
  // Otherwise, find the obj that extends onto mr.start().

  assert(cur <= mr.start()
         && (oop(cur)->klass_or_null() == NULL ||
             cur + oop(cur)->size() > mr.start()),
         "postcondition of block_start");
  oop obj;
  while (cur < mr.end()) {
    obj = oop(cur);
    if (obj->klass_or_null() == NULL) {
      // Ran into an unparseable point.
      return cur;
    } else if (!g1h->is_obj_dead(obj)) {
      cl->do_object(obj);
    }
    if (cl->abort()) return cur;
    // The check above must occur before the operation below, since an
    // abort might invalidate the "size" operation.
    cur += block_size(cur);
  }
  return NULL;
}

// Humongous objects are allocated directly in the old-gen.  Need
// special handling for concurrent processing encountering an
// in-progress allocation.
static bool do_oops_on_card_in_humongous(MemRegion mr,
                                         FilterOutOfRegionClosure* cl,
                                         HeapRegion* hr,
                                         G1CollectedHeap* g1h) {
  assert(hr->isHumongous(), "precondition");
  HeapRegion* sr = hr->humongous_start_region();
  oop obj = oop(sr->bottom());

  // If concurrent and klass_or_null is NULL, then space has been
  // allocated but the object has not yet been published by setting
  // the klass.  That can only happen if the card is stale.  However,
  // we've already set the card clean, so we must return failure,
  // since the allocating thread could have performed a write to the
  // card that might be missed otherwise.
  if (!g1h->is_gc_active() && (obj->klass_or_null_acquire() == NULL)) {
    return false;
  }

  // Only filler objects follow a humongous object in the containing
  // regions, and we can ignore those.  So only process the one
  // humongous object.
  if (!g1h->is_obj_dead(obj, sr)) {
    if (obj->is_objArray() || (sr->bottom() < mr.start())) {
      // objArrays are always marked precisely, so limit processing
      // with mr.  Non-objArrays might be precisely marked, and since
      // it's humongous it's worthwhile avoiding full processing.
      // However, the card could be stale and only cover filler
      // objects.  That should be rare, so not worth checking for;
      // instead let it fall out from the bounded iteration.
      obj->oop_iterate(cl, mr);
    } else {
      // If obj is not an objArray and mr contains the start of the
      // obj, then this could be an imprecise mark, and we need to
      // process the entire object.
      obj->oop_iterate(cl);
    }
  }
  return true;
}

bool HeapRegion::oops_on_card_seq_iterate_careful(MemRegion mr,
                                                  FilterOutOfRegionClosure* cl,
                                                  jbyte* card_ptr) {
  assert(card_ptr != NULL, "pre-condition");
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // If we're within a stop-world GC, then we might look at a card in a
  // GC alloc region that extends onto a GC LAB, which may not be
  // parseable.  Stop such at the "scan_top" of the region.
  if (g1h->is_gc_active()) {
    mr = mr.intersection(MemRegion(bottom(), scan_top()));
  } else {
    mr = mr.intersection(used_region());
  }
  if (mr.is_empty()) {
    return true;
  }

  // The intersection of the incoming mr (for the card) and the
  // allocated part of the region is non-empty. This implies that
  // we have actually allocated into this region. The code in
  // G1CollectedHeap.cpp that allocates a new region sets the
  // is_young tag on the region before allocating. Thus we
  // safely know if this region is young.
  if (is_young()) {
    return true;
  }

  // We can only clean the card here, after we make the decision that
  // the card is not young.
  *card_ptr = CardTableModRefBS::clean_card_val();
  // We must complete this write before we do any of the reads below.
  OrderAccess::storeload();

  // Special handling for humongous regions.
  if (isHumongous()) {
    return do_oops_on_card_in_humongous(mr, cl, this, g1h);
  }

  // During GC we limit mr by scan_top. So we never get here with an
  // mr covering objects allocated during GC.  Non-humongous objects
  // are only allocated in the old-gen during GC.  So the parts of the
  // heap that may be examined here are always parsable; there's no
  // need to use klass_or_null here to detect in-progress allocations.

  // Cache the boundaries of the memory region in some const locals
  HeapWord* const start = mr.start();
  HeapWord* const end = mr.end();

  // Find the obj that extends onto mr.start().
  // Update BOT as needed while finding start of (possibly dead)
  // object containing the start of the region.
  HeapWord* cur = block_start(start);

#ifdef ASSERT
  {
    assert(cur <= start,
           err_msg("cur: " PTR_FORMAT ", start: " PTR_FORMAT, p2i(cur), p2i(start)));
    HeapWord* next = cur + block_size(cur);
    assert(start < next,
           err_msg("start: " PTR_FORMAT ", next: " PTR_FORMAT, p2i(start), p2i(next)));
  }
#endif

  do {
    oop obj = oop(cur);
    assert(obj->is_oop(true), err_msg("Not an oop at " PTR_FORMAT, p2i(cur)));
    assert(obj->klass_or_null() != NULL,
           err_msg("Unparsable heap at " PTR_FORMAT, p2i(cur)));

    if (g1h->is_obj_dead(obj, this)) {
      // Carefully step over dead object.
      cur += block_size(cur);
    } else {
      // Step over live object, and process its references.
      cur += obj->size();
      // Non-objArrays are usually marked imprecise at the object
      // start, in which case we need to iterate over them in full.
      // objArrays are precisely marked, but can still be iterated
      // over in full if completely covered.
      if (!obj->is_objArray() || (((HeapWord*)obj) >= start && cur <= end)) {
        obj->oop_iterate(cl);
      } else {
        obj->oop_iterate(cl, mr);
      }
    }
  } while (cur < end);

  return true;
}

// Code roots support

void HeapRegion::add_strong_code_root(nmethod* nm) {
  HeapRegionRemSet* hrrs = rem_set();
  hrrs->add_strong_code_root(nm);
}

void HeapRegion::add_strong_code_root_locked(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  HeapRegionRemSet* hrrs = rem_set();
  hrrs->add_strong_code_root_locked(nm);
}

void HeapRegion::remove_strong_code_root(nmethod* nm) {
  HeapRegionRemSet* hrrs = rem_set();
  hrrs->remove_strong_code_root(nm);
}

void HeapRegion::strong_code_roots_do(CodeBlobClosure* blk) const {
  HeapRegionRemSet* hrrs = rem_set();
  hrrs->strong_code_roots_do(blk);
}

class VerifyStrongCodeRootOopClosure: public OopClosure {
  const HeapRegion* _hr;
  nmethod* _nm;
  bool _failures;
  bool _has_oops_in_region;

  template <class T> void do_oop_work(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);

      // Note: not all the oops embedded in the nmethod are in the
      // current region. We only look at those which are.
      if (_hr->is_in(obj)) {
        // Object is in the region. Check that its less than top
        if (_hr->top() <= (HeapWord*)obj) {
          // Object is above top
          gclog_or_tty->print_cr("Object " PTR_FORMAT " in region "
                                 "[" PTR_FORMAT ", " PTR_FORMAT ") is above "
                                 "top " PTR_FORMAT,
                                 (void *)obj, _hr->bottom(), _hr->end(), _hr->top());
          _failures = true;
          return;
        }
        // Nmethod has at least one oop in the current region
        _has_oops_in_region = true;
      }
    }
  }

public:
  VerifyStrongCodeRootOopClosure(const HeapRegion* hr, nmethod* nm):
    _hr(hr), _failures(false), _has_oops_in_region(false) {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }

  bool failures()           { return _failures; }
  bool has_oops_in_region() { return _has_oops_in_region; }
};

class VerifyStrongCodeRootCodeBlobClosure: public CodeBlobClosure {
  const HeapRegion* _hr;
  bool _failures;
public:
  VerifyStrongCodeRootCodeBlobClosure(const HeapRegion* hr) :
    _hr(hr), _failures(false) {}

  void do_code_blob(CodeBlob* cb) {
    nmethod* nm = (cb == NULL) ? NULL : cb->as_nmethod_or_null();
    if (nm != NULL) {
      // Verify that the nemthod is live
      if (!nm->is_alive()) {
        gclog_or_tty->print_cr("region [" PTR_FORMAT "," PTR_FORMAT "] has dead nmethod "
                               PTR_FORMAT " in its strong code roots",
                               _hr->bottom(), _hr->end(), nm);
        _failures = true;
      } else {
        VerifyStrongCodeRootOopClosure oop_cl(_hr, nm);
        nm->oops_do(&oop_cl);
        if (!oop_cl.has_oops_in_region()) {
          gclog_or_tty->print_cr("region [" PTR_FORMAT "," PTR_FORMAT "] has nmethod "
                                 PTR_FORMAT " in its strong code roots "
                                 "with no pointers into region",
                                 _hr->bottom(), _hr->end(), nm);
          _failures = true;
        } else if (oop_cl.failures()) {
          gclog_or_tty->print_cr("region [" PTR_FORMAT "," PTR_FORMAT "] has other "
                                 "failures for nmethod " PTR_FORMAT,
                                 _hr->bottom(), _hr->end(), nm);
          _failures = true;
        }
      }
    }
  }

  bool failures()       { return _failures; }
};

void HeapRegion::verify_strong_code_roots(VerifyOption vo, bool* failures) const {
  if (!G1VerifyHeapRegionCodeRoots) {
    // We're not verifying code roots.
    return;
  }
  if (vo == VerifyOption_G1UseMarkWord) {
    // Marking verification during a full GC is performed after class
    // unloading, code cache unloading, etc so the strong code roots
    // attached to each heap region are in an inconsistent state. They won't
    // be consistent until the strong code roots are rebuilt after the
    // actual GC. Skip verifying the strong code roots in this particular
    // time.
    assert(VerifyDuringGC, "only way to get here");
    return;
  }

  HeapRegionRemSet* hrrs = rem_set();
  size_t strong_code_roots_length = hrrs->strong_code_roots_list_length();

  // if this region is empty then there should be no entries
  // on its strong code root list
  if (is_empty()) {
    if (strong_code_roots_length > 0) {
      gclog_or_tty->print_cr("region [" PTR_FORMAT "," PTR_FORMAT "] is empty "
                             "but has " SIZE_FORMAT " code root entries",
                             bottom(), end(), strong_code_roots_length);
      *failures = true;
    }
    return;
  }

  if (continuesHumongous()) {
    if (strong_code_roots_length > 0) {
      gclog_or_tty->print_cr("region " HR_FORMAT " is a continuation of a humongous "
                             "region but has " SIZE_FORMAT " code root entries",
                             HR_FORMAT_PARAMS(this), strong_code_roots_length);
      *failures = true;
    }
    return;
  }

  VerifyStrongCodeRootCodeBlobClosure cb_cl(this);
  strong_code_roots_do(&cb_cl);

  if (cb_cl.failures()) {
    *failures = true;
  }
}

void HeapRegion::print() const { print_on(gclog_or_tty); }
void HeapRegion::print_on(outputStream* st) const {
  st->print("AC%4u", allocation_context());
  st->print(" %2s", get_short_type_str());
  if (in_collection_set())
    st->print(" CS");
  else
    st->print("   ");
  st->print(" TS %5d", _gc_time_stamp);
  st->print(" PTAMS " PTR_FORMAT " NTAMS " PTR_FORMAT,
            prev_top_at_mark_start(), next_top_at_mark_start());
  G1OffsetTableContigSpace::print_on(st);
}

class G1VerificationClosure : public OopClosure {
protected:
  G1CollectedHeap* _g1h;
  CardTableModRefBS* _bs;
  oop _containing_obj;
  bool _failures;
  int _n_failures;
  VerifyOption _vo;
public:
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  G1VerificationClosure(G1CollectedHeap* g1h, VerifyOption vo) :
    _g1h(g1h), _bs(NULL), _containing_obj(NULL),
    _failures(false), _n_failures(0), _vo(vo)
  {
    BarrierSet* bs = _g1h->barrier_set();
    if (bs->is_a(BarrierSet::CardTableModRef))
      _bs = (CardTableModRefBS*)bs;
  }

  void set_containing_obj(oop obj) {
    _containing_obj = obj;
  }

  bool failures() { return _failures; }
  int n_failures() { return _n_failures; }

  void print_object(outputStream* out, oop obj) {
#ifdef PRODUCT
    Klass* k = obj->klass();
    const char* class_name = InstanceKlass::cast(k)->external_name();
    out->print_cr("class name %s", class_name);
#else // PRODUCT
    obj->print_on(out);
#endif // PRODUCT
  }
};

class VerifyLiveClosure : public G1VerificationClosure {
public:
  VerifyLiveClosure(G1CollectedHeap* g1h, VerifyOption vo) : G1VerificationClosure(g1h, vo) {}
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p) { do_oop_work(p); }

  template <class T>
  void do_oop_work(T* p) {
    assert(_containing_obj != NULL, "Precondition");
    assert(!_g1h->is_obj_dead_cond(_containing_obj, _vo),
      "Precondition");
    verify_liveness(p);
  }

  template <class T>
  void verify_liveness(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      bool failed = false;
      if (!_g1h->is_in_closed_subset(obj) || _g1h->is_obj_dead_cond(obj, _vo)) {
        MutexLockerEx x(ParGCRareEvent_lock,
          Mutex::_no_safepoint_check_flag);

        if (!_failures) {
          gclog_or_tty->cr();
          gclog_or_tty->print_cr("----------");
        }
        if (!_g1h->is_in_closed_subset(obj)) {
          HeapRegion* from = _g1h->heap_region_containing((HeapWord*)p);
          gclog_or_tty->print_cr("Field " PTR_FORMAT
                                 " of live obj " PTR_FORMAT " in region "
                                 "[" PTR_FORMAT ", " PTR_FORMAT ")",
                                 p, (void*) _containing_obj,
                                 from->bottom(), from->end());
          print_object(gclog_or_tty, _containing_obj);
          gclog_or_tty->print_cr("points to obj " PTR_FORMAT " not in the heap",
                                 (void*) obj);
        } else {
          HeapRegion* from = _g1h->heap_region_containing((HeapWord*)p);
          HeapRegion* to   = _g1h->heap_region_containing((HeapWord*)obj);
          gclog_or_tty->print_cr("Field " PTR_FORMAT
                                 " of live obj " PTR_FORMAT " in region "
                                 "[" PTR_FORMAT ", " PTR_FORMAT ")",
                                 p, (void*) _containing_obj,
                                 from->bottom(), from->end());
          print_object(gclog_or_tty, _containing_obj);
          gclog_or_tty->print_cr("points to dead obj " PTR_FORMAT " in region "
                                 "[" PTR_FORMAT ", " PTR_FORMAT ")",
                                 (void*) obj, to->bottom(), to->end());
          print_object(gclog_or_tty, obj);
        }
        gclog_or_tty->print_cr("----------");
        gclog_or_tty->flush();
        _failures = true;
        failed = true;
        _n_failures++;
      }
    }
  }
};

class VerifyRemSetClosure : public G1VerificationClosure {
public:
  VerifyRemSetClosure(G1CollectedHeap* g1h, VerifyOption vo) : G1VerificationClosure(g1h, vo) {}
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p) { do_oop_work(p); }

  template <class T>
  void do_oop_work(T* p) {
    assert(_containing_obj != NULL, "Precondition");
    assert(!_g1h->is_obj_dead_cond(_containing_obj, _vo),
      "Precondition");
    verify_remembered_set(p);
  }

  template <class T>
  void verify_remembered_set(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      bool failed = false;
      HeapRegion* from = _g1h->heap_region_containing((HeapWord*)p);
      HeapRegion* to   = _g1h->heap_region_containing(obj);
      if (from != NULL && to != NULL &&
          from != to &&
          !to->isHumongous()) {
        jbyte cv_obj = *_bs->byte_for_const(_containing_obj);
        jbyte cv_field = *_bs->byte_for_const(p);
        const jbyte dirty = CardTableModRefBS::dirty_card_val();

        bool is_bad = !(from->is_young()
                        || to->rem_set()->contains_reference(p)
                        || !G1HRRSFlushLogBuffersOnVerify && // buffers were not flushed
                            (_containing_obj->is_objArray() ?
                                cv_field == dirty
                             : cv_obj == dirty || cv_field == dirty));
        if (is_bad) {
          MutexLockerEx x(ParGCRareEvent_lock,
                          Mutex::_no_safepoint_check_flag);

          if (!_failures) {
            gclog_or_tty->cr();
            gclog_or_tty->print_cr("----------");
          }
          gclog_or_tty->print_cr("Missing rem set entry:");
          gclog_or_tty->print_cr("Field " PTR_FORMAT " "
                                 "of obj " PTR_FORMAT ", "
                                 "in region " HR_FORMAT,
                                 p, (void*) _containing_obj,
                                 HR_FORMAT_PARAMS(from));
          _containing_obj->print_on(gclog_or_tty);
          gclog_or_tty->print_cr("points to obj " PTR_FORMAT " "
                                 "in region " HR_FORMAT,
                                 (void*) obj,
                                 HR_FORMAT_PARAMS(to));
          if (obj->is_oop()) {
            obj->print_on(gclog_or_tty);
          }
          gclog_or_tty->print_cr("Obj head CTE = %d, field CTE = %d.",
                        cv_obj, cv_field);
          gclog_or_tty->print_cr("----------");
          gclog_or_tty->flush();
          _failures = true;
          if (!failed) _n_failures++;
        }
      }
    }
  }
};

// This really ought to be commoned up into OffsetTableContigSpace somehow.
// We would need a mechanism to make that code skip dead objects.

void HeapRegion::verify(VerifyOption vo,
                        bool* failures) const {
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  *failures = false;
  HeapWord* p = bottom();
  HeapWord* prev_p = NULL;
  VerifyLiveClosure vl_cl(g1, vo);
  VerifyRemSetClosure vr_cl(g1, vo);
  bool is_humongous = isHumongous();
  bool do_bot_verify = !is_young();
  size_t object_num = 0;
  while (p < top()) {
    oop obj = oop(p);
    size_t obj_size = block_size(p);
    object_num += 1;

    if (is_humongous != g1->isHumongous(obj_size) &&
        !g1->is_obj_dead(obj, this)) { // Dead objects may have bigger block_size since they span several objects.
      gclog_or_tty->print_cr("obj " PTR_FORMAT " is of %shumongous size ("
                             SIZE_FORMAT " words) in a %shumongous region",
                             p, g1->isHumongous(obj_size) ? "" : "non-",
                             obj_size, is_humongous ? "" : "non-");
       *failures = true;
       return;
    }

    // If it returns false, verify_for_object() will output the
    // appropriate message.
    if (do_bot_verify &&
        !g1->is_obj_dead(obj, this) &&
        !_offsets.verify_for_object(p, obj_size)) {
      *failures = true;
      return;
    }

    if (!g1->is_obj_dead_cond(obj, this, vo)) {
      if (obj->is_oop()) {
        Klass* klass = obj->klass();
        bool is_metaspace_object = Metaspace::contains(klass) ||
                                   (vo == VerifyOption_G1UsePrevMarking &&
                                   ClassLoaderDataGraph::unload_list_contains(klass));
        if (!is_metaspace_object) {
          gclog_or_tty->print_cr("klass " PTR_FORMAT " of object " PTR_FORMAT " "
                                 "not metadata", klass, (void *)obj);
          *failures = true;
          return;
        } else if (!klass->is_klass()) {
          gclog_or_tty->print_cr("klass " PTR_FORMAT " of object " PTR_FORMAT " "
                                 "not a klass", klass, (void *)obj);
          *failures = true;
          return;
        } else {
          vl_cl.set_containing_obj(obj);
          if (!g1->full_collection() || G1VerifyRSetsDuringFullGC) {
            // verify liveness and rem_set
            vr_cl.set_containing_obj(obj);
            G1Mux2Closure mux(&vl_cl, &vr_cl);
            obj->oop_iterate_no_header(&mux);

            if (vr_cl.failures()) {
              *failures = true;
            }
            if (G1MaxVerifyFailures >= 0 &&
              vr_cl.n_failures() >= G1MaxVerifyFailures) {
              return;
            }
          } else {
            // verify only liveness
            obj->oop_iterate_no_header(&vl_cl);
          }
          if (vl_cl.failures()) {
            *failures = true;
          }
          if (G1MaxVerifyFailures >= 0 &&
              vl_cl.n_failures() >= G1MaxVerifyFailures) {
            return;
          }
        }
      } else {
        gclog_or_tty->print_cr(PTR_FORMAT " not an oop", (void *)obj);
        *failures = true;
        return;
      }
    }
    prev_p = p;
    p += obj_size;
  }

  if (p != top()) {
    gclog_or_tty->print_cr("end of last object " PTR_FORMAT " "
                           "does not match top " PTR_FORMAT, p, top());
    *failures = true;
    return;
  }

  HeapWord* the_end = end();
  assert(p == top(), "it should still hold");
  // Do some extra BOT consistency checking for addresses in the
  // range [top, end). BOT look-ups in this range should yield
  // top. No point in doing that if top == end (there's nothing there).
  if (p < the_end) {
    // Look up top
    HeapWord* addr_1 = p;
    HeapWord* b_start_1 = _offsets.block_start_const(addr_1);
    if (b_start_1 != p) {
      gclog_or_tty->print_cr("BOT look up for top: " PTR_FORMAT " "
                             " yielded " PTR_FORMAT ", expecting " PTR_FORMAT,
                             addr_1, b_start_1, p);
      *failures = true;
      return;
    }

    // Look up top + 1
    HeapWord* addr_2 = p + 1;
    if (addr_2 < the_end) {
      HeapWord* b_start_2 = _offsets.block_start_const(addr_2);
      if (b_start_2 != p) {
        gclog_or_tty->print_cr("BOT look up for top + 1: " PTR_FORMAT " "
                               " yielded " PTR_FORMAT ", expecting " PTR_FORMAT,
                               addr_2, b_start_2, p);
        *failures = true;
        return;
      }
    }

    // Look up an address between top and end
    size_t diff = pointer_delta(the_end, p) / 2;
    HeapWord* addr_3 = p + diff;
    if (addr_3 < the_end) {
      HeapWord* b_start_3 = _offsets.block_start_const(addr_3);
      if (b_start_3 != p) {
        gclog_or_tty->print_cr("BOT look up for top + diff: " PTR_FORMAT " "
                               " yielded " PTR_FORMAT ", expecting " PTR_FORMAT,
                               addr_3, b_start_3, p);
        *failures = true;
        return;
      }
    }

    // Loook up end - 1
    HeapWord* addr_4 = the_end - 1;
    HeapWord* b_start_4 = _offsets.block_start_const(addr_4);
    if (b_start_4 != p) {
      gclog_or_tty->print_cr("BOT look up for end - 1: " PTR_FORMAT " "
                             " yielded " PTR_FORMAT ", expecting " PTR_FORMAT,
                             addr_4, b_start_4, p);
      *failures = true;
      return;
    }
  }

  if (is_humongous && object_num > 1) {
    gclog_or_tty->print_cr("region [" PTR_FORMAT "," PTR_FORMAT "] is humongous "
                           "but has " SIZE_FORMAT ", objects",
                           bottom(), end(), object_num);
    *failures = true;
    return;
  }

  verify_strong_code_roots(vo, failures);
}

void HeapRegion::verify() const {
  bool dummy = false;
  verify(VerifyOption_G1UsePrevMarking, /* failures */ &dummy);
}

void HeapRegion::verify_rem_set(VerifyOption vo, bool* failures) const {
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  *failures = false;
  HeapWord* p = bottom();
  HeapWord* prev_p = NULL;
  VerifyRemSetClosure vr_cl(g1, vo);
  while (p < top()) {
    oop obj = oop(p);
    size_t obj_size = block_size(p);

    if (!g1->is_obj_dead_cond(obj, this, vo)) {
      if (obj->is_oop()) {
        vr_cl.set_containing_obj(obj);
        obj->oop_iterate_no_header(&vr_cl);

        if (vr_cl.failures()) {
          *failures = true;
        }
        if (G1MaxVerifyFailures >= 0 &&
          vr_cl.n_failures() >= G1MaxVerifyFailures) {
          return;
        }
      } else {
        gclog_or_tty->print_cr(PTR_FORMAT " not an oop", p2i(obj));
        *failures = true;
        return;
      }
    }

    prev_p = p;
    p += obj_size;
  }
}

void HeapRegion::verify_rem_set() const {
  bool failures = false;
  verify_rem_set(VerifyOption_G1UsePrevMarking, &failures);
  guarantee(!failures, "HeapRegion RemSet verification failed");
}

// G1OffsetTableContigSpace code; copied from space.cpp.  Hope this can go
// away eventually.

void G1OffsetTableContigSpace::clear(bool mangle_space) {
  set_top(bottom());
  _scan_top = bottom();
  CompactibleSpace::clear(mangle_space);
  reset_bot();
}

void G1OffsetTableContigSpace::set_bottom(HeapWord* new_bottom) {
  Space::set_bottom(new_bottom);
  _offsets.set_bottom(new_bottom);
}

void G1OffsetTableContigSpace::set_end(HeapWord* new_end) {
  Space::set_end(new_end);
  _offsets.resize(new_end - bottom());
}

void G1OffsetTableContigSpace::print() const {
  print_short();
  gclog_or_tty->print_cr(" [" INTPTR_FORMAT ", " INTPTR_FORMAT ", "
                INTPTR_FORMAT ", " INTPTR_FORMAT ")",
                bottom(), top(), _offsets.threshold(), end());
}

HeapWord* G1OffsetTableContigSpace::initialize_threshold() {
  return _offsets.initialize_threshold();
}

HeapWord* G1OffsetTableContigSpace::cross_threshold(HeapWord* start,
                                                    HeapWord* end) {
  _offsets.alloc_block(start, end);
  return _offsets.threshold();
}

HeapWord* G1OffsetTableContigSpace::scan_top() const {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  HeapWord* local_top = top();
  OrderAccess::loadload();
  const unsigned local_time_stamp = _gc_time_stamp;
  assert(local_time_stamp <= g1h->get_gc_time_stamp(), "invariant");
  if (local_time_stamp < g1h->get_gc_time_stamp()) {
    return local_top;
  } else {
    return _scan_top;
  }
}

void G1OffsetTableContigSpace::record_timestamp() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  unsigned curr_gc_time_stamp = g1h->get_gc_time_stamp();

  if (_gc_time_stamp < curr_gc_time_stamp) {
    // Setting the time stamp here tells concurrent readers to look at
    // scan_top to know the maximum allowed address to look at.

    // scan_top should be bottom for all regions except for the
    // retained old alloc region which should have scan_top == top
    HeapWord* st = _scan_top;
    guarantee(st == _bottom || st == _top, "invariant");

    _gc_time_stamp = curr_gc_time_stamp;
  }
}

void G1OffsetTableContigSpace::record_retained_region() {
  // scan_top is the maximum address where it's safe for the next gc to
  // scan this region.
  _scan_top = top();
}

void G1OffsetTableContigSpace::safe_object_iterate(ObjectClosure* blk) {
  object_iterate(blk);
}

void G1OffsetTableContigSpace::object_iterate(ObjectClosure* blk) {
  HeapWord* p = bottom();
  while (p < top()) {
    if (block_is_obj(p)) {
      blk->do_object(oop(p));
    }
    p += block_size(p);
  }
}

#define block_is_always_obj(q) true
void G1OffsetTableContigSpace::prepare_for_compaction(CompactPoint* cp) {
  SCAN_AND_FORWARD(cp, top, block_is_always_obj, block_size);
}
#undef block_is_always_obj

G1OffsetTableContigSpace::
G1OffsetTableContigSpace(G1BlockOffsetSharedArray* sharedOffsetArray,
                         MemRegion mr) :
  _offsets(sharedOffsetArray, mr),
  _par_alloc_lock(Mutex::leaf, "OffsetTableContigSpace par alloc lock", true),
  _gc_time_stamp(0)
{
  _offsets.set_space(this);
}

void G1OffsetTableContigSpace::initialize(MemRegion mr, bool clear_space, bool mangle_space) {
  CompactibleSpace::initialize(mr, clear_space, mangle_space);
  _top = bottom();
  _scan_top = bottom();
  set_saved_mark_word(NULL);
  reset_bot();
}

