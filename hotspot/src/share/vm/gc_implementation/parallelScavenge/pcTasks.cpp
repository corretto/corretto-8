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
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc_implementation/parallelScavenge/pcTasks.hpp"
#include "gc_implementation/parallelScavenge/psParallelCompact.hpp"
#include "gc_implementation/shared/gcTimer.hpp"
#include "gc_implementation/shared/gcTraceTime.hpp"
#include "gc_interface/collectedHeap.hpp"
#include "memory/universe.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "services/management.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

//
// ThreadRootsMarkingTask
//

void ThreadRootsMarkingTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  ResourceMark rm;

  NOT_PRODUCT(GCTraceTime tm("ThreadRootsMarkingTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, NULL, PSParallelCompact::gc_tracer()->gc_id()));
  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);
  CLDToOopClosure mark_and_push_from_clds(&mark_and_push_closure, true);
  MarkingCodeBlobClosure mark_and_push_in_blobs(&mark_and_push_closure, !CodeBlobToOopClosure::FixRelocations);

  if (_java_thread != NULL)
    _java_thread->oops_do(
        &mark_and_push_closure,
        &mark_and_push_from_clds,
        &mark_and_push_in_blobs);

  if (_vm_thread != NULL)
    _vm_thread->oops_do(
        &mark_and_push_closure,
        &mark_and_push_from_clds,
        &mark_and_push_in_blobs);

  // Do the real work
  cm->follow_marking_stacks();
}


void MarkFromRootsTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(GCTraceTime tm("MarkFromRootsTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, NULL, PSParallelCompact::gc_tracer()->gc_id()));
  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);
  PSParallelCompact::FollowKlassClosure follow_klass_closure(&mark_and_push_closure);

  switch (_root_type) {
    case universe:
      Universe::oops_do(&mark_and_push_closure);
      break;

    case jni_handles:
      JNIHandles::oops_do(&mark_and_push_closure);
      break;

    case threads:
    {
      ResourceMark rm;
      MarkingCodeBlobClosure each_active_code_blob(&mark_and_push_closure, !CodeBlobToOopClosure::FixRelocations);
      CLDToOopClosure mark_and_push_from_cld(&mark_and_push_closure);
      Threads::oops_do(&mark_and_push_closure, &mark_and_push_from_cld, &each_active_code_blob);
    }
    break;

    case object_synchronizer:
      ObjectSynchronizer::oops_do(&mark_and_push_closure);
      break;

    case flat_profiler:
      FlatProfiler::oops_do(&mark_and_push_closure);
      break;

    case management:
      Management::oops_do(&mark_and_push_closure);
      break;

    case jvmti:
      JvmtiExport::oops_do(&mark_and_push_closure);
      break;

    case system_dictionary:
      SystemDictionary::always_strong_oops_do(&mark_and_push_closure);
      break;

    case class_loader_data:
      ClassLoaderDataGraph::always_strong_oops_do(&mark_and_push_closure, &follow_klass_closure, true);
      break;

    case code_cache:
      // Do not treat nmethods as strong roots for mark/sweep, since we can unload them.
      //CodeCache::scavenge_root_nmethods_do(CodeBlobToOopClosure(&mark_and_push_closure));
      break;

    default:
      fatal("Unknown root type");
  }

  // Do the real work
  cm->follow_marking_stacks();
}


//
// RefProcTaskProxy
//

