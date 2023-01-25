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
#include "gc_implementation/shared/adaptiveSizePolicy.hpp"
#include "gc_implementation/shared/gcPolicyCounters.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "memory/cardTableRS.hpp"
#include "memory/collectorPolicy.hpp"
#include "memory/gcLocker.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/generationSpec.hpp"
#include "memory/space.hpp"
#include "memory/universe.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/concurrentMarkSweep/cmsAdaptiveSizePolicy.hpp"
#include "gc_implementation/concurrentMarkSweep/cmsGCAdaptivePolicyCounters.hpp"
#endif // INCLUDE_ALL_GCS

// CollectorPolicy methods.

CollectorPolicy::CollectorPolicy() :
    _space_alignment(0),
    _heap_alignment(0),
    _initial_heap_byte_size(InitialHeapSize),
    _max_heap_byte_size(MaxHeapSize),
    _min_heap_byte_size(Arguments::min_heap_size()),
    _max_heap_size_cmdline(false),
    _size_policy(NULL),
    _should_clear_all_soft_refs(false),
    _all_soft_refs_clear(false)
{}

#ifdef ASSERT
void CollectorPolicy::assert_flags() {
  assert(InitialHeapSize <= MaxHeapSize, "Ergonomics decided on incompatible initial and maximum heap sizes");
  assert(InitialHeapSize % _heap_alignment == 0, "InitialHeapSize alignment");
  assert(MaxHeapSize % _heap_alignment == 0, "MaxHeapSize alignment");
}

void CollectorPolicy::assert_size_info() {
  assert(InitialHeapSize == _initial_heap_byte_size, "Discrepancy between InitialHeapSize flag and local storage");
  assert(MaxHeapSize == _max_heap_byte_size, "Discrepancy between MaxHeapSize flag and local storage");
  assert(_max_heap_byte_size >= _min_heap_byte_size, "Ergonomics decided on incompatible minimum and maximum heap sizes");
  assert(_initial_heap_byte_size >= _min_heap_byte_size, "Ergonomics decided on incompatible initial and minimum heap sizes");
  assert(_max_heap_byte_size >= _initial_heap_byte_size, "Ergonomics decided on incompatible initial and maximum heap sizes");
  assert(_min_heap_byte_size % _heap_alignment == 0, "min_heap_byte_size alignment");
  assert(_initial_heap_byte_size % _heap_alignment == 0, "initial_heap_byte_size alignment");
  assert(_max_heap_byte_size % _heap_alignment == 0, "max_heap_byte_size alignment");
}
#endif // ASSERT

void CollectorPolicy::initialize_flags() {
  assert(_space_alignment != 0, "Space alignment not set up properly");
  assert(_heap_alignment != 0, "Heap alignment not set up properly");
  assert(_heap_alignment >= _space_alignment,
         err_msg("heap_alignment: " SIZE_FORMAT " less than space_alignment: " SIZE_FORMAT,
                 _heap_alignment, _space_alignment));
  assert(_heap_alignment % _space_alignment == 0,
         err_msg("heap_alignment: " SIZE_FORMAT " not aligned by space_alignment: " SIZE_FORMAT,
                 _heap_alignment, _space_alignment));

  if (FLAG_IS_CMDLINE(MaxHeapSize)) {
    if (FLAG_IS_CMDLINE(InitialHeapSize) && InitialHeapSize > MaxHeapSize) {
      vm_exit_during_initialization("Initial heap size set to a larger value than the maximum heap size");
    }
    if (_min_heap_byte_size != 0 && MaxHeapSize < _min_heap_byte_size) {
      vm_exit_during_initialization("Incompatible minimum and maximum heap sizes specified");
    }
    _max_heap_size_cmdline = true;
  }

  // Check heap parameter properties
  if (InitialHeapSize < M) {
    vm_exit_during_initialization("Too small initial heap");
  }
  if (_min_heap_byte_size < M) {
    vm_exit_during_initialization("Too small minimum heap");
  }

  // User inputs from -Xmx and -Xms must be aligned
  _min_heap_byte_size = align_size_up(_min_heap_byte_size, _heap_alignment);
  uintx aligned_initial_heap_size = align_size_up(InitialHeapSize, _heap_alignment);
  uintx aligned_max_heap_size = align_size_up(MaxHeapSize, _heap_alignment);

  // Write back to flags if the values changed
  if (aligned_initial_heap_size != InitialHeapSize) {
    FLAG_SET_ERGO(uintx, InitialHeapSize, aligned_initial_heap_size);
  }
  if (aligned_max_heap_size != MaxHeapSize) {
    FLAG_SET_ERGO(uintx, MaxHeapSize, aligned_max_heap_size);
  }

  if (FLAG_IS_CMDLINE(InitialHeapSize) && _min_heap_byte_size != 0 &&
      InitialHeapSize < _min_heap_byte_size) {
    vm_exit_during_initialization("Incompatible minimum and initial heap sizes specified");
  }
  if (!FLAG_IS_DEFAULT(InitialHeapSize) && InitialHeapSize > MaxHeapSize) {
    FLAG_SET_ERGO(uintx, MaxHeapSize, InitialHeapSize);
  } else if (!FLAG_IS_DEFAULT(MaxHeapSize) && InitialHeapSize > MaxHeapSize) {
    FLAG_SET_ERGO(uintx, InitialHeapSize, MaxHeapSize);
    if (InitialHeapSize < _min_heap_byte_size) {
      _min_heap_byte_size = InitialHeapSize;
    }
  }

  _initial_heap_byte_size = InitialHeapSize;
  _max_heap_byte_size = MaxHeapSize;

  FLAG_SET_ERGO(uintx, MinHeapDeltaBytes, align_size_up(MinHeapDeltaBytes, _space_alignment));

  DEBUG_ONLY(CollectorPolicy::assert_flags();)
}

