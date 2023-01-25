/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/sharedHeap.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/java.hpp"
#include "utilities/copy.hpp"
#include "utilities/workgroup.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

SharedHeap* SharedHeap::_sh;

SharedHeap::SharedHeap(CollectorPolicy* policy_) :
  CollectedHeap(),
  _collector_policy(policy_),
  _rem_set(NULL),
  _strong_roots_parity(0),
  _workers(NULL)
{
  _sh = this;  // ch is static, should be set only once.
  if ((UseParNewGC ||
      (UseConcMarkSweepGC && (CMSParallelInitialMarkEnabled ||
                              CMSParallelRemarkEnabled)) ||
       UseG1GC) &&
      ParallelGCThreads > 0) {
    _workers = new FlexibleWorkGang("Parallel GC Threads", ParallelGCThreads,
                            /* are_GC_task_threads */true,
                            /* are_ConcurrentGC_threads */false);
    if (_workers == NULL) {
      vm_exit_during_initialization("Failed necessary allocation.");
    } else {
      _workers->initialize_workers();
    }
  }
}

bool SharedHeap::heap_lock_held_for_gc() {
  Thread* t = Thread::current();
  return    Heap_lock->owned_by_self()
         || (   (t->is_GC_task_thread() ||  t->is_VM_thread())
             && _thread_holds_heap_lock_for_gc);
}

void SharedHeap::set_par_threads(uint t) {
  assert(t == 0 || !UseSerialGC, "Cannot have parallel threads");
  _n_par_threads = t;
}

void SharedHeap::change_strong_roots_parity() {
  // Also set the new collection parity.
  assert(_strong_roots_parity >= 0 && _strong_roots_parity <= 2,
         "Not in range.");
  _strong_roots_parity++;
  if (_strong_roots_parity == 3) _strong_roots_parity = 1;
  assert(_strong_roots_parity >= 1 && _strong_roots_parity <= 2,
         "Not in range.");
}

SharedHeap::StrongRootsScope::StrongRootsScope(SharedHeap* heap, bool activate)
  : MarkScope(activate), _sh(heap)
{
  if (_active) {
    _sh->change_strong_roots_parity();
    // Zero the claimed high water mark in the StringTable
    StringTable::clear_parallel_claimed_index();
  }
}

void SharedHeap::set_barrier_set(BarrierSet* bs) {
  _barrier_set = bs;
  // Cached barrier set for fast access in oops
  oopDesc::set_bs(bs);
}

void SharedHeap::post_initialize() {
  CollectedHeap::post_initialize();
  ref_processing_init();
}

void SharedHeap::ref_processing_init() {}

// Some utilities.
void SharedHeap::print_size_transition(outputStream* out,
                                       size_t bytes_before,
                                       size_t bytes_after,
                                       size_t capacity) {
  out->print(" %d%s->%d%s(%d%s)",
             byte_size_in_proper_unit(bytes_before),
             proper_unit_for_byte_size(bytes_before),
             byte_size_in_proper_unit(bytes_after),
             proper_unit_for_byte_size(bytes_after),
             byte_size_in_proper_unit(capacity),
             proper_unit_for_byte_size(capacity));
}
