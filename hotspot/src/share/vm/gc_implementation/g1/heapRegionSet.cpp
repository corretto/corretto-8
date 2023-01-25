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
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "gc_implementation/g1/heapRegionSet.inline.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

uint FreeRegionList::_unrealistically_long_length = 0;

void HeapRegionSetBase::fill_in_ext_msg(hrs_ext_msg* msg, const char* message) {
  msg->append("[%s] %s ln: %u cy: " SIZE_FORMAT,
              name(), message, length(), total_capacity_bytes());
  fill_in_ext_msg_extra(msg);
}

#ifndef PRODUCT
void HeapRegionSetBase::verify_region(HeapRegion* hr) {
  assert(hr->containing_set() == this, err_msg("Inconsistent containing set for %u", hr->hrm_index()));
  assert(!hr->is_young(), err_msg("Adding young region %u", hr->hrm_index())); // currently we don't use these sets for young regions
  assert(hr->isHumongous() == regions_humongous(), err_msg("Wrong humongous state for region %u and set %s", hr->hrm_index(), name()));
  assert(hr->is_free() == regions_free(), err_msg("Wrong free state for region %u and set %s", hr->hrm_index(), name()));
  assert(!hr->is_free() || hr->is_empty(), err_msg("Free region %u is not empty for set %s", hr->hrm_index(), name()));
  assert(!hr->is_empty() || hr->is_free(), err_msg("Empty region %u is not free for set %s", hr->hrm_index(), name()));
  assert(hr->rem_set()->verify_ready_for_par_iteration(), err_msg("Wrong iteration state %u", hr->hrm_index()));
}
#endif

void HeapRegionSetBase::verify() {
  // It's important that we also observe the MT safety protocol even
  // for the verification calls. If we do verification without the
  // appropriate locks and the set changes underneath our feet
  // verification might fail and send us on a wild goose chase.
  check_mt_safety();

  guarantee(( is_empty() && length() == 0 && total_capacity_bytes() == 0) ||
            (!is_empty() && length() >= 0 && total_capacity_bytes() >= 0),
            hrs_ext_msg(this, "invariant"));
}

void HeapRegionSetBase::verify_start() {
  // See comment in verify() about MT safety and verification.
  check_mt_safety();
  assert(!_verify_in_progress,
         hrs_ext_msg(this, "verification should not be in progress"));

  // Do the basic verification first before we do the checks over the regions.
  HeapRegionSetBase::verify();

  _verify_in_progress = true;
}

void HeapRegionSetBase::verify_end() {
  // See comment in verify() about MT safety and verification.
  check_mt_safety();
  assert(_verify_in_progress,
         hrs_ext_msg(this, "verification should be in progress"));

  _verify_in_progress = false;
}

void HeapRegionSetBase::print_on(outputStream* out, bool print_contents) {
  out->cr();
  out->print_cr("Set: %s (" PTR_FORMAT ")", name(), this);
  out->print_cr("  Region Assumptions");
  out->print_cr("    humongous         : %s", BOOL_TO_STR(regions_humongous()));
  out->print_cr("    free              : %s", BOOL_TO_STR(regions_free()));
  out->print_cr("  Attributes");
  out->print_cr("    length            : %14u", length());
  out->print_cr("    total capacity    : " SIZE_FORMAT_W(14) " bytes",
                total_capacity_bytes());
}

HeapRegionSetBase::HeapRegionSetBase(const char* name, bool humongous, bool free, HRSMtSafeChecker* mt_safety_checker)
  : _name(name), _verify_in_progress(false),
    _is_humongous(humongous), _is_free(free), _mt_safety_checker(mt_safety_checker),
    _count()
{ }

void FreeRegionList::set_unrealistically_long_length(uint len) {
  guarantee(_unrealistically_long_length == 0, "should only be set once");
  _unrealistically_long_length = len;
}

