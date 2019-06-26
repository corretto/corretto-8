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

#include "precompiled.hpp"
#include "gc_implementation/g1/g1BlockOffsetTable.inline.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "memory/space.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "services/memTracker.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

//////////////////////////////////////////////////////////////////////
// G1BlockOffsetSharedArray
//////////////////////////////////////////////////////////////////////

G1BlockOffsetSharedArray::G1BlockOffsetSharedArray(MemRegion heap, G1RegionToSpaceMapper* storage) :
  _reserved(), _end(NULL), _listener(), _offset_array(NULL) {

  _reserved = heap;
  _end = NULL;

  MemRegion bot_reserved = storage->reserved();

  _offset_array = (u_char*)bot_reserved.start();
  _end = _reserved.end();

  storage->set_mapping_changed_listener(&_listener);

  if (TraceBlockOffsetTable) {
    gclog_or_tty->print_cr("G1BlockOffsetSharedArray::G1BlockOffsetSharedArray: ");
    gclog_or_tty->print_cr("  "
                  "  rs.base(): " INTPTR_FORMAT
                  "  rs.size(): " INTPTR_FORMAT
                  "  rs end(): " INTPTR_FORMAT,
                  bot_reserved.start(), bot_reserved.byte_size(), bot_reserved.end());
  }
}

bool G1BlockOffsetSharedArray::is_card_boundary(HeapWord* p) const {
  assert(p >= _reserved.start(), "just checking");
  size_t delta = pointer_delta(p, _reserved.start());
  return (delta & right_n_bits(LogN_words)) == (size_t)NoBits;
}

//////////////////////////////////////////////////////////////////////
// G1BlockOffsetArray
//////////////////////////////////////////////////////////////////////

G1BlockOffsetArray::G1BlockOffsetArray(G1BlockOffsetSharedArray* array,
                                       MemRegion mr) :
  G1BlockOffsetTable(mr.start(), mr.end()),
  _unallocated_block(_bottom),
  _array(array), _gsp(NULL) {
  assert(_bottom <= _end, "arguments out of order");
}

void G1BlockOffsetArray::set_space(G1OffsetTableContigSpace* sp) {
  _gsp = sp;
}

// The arguments follow the normal convention of denoting
// a right-open interval: [start, end)
void
G1BlockOffsetArray:: set_remainder_to_point_to_start(HeapWord* start, HeapWord* end) {

  if (start >= end) {
    // The start address is equal to the end address (or to
    // the right of the end address) so there are not cards
    // that need to be updated..
    return;
  }

  // Write the backskip value for each region.
  //
  //    offset
  //    card             2nd                       3rd
  //     | +- 1st        |                         |
  //     v v             v                         v
  //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+-+-+-
  //    |x|0|0|0|0|0|0|0|1|1|1|1|1|1| ... |1|1|1|1|2|2|2|2|2|2| ...
  //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+-+-+-
  //    11              19                        75
  //      12
  //
  //    offset card is the card that points to the start of an object
  //      x - offset value of offset card
  //    1st - start of first logarithmic region
  //      0 corresponds to logarithmic value N_words + 0 and 2**(3 * 0) = 1
  //    2nd - start of second logarithmic region
  //      1 corresponds to logarithmic value N_words + 1 and 2**(3 * 1) = 8
  //    3rd - start of third logarithmic region
  //      2 corresponds to logarithmic value N_words + 2 and 2**(3 * 2) = 64
  //
  //    integer below the block offset entry is an example of
  //    the index of the entry
  //
  //    Given an address,
  //      Find the index for the address
  //      Find the block offset table entry
  //      Convert the entry to a back slide
  //        (e.g., with today's, offset = 0x81 =>
  //          back slip = 2**(3*(0x81 - N_words)) = 2**3) = 8
  //      Move back N (e.g., 8) entries and repeat with the
  //        value of the new entry
  //
  size_t start_card = _array->index_for(start);
  size_t end_card = _array->index_for(end-1);
  assert(start ==_array->address_for_index(start_card), "Precondition");
  assert(end ==_array->address_for_index(end_card)+N_words, "Precondition");
  set_remainder_to_point_to_start_incl(start_card, end_card); // closed interval
}