void RefProcTaskProxy::do_it(GCTaskManager* manager, uint which)
{
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(GCTraceTime tm("RefProcTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, NULL, PSParallelCompact::gc_tracer()->gc_id()));
  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);
  PSParallelCompact::FollowStackClosure follow_stack_closure(cm);
  _rp_task.work(_work_id, *PSParallelCompact::is_alive_closure(),
                mark_and_push_closure, follow_stack_closure);
}

//
// RefProcTaskExecutor
//

void RefProcTaskExecutor::execute(ProcessTask& task)
{
  ParallelScavengeHeap* heap = PSParallelCompact::gc_heap();
  uint parallel_gc_threads = heap->gc_task_manager()->workers();
  uint active_gc_threads = heap->gc_task_manager()->active_workers();
  OopTaskQueueSet* qset = ParCompactionManager::stack_array();
  ParallelTaskTerminator terminator(active_gc_threads, qset);
  GCTaskQueue* q = GCTaskQueue::create();
  for(uint i=0; i<parallel_gc_threads; i++) {
    q->enqueue(new RefProcTaskProxy(task, i));
  }
  if (task.marks_oops_alive()) {
    if (parallel_gc_threads>1) {
      for (uint j=0; j<active_gc_threads; j++) {
        q->enqueue(new StealMarkingTask(&terminator));
      }
    }
  }
  PSParallelCompact::gc_task_manager()->execute_and_wait(q);
}

void RefProcTaskExecutor::execute(EnqueueTask& task)
{
  ParallelScavengeHeap* heap = PSParallelCompact::gc_heap();
  uint parallel_gc_threads = heap->gc_task_manager()->workers();
  GCTaskQueue* q = GCTaskQueue::create();
  for(uint i=0; i<parallel_gc_threads; i++) {
    q->enqueue(new RefEnqueueTaskProxy(task, i));
  }
  PSParallelCompact::gc_task_manager()->execute_and_wait(q);
}

//
// StealMarkingTask
//

StealMarkingTask::StealMarkingTask(ParallelTaskTerminator* t) :
  _terminator(t) {}

void StealMarkingTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(GCTraceTime tm("StealMarkingTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, NULL, PSParallelCompact::gc_tracer()->gc_id()));

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);

  oop obj = NULL;
  ObjArrayTask task;
  int random_seed = 17;
  do {
    while (ParCompactionManager::steal_objarray(which, &random_seed, task)) {
      ObjArrayKlass* k = (ObjArrayKlass*)task.obj()->klass();
      k->oop_follow_contents(cm, task.obj(), task.index());
      cm->follow_marking_stacks();
    }
    while (ParCompactionManager::steal(which, &random_seed, obj)) {
      obj->follow_contents(cm);
      cm->follow_marking_stacks();
    }
  } while (!terminator()->offer_termination());
}

//
// StealRegionCompactionTask
//

StealRegionCompactionTask::StealRegionCompactionTask(ParallelTaskTerminator* t):
  _terminator(t) {}

void StealRegionCompactionTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(GCTraceTime tm("StealRegionCompactionTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, NULL, PSParallelCompact::gc_tracer()->gc_id()));

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);


  // If not all threads are active, get a draining stack
  // from the list.  Else, just use this threads draining stack.
  uint which_stack_index;
  bool use_all_workers = manager->all_workers_active();
  if (use_all_workers) {
    which_stack_index = which;
    assert(manager->active_workers() == ParallelGCThreads,
           err_msg("all_workers_active has been incorrectly set: "
                   " active %d  ParallelGCThreads %d", manager->active_workers(),
                   ParallelGCThreads));
  } else {
    which_stack_index = ParCompactionManager::pop_recycled_stack_index();
  }

  cm->set_region_stack_index(which_stack_index);
  cm->set_region_stack(ParCompactionManager::region_list(which_stack_index));
  if (TraceDynamicGCThreads) {
    gclog_or_tty->print_cr("StealRegionCompactionTask::do_it "
                           "region_stack_index %d region_stack = 0x%x "
                           " empty (%d) use all workers %d",
    which_stack_index, ParCompactionManager::region_list(which_stack_index),
    cm->region_stack()->is_empty(),
    use_all_workers);
  }

  // Has to drain stacks first because there may be regions on
  // preloaded onto the stack and this thread may never have
  // done a draining task.  Are the draining tasks needed?

  cm->drain_region_stacks();

  size_t region_index = 0;
  int random_seed = 17;

  // If we're the termination task, try 10 rounds of stealing before
  // setting the termination flag

  while(true) {
    if (ParCompactionManager::steal(which, &random_seed, region_index)) {
      PSParallelCompact::fill_and_update_region(cm, region_index);
      cm->drain_region_stacks();
    } else {
      if (terminator()->offer_termination()) {
        break;
      }
      // Go around again.
    }
  }
  return;
}

UpdateDensePrefixTask::UpdateDensePrefixTask(
                                   PSParallelCompact::SpaceId space_id,
                                   size_t region_index_start,
                                   size_t region_index_end) :
  _space_id(space_id), _region_index_start(region_index_start),
  _region_index_end(region_index_end) {}

void UpdateDensePrefixTask::do_it(GCTaskManager* manager, uint which) {

  NOT_PRODUCT(GCTraceTime tm("UpdateDensePrefixTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, NULL, PSParallelCompact::gc_tracer()->gc_id()));

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  PSParallelCompact::update_and_deadwood_in_dense_prefix(cm,
                                                         _space_id,
                                                         _region_index_start,
                                                         _region_index_end);
}

void DrainStacksCompactionTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(GCTraceTime tm("DrainStacksCompactionTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, NULL, PSParallelCompact::gc_tracer()->gc_id()));

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  uint which_stack_index;
  bool use_all_workers = manager->all_workers_active();
  if (use_all_workers) {
    which_stack_index = which;
    assert(manager->active_workers() == ParallelGCThreads,
           err_msg("all_workers_active has been incorrectly set: "
                   " active %d  ParallelGCThreads %d", manager->active_workers(),
                   ParallelGCThreads));
  } else {
    which_stack_index = stack_index();
  }

  cm->set_region_stack(ParCompactionManager::region_list(which_stack_index));
  if (TraceDynamicGCThreads) {
    gclog_or_tty->print_cr("DrainStacksCompactionTask::do_it which = %d "
                           "which_stack_index = %d/empty(%d) "
                           "use all workers %d",
                           which, which_stack_index,
                           cm->region_stack()->is_empty(),
                           use_all_workers);
  }

  cm->set_region_stack_index(which_stack_index);

  // Process any regions already in the compaction managers stacks.
  cm->drain_region_stacks();

  assert(cm->region_stack()->is_empty(), "Not empty");

  if (!use_all_workers) {
    // Always give up the region stack.
    assert(cm->region_stack() ==
           ParCompactionManager::region_list(cm->region_stack_index()),
           "region_stack and region_stack_index are inconsistent");
    ParCompactionManager::push_recycled_stack_index(cm->region_stack_index());

    if (TraceDynamicGCThreads) {
      void* old_region_stack = (void*) cm->region_stack();
      int old_region_stack_index = cm->region_stack_index();
      gclog_or_tty->print_cr("Pushing region stack 0x%x/%d",
        old_region_stack, old_region_stack_index);
    }

    cm->set_region_stack(NULL);
    cm->set_region_stack_index((uint)max_uintx);
  }
}