void CollectorPolicy::initialize_size_info() {
  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print_cr("Minimum heap " SIZE_FORMAT "  Initial heap "
      SIZE_FORMAT "  Maximum heap " SIZE_FORMAT,
      _min_heap_byte_size, _initial_heap_byte_size, _max_heap_byte_size);
  }

  DEBUG_ONLY(CollectorPolicy::assert_size_info();)
}

bool CollectorPolicy::use_should_clear_all_soft_refs(bool v) {
  bool result = _should_clear_all_soft_refs;
  set_should_clear_all_soft_refs(false);
  return result;
}

GenRemSet* CollectorPolicy::create_rem_set(MemRegion whole_heap,
                                           int max_covered_regions) {
  return new CardTableRS(whole_heap, max_covered_regions);
}

void CollectorPolicy::cleared_all_soft_refs() {
  // If near gc overhear limit, continue to clear SoftRefs.  SoftRefs may
  // have been cleared in the last collection but if the gc overhear
  // limit continues to be near, SoftRefs should still be cleared.
  if (size_policy() != NULL) {
    _should_clear_all_soft_refs = size_policy()->gc_overhead_limit_near();
  }
  _all_soft_refs_clear = true;
}

size_t CollectorPolicy::compute_heap_alignment() {
  // The card marking array and the offset arrays for old generations are
  // committed in os pages as well. Make sure they are entirely full (to
  // avoid partial page problems), e.g. if 512 bytes heap corresponds to 1
  // byte entry and the os page size is 4096, the maximum heap size should
  // be 512*4096 = 2MB aligned.

  // There is only the GenRemSet in Hotspot and only the GenRemSet::CardTable
  // is supported.
  // Requirements of any new remembered set implementations must be added here.
  size_t alignment = GenRemSet::max_alignment_constraint(GenRemSet::CardTable);

  if (UseLargePages) {
      // In presence of large pages we have to make sure that our
      // alignment is large page aware.
      alignment = lcm(os::large_page_size(), alignment);
  }

  return alignment;
}

// GenCollectorPolicy methods.

GenCollectorPolicy::GenCollectorPolicy() :
    _min_gen0_size(0),
    _initial_gen0_size(0),
    _max_gen0_size(0),
    _gen_alignment(0),
    _generations(NULL)
{}

size_t GenCollectorPolicy::scale_by_NewRatio_aligned(size_t base_size) {
  return align_size_down_bounded(base_size / (NewRatio + 1), _gen_alignment);
}

size_t GenCollectorPolicy::bound_minus_alignment(size_t desired_size,
                                                 size_t maximum_size) {
  size_t max_minus = maximum_size - _gen_alignment;
  return desired_size < max_minus ? desired_size : max_minus;
}


void GenCollectorPolicy::initialize_size_policy(size_t init_eden_size,
                                                size_t init_promo_size,
                                                size_t init_survivor_size) {
  const double max_gc_pause_sec = ((double) MaxGCPauseMillis) / 1000.0;
  _size_policy = new AdaptiveSizePolicy(init_eden_size,
                                        init_promo_size,
                                        init_survivor_size,
                                        max_gc_pause_sec,
                                        GCTimeRatio);
}

size_t GenCollectorPolicy::young_gen_size_lower_bound() {
  // The young generation must be aligned and have room for eden + two survivors
  return align_size_up(3 * _space_alignment, _gen_alignment);
}

#ifdef ASSERT
void GenCollectorPolicy::assert_flags() {
  CollectorPolicy::assert_flags();
  assert(NewSize >= _min_gen0_size, "Ergonomics decided on a too small young gen size");
  assert(NewSize <= MaxNewSize, "Ergonomics decided on incompatible initial and maximum young gen sizes");
  assert(FLAG_IS_DEFAULT(MaxNewSize) || MaxNewSize < MaxHeapSize, "Ergonomics decided on incompatible maximum young gen and heap sizes");
  assert(NewSize % _gen_alignment == 0, "NewSize alignment");
  assert(FLAG_IS_DEFAULT(MaxNewSize) || MaxNewSize % _gen_alignment == 0, "MaxNewSize alignment");
}

void TwoGenerationCollectorPolicy::assert_flags() {
  GenCollectorPolicy::assert_flags();
  assert(OldSize + NewSize <= MaxHeapSize, "Ergonomics decided on incompatible generation and heap sizes");
  assert(OldSize % _gen_alignment == 0, "OldSize alignment");
}

void GenCollectorPolicy::assert_size_info() {
  CollectorPolicy::assert_size_info();
  // GenCollectorPolicy::initialize_size_info may update the MaxNewSize
  assert(MaxNewSize < MaxHeapSize, "Ergonomics decided on incompatible maximum young and heap sizes");
  assert(NewSize == _initial_gen0_size, "Discrepancy between NewSize flag and local storage");
  assert(MaxNewSize == _max_gen0_size, "Discrepancy between MaxNewSize flag and local storage");
  assert(_min_gen0_size <= _initial_gen0_size, "Ergonomics decided on incompatible minimum and initial young gen sizes");
  assert(_initial_gen0_size <= _max_gen0_size, "Ergonomics decided on incompatible initial and maximum young gen sizes");
  assert(_min_gen0_size % _gen_alignment == 0, "_min_gen0_size alignment");
  assert(_initial_gen0_size % _gen_alignment == 0, "_initial_gen0_size alignment");
  assert(_max_gen0_size % _gen_alignment == 0, "_max_gen0_size alignment");
}

void TwoGenerationCollectorPolicy::assert_size_info() {
  GenCollectorPolicy::assert_size_info();
  assert(OldSize == _initial_gen1_size, "Discrepancy between OldSize flag and local storage");
  assert(_min_gen1_size <= _initial_gen1_size, "Ergonomics decided on incompatible minimum and initial old gen sizes");
  assert(_initial_gen1_size <= _max_gen1_size, "Ergonomics decided on incompatible initial and maximum old gen sizes");
  assert(_max_gen1_size % _gen_alignment == 0, "_max_gen1_size alignment");
  assert(_initial_gen1_size % _gen_alignment == 0, "_initial_gen1_size alignment");
  assert(_max_heap_byte_size <= (_max_gen0_size + _max_gen1_size), "Total maximum heap sizes must be sum of generation maximum sizes");
}
#endif // ASSERT