// Unlike the normal convention in this code, the argument here denotes
// a closed, inclusive interval: [start_card, end_card], cf set_remainder_to_point_to_start()
// above.
void
G1BlockOffsetArray::set_remainder_to_point_to_start_incl(size_t start_card, size_t end_card) {
  if (start_card > end_card) {
    return;
  }
  assert(start_card > _array->index_for(_bottom), "Cannot be first card");
  assert(_array->offset_array(start_card-1) <= N_words,
         "Offset card has an unexpected value");
  size_t start_card_for_region = start_card;
  u_char offset = max_jubyte;
  for (int i = 0; i < BlockOffsetArray::N_powers; i++) {
    // -1 so that the the card with the actual offset is counted.  Another -1
    // so that the reach ends in this region and not at the start
    // of the next.
    size_t reach = start_card - 1 + (BlockOffsetArray::power_to_cards_back(i+1) - 1);
    offset = N_words + i;
    if (reach >= end_card) {
      _array->set_offset_array(start_card_for_region, end_card, offset);
      start_card_for_region = reach + 1;
      break;
    }
    _array->set_offset_array(start_card_for_region, reach, offset);
    start_card_for_region = reach + 1;
  }
  assert(start_card_for_region > end_card, "Sanity check");
  DEBUG_ONLY(check_all_cards(start_card, end_card);)
}

// The card-interval [start_card, end_card] is a closed interval; this
// is an expensive check -- use with care and only under protection of
// suitable flag.
void G1BlockOffsetArray::check_all_cards(size_t start_card, size_t end_card) const {

  if (end_card < start_card) {
    return;
  }
  guarantee(_array->offset_array(start_card) == N_words, "Wrong value in second card");
  for (size_t c = start_card + 1; c <= end_card; c++ /* yeah! */) {
    u_char entry = _array->offset_array(c);
    if (c - start_card > BlockOffsetArray::power_to_cards_back(1)) {
      guarantee(entry > N_words,
                err_msg("Should be in logarithmic region - "
                        "entry: " UINT32_FORMAT ", "
                        "_array->offset_array(c): " UINT32_FORMAT ", "
                        "N_words: " UINT32_FORMAT,
                        entry, _array->offset_array(c), N_words));
    }
    size_t backskip = BlockOffsetArray::entry_to_cards_back(entry);
    size_t landing_card = c - backskip;
    guarantee(landing_card >= (start_card - 1), "Inv");
    if (landing_card >= start_card) {
      guarantee(_array->offset_array(landing_card) <= entry,
                err_msg("Monotonicity - landing_card offset: " UINT32_FORMAT ", "
                        "entry: " UINT32_FORMAT,
                        _array->offset_array(landing_card), entry));
    } else {
      guarantee(landing_card == start_card - 1, "Tautology");
      // Note that N_words is the maximum offset value
      guarantee(_array->offset_array(landing_card) <= N_words,
                err_msg("landing card offset: " UINT32_FORMAT ", "
                        "N_words: " UINT32_FORMAT,
                        _array->offset_array(landing_card), N_words));
    }
  }
}

HeapWord* G1BlockOffsetArray::block_start_unsafe(const void* addr) {
  assert(_bottom <= addr && addr < _end,
         "addr must be covered by this Array");
  // Must read this exactly once because it can be modified by parallel
  // allocation.
  HeapWord* ub = _unallocated_block;
  if (BlockOffsetArrayUseUnallocatedBlock && addr >= ub) {
    assert(ub < _end, "tautology (see above)");
    return ub;
  }
  // Otherwise, find the block start using the table.
  HeapWord* q = block_at_or_preceding(addr, false, 0);
  return forward_to_block_containing_addr(q, addr);
}

// This duplicates a little code from the above: unavoidable.
HeapWord*
G1BlockOffsetArray::block_start_unsafe_const(const void* addr) const {
  assert(_bottom <= addr && addr < _end,
         "addr must be covered by this Array");
  // Must read this exactly once because it can be modified by parallel
  // allocation.
  HeapWord* ub = _unallocated_block;
  if (BlockOffsetArrayUseUnallocatedBlock && addr >= ub) {
    assert(ub < _end, "tautology (see above)");
    return ub;
  }
  // Otherwise, find the block start using the table.
  HeapWord* q = block_at_or_preceding(addr, false, 0);
  HeapWord* n = q + block_size(q);
  return forward_to_block_containing_addr_const(q, n, addr);
}


