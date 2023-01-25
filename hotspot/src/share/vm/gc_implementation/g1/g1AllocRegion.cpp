/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1AllocRegion.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "runtime/orderAccess.inline.hpp"

G1CollectedHeap* G1AllocRegion::_g1h = NULL;
HeapRegion* G1AllocRegion::_dummy_region = NULL;

void G1AllocRegion::setup(G1CollectedHeap* g1h, HeapRegion* dummy_region) {
  assert(_dummy_region == NULL, "should be set once");
  assert(dummy_region != NULL, "pre-condition");
  assert(dummy_region->free() == 0, "pre-condition");

  // Make sure that any allocation attempt on this region will fail
  // and will not trigger any asserts.
  assert(allocate(dummy_region, 1, false) == NULL, "should fail");
  assert(par_allocate(dummy_region, 1, false) == NULL, "should fail");
  assert(allocate(dummy_region, 1, true) == NULL, "should fail");
  assert(par_allocate(dummy_region, 1, true) == NULL, "should fail");

  _g1h = g1h;
  _dummy_region = dummy_region;
}

void G1AllocRegion::fill_up_remaining_space(HeapRegion* alloc_region,
                                            bool bot_updates) {
  assert(alloc_region != NULL && alloc_region != _dummy_region,
         "pre-condition");

  // Other threads might still be trying to allocate using a CAS out
  // of the region we are trying to retire, as they can do so without
  // holding the lock. So, we first have to make sure that noone else
  // can allocate out of it by doing a maximal allocation. Even if our
  // CAS attempt fails a few times, we'll succeed sooner or later
  // given that failed CAS attempts mean that the region is getting
  // closed to being full.
  size_t free_word_size = alloc_region->free() / HeapWordSize;

  // This is the minimum free chunk we can turn into a dummy
  // object. If the free space falls below this, then noone can
  // allocate in this region anyway (all allocation requests will be
  // of a size larger than this) so we won't have to perform the dummy
  // allocation.
  size_t min_word_size_to_fill = CollectedHeap::min_fill_size();

  while (free_word_size >= min_word_size_to_fill) {
    HeapWord* dummy = par_allocate(alloc_region, free_word_size, bot_updates);
    if (dummy != NULL) {
      // If the allocation was successful we should fill in the space.
      CollectedHeap::fill_with_object(dummy, free_word_size);
      alloc_region->set_pre_dummy_top(dummy);
      break;
    }

    free_word_size = alloc_region->free() / HeapWordSize;
    // It's also possible that someone else beats us to the
    // allocation and they fill up the region. In that case, we can
    // just get out of the loop.
  }
  assert(alloc_region->free() / HeapWordSize < min_word_size_to_fill,
         "post-condition");
}

void G1AllocRegion::retire(bool fill_up) {
  assert(_alloc_region != NULL, ar_ext_msg(this, "not initialized properly"));

  trace("retiring");
  HeapRegion* alloc_region = _alloc_region;
  if (alloc_region != _dummy_region) {
    // We never have to check whether the active region is empty or not,
    // and potentially free it if it is, given that it's guaranteed that
    // it will never be empty.
    assert(!alloc_region->is_empty(),
           ar_ext_msg(this, "the alloc region should never be empty"));

    if (fill_up) {
      fill_up_remaining_space(alloc_region, _bot_updates);
    }

    assert(alloc_region->used() >= _used_bytes_before,
           ar_ext_msg(this, "invariant"));
    size_t allocated_bytes = alloc_region->used() - _used_bytes_before;
    retire_region(alloc_region, allocated_bytes);
    _used_bytes_before = 0;
    _alloc_region = _dummy_region;
  }
  trace("retired");
}