void GenCollectorPolicy::initialize_flags() {
  CollectorPolicy::initialize_flags();

  assert(_gen_alignment != 0, "Generation alignment not set up properly");
  assert(_heap_alignment >= _gen_alignment,
         err_msg("heap_alignment: " SIZE_FORMAT " less than gen_alignment: " SIZE_FORMAT,
                 _heap_alignment, _gen_alignment));
  assert(_gen_alignment % _space_alignment == 0,
         err_msg("gen_alignment: " SIZE_FORMAT " not aligned by space_alignment: " SIZE_FORMAT,
                 _gen_alignment, _space_alignment));
  assert(_heap_alignment % _gen_alignment == 0,
         err_msg("heap_alignment: " SIZE_FORMAT " not aligned by gen_alignment: " SIZE_FORMAT,
                 _heap_alignment, _gen_alignment));

  // All generational heaps have a youngest gen; handle those flags here

  // Make sure the heap is large enough for two generations
  uintx smallest_new_size = young_gen_size_lower_bound();
  uintx smallest_heap_size = align_size_up(smallest_new_size + align_size_up(_space_alignment, _gen_alignment),
                                           _heap_alignment);
  if (MaxHeapSize < smallest_heap_size) {
    FLAG_SET_ERGO(uintx, MaxHeapSize, smallest_heap_size);
    _max_heap_byte_size = MaxHeapSize;
  }
  // If needed, synchronize _min_heap_byte size and _initial_heap_byte_size
  if (_min_heap_byte_size < smallest_heap_size) {
    _min_heap_byte_size = smallest_heap_size;
    if (InitialHeapSize < _min_heap_byte_size) {
      FLAG_SET_ERGO(uintx, InitialHeapSize, smallest_heap_size);
      _initial_heap_byte_size = smallest_heap_size;
    }
  }

  // Now take the actual NewSize into account. We will silently increase NewSize
  // if the user specified a smaller or unaligned value.
  smallest_new_size = MAX2(smallest_new_size, (uintx)align_size_down(NewSize, _gen_alignment));
  if (smallest_new_size != NewSize) {
    // Do not use FLAG_SET_ERGO to update NewSize here, since this will override
    // if NewSize was set on the command line or not. This information is needed
    // later when setting the initial and minimum young generation size.
    NewSize = smallest_new_size;
  }
  _initial_gen0_size = NewSize;

  if (!FLAG_IS_DEFAULT(MaxNewSize)) {
    uintx min_new_size = MAX2(_gen_alignment, _min_gen0_size);

    if (MaxNewSize >= MaxHeapSize) {
      // Make sure there is room for an old generation
      uintx smaller_max_new_size = MaxHeapSize - _gen_alignment;
      if (FLAG_IS_CMDLINE(MaxNewSize)) {
        warning("MaxNewSize (" SIZE_FORMAT "k) is equal to or greater than the entire "
                "heap (" SIZE_FORMAT "k).  A new max generation size of " SIZE_FORMAT "k will be used.",
                MaxNewSize/K, MaxHeapSize/K, smaller_max_new_size/K);
      }
      FLAG_SET_ERGO(uintx, MaxNewSize, smaller_max_new_size);
      if (NewSize > MaxNewSize) {
        FLAG_SET_ERGO(uintx, NewSize, MaxNewSize);
        _initial_gen0_size = NewSize;
      }
    } else if (MaxNewSize < min_new_size) {
      FLAG_SET_ERGO(uintx, MaxNewSize, min_new_size);
    } else if (!is_size_aligned(MaxNewSize, _gen_alignment)) {
      FLAG_SET_ERGO(uintx, MaxNewSize, align_size_down(MaxNewSize, _gen_alignment));
    }
    _max_gen0_size = MaxNewSize;
  }

  if (NewSize > MaxNewSize) {
    // At this point this should only happen if the user specifies a large NewSize and/or
    // a small (but not too small) MaxNewSize.
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      warning("NewSize (" SIZE_FORMAT "k) is greater than the MaxNewSize (" SIZE_FORMAT "k). "
              "A new max generation size of " SIZE_FORMAT "k will be used.",
              NewSize/K, MaxNewSize/K, NewSize/K);
    }
    FLAG_SET_ERGO(uintx, MaxNewSize, NewSize);
    _max_gen0_size = MaxNewSize;
  }

  if (SurvivorRatio < 1 || NewRatio < 1) {
    vm_exit_during_initialization("Invalid young gen ratio specified");
  }

  DEBUG_ONLY(GenCollectorPolicy::assert_flags();)
}

