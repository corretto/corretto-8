/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoader.hpp"
#include "classfile/javaClasses.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "memory/gcLocker.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/oopFactory.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/preserveException.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#endif // INCLUDE_ALL_GCS

#ifndef USDT2
HS_DTRACE_PROBE_DECL1(hotspot, gc__begin, bool);
HS_DTRACE_PROBE_DECL(hotspot, gc__end);
#endif /* !USDT2 */

// The same dtrace probe can't be inserted in two different files, so we
// have to call it here, so it's only in one file.  Can't create new probes
// for the other file anymore.   The dtrace probes have to remain stable.
void VM_GC_Operation::notify_gc_begin(bool full) {
#ifndef USDT2
  HS_DTRACE_PROBE1(hotspot, gc__begin, full);
  HS_DTRACE_WORKAROUND_TAIL_CALL_BUG();
#else /* USDT2 */
  HOTSPOT_GC_BEGIN(
                   full);
#endif /* USDT2 */
}

void VM_GC_Operation::notify_gc_end() {
#ifndef USDT2
  HS_DTRACE_PROBE(hotspot, gc__end);
  HS_DTRACE_WORKAROUND_TAIL_CALL_BUG();
#else /* USDT2 */
  HOTSPOT_GC_END(
);
#endif /* USDT2 */
}

void VM_GC_Operation::acquire_pending_list_lock() {
  // we may enter this with pending exception set
  InstanceRefKlass::acquire_pending_list_lock(&_pending_list_basic_lock);
}


void VM_GC_Operation::release_and_notify_pending_list_lock() {

  InstanceRefKlass::release_and_notify_pending_list_lock(&_pending_list_basic_lock);
}

// Allocations may fail in several threads at about the same time,
// resulting in multiple gc requests.  We only want to do one of them.
// In case a GC locker is active and the need for a GC is already signalled,
// we want to skip this GC attempt altogether, without doing a futile
// safepoint operation.
bool VM_GC_Operation::skip_operation() const {
  bool skip = (_gc_count_before != Universe::heap()->total_collections());
  if (_full && skip) {
    skip = (_full_gc_count_before != Universe::heap()->total_full_collections());
  }
  if (!skip && GC_locker::is_active_and_needs_gc()) {
    skip = Universe::heap()->is_maximal_no_gc();
    assert(!(skip && (_gc_cause == GCCause::_gc_locker)),
           "GC_locker cannot be active when initiating GC");
  }
  return skip;
}

bool VM_GC_Operation::doit_prologue() {
  assert(Thread::current()->is_Java_thread(), "just checking");
  assert(((_gc_cause != GCCause::_no_gc) &&
          (_gc_cause != GCCause::_no_cause_specified)), "Illegal GCCause");

  // To be able to handle a GC the VM initialization needs to be completed.
  if (!is_init_completed()) {
    vm_exit_during_initialization(
      err_msg("GC triggered before VM initialization completed. Try increasing "
              "NewSize, current value " UINTX_FORMAT "%s.",
              byte_size_in_proper_unit(NewSize),
              proper_unit_for_byte_size(NewSize)));
  }

  acquire_pending_list_lock();
  // If the GC count has changed someone beat us to the collection
  // Get the Heap_lock after the pending_list_lock.
  Heap_lock->lock();

  // Check invocations
  if (skip_operation()) {
    // skip collection
    Heap_lock->unlock();
    release_and_notify_pending_list_lock();
    _prologue_succeeded = false;
  } else {
    _prologue_succeeded = true;
    SharedHeap* sh = SharedHeap::heap();
    if (sh != NULL) sh->_thread_holds_heap_lock_for_gc = true;
  }
  return _prologue_succeeded;
}


void VM_GC_Operation::doit_epilogue() {
  assert(Thread::current()->is_Java_thread(), "just checking");
  // Release the Heap_lock first.
  SharedHeap* sh = SharedHeap::heap();
  if (sh != NULL) sh->_thread_holds_heap_lock_for_gc = false;
  Heap_lock->unlock();
  release_and_notify_pending_list_lock();
}