void FreeRegionList::fill_in_ext_msg_extra(hrs_ext_msg* msg) {
  msg->append(" hd: " PTR_FORMAT " tl: " PTR_FORMAT, _head, _tail);
}

void FreeRegionList::remove_all() {
  check_mt_safety();
  verify_optional();

  HeapRegion* curr = _head;
  while (curr != NULL) {
    verify_region(curr);

    HeapRegion* next = curr->next();
    curr->set_next(NULL);
    curr->set_prev(NULL);
    curr->set_containing_set(NULL);
    curr = next;
  }
  clear();

  verify_optional();
}

void FreeRegionList::add_ordered(FreeRegionList* from_list) {
  check_mt_safety();
  from_list->check_mt_safety();

  verify_optional();
  from_list->verify_optional();

  if (from_list->is_empty()) {
    return;
  }

  #ifdef ASSERT
  FreeRegionListIterator iter(from_list);
  while (iter.more_available()) {
    HeapRegion* hr = iter.get_next();
    // In set_containing_set() we check that we either set the value
    // from NULL to non-NULL or vice versa to catch bugs. So, we have
    // to NULL it first before setting it to the value.
    hr->set_containing_set(NULL);
    hr->set_containing_set(this);
  }
  #endif // ASSERT

  if (is_empty()) {
    assert(length() == 0 && _tail == NULL, hrs_ext_msg(this, "invariant"));
    _head = from_list->_head;
    _tail = from_list->_tail;
  } else {
    HeapRegion* curr_to = _head;
    HeapRegion* curr_from = from_list->_head;

    while (curr_from != NULL) {
      while (curr_to != NULL && curr_to->hrm_index() < curr_from->hrm_index()) {
        curr_to = curr_to->next();
      }

      if (curr_to == NULL) {
        // The rest of the from list should be added as tail
        _tail->set_next(curr_from);
        curr_from->set_prev(_tail);
        curr_from = NULL;
      } else {
        HeapRegion* next_from = curr_from->next();

        curr_from->set_next(curr_to);
        curr_from->set_prev(curr_to->prev());
        if (curr_to->prev() == NULL) {
          _head = curr_from;
        } else {
          curr_to->prev()->set_next(curr_from);
        }
        curr_to->set_prev(curr_from);

        curr_from = next_from;
      }
    }

    if (_tail->hrm_index() < from_list->_tail->hrm_index()) {
      _tail = from_list->_tail;
    }
  }

  _count.increment(from_list->length(), from_list->total_capacity_bytes());
  from_list->clear();

  verify_optional();
  from_list->verify_optional();
}

void FreeRegionList::remove_starting_at(HeapRegion* first, uint num_regions) {
  check_mt_safety();
  assert(num_regions >= 1, hrs_ext_msg(this, "pre-condition"));
  assert(!is_empty(), hrs_ext_msg(this, "pre-condition"));

  verify_optional();
  DEBUG_ONLY(uint old_length = length();)

  HeapRegion* curr = first;
  uint count = 0;
  while (count < num_regions) {
    verify_region(curr);
    HeapRegion* next = curr->next();
    HeapRegion* prev = curr->prev();

    assert(count < num_regions,
           hrs_err_msg("[%s] should not come across more regions "
                       "pending for removal than num_regions: %u",
                       name(), num_regions));

    if (prev == NULL) {
      assert(_head == curr, hrs_ext_msg(this, "invariant"));
      _head = next;
    } else {
      assert(_head != curr, hrs_ext_msg(this, "invariant"));
      prev->set_next(next);
    }
    if (next == NULL) {
      assert(_tail == curr, hrs_ext_msg(this, "invariant"));
      _tail = prev;
    } else {
      assert(_tail != curr, hrs_ext_msg(this, "invariant"));
      next->set_prev(prev);
    }
    if (_last == curr) {
      _last = NULL;
    }

    curr->set_next(NULL);
    curr->set_prev(NULL);
    remove(curr);

    count++;
    curr = next;
  }

  assert(count == num_regions,
         hrs_err_msg("[%s] count: %u should be == num_regions: %u",
                     name(), count, num_regions));
  assert(length() + num_regions == old_length,
         hrs_err_msg("[%s] new length should be consistent "
                     "new length: %u old length: %u num_regions: %u",
                     name(), length(), old_length, num_regions));

  verify_optional();
}