HeapWord*
G1BlockOffsetArray::forward_to_block_containing_addr_slow(HeapWord* q,
                                                          HeapWord* n,
                                                          const void* addr) {
  // We're not in the normal case.  We need to handle an important subcase
  // here: LAB allocation.  An allocation previously recorded in the
  // offset table was actually a lab allocation, and was divided into
  // several objects subsequently.  Fix this situation as we answer the
  // query, by updating entries as we cross them.

  // If the fist object's end q is at the card boundary. Start refining
  // with the corresponding card (the value of the entry will be basically
  // set to 0). If the object crosses the boundary -- start from the next card.
  size_t n_index = _array->index_for(n);
  size_t next_index = _array->index_for(n) + !_array->is_card_boundary(n);
  // Calculate a consistent next boundary.  If "n" is not at the boundary
  // already, step to the boundary.
  HeapWord* next_boundary = _array->address_for_index(n_index) +
                            (n_index == next_index ? 0 : N_words);
  assert(next_boundary <= _array->_end,
         err_msg("next_boundary is beyond the end of the covered region "
                 " next_boundary " PTR_FORMAT " _array->_end " PTR_FORMAT,
                 next_boundary, _array->_end));
  if (addr >= gsp()->top()) return gsp()->top();
  while (next_boundary < addr) {
    while (n <= next_boundary) {
      q = n;
      oop obj = oop(q);
      if (obj->klass_or_null() == NULL) return q;
      n += block_size(q);
    }
    assert(q <= next_boundary && n > next_boundary, "Consequence of loop");
    // [q, n) is the block that crosses the boundary.
    alloc_block_work2(&next_boundary, &next_index, q, n);
  }
  return forward_to_block_containing_addr_const(q, n, addr);
}

// Note that the committed size of the covered space may have changed,
// so the table size might also wish to change.
void G1BlockOffsetArray::resize(size_t new_word_size) {
  HeapWord* new_end = _bottom + new_word_size;
  _end = new_end;  // update _end
}

//
//              threshold_
//              |   _index_
//              v   v
//      +-------+-------+-------+-------+-------+
//      | i-1   |   i   | i+1   | i+2   | i+3   |
//      +-------+-------+-------+-------+-------+
//       ( ^    ]
//         block-start
//
void G1BlockOffsetArray::alloc_block_work2(HeapWord** threshold_, size_t* index_,
                                           HeapWord* blk_start, HeapWord* blk_end) {
  // For efficiency, do copy-in/copy-out.
  HeapWord* threshold = *threshold_;
  size_t    index = *index_;

  assert(blk_start != NULL && blk_end > blk_start,
         "phantom block");
  assert(blk_end > threshold, "should be past threshold");
  assert(blk_start <= threshold, "blk_start should be at or before threshold");
  assert(pointer_delta(threshold, blk_start) <= N_words,
         "offset should be <= BlockOffsetSharedArray::N");
  assert(Universe::heap()->is_in_reserved(blk_start),
         "reference must be into the heap");
  assert(Universe::heap()->is_in_reserved(blk_end-1),
         "limit must be within the heap");
  assert(threshold == _array->_reserved.start() + index*N_words,
         "index must agree with threshold");

  DEBUG_ONLY(size_t orig_index = index;)

  // Mark the card that holds the offset into the block.  Note
  // that _next_offset_index and _next_offset_threshold are not
  // updated until the end of this method.
  _array->set_offset_array(index, threshold, blk_start);

  // We need to now mark the subsequent cards that this blk spans.

  // Index of card on which blk ends.
  size_t end_index   = _array->index_for(blk_end - 1);

  // Are there more cards left to be updated?
  if (index + 1 <= end_index) {
    HeapWord* rem_st  = _array->address_for_index(index + 1);
    // Calculate rem_end this way because end_index
    // may be the last valid index in the covered region.
    HeapWord* rem_end = _array->address_for_index(end_index) +  N_words;
    set_remainder_to_point_to_start(rem_st, rem_end);
  }

  index = end_index + 1;
  // Calculate threshold_ this way because end_index
  // may be the last valid index in the covered region.
  threshold = _array->address_for_index(end_index) + N_words;
  assert(threshold >= blk_end, "Incorrect offset threshold");

  // index_ and threshold_ updated here.
  *threshold_ = threshold;
  *index_ = index;

#ifdef ASSERT
  // The offset can be 0 if the block starts on a boundary.  That
  // is checked by an assertion above.
  size_t start_index = _array->index_for(blk_start);
  HeapWord* boundary = _array->address_for_index(start_index);
  assert((_array->offset_array(orig_index) == 0 &&
          blk_start == boundary) ||
          (_array->offset_array(orig_index) > 0 &&
         _array->offset_array(orig_index) <= N_words),
         err_msg("offset array should have been set - "
                  "orig_index offset: " UINT32_FORMAT ", "
                  "blk_start: " PTR_FORMAT ", "
                  "boundary: " PTR_FORMAT,
                  _array->offset_array(orig_index),
                  blk_start, boundary));
  for (size_t j = orig_index + 1; j <= end_index; j++) {
    assert(_array->offset_array(j) > 0 &&
           _array->offset_array(j) <=
             (u_char) (N_words+BlockOffsetArray::N_powers-1),
           err_msg("offset array should have been set - "
                   UINT32_FORMAT " not > 0 OR "
                   UINT32_FORMAT " not <= " UINT32_FORMAT,
                   _array->offset_array(j),
                   _array->offset_array(j),
                   (u_char) (N_words+BlockOffsetArray::N_powers-1)));
  }
#endif
}