bool VM_GC_HeapInspection::doit_prologue() {
  if (Universe::heap()->supports_heap_inspection()) {
    return VM_GC_Operation::doit_prologue();
  } else {
    return false;
  }
}

bool VM_GC_HeapInspection::skip_operation() const {
  assert(Universe::heap()->supports_heap_inspection(), "huh?");
  return false;
}

bool VM_GC_HeapInspection::collect() {
  if (GC_locker::is_active()) {
    return false;
  }
  Universe::heap()->collect_as_vm_thread(GCCause::_heap_inspection);
  return true;
}

void VM_GC_HeapInspection::doit() {
  HandleMark hm;
  Universe::heap()->ensure_parsability(false); // must happen, even if collection does
                                               // not happen (e.g. due to GC_locker)
                                               // or _full_gc being false
  if (_full_gc) {
    if (!collect()) {
      // The collection attempt was skipped because the gc locker is held.
      // The following dump may then be a tad misleading to someone expecting
      // only live objects to show up in the dump (see CR 6944195). Just issue
      // a suitable warning in that case and do not attempt to do a collection.
      // The latter is a subtle point, because even a failed attempt
      // to GC will, in fact, induce one in the future, which we
      // probably want to avoid in this case because the GC that we may
      // be about to attempt holds value for us only
      // if it happens now and not if it happens in the eventual
      // future.
      warning("GC locker is held; pre-dump GC was skipped");
    }
  }
  HeapInspection inspect(_csv_format, _print_help, _print_class_stats,
                         _columns);
  inspect.heap_inspection(_out);
}


void VM_GenCollectForAllocation::doit() {
  SvcGCMarker sgcm(SvcGCMarker::MINOR);

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  GCCauseSetter gccs(gch, _gc_cause);
  _result = gch->satisfy_failed_allocation(_word_size, _tlab);
  assert(gch->is_in_reserved_or_null(_result), "result not in heap");

  if (_result == NULL && GC_locker::is_active_and_needs_gc()) {
    set_gc_locked();
  }
}

static bool is_full_gc(int max_level) {
  // Return true if max_level is all generations
  return (max_level == (GenCollectedHeap::heap()->n_gens() - 1));
}

VM_GenCollectFull::VM_GenCollectFull(uint gc_count_before,
                                     uint full_gc_count_before,
                                     GCCause::Cause gc_cause,
                                     int max_level) :
  VM_GC_Operation(gc_count_before, gc_cause, full_gc_count_before,
                  is_full_gc(max_level) /* full */),
  _max_level(max_level) { }

void VM_GenCollectFull::doit() {
  SvcGCMarker sgcm(SvcGCMarker::FULL);

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  GCCauseSetter gccs(gch, _gc_cause);
  gch->do_full_collection(gch->must_clear_all_soft_refs(), _max_level);
}

VM_CollectForMetadataAllocation::VM_CollectForMetadataAllocation(ClassLoaderData* loader_data,
                                                                 size_t size,
                                                                 Metaspace::MetadataType mdtype,
                                                                 uint gc_count_before,
                                                                 uint full_gc_count_before,
                                                                 GCCause::Cause gc_cause)
    : VM_GC_Operation(gc_count_before, gc_cause, full_gc_count_before, true),
      _loader_data(loader_data), _size(size), _mdtype(mdtype), _result(NULL) {
  assert(_size != 0, "An allocation should always be requested with this operation.");
  AllocTracer::send_allocation_requiring_gc_event(_size * HeapWordSize, GCId::peek());
}