void FreeRegionList::verify() {
  // See comment in HeapRegionSetBase::verify() about MT safety and
  // verification.
  check_mt_safety();

  // This will also do the basic verification too.
  verify_start();

  verify_list();

  verify_end();
}

void FreeRegionList::clear() {
  _count = HeapRegionSetCount();
  _head = NULL;
  _tail = NULL;
  _last = NULL;
}

void FreeRegionList::print_on(outputStream* out, bool print_contents) {
  HeapRegionSetBase::print_on(out, print_contents);
  out->print_cr("  Linking");
  out->print_cr("    head              : " PTR_FORMAT, _head);
  out->print_cr("    tail              : " PTR_FORMAT, _tail);

  if (print_contents) {
    out->print_cr("  Contents");
    FreeRegionListIterator iter(this);
    while (iter.more_available()) {
      HeapRegion* hr = iter.get_next();
      hr->print_on(out);
    }
  }

  out->cr();
}

void FreeRegionList::verify_list() {
  HeapRegion* curr = _head;
  HeapRegion* prev1 = NULL;
  HeapRegion* prev0 = NULL;
  uint count = 0;
  size_t capacity = 0;
  uint last_index = 0;

  guarantee(_head == NULL || _head->prev() == NULL, "_head should not have a prev");
  while (curr != NULL) {
    verify_region(curr);

    count++;
    guarantee(count < _unrealistically_long_length,
        hrs_err_msg("[%s] the calculated length: %u seems very long, is there maybe a cycle? curr: " PTR_FORMAT " prev0: " PTR_FORMAT " " "prev1: " PTR_FORMAT " length: %u", name(), count, curr, prev0, prev1, length()));

    if (curr->next() != NULL) {
      guarantee(curr->next()->prev() == curr, "Next or prev pointers messed up");
    }
    guarantee(curr->hrm_index() == 0 || curr->hrm_index() > last_index, "List should be sorted");
    last_index = curr->hrm_index();

    capacity += curr->capacity();

    prev1 = prev0;
    prev0 = curr;
    curr = curr->next();
  }

  guarantee(_tail == prev0, err_msg("Expected %s to end with %u but it ended with %u.", name(), _tail->hrm_index(), prev0->hrm_index()));
  guarantee(_tail == NULL || _tail->next() == NULL, "_tail should not have a next");
  guarantee(length() == count, err_msg("%s count mismatch. Expected %u, actual %u.", name(), length(), count));
  guarantee(total_capacity_bytes() == capacity, err_msg("%s capacity mismatch. Expected " SIZE_FORMAT ", actual " SIZE_FORMAT,
      name(), total_capacity_bytes(), capacity));
}

// Note on the check_mt_safety() methods below:
//
// Verification of the "master" heap region sets / lists that are
// maintained by G1CollectedHeap is always done during a STW pause and
// by the VM thread at the start / end of the pause. The standard
// verification methods all assert check_mt_safety(). This is
// important as it ensures that verification is done without
// concurrent updates taking place at the same time. It follows, that,
// for the "master" heap region sets / lists, the check_mt_safety()
// method should include the VM thread / STW case.

void MasterFreeRegionListMtSafeChecker::check() {
  // Master Free List MT safety protocol:
  // (a) If we're at a safepoint, operations on the master free list
  // should be invoked by either the VM thread (which will serialize
  // them) or by the GC workers while holding the
  // FreeList_lock.
  // (b) If we're not at a safepoint, operations on the master free
  // list should be invoked while holding the Heap_lock.

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread() ||
              FreeList_lock->owned_by_self(), "master free list MT safety protocol at a safepoint");
  } else {
    guarantee(Heap_lock->owned_by_self(), "master free list MT safety protocol outside a safepoint");
  }
}