HeapWord* G1AllocRegion::new_alloc_region_and_allocate(size_t word_size,
                                                       bool force) {
  assert(_alloc_region == _dummy_region, ar_ext_msg(this, "pre-condition"));
  assert(_used_bytes_before == 0, ar_ext_msg(this, "pre-condition"));

  trace("attempting region allocation");
  HeapRegion* new_alloc_region = allocate_new_region(word_size, force);
  if (new_alloc_region != NULL) {
    new_alloc_region->reset_pre_dummy_top();
    // Need to do this before the allocation
    _used_bytes_before = new_alloc_region->used();
    HeapWord* result = allocate(new_alloc_region, word_size, _bot_updates);
    assert(result != NULL, ar_ext_msg(this, "the allocation should succeeded"));

    OrderAccess::storestore();
    // Note that we first perform the allocation and then we store the
    // region in _alloc_region. This is the reason why an active region
    // can never be empty.
    update_alloc_region(new_alloc_region);
    trace("region allocation successful");
    return result;
  } else {
    trace("region allocation failed");
    return NULL;
  }
  ShouldNotReachHere();
}

void G1AllocRegion::fill_in_ext_msg(ar_ext_msg* msg, const char* message) {
  msg->append("[%s] %s c: %u b: %s r: " PTR_FORMAT " u: " SIZE_FORMAT,
              _name, message, _count, BOOL_TO_STR(_bot_updates),
              p2i(_alloc_region), _used_bytes_before);
}

void G1AllocRegion::init() {
  trace("initializing");
  assert(_alloc_region == NULL && _used_bytes_before == 0,
         ar_ext_msg(this, "pre-condition"));
  assert(_dummy_region != NULL, ar_ext_msg(this, "should have been set"));
  _alloc_region = _dummy_region;
  _count = 0;
  trace("initialized");
}

void G1AllocRegion::set(HeapRegion* alloc_region) {
  trace("setting");
  // We explicitly check that the region is not empty to make sure we
  // maintain the "the alloc region cannot be empty" invariant.
  assert(alloc_region != NULL && !alloc_region->is_empty(),
         ar_ext_msg(this, "pre-condition"));
  assert(_alloc_region == _dummy_region &&
         _used_bytes_before == 0 && _count == 0,
         ar_ext_msg(this, "pre-condition"));

  _used_bytes_before = alloc_region->used();
  _alloc_region = alloc_region;
  _count += 1;
  trace("set");
}

void G1AllocRegion::update_alloc_region(HeapRegion* alloc_region) {
  trace("update");
  // We explicitly check that the region is not empty to make sure we
  // maintain the "the alloc region cannot be empty" invariant.
  assert(alloc_region != NULL && !alloc_region->is_empty(),
         ar_ext_msg(this, "pre-condition"));

  _alloc_region = alloc_region;
  _alloc_region->set_allocation_context(allocation_context());
  _count += 1;
  trace("updated");
}

HeapRegion* G1AllocRegion::release() {
  trace("releasing");
  HeapRegion* alloc_region = _alloc_region;
  retire(false /* fill_up */);
  assert(_alloc_region == _dummy_region,
         ar_ext_msg(this, "post-condition of retire()"));
  _alloc_region = NULL;
  trace("released");
  return (alloc_region == _dummy_region) ? NULL : alloc_region;
}