void TwoGenerationCollectorPolicy::initialize_flags() {
  GenCollectorPolicy::initialize_flags();

  if (!is_size_aligned(OldSize, _gen_alignment)) {
    FLAG_SET_ERGO(uintx, OldSize, align_size_down(OldSize, _gen_alignment));
  }

  if (FLAG_IS_CMDLINE(OldSize) && FLAG_IS_DEFAULT(MaxHeapSize)) {
    // NewRatio will be used later to set the young generation size so we use
    // it to calculate how big the heap should be based on the requested OldSize
    // and NewRatio.
    assert(NewRatio > 0, "NewRatio should have been set up earlier");
    size_t calculated_heapsize = (OldSize / NewRatio) * (NewRatio + 1);

    calculated_heapsize = align_size_up(calculated_heapsize, _heap_alignment);
    FLAG_SET_ERGO(uintx, MaxHeapSize, calculated_heapsize);
    _max_heap_byte_size = MaxHeapSize;
    FLAG_SET_ERGO(uintx, InitialHeapSize, calculated_heapsize);
    _initial_heap_byte_size = InitialHeapSize;
  }

  // adjust max heap size if necessary
  if (NewSize + OldSize > MaxHeapSize) {
    if (_max_heap_size_cmdline) {
      // somebody set a maximum heap size with the intention that we should not
      // exceed it. Adjust New/OldSize as necessary.
      uintx calculated_size = NewSize + OldSize;
      double shrink_factor = (double) MaxHeapSize / calculated_size;
      uintx smaller_new_size = align_size_down((uintx)(NewSize * shrink_factor), _gen_alignment);
      FLAG_SET_ERGO(uintx, NewSize, MAX2(young_gen_size_lower_bound(), smaller_new_size));
      _initial_gen0_size = NewSize;

      // OldSize is already aligned because above we aligned MaxHeapSize to
      // _heap_alignment, and we just made sure that NewSize is aligned to
      // _gen_alignment. In initialize_flags() we verified that _heap_alignment
      // is a multiple of _gen_alignment.
      FLAG_SET_ERGO(uintx, OldSize, MaxHeapSize - NewSize);
    } else {
      FLAG_SET_ERGO(uintx, MaxHeapSize, align_size_up(NewSize + OldSize, _heap_alignment));
      _max_heap_byte_size = MaxHeapSize;
    }
  }

  always_do_update_barrier = UseConcMarkSweepGC;

  DEBUG_ONLY(TwoGenerationCollectorPolicy::assert_flags();)
}

// Values set on the command line win over any ergonomically
// set command line parameters.
// Ergonomic choice of parameters are done before this
// method is called.  Values for command line parameters such as NewSize
// and MaxNewSize feed those ergonomic choices into this method.
// This method makes the final generation sizings consistent with
// themselves and with overall heap sizings.
// In the absence of explicitly set command line flags, policies
// such as the use of NewRatio are used to size the generation.
void GenCollectorPolicy::initialize_size_info() {
  CollectorPolicy::initialize_size_info();

  // _space_alignment is used for alignment within a generation.
  // There is additional alignment done down stream for some
  // collectors that sometimes causes unwanted rounding up of
  // generations sizes.

  // Determine maximum size of gen0

  size_t max_new_size = 0;
  if (!FLAG_IS_DEFAULT(MaxNewSize)) {
    max_new_size = MaxNewSize;
  } else {
    max_new_size = scale_by_NewRatio_aligned(_max_heap_byte_size);
    // Bound the maximum size by NewSize below (since it historically
    // would have been NewSize and because the NewRatio calculation could
    // yield a size that is too small) and bound it by MaxNewSize above.
    // Ergonomics plays here by previously calculating the desired
    // NewSize and MaxNewSize.
    max_new_size = MIN2(MAX2(max_new_size, NewSize), MaxNewSize);
  }
  assert(max_new_size > 0, "All paths should set max_new_size");

  // Given the maximum gen0 size, determine the initial and
  // minimum gen0 sizes.

  if (_max_heap_byte_size == _min_heap_byte_size) {
    // The maximum and minimum heap sizes are the same so
    // the generations minimum and initial must be the
    // same as its maximum.
    _min_gen0_size = max_new_size;
    _initial_gen0_size = max_new_size;
    _max_gen0_size = max_new_size;
  } else {
    size_t desired_new_size = 0;
    if (FLAG_IS_CMDLINE(NewSize)) {
      // If NewSize is set on the command line, we must use it as
      // the initial size and it also makes sense to use it as the
      // lower limit.
      _min_gen0_size = NewSize;
      desired_new_size = NewSize;
      max_new_size = MAX2(max_new_size, NewSize);
    } else if (FLAG_IS_ERGO(NewSize)) {
      // If NewSize is set ergonomically, we should use it as a lower
      // limit, but use NewRatio to calculate the initial size.
      _min_gen0_size = NewSize;
      desired_new_size =
        MAX2(scale_by_NewRatio_aligned(_initial_heap_byte_size), NewSize);
      max_new_size = MAX2(max_new_size, NewSize);
    } else {
      // For the case where NewSize is the default, use NewRatio
      // to size the minimum and initial generation sizes.
      // Use the default NewSize as the floor for these values.  If
      // NewRatio is overly large, the resulting sizes can be too
      // small.
      _min_gen0_size = MAX2(scale_by_NewRatio_aligned(_min_heap_byte_size), NewSize);
      desired_new_size =
        MAX2(scale_by_NewRatio_aligned(_initial_heap_byte_size), NewSize);
    }

    assert(_min_gen0_size > 0, "Sanity check");
    _initial_gen0_size = desired_new_size;
    _max_gen0_size = max_new_size;

    // At this point the desirable initial and minimum sizes have been
    // determined without regard to the maximum sizes.

    // Bound the sizes by the corresponding overall heap sizes.
    _min_gen0_size = bound_minus_alignment(_min_gen0_size, _min_heap_byte_size);
    _initial_gen0_size = bound_minus_alignment(_initial_gen0_size, _initial_heap_byte_size);
    _max_gen0_size = bound_minus_alignment(_max_gen0_size, _max_heap_byte_size);

    // At this point all three sizes have been checked against the
    // maximum sizes but have not been checked for consistency
    // among the three.

    // Final check min <= initial <= max
    _min_gen0_size = MIN2(_min_gen0_size, _max_gen0_size);
    _initial_gen0_size = MAX2(MIN2(_initial_gen0_size, _max_gen0_size), _min_gen0_size);
    _min_gen0_size = MIN2(_min_gen0_size, _initial_gen0_size);
  }

  // Write back to flags if necessary
  if (NewSize != _initial_gen0_size) {
    FLAG_SET_ERGO(uintx, NewSize, _initial_gen0_size);
  }

  if (MaxNewSize != _max_gen0_size) {
    FLAG_SET_ERGO(uintx, MaxNewSize, _max_gen0_size);
  }

  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print_cr("1: Minimum gen0 " SIZE_FORMAT "  Initial gen0 "
      SIZE_FORMAT "  Maximum gen0 " SIZE_FORMAT,
      _min_gen0_size, _initial_gen0_size, _max_gen0_size);
  }

  DEBUG_ONLY(GenCollectorPolicy::assert_size_info();)
}

