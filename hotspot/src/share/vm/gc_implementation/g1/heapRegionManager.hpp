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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONMANAGER_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONMANAGER_HPP

#include "gc_implementation/g1/g1BiasedArray.hpp"
#include "gc_implementation/g1/g1RegionToSpaceMapper.hpp"
#include "gc_implementation/g1/heapRegionSet.hpp"
#include "services/memoryUsage.hpp"

class HeapRegion;
class HeapRegionClosure;
class FreeRegionList;

class G1HeapRegionTable : public G1BiasedMappedArray<HeapRegion*> {
 protected:
  virtual HeapRegion* default_value() const { return NULL; }
};

// This class keeps track of the actual heap memory, auxiliary data
// and its metadata (i.e., HeapRegion instances) and the list of free regions.
//
// This allows maximum flexibility for deciding what to commit or uncommit given
// a request from outside.
//
// HeapRegions are kept in the _regions array in address order. A region's
// index in the array corresponds to its index in the heap (i.e., 0 is the
// region at the bottom of the heap, 1 is the one after it, etc.). Two
// regions that are consecutive in the array should also be adjacent in the
// address space (i.e., region(i).end() == region(i+1).bottom().
//
// We create a HeapRegion when we commit the region's address space
// for the first time. When we uncommit the address space of a
// region we retain the HeapRegion to be able to re-use it in the
// future (in case we recommit it).
//
// We keep track of three lengths:
//
// * _num_committed (returned by length()) is the number of currently
//   committed regions. These may not be contiguous.
// * _allocated_heapregions_length (not exposed outside this class) is the
//   number of regions+1 for which we have HeapRegions.
// * max_length() returns the maximum number of regions the heap can have.
//

class HeapRegionManager: public CHeapObj<mtGC> {
  friend class VMStructs;

  G1HeapRegionTable _regions;

  G1RegionToSpaceMapper* _heap_mapper;
  G1RegionToSpaceMapper* _prev_bitmap_mapper;
  G1RegionToSpaceMapper* _next_bitmap_mapper;
  G1RegionToSpaceMapper* _bot_mapper;
  G1RegionToSpaceMapper* _cardtable_mapper;
  G1RegionToSpaceMapper* _card_counts_mapper;

  FreeRegionList _free_list;

  // Each bit in this bitmap indicates that the corresponding region is available
  // for allocation.
  BitMap _available_map;

   // The number of regions committed in the heap.
  uint _num_committed;

  // Internal only. The highest heap region +1 we allocated a HeapRegion instance for.
  uint _allocated_heapregions_length;

   HeapWord* heap_bottom() const { return _regions.bottom_address_mapped(); }
   HeapWord* heap_end() const {return _regions.end_address_mapped(); }

  void make_regions_available(uint index, uint num_regions = 1);

  // Pass down commit calls to the VirtualSpace.
  void commit_regions(uint index, size_t num_regions = 1);
  void uncommit_regions(uint index, size_t num_regions = 1);

  // Notify other data structures about change in the heap layout.
  void update_committed_space(HeapWord* old_end, HeapWord* new_end);
  // Calculate the starting region for each worker during parallel iteration so
  // that they do not all start from the same region.
  uint start_region_for_worker(uint worker_i, uint num_workers, uint num_regions) const;

  // Find a contiguous set of empty or uncommitted regions of length num and return
  // the index of the first region or G1_NO_HRM_INDEX if the search was unsuccessful.
  // If only_empty is true, only empty regions are considered.
  // Searches from bottom to top of the heap, doing a first-fit.
  uint find_contiguous(size_t num, bool only_empty);
  // Finds the next sequence of unavailable regions starting from start_idx. Returns the
  // length of the sequence found. If this result is zero, no such sequence could be found,
  // otherwise res_idx indicates the start index of these regions.
  uint find_unavailable_from_idx(uint start_idx, uint* res_idx) const;
  // Finds the next sequence of empty regions starting from start_idx, going backwards in
  // the heap. Returns the length of the sequence found. If this value is zero, no
  // sequence could be found, otherwise res_idx contains the start index of this range.
  uint find_empty_from_idx_reverse(uint start_idx, uint* res_idx) const;
  // Allocate a new HeapRegion for the given index.
  HeapRegion* new_heap_region(uint hrm_index);
#ifdef ASSERT
public:
  bool is_free(HeapRegion* hr) const;
#endif
  // Returns whether the given region is available for allocation.
  bool is_available(uint region) const;