void SecondaryFreeRegionListMtSafeChecker::check() {
  // Secondary Free List MT safety protocol:
  // Operations on the secondary free list should always be invoked
  // while holding the SecondaryFreeList_lock.

  guarantee(SecondaryFreeList_lock->owned_by_self(), "secondary free list MT safety protocol");
}

void OldRegionSetMtSafeChecker::check() {
  // Master Old Set MT safety protocol:
  // (a) If we're at a safepoint, operations on the master old set
  // should be invoked:
  // - by the VM thread (which will serialize them), or
  // - by the GC workers while holding the FreeList_lock, if we're
  //   at a safepoint for an evacuation pause (this lock is taken
  //   anyway when an GC alloc region is retired so that a new one
  //   is allocated from the free list), or
  // - by the GC workers while holding the OldSets_lock, if we're at a
  //   safepoint for a cleanup pause.
  // (b) If we're not at a safepoint, operations on the master old set
  // should be invoked while holding the Heap_lock.

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread()
        || FreeList_lock->owned_by_self() || OldSets_lock->owned_by_self(),
        "master old set MT safety protocol at a safepoint");
  } else {
    guarantee(Heap_lock->owned_by_self(), "master old set MT safety protocol outside a safepoint");
  }
}

void HumongousRegionSetMtSafeChecker::check() {
  // Humongous Set MT safety protocol:
  // (a) If we're at a safepoint, operations on the master humongous
  // set should be invoked by either the VM thread (which will
  // serialize them) or by the GC workers while holding the
  // OldSets_lock.
  // (b) If we're not at a safepoint, operations on the master
  // humongous set should be invoked while holding the Heap_lock.

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread() ||
              OldSets_lock->owned_by_self(),
              "master humongous set MT safety protocol at a safepoint");
  } else {
    guarantee(Heap_lock->owned_by_self(),
              "master humongous set MT safety protocol outside a safepoint");
  }
}

void FreeRegionList_test() {
  FreeRegionList l("test");

  const uint num_regions_in_test = 5;
  // Create a fake heap. It does not need to be valid, as the HeapRegion constructor
  // does not access it.
  MemRegion heap(NULL, num_regions_in_test * HeapRegion::GrainWords);
  // Allocate a fake BOT because the HeapRegion constructor initializes
  // the BOT.
  size_t bot_size = G1BlockOffsetSharedArray::compute_size(heap.word_size());
  HeapWord* bot_data = NEW_C_HEAP_ARRAY(HeapWord, bot_size, mtGC);
  ReservedSpace bot_rs(G1BlockOffsetSharedArray::compute_size(heap.word_size()));
  G1RegionToSpaceMapper* bot_storage =
    G1RegionToSpaceMapper::create_mapper(bot_rs,
                                         bot_rs.size(),
                                         os::vm_page_size(),
                                         HeapRegion::GrainBytes,
                                         G1BlockOffsetSharedArray::N_bytes,
                                         mtGC);
  G1BlockOffsetSharedArray oa(heap, bot_storage);
  bot_storage->commit_regions(0, num_regions_in_test);
  HeapRegion hr0(0, &oa, heap);
  HeapRegion hr1(1, &oa, heap);
  HeapRegion hr2(2, &oa, heap);
  HeapRegion hr3(3, &oa, heap);
  HeapRegion hr4(4, &oa, heap);
  l.add_ordered(&hr1);
  l.add_ordered(&hr0);
  l.add_ordered(&hr3);
  l.add_ordered(&hr4);
  l.add_ordered(&hr2);
  assert(l.length() == num_regions_in_test, "wrong length");
  l.verify_list();

  bot_storage->uncommit_regions(0, num_regions_in_test);
  delete bot_storage;
  FREE_C_HEAP_ARRAY(HeapWord, bot_data, mtGC);
}