// Call this method during the sizing of the gen1 to make
// adjustments to gen0 because of gen1 sizing policy.  gen0 initially has
// the most freedom in sizing because it is done before the
// policy for gen1 is applied.  Once gen1 policies have been applied,
// there may be conflicts in the shape of the heap and this method
// is used to make the needed adjustments.  The application of the
// policies could be more sophisticated (iterative for example) but
// keeping it simple also seems a worthwhile goal.
bool TwoGenerationCollectorPolicy::adjust_gen0_sizes(size_t* gen0_size_ptr,
                                                     size_t* gen1_size_ptr,
                                                     const size_t heap_size) {
  bool result = false;

  if ((*gen0_size_ptr + *gen1_size_ptr) > heap_size) {
    uintx smallest_new_size = young_gen_size_lower_bound();
    if ((heap_size < (*gen0_size_ptr + _min_gen1_size)) &&
        (heap_size >= _min_gen1_size + smallest_new_size)) {
      // Adjust gen0 down to accommodate _min_gen1_size
      *gen0_size_ptr = align_size_down_bounded(heap_size - _min_gen1_size, _gen_alignment);
      result = true;
    } else {
      *gen1_size_ptr = align_size_down_bounded(heap_size - *gen0_size_ptr, _gen_alignment);
    }
  }
  return result;
}

// Minimum sizes of the generations may be different than
// the initial sizes.  An inconsistently is permitted here
// in the total size that can be specified explicitly by
// command line specification of OldSize and NewSize and
// also a command line specification of -Xms.  Issue a warning
// but allow the values to pass.

void TwoGenerationCollectorPolicy::initialize_size_info() {
  GenCollectorPolicy::initialize_size_info();

  // At this point the minimum, initial and maximum sizes
  // of the overall heap and of gen0 have been determined.
  // The maximum gen1 size can be determined from the maximum gen0
  // and maximum heap size since no explicit flags exits
  // for setting the gen1 maximum.
  _max_gen1_size = MAX2(_max_heap_byte_size - _max_gen0_size, _gen_alignment);

  // If no explicit command line flag has been set for the
  // gen1 size, use what is left for gen1.
  if (!FLAG_IS_CMDLINE(OldSize)) {
    // The user has not specified any value but the ergonomics
    // may have chosen a value (which may or may not be consistent
    // with the overall heap size).  In either case make
    // the minimum, maximum and initial sizes consistent
    // with the gen0 sizes and the overall heap sizes.
    _min_gen1_size = MAX2(_min_heap_byte_size - _min_gen0_size, _gen_alignment);
    _initial_gen1_size = MAX2(_initial_heap_byte_size - _initial_gen0_size, _gen_alignment);
    // _max_gen1_size has already been made consistent above
    FLAG_SET_ERGO(uintx, OldSize, _initial_gen1_size);
  } else {
    // It's been explicitly set on the command line.  Use the
    // OldSize and then determine the consequences.
    _min_gen1_size = MIN2(OldSize, _min_heap_byte_size - _min_gen0_size);
    _initial_gen1_size = OldSize;

    // If the user has explicitly set an OldSize that is inconsistent
    // with other command line flags, issue a warning.
    // The generation minimums and the overall heap mimimum should
    // be within one generation alignment.
    if ((_min_gen1_size + _min_gen0_size + _gen_alignment) < _min_heap_byte_size) {
      warning("Inconsistency between minimum heap size and minimum "
              "generation sizes: using minimum heap = " SIZE_FORMAT,
              _min_heap_byte_size);
    }
    if (OldSize > _max_gen1_size) {
      warning("Inconsistency between maximum heap size and maximum "
              "generation sizes: using maximum heap = " SIZE_FORMAT
              " -XX:OldSize flag is being ignored",
              _max_heap_byte_size);
    }
    // If there is an inconsistency between the OldSize and the minimum and/or
    // initial size of gen0, since OldSize was explicitly set, OldSize wins.
    if (adjust_gen0_sizes(&_min_gen0_size, &_min_gen1_size, _min_heap_byte_size)) {
      if (PrintGCDetails && Verbose) {
        gclog_or_tty->print_cr("2: Minimum gen0 " SIZE_FORMAT "  Initial gen0 "
              SIZE_FORMAT "  Maximum gen0 " SIZE_FORMAT,
              _min_gen0_size, _initial_gen0_size, _max_gen0_size);
      }
    }
    // Initial size
    if (adjust_gen0_sizes(&_initial_gen0_size, &_initial_gen1_size,
                          _initial_heap_byte_size)) {
      if (PrintGCDetails && Verbose) {
        gclog_or_tty->print_cr("3: Minimum gen0 " SIZE_FORMAT "  Initial gen0 "
          SIZE_FORMAT "  Maximum gen0 " SIZE_FORMAT,
          _min_gen0_size, _initial_gen0_size, _max_gen0_size);
      }
    }
  }
  // Enforce the maximum gen1 size.
  _min_gen1_size = MIN2(_min_gen1_size, _max_gen1_size);

  // Check that min gen1 <= initial gen1 <= max gen1
  _initial_gen1_size = MAX2(_initial_gen1_size, _min_gen1_size);
  _initial_gen1_size = MIN2(_initial_gen1_size, _max_gen1_size);

  // Write back to flags if necessary
  if (NewSize != _initial_gen0_size) {
    FLAG_SET_ERGO(uintx, NewSize, _initial_gen0_size);
  }

  if (MaxNewSize != _max_gen0_size) {
    FLAG_SET_ERGO(uintx, MaxNewSize, _max_gen0_size);
  }

  if (OldSize != _initial_gen1_size) {
    FLAG_SET_ERGO(uintx, OldSize, _initial_gen1_size);
  }

  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print_cr("Minimum gen1 " SIZE_FORMAT "  Initial gen1 "
      SIZE_FORMAT "  Maximum gen1 " SIZE_FORMAT,
      _min_gen1_size, _initial_gen1_size, _max_gen1_size);
  }

  DEBUG_ONLY(TwoGenerationCollectorPolicy::assert_size_info();)
}