 public:
  // Empty constructor, we'll initialize it with the initialize() method.
  HeapRegionManager() : _regions(), _heap_mapper(NULL), _num_committed(0),
                    _next_bitmap_mapper(NULL), _prev_bitmap_mapper(NULL), _bot_mapper(NULL),
                    _allocated_heapregions_length(0), _available_map(),
                    _free_list("Free list", new MasterFreeRegionListMtSafeChecker())
  { }

  void initialize(G1RegionToSpaceMapper* heap_storage,
                  G1RegionToSpaceMapper* prev_bitmap,
                  G1RegionToSpaceMapper* next_bitmap,
                  G1RegionToSpaceMapper* bot,
                  G1RegionToSpaceMapper* cardtable,
                  G1RegionToSpaceMapper* card_counts);

  // Return the "dummy" region used for G1AllocRegion. This is currently a hardwired
  // new HeapRegion that owns HeapRegion at index 0. Since at the moment we commit
  // the heap from the lowest address, this region (and its associated data
  // structures) are available and we do not need to check further.
  HeapRegion* get_dummy_region() { return new_heap_region(0); }

  // Return the HeapRegion at the given index. Assume that the index
  // is valid.
  inline HeapRegion* at(uint index) const;

  // If addr is within the committed space return its corresponding
  // HeapRegion, otherwise return NULL.
  inline HeapRegion* addr_to_region(HeapWord* addr) const;

  // Insert the given region into the free region list.
  inline void insert_into_free_list(HeapRegion* hr);

  // Insert the given region list into the global free region list.
  void insert_list_into_free_list(FreeRegionList* list) {
    _free_list.add_ordered(list);
  }

  HeapRegion* allocate_free_region(bool is_old) {
    HeapRegion* hr = _free_list.remove_region(is_old);

    if (hr != NULL) {
      assert(hr->next() == NULL, "Single region should not have next");
      assert(is_available(hr->hrm_index()), "Must be committed");
    }
    return hr;
  }

  inline void allocate_free_regions_starting_at(uint first, uint num_regions);

  // Remove all regions from the free list.
  void remove_all_free_regions() {
    _free_list.remove_all();
  }

  // Return the number of committed free regions in the heap.
  uint num_free_regions() const {
    return _free_list.length();
  }

  size_t total_capacity_bytes() const {
    return num_free_regions() * HeapRegion::GrainBytes;
  }

  // Return the number of available (uncommitted) regions.
  uint available() const { return max_length() - length(); }

  // Return the number of regions that have been committed in the heap.
  uint length() const { return _num_committed; }

  // Return the maximum number of regions in the heap.
  uint max_length() const { return (uint)_regions.length(); }

  MemoryUsage get_auxiliary_data_memory_usage() const;

  MemRegion reserved() const { return MemRegion(heap_bottom(), heap_end()); }

  // Expand the sequence to reflect that the heap has grown. Either create new
  // HeapRegions, or re-use existing ones. Returns the number of regions the
  // sequence was expanded by. If a HeapRegion allocation fails, the resulting
  // number of regions might be smaller than what's desired.
  uint expand_by(uint num_regions);

  // Makes sure that the regions from start to start+num_regions-1 are available
  // for allocation. Returns the number of regions that were committed to achieve
  // this.
  uint expand_at(uint start, uint num_regions);

  // Find a contiguous set of empty regions of length num. Returns the start index of
  // that set, or G1_NO_HRM_INDEX.
  uint find_contiguous_only_empty(size_t num) { return find_contiguous(num, true); }
  // Find a contiguous set of empty or unavailable regions of length num. Returns the
  // start index of that set, or G1_NO_HRM_INDEX.
  uint find_contiguous_empty_or_unavailable(size_t num) { return find_contiguous(num, false); }

  HeapRegion* next_region_in_heap(const HeapRegion* r) const;

  // Apply blk->doHeapRegion() on all committed regions in address order,
  // terminating the iteration early if doHeapRegion() returns true.
  void iterate(HeapRegionClosure* blk) const;

  void par_iterate(HeapRegionClosure* blk, uint worker_id, uint no_of_par_workers, jint claim_value) const;

  // Uncommit up to num_regions_to_remove regions that are completely free.
  // Return the actual number of uncommitted regions.
  uint shrink_by(uint num_regions_to_remove);

  void verify();

  // Do some sanity checking.
  void verify_optional() PRODUCT_RETURN;
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONMANAGER_HPP