bool
G1BlockOffsetArray::verify_for_object(HeapWord* obj_start,
                                      size_t word_size) const {
  size_t first_card = _array->index_for(obj_start);
  size_t last_card = _array->index_for(obj_start + word_size - 1);
  if (!_array->is_card_boundary(obj_start)) {
    // If the object is not on a card boundary the BOT entry of the
    // first card should point to another object so we should not
    // check that one.
    first_card += 1;
  }
  for (size_t card = first_card; card <= last_card; card += 1) {
    HeapWord* card_addr = _array->address_for_index(card);
    HeapWord* block_start = block_start_const(card_addr);
    if (block_start != obj_start) {
      gclog_or_tty->print_cr("block start: " PTR_FORMAT " is incorrect - "
                             "card index: " SIZE_FORMAT " "
                             "card addr: " PTR_FORMAT " BOT entry: %u "
                             "obj: " PTR_FORMAT " word size: " SIZE_FORMAT " "
                             "cards: [" SIZE_FORMAT "," SIZE_FORMAT "]",
                             block_start, card, card_addr,
                             _array->offset_array(card),
                             obj_start, word_size, first_card, last_card);
      return false;
    }
  }
  return true;
}

#ifndef PRODUCT
void
G1BlockOffsetArray::print_on(outputStream* out) {
  size_t from_index = _array->index_for(_bottom);
  size_t to_index = _array->index_for(_end);
  out->print_cr(">> BOT for area [" PTR_FORMAT "," PTR_FORMAT ") "
                "cards [" SIZE_FORMAT "," SIZE_FORMAT ")",
                _bottom, _end, from_index, to_index);
  for (size_t i = from_index; i < to_index; ++i) {
    out->print_cr("  entry " SIZE_FORMAT_W(8) " | " PTR_FORMAT " : %3u",
                  i, _array->address_for_index(i),
                  (uint) _array->offset_array(i));
  }
}
#endif // !PRODUCT

//////////////////////////////////////////////////////////////////////
// G1BlockOffsetArrayContigSpace
//////////////////////////////////////////////////////////////////////

HeapWord*
G1BlockOffsetArrayContigSpace::block_start_unsafe(const void* addr) {
  assert(_bottom <= addr && addr < _end,
         "addr must be covered by this Array");
  HeapWord* q = block_at_or_preceding(addr, true, _next_offset_index-1);
  return forward_to_block_containing_addr(q, addr);
}

HeapWord*
G1BlockOffsetArrayContigSpace::
block_start_unsafe_const(const void* addr) const {
  assert(_bottom <= addr && addr < _end,
         "addr must be covered by this Array");
  HeapWord* q = block_at_or_preceding(addr, true, _next_offset_index-1);
  HeapWord* n = q + block_size(q);
  return forward_to_block_containing_addr_const(q, n, addr);
}

G1BlockOffsetArrayContigSpace::
G1BlockOffsetArrayContigSpace(G1BlockOffsetSharedArray* array,
                              MemRegion mr) :
  G1BlockOffsetArray(array, mr)
{
  _next_offset_threshold = NULL;
  _next_offset_index = 0;
}

HeapWord* G1BlockOffsetArrayContigSpace::initialize_threshold_raw() {
  _next_offset_index = _array->index_for_raw(_bottom);
  _next_offset_index++;
  _next_offset_threshold =
    _array->address_for_index_raw(_next_offset_index);
  return _next_offset_threshold;
}

void G1BlockOffsetArrayContigSpace::zero_bottom_entry_raw() {
  size_t bottom_index = _array->index_for_raw(_bottom);
  assert(_array->address_for_index_raw(bottom_index) == _bottom,
         "Precondition of call");
  _array->set_offset_array_raw(bottom_index, 0);
}

HeapWord* G1BlockOffsetArrayContigSpace::initialize_threshold() {
  _next_offset_index = _array->index_for(_bottom);
  _next_offset_index++;
  _next_offset_threshold =
    _array->address_for_index(_next_offset_index);
  return _next_offset_threshold;
}

void
G1BlockOffsetArrayContigSpace::set_for_starts_humongous(HeapWord* new_top) {
  assert(new_top <= _end, "_end should have already been updated");

  // The first BOT entry should have offset 0.
  reset_bot();
  alloc_block(_bottom, new_top);
 }

#ifndef PRODUCT
void
G1BlockOffsetArrayContigSpace::print_on(outputStream* out) {
  G1BlockOffsetArray::print_on(out);
  out->print_cr("  next offset threshold: " PTR_FORMAT, _next_offset_threshold);
  out->print_cr("  next offset index:     " SIZE_FORMAT, _next_offset_index);
}
#endif // !PRODUCT
