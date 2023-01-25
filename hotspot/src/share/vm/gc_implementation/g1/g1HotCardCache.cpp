/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/dirtyCardQueue.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1HotCardCache.hpp"
#include "gc_implementation/g1/g1RemSet.hpp"
#include "runtime/atomic.hpp"

G1HotCardCache::G1HotCardCache(G1CollectedHeap *g1h):
  _g1h(g1h), _hot_cache(NULL), _use_cache(false), _card_counts(g1h) {}

void G1HotCardCache::initialize(G1RegionToSpaceMapper* card_counts_storage) {
  if (default_use_cache()) {
    _use_cache = true;

    _hot_cache_size = (size_t)1 << G1ConcRSLogCacheSize;
    _hot_cache = NEW_C_HEAP_ARRAY(jbyte*, _hot_cache_size, mtGC);

    reset_hot_cache_internal();

    // For refining the cards in the hot cache in parallel
    _hot_cache_par_chunk_size = (int)(ParallelGCThreads > 0 ? ClaimChunkSize : _hot_cache_size);
    _hot_cache_par_claimed_idx = 0;

    _card_counts.initialize(card_counts_storage);
  }
}

G1HotCardCache::~G1HotCardCache() {
  if (default_use_cache()) {
    assert(_hot_cache != NULL, "Logic");
    FREE_C_HEAP_ARRAY(jbyte*, _hot_cache, mtGC);
  }
}

jbyte* G1HotCardCache::insert(jbyte* card_ptr) {
  uint count = _card_counts.add_card_count(card_ptr);
  if (!_card_counts.is_hot(count)) {
    // The card is not hot so do not store it in the cache;
    // return it for immediate refining.
    return card_ptr;
  }
  // Otherwise, the card is hot.
  size_t index = Atomic::add_ptr((intptr_t)1, (volatile intptr_t*)&_hot_cache_idx) - 1;
  size_t masked_index = index & (_hot_cache_size - 1);
  jbyte* current_ptr = _hot_cache[masked_index];

  // Try to store the new card pointer into the cache. Compare-and-swap to guard
  // against the unlikely event of a race resulting in another card pointer to
  // have already been written to the cache. In this case we will return
  // card_ptr in favor of the other option, which would be starting over. This
  // should be OK since card_ptr will likely be the older card already when/if
  // this ever happens.
  jbyte* previous_ptr = (jbyte*)Atomic::cmpxchg_ptr(card_ptr,
                                                    &_hot_cache[masked_index],
                                                    current_ptr);
  return (previous_ptr == current_ptr) ? previous_ptr : card_ptr;
}

void G1HotCardCache::drain(uint worker_i,
                           G1RemSet* g1rs,
                           DirtyCardQueue* into_cset_dcq) {
  if (!default_use_cache()) {
    assert(_hot_cache == NULL, "Logic");
    return;
  }

  assert(_hot_cache != NULL, "Logic");
  assert(!use_cache(), "cache should be disabled");
  while (_hot_cache_par_claimed_idx < _hot_cache_size) {
    size_t end_idx = Atomic::add_ptr((intptr_t)_hot_cache_par_chunk_size,
                                     (volatile intptr_t*)&_hot_cache_par_claimed_idx);
    size_t start_idx = end_idx - _hot_cache_par_chunk_size;
    // The current worker has successfully claimed the chunk [start_idx..end_idx)
    end_idx = MIN2(end_idx, _hot_cache_size);
    for (size_t i = start_idx; i < end_idx; i++) {
      jbyte* card_ptr = _hot_cache[i];
      if (card_ptr != NULL) {
        if (g1rs->refine_card(card_ptr, worker_i, true)) {
          // The part of the heap spanned by the card contains references
          // that point into the current collection set.
          // We need to record the card pointer in the DirtyCardQueueSet
          // that we use for such cards.
          //
          // The only time we care about recording cards that contain
          // references that point into the collection set is during
          // RSet updating while within an evacuation pause.
          // In this case worker_i should be the id of a GC worker thread
          assert(SafepointSynchronize::is_at_safepoint(), "Should be at a safepoint");
          assert(worker_i < ParallelGCThreads,
                 err_msg("incorrect worker id: %u", worker_i));

          into_cset_dcq->enqueue(card_ptr);
        }
      } else {
        break;
      }
    }
  }

  // The existing entries in the hot card cache, which were just refined
  // above, are discarded prior to re-enabling the cache near the end of the GC.
}

void G1HotCardCache::reset_card_counts(HeapRegion* hr) {
  _card_counts.clear_region(hr);
}

void G1HotCardCache::reset_card_counts() {
  _card_counts.clear_all();
}
