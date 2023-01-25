/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1CARDCOUNTS_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1CARDCOUNTS_HPP

#include "gc_implementation/g1/g1RegionToSpaceMapper.hpp"
#include "memory/allocation.hpp"
#include "runtime/virtualspace.hpp"
#include "utilities/globalDefinitions.hpp"

class CardTableModRefBS;
class G1CardCounts;
class G1CollectedHeap;
class G1RegionToSpaceMapper;
class HeapRegion;

class G1CardCountsMappingChangedListener : public G1MappingChangedListener {
 private:
  G1CardCounts* _counts;
 public:
  void set_cardcounts(G1CardCounts* counts) { _counts = counts; }

  virtual void on_commit(uint start_idx, size_t num_regions, bool zero_filled);
};

// Table to track the number of times a card has been refined. Once
// a card has been refined a certain number of times, it is
// considered 'hot' and its refinement is delayed by inserting the
// card into the hot card cache. The card will then be refined when
// it is evicted from the hot card cache, or when the hot card cache
// is 'drained' during the next evacuation pause.

class G1CardCounts: public CHeapObj<mtGC> {
  G1CardCountsMappingChangedListener _listener;

  G1CollectedHeap* _g1h;

  // The table of counts
  jubyte* _card_counts;

  // Max capacity of the reserved space for the counts table
  size_t _reserved_max_card_num;

  // CardTable bottom.
  const jbyte* _ct_bot;

  // Barrier set
  CardTableModRefBS* _ct_bs;

  // Returns true if the card counts table has been reserved.
  bool has_reserved_count_table() { return _card_counts != NULL; }

  // Returns true if the card counts table has been reserved and committed.
  bool has_count_table() {
    return has_reserved_count_table();
  }

  size_t ptr_2_card_num(const jbyte* card_ptr) {
    assert(card_ptr >= _ct_bot,
           err_msg("Invalid card pointer: "
                   "card_ptr: " PTR_FORMAT ", "
                   "_ct_bot: " PTR_FORMAT,
                   p2i(card_ptr), p2i(_ct_bot)));
    size_t card_num = pointer_delta(card_ptr, _ct_bot, sizeof(jbyte));
    assert(card_num >= 0 && card_num < _reserved_max_card_num,
           err_msg("card pointer out of range: " PTR_FORMAT, p2i(card_ptr)));
    return card_num;
  }

  jbyte* card_num_2_ptr(size_t card_num) {
    assert(card_num >= 0 && card_num < _reserved_max_card_num,
           err_msg("card num out of range: " SIZE_FORMAT, card_num));
    return (jbyte*) (_ct_bot + card_num);
  }

  // Clear the counts table for the given (exclusive) index range.
  void clear_range(size_t from_card_num, size_t to_card_num);

 public:
  G1CardCounts(G1CollectedHeap* g1h);

  void initialize(G1RegionToSpaceMapper* mapper);

  // Increments the refinement count for the given card.
  // Returns the pre-increment count value.
  uint add_card_count(jbyte* card_ptr);

  // Returns true if the given count is high enough to be considered
  // 'hot'; false otherwise.
  bool is_hot(uint count);

  // Clears the card counts for the cards spanned by the region
  void clear_region(HeapRegion* hr);

  // Clears the card counts for the cards spanned by the MemRegion
  void clear_range(MemRegion mr);

  // Clear the entire card counts table during GC.
  void clear_all();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1CARDCOUNTS_HPP