HeapWord* GenCollectorPolicy::mem_allocate_work(size_t size,
                                        bool is_tlab,
                                        bool* gc_overhead_limit_was_exceeded) {
  GenCollectedHeap *gch = GenCollectedHeap::heap();

  debug_only(gch->check_for_valid_allocation_state());
  assert(gch->no_gc_in_progress(), "Allocation during gc not allowed");

  // In general gc_overhead_limit_was_exceeded should be false so
  // set it so here and reset it to true only if the gc time
  // limit is being exceeded as checked below.
  *gc_overhead_limit_was_exceeded = false;

  HeapWord* result = NULL;

  // Loop until the allocation is satisified,
  // or unsatisfied after GC.
  for (uint try_count = 1, gclocker_stalled_count = 0; /* return or throw */; try_count += 1) {
    HandleMark hm; // discard any handles allocated in each iteration

    // First allocation attempt is lock-free.
    Generation *gen0 = gch->get_gen(0);
    assert(gen0->supports_inline_contig_alloc(),
      "Otherwise, must do alloc within heap lock");
    if (gen0->should_allocate(size, is_tlab)) {
      result = gen0->par_allocate(size, is_tlab);
      if (result != NULL) {
        assert(gch->is_in_reserved(result), "result not in heap");
        return result;
      }
    }
    uint gc_count_before;  // read inside the Heap_lock locked region
    {
      MutexLocker ml(Heap_lock);
      if (PrintGC && Verbose) {
        gclog_or_tty->print_cr("TwoGenerationCollectorPolicy::mem_allocate_work:"
                      " attempting locked slow path allocation");
      }
      // Note that only large objects get a shot at being
      // allocated in later generations.
      bool first_only = ! should_try_older_generation_allocation(size);

      result = gch->attempt_allocation(size, is_tlab, first_only);
      if (result != NULL) {
        assert(gch->is_in_reserved(result), "result not in heap");
        return result;
      }

      if (GC_locker::is_active_and_needs_gc()) {
        if (is_tlab) {
          return NULL;  // Caller will retry allocating individual object
        }
        if (!gch->is_maximal_no_gc()) {
          // Try and expand heap to satisfy request
          result = expand_heap_and_allocate(size, is_tlab);
          // result could be null if we are out of space
          if (result != NULL) {
            return result;
          }
        }

        if (gclocker_stalled_count > GCLockerRetryAllocationCount) {
          return NULL; // we didn't get to do a GC and we didn't get any memory
        }

        // If this thread is not in a jni critical section, we stall
        // the requestor until the critical section has cleared and
        // GC allowed. When the critical section clears, a GC is
        // initiated by the last thread exiting the critical section; so
        // we retry the allocation sequence from the beginning of the loop,
        // rather than causing more, now probably unnecessary, GC attempts.
        JavaThread* jthr = JavaThread::current();
        if (!jthr->in_critical()) {
          MutexUnlocker mul(Heap_lock);
          // Wait for JNI critical section to be exited
          GC_locker::stall_until_clear();
          gclocker_stalled_count += 1;
          continue;
        } else {
          if (CheckJNICalls) {
            fatal("Possible deadlock due to allocating while"
                  " in jni critical section");
          }
          return NULL;
        }
      }

      // Read the gc count while the heap lock is held.
      gc_count_before = Universe::heap()->total_collections();
    }

    VM_GenCollectForAllocation op(size, is_tlab, gc_count_before);
    VMThread::execute(&op);
    if (op.prologue_succeeded()) {
      result = op.result();
      if (op.gc_locked()) {
         assert(result == NULL, "must be NULL if gc_locked() is true");
         continue;  // retry and/or stall as necessary
      }

      // Allocation has failed and a collection
      // has been done.  If the gc time limit was exceeded the
      // this time, return NULL so that an out-of-memory
      // will be thrown.  Clear gc_overhead_limit_exceeded
      // so that the overhead exceeded does not persist.

      const bool limit_exceeded = size_policy()->gc_overhead_limit_exceeded();
      const bool softrefs_clear = all_soft_refs_clear();

      if (limit_exceeded && softrefs_clear) {
        *gc_overhead_limit_was_exceeded = true;
        size_policy()->set_gc_overhead_limit_exceeded(false);
        if (op.result() != NULL) {
          CollectedHeap::fill_with_object(op.result(), size);
        }
        return NULL;
      }
      assert(result == NULL || gch->is_in_reserved(result),
             "result not in heap");
      return result;
    }

    // Give a warning if we seem to be looping forever.
    if ((QueuedAllocationWarningCount > 0) &&
        (try_count % QueuedAllocationWarningCount == 0)) {
          warning("TwoGenerationCollectorPolicy::mem_allocate_work retries %d times \n\t"
                  " size=" SIZE_FORMAT " %s", try_count, size, is_tlab ? "(TLAB)" : "");
    }
  }
}

HeapWord* GenCollectorPolicy::expand_heap_and_allocate(size_t size,
                                                       bool   is_tlab) {
  GenCollectedHeap *gch = GenCollectedHeap::heap();
  HeapWord* result = NULL;
  for (int i = number_of_generations() - 1; i >= 0 && result == NULL; i--) {
    Generation *gen = gch->get_gen(i);
    if (gen->should_allocate(size, is_tlab)) {
      result = gen->expand_and_allocate(size, is_tlab);
    }
  }
  assert(result == NULL || gch->is_in_reserved(result), "result not in heap");
  return result;
}