#if G1_ALLOC_REGION_TRACING
void G1AllocRegion::trace(const char* str, size_t word_size, HeapWord* result) {
  // All the calls to trace that set either just the size or the size
  // and the result are considered part of level 2 tracing and are
  // skipped during level 1 tracing.
  if ((word_size == 0 && result == NULL) || (G1_ALLOC_REGION_TRACING > 1)) {
    const size_t buffer_length = 128;
    char hr_buffer[buffer_length];
    char rest_buffer[buffer_length];

    HeapRegion* alloc_region = _alloc_region;
    if (alloc_region == NULL) {
      jio_snprintf(hr_buffer, buffer_length, "NULL");
    } else if (alloc_region == _dummy_region) {
      jio_snprintf(hr_buffer, buffer_length, "DUMMY");
    } else {
      jio_snprintf(hr_buffer, buffer_length,
                   HR_FORMAT, HR_FORMAT_PARAMS(alloc_region));
    }

    if (G1_ALLOC_REGION_TRACING > 1) {
      if (result != NULL) {
        jio_snprintf(rest_buffer, buffer_length, SIZE_FORMAT " " PTR_FORMAT,
                     word_size, result);
      } else if (word_size != 0) {
        jio_snprintf(rest_buffer, buffer_length, SIZE_FORMAT, word_size);
      } else {
        jio_snprintf(rest_buffer, buffer_length, "");
      }
    } else {
      jio_snprintf(rest_buffer, buffer_length, "");
    }

    tty->print_cr("[%s] %u %s : %s %s",
                  _name, _count, hr_buffer, str, rest_buffer);
  }
}
#endif // G1_ALLOC_REGION_TRACING

G1AllocRegion::G1AllocRegion(const char* name,
                             bool bot_updates)
  : _name(name), _bot_updates(bot_updates),
    _alloc_region(NULL), _count(0), _used_bytes_before(0),
    _allocation_context(AllocationContext::system()) { }


HeapRegion* MutatorAllocRegion::allocate_new_region(size_t word_size,
                                                    bool force) {
  return _g1h->new_mutator_alloc_region(word_size, force);
}

void MutatorAllocRegion::retire_region(HeapRegion* alloc_region,
                                       size_t allocated_bytes) {
  _g1h->retire_mutator_alloc_region(alloc_region, allocated_bytes);
}

HeapRegion* SurvivorGCAllocRegion::allocate_new_region(size_t word_size,
                                                       bool force) {
  assert(!force, "not supported for GC alloc regions");
  return _g1h->new_gc_alloc_region(word_size, count(), InCSetState::Young);
}

void SurvivorGCAllocRegion::retire_region(HeapRegion* alloc_region,
                                          size_t allocated_bytes) {
  _g1h->retire_gc_alloc_region(alloc_region, allocated_bytes, InCSetState::Young);
}

HeapRegion* OldGCAllocRegion::allocate_new_region(size_t word_size,
                                                  bool force) {
  assert(!force, "not supported for GC alloc regions");
  return _g1h->new_gc_alloc_region(word_size, count(), InCSetState::Old);
}

void OldGCAllocRegion::retire_region(HeapRegion* alloc_region,
                                     size_t allocated_bytes) {
  _g1h->retire_gc_alloc_region(alloc_region, allocated_bytes, InCSetState::Old);
}

HeapRegion* OldGCAllocRegion::release() {
  HeapRegion* cur = get();
  if (cur != NULL) {
    // Determine how far we are from the next card boundary. If it is smaller than
    // the minimum object size we can allocate into, expand into the next card.
    HeapWord* top = cur->top();
    HeapWord* aligned_top = (HeapWord*)align_ptr_up(top, G1BlockOffsetSharedArray::N_bytes);

    size_t to_allocate_words = pointer_delta(aligned_top, top, HeapWordSize);

    if (to_allocate_words != 0) {
      // We are not at a card boundary. Fill up, possibly into the next, taking the
      // end of the region and the minimum object size into account.
      to_allocate_words = MIN2(pointer_delta(cur->end(), cur->top(), HeapWordSize),
                               MAX2(to_allocate_words, G1CollectedHeap::min_fill_size()));

      // Skip allocation if there is not enough space to allocate even the smallest
      // possible object. In this case this region will not be retained, so the
      // original problem cannot occur.
      if (to_allocate_words >= G1CollectedHeap::min_fill_size()) {
        HeapWord* dummy = attempt_allocation(to_allocate_words, true /* bot_updates */);
        CollectedHeap::fill_with_object(dummy, to_allocate_words);
      }
    }
  }
  return G1AllocRegion::release();
}