// Returns true iff concurrent GCs unloads metadata.
bool VM_CollectForMetadataAllocation::initiate_concurrent_GC() {
#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC && CMSClassUnloadingEnabled) {
    MetaspaceGC::set_should_concurrent_collect(true);
    return true;
  }

  if (UseG1GC && ClassUnloadingWithConcurrentMark) {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    g1h->g1_policy()->set_initiate_conc_mark_if_possible();

    GCCauseSetter x(g1h, _gc_cause);

    // At this point we are supposed to start a concurrent cycle. We
    // will do so if one is not already in progress.
    bool should_start = g1h->g1_policy()->force_initial_mark_if_outside_cycle(_gc_cause);

    if (should_start) {
      double pause_target = g1h->g1_policy()->max_pause_time_ms();
      g1h->do_collection_pause_at_safepoint(pause_target);
    }
    return true;
  }
#endif

  return false;
}

static void log_metaspace_alloc_failure_for_concurrent_GC() {
  if (Verbose && PrintGCDetails) {
    if (UseConcMarkSweepGC) {
      gclog_or_tty->print_cr("\nCMS full GC for Metaspace");
    } else if (UseG1GC) {
      gclog_or_tty->print_cr("\nG1 full GC for Metaspace");
    }
  }
}

void VM_CollectForMetadataAllocation::doit() {
  SvcGCMarker sgcm(SvcGCMarker::FULL);

  CollectedHeap* heap = Universe::heap();
  GCCauseSetter gccs(heap, _gc_cause);

  // Check again if the space is available.  Another thread
  // may have similarly failed a metadata allocation and induced
  // a GC that freed space for the allocation.
  if (!MetadataAllocationFailALot) {
    _result = _loader_data->metaspace_non_null()->allocate(_size, _mdtype);
    if (_result != NULL) {
      return;
    }
  }

  if (initiate_concurrent_GC()) {
    // For CMS and G1 expand since the collection is going to be concurrent.
    _result = _loader_data->metaspace_non_null()->expand_and_allocate(_size, _mdtype);
    if (_result != NULL) {
      return;
    }

    log_metaspace_alloc_failure_for_concurrent_GC();
  }

  // Don't clear the soft refs yet.
  heap->collect_as_vm_thread(GCCause::_metadata_GC_threshold);
  // After a GC try to allocate without expanding.  Could fail
  // and expansion will be tried below.
  _result = _loader_data->metaspace_non_null()->allocate(_size, _mdtype);
  if (_result != NULL) {
    return;
  }

  // If still failing, allow the Metaspace to expand.
  // See delta_capacity_until_GC() for explanation of the
  // amount of the expansion.
  // This should work unless there really is no more space
  // or a MaxMetaspaceSize has been specified on the command line.
  _result = _loader_data->metaspace_non_null()->expand_and_allocate(_size, _mdtype);
  if (_result != NULL) {
    return;
  }

  // If expansion failed, do a last-ditch collection and try allocating
  // again.  A last-ditch collection will clear softrefs.  This
  // behavior is similar to the last-ditch collection done for perm
  // gen when it was full and a collection for failed allocation
  // did not free perm gen space.
  heap->collect_as_vm_thread(GCCause::_last_ditch_collection);
  _result = _loader_data->metaspace_non_null()->allocate(_size, _mdtype);
  if (_result != NULL) {
    return;
  }

  if (Verbose && PrintGCDetails) {
    gclog_or_tty->print_cr("\nAfter Metaspace GC failed to allocate size "
                           SIZE_FORMAT, _size);
  }

  if (GC_locker::is_active_and_needs_gc()) {
    set_gc_locked();
  }
}

VM_CollectForAllocation::VM_CollectForAllocation(size_t word_size, uint gc_count_before, GCCause::Cause cause)
    : VM_GC_Operation(gc_count_before, cause), _result(NULL), _word_size(word_size) {
  // Only report if operation was really caused by an allocation.
  if (_word_size != 0) {
    AllocTracer::send_allocation_requiring_gc_event(_word_size * HeapWordSize, GCId::peek());
  }
}