HeapWord* GenCollectorPolicy::satisfy_failed_allocation(size_t size,
                                                        bool   is_tlab) {
  GenCollectedHeap *gch = GenCollectedHeap::heap();
  GCCauseSetter x(gch, GCCause::_allocation_failure);
  HeapWord* result = NULL;

  assert(size != 0, "Precondition violated");
  if (GC_locker::is_active_and_needs_gc()) {
    // GC locker is active; instead of a collection we will attempt
    // to expand the heap, if there's room for expansion.
    if (!gch->is_maximal_no_gc()) {
      result = expand_heap_and_allocate(size, is_tlab);
    }
    return result;   // could be null if we are out of space
  } else if (!gch->incremental_collection_will_fail(false /* don't consult_young */)) {
    // Do an incremental collection.
    gch->do_collection(false            /* full */,
                       false            /* clear_all_soft_refs */,
                       size             /* size */,
                       is_tlab          /* is_tlab */,
                       number_of_generations() - 1 /* max_level */);
  } else {
    if (Verbose && PrintGCDetails) {
      gclog_or_tty->print(" :: Trying full because partial may fail :: ");
    }
    // Try a full collection; see delta for bug id 6266275
    // for the original code and why this has been simplified
    // with from-space allocation criteria modified and
    // such allocation moved out of the safepoint path.
    gch->do_collection(true             /* full */,
                       false            /* clear_all_soft_refs */,
                       size             /* size */,
                       is_tlab          /* is_tlab */,
                       number_of_generations() - 1 /* max_level */);
  }

  result = gch->attempt_allocation(size, is_tlab, false /*first_only*/);

  if (result != NULL) {
    assert(gch->is_in_reserved(result), "result not in heap");
    return result;
  }

  // OK, collection failed, try expansion.
  result = expand_heap_and_allocate(size, is_tlab);
  if (result != NULL) {
    return result;
  }

  // If we reach this point, we're really out of memory. Try every trick
  // we can to reclaim memory. Force collection of soft references. Force
  // a complete compaction of the heap. Any additional methods for finding
  // free memory should be here, especially if they are expensive. If this
  // attempt fails, an OOM exception will be thrown.
  {
    UIntFlagSetting flag_change(MarkSweepAlwaysCompactCount, 1); // Make sure the heap is fully compacted

    gch->do_collection(true             /* full */,
                       true             /* clear_all_soft_refs */,
                       size             /* size */,
                       is_tlab          /* is_tlab */,
                       number_of_generations() - 1 /* max_level */);
  }

  result = gch->attempt_allocation(size, is_tlab, false /* first_only */);
  if (result != NULL) {
    assert(gch->is_in_reserved(result), "result not in heap");
    return result;
  }

  assert(!should_clear_all_soft_refs(),
    "Flag should have been handled and cleared prior to this point");

  // What else?  We might try synchronous finalization later.  If the total
  // space available is large enough for the allocation, then a more
  // complete compaction phase than we've tried so far might be
  // appropriate.
  return NULL;
}

MetaWord* CollectorPolicy::satisfy_failed_metadata_allocation(
                                                 ClassLoaderData* loader_data,
                                                 size_t word_size,
                                                 Metaspace::MetadataType mdtype) {
  uint loop_count = 0;
  uint gc_count = 0;
  uint full_gc_count = 0;

  assert(!Heap_lock->owned_by_self(), "Should not be holding the Heap_lock");

  do {
    MetaWord* result = NULL;
    if (GC_locker::is_active_and_needs_gc()) {
      // If the GC_locker is active, just expand and allocate.
      // If that does not succeed, wait if this thread is not
      // in a critical section itself.
      result =
        loader_data->metaspace_non_null()->expand_and_allocate(word_size,
                                                               mdtype);
      if (result != NULL) {
        return result;
      }
      JavaThread* jthr = JavaThread::current();
      if (!jthr->in_critical()) {
        // Wait for JNI critical section to be exited
        GC_locker::stall_until_clear();
        // The GC invoked by the last thread leaving the critical
        // section will be a young collection and a full collection
        // is (currently) needed for unloading classes so continue
        // to the next iteration to get a full GC.
        continue;
      } else {
        if (CheckJNICalls) {
          fatal("Possible deadlock due to allocating while"
                " in jni critical section");
        }
        return NULL;
      }
    }

    {  // Need lock to get self consistent gc_count's
      MutexLocker ml(Heap_lock);
      gc_count      = Universe::heap()->total_collections();
      full_gc_count = Universe::heap()->total_full_collections();
    }

    // Generate a VM operation
    VM_CollectForMetadataAllocation op(loader_data,
                                       word_size,
                                       mdtype,
                                       gc_count,
                                       full_gc_count,
                                       GCCause::_metadata_GC_threshold);
    VMThread::execute(&op);

    // If GC was locked out, try again.  Check
    // before checking success because the prologue
    // could have succeeded and the GC still have
    // been locked out.
    if (op.gc_locked()) {
      continue;
    }

    if (op.prologue_succeeded()) {
      return op.result();
    }
    loop_count++;
    if ((QueuedAllocationWarningCount > 0) &&
        (loop_count % QueuedAllocationWarningCount == 0)) {
      warning("satisfy_failed_metadata_allocation() retries %d times \n\t"
              " size=" SIZE_FORMAT, loop_count, word_size);
    }
  } while (true);  // Until a GC is done
}

// Return true if any of the following is true:
// . the allocation won't fit into the current young gen heap
// . gc locker is occupied (jni critical section)
// . heap memory is tight -- the most recent previous collection
//   was a full collection because a partial collection (would
//   have) failed and is likely to fail again
bool GenCollectorPolicy::should_try_older_generation_allocation(
        size_t word_size) const {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  size_t gen0_capacity = gch->get_gen(0)->capacity_before_gc();
  return    (word_size > heap_word_size(gen0_capacity))
         || GC_locker::is_active_and_needs_gc()
         || gch->incremental_collection_failed();
}


//
// MarkSweepPolicy methods
//

void MarkSweepPolicy::initialize_alignments() {
  _space_alignment = _gen_alignment = (uintx)Generation::GenGrain;
  _heap_alignment = compute_heap_alignment();
}

void MarkSweepPolicy::initialize_generations() {
  _generations = NEW_C_HEAP_ARRAY3(GenerationSpecPtr, number_of_generations(), mtGC, CURRENT_PC,
    AllocFailStrategy::RETURN_NULL);
  if (_generations == NULL) {
    vm_exit_during_initialization("Unable to allocate gen spec");
  }

  if (UseParNewGC) {
    _generations[0] = new GenerationSpec(Generation::ParNew, _initial_gen0_size, _max_gen0_size);
  } else {
    _generations[0] = new GenerationSpec(Generation::DefNew, _initial_gen0_size, _max_gen0_size);
  }
  _generations[1] = new GenerationSpec(Generation::MarkSweepCompact, _initial_gen1_size, _max_gen1_size);

  if (_generations[0] == NULL || _generations[1] == NULL) {
    vm_exit_during_initialization("Unable to allocate gen spec");
  }
}

void MarkSweepPolicy::initialize_gc_policy_counters() {
  // initialize the policy counters - 2 collectors, 3 generations
  if (UseParNewGC) {
    _gc_policy_counters = new GCPolicyCounters("ParNew:MSC", 2, 3);
  } else {
    _gc_policy_counters = new GCPolicyCounters("Copy:MSC", 2, 3);
  }
}

/////////////// Unit tests ///////////////

#ifndef PRODUCT
// Testing that the NewSize flag is handled correct is hard because it
// depends on so many other configurable variables. This test only tries to
// verify that there are some basic rules for NewSize honored by the policies.
class TestGenCollectorPolicy {
public:
  static void test() {
    size_t flag_value;

    save_flags();

    // Set some limits that makes the math simple.
    FLAG_SET_ERGO(uintx, MaxHeapSize, 180 * M);
    FLAG_SET_ERGO(uintx, InitialHeapSize, 120 * M);
    Arguments::set_min_heap_size(40 * M);

    // If NewSize is set on the command line, it should be used
    // for both min and initial young size if less than min heap.
    flag_value = 20 * M;
    FLAG_SET_CMDLINE(uintx, NewSize, flag_value);
    verify_min(flag_value);
    verify_initial(flag_value);

    // If NewSize is set on command line, but is larger than the min
    // heap size, it should only be used for initial young size.
    flag_value = 80 * M;
    FLAG_SET_CMDLINE(uintx, NewSize, flag_value);
    verify_initial(flag_value);

    // If NewSize has been ergonomically set, the collector policy
    // should use it for min but calculate the initial young size
    // using NewRatio.
    flag_value = 20 * M;
    FLAG_SET_ERGO(uintx, NewSize, flag_value);
    verify_min(flag_value);
    verify_scaled_initial(InitialHeapSize);

    restore_flags();

  }

  static void verify_min(size_t expected) {
    MarkSweepPolicy msp;
    msp.initialize_all();

    assert(msp.min_gen0_size() <= expected, err_msg("%zu  > %zu", msp.min_gen0_size(), expected));
  }

  static void verify_initial(size_t expected) {
    MarkSweepPolicy msp;
    msp.initialize_all();

    assert(msp.initial_gen0_size() == expected, err_msg("%zu != %zu", msp.initial_gen0_size(), expected));
  }

  static void verify_scaled_initial(size_t initial_heap_size) {
    MarkSweepPolicy msp;
    msp.initialize_all();

    size_t expected = msp.scale_by_NewRatio_aligned(initial_heap_size);
    assert(msp.initial_gen0_size() == expected, err_msg("%zu != %zu", msp.initial_gen0_size(), expected));
    assert(FLAG_IS_ERGO(NewSize) && NewSize == expected,
        err_msg("NewSize should have been set ergonomically to %zu, but was %zu", expected, NewSize));
  }

private:
  static size_t original_InitialHeapSize;
  static size_t original_MaxHeapSize;
  static size_t original_MaxNewSize;
  static size_t original_MinHeapDeltaBytes;
  static size_t original_NewSize;
  static size_t original_OldSize;

  static void save_flags() {
    original_InitialHeapSize   = InitialHeapSize;
    original_MaxHeapSize       = MaxHeapSize;
    original_MaxNewSize        = MaxNewSize;
    original_MinHeapDeltaBytes = MinHeapDeltaBytes;
    original_NewSize           = NewSize;
    original_OldSize           = OldSize;
  }

  static void restore_flags() {
    InitialHeapSize   = original_InitialHeapSize;
    MaxHeapSize       = original_MaxHeapSize;
    MaxNewSize        = original_MaxNewSize;
    MinHeapDeltaBytes = original_MinHeapDeltaBytes;
    NewSize           = original_NewSize;
    OldSize           = original_OldSize;
  }
};

size_t TestGenCollectorPolicy::original_InitialHeapSize   = 0;
size_t TestGenCollectorPolicy::original_MaxHeapSize       = 0;
size_t TestGenCollectorPolicy::original_MaxNewSize        = 0;
size_t TestGenCollectorPolicy::original_MinHeapDeltaBytes = 0;
size_t TestGenCollectorPolicy::original_NewSize           = 0;
size_t TestGenCollectorPolicy::original_OldSize           = 0;

void TestNewSize_test() {
  TestGenCollectorPolicy::test();
}

#endif
