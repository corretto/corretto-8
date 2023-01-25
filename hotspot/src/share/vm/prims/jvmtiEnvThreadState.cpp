/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jvmtiEnvThreadState.hpp"
#include "prims/jvmtiEventController.inline.hpp"
#include "prims/jvmtiImpl.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/signature.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vm_operations.hpp"


///////////////////////////////////////////////////////////////
//
// class JvmtiFramePop
//

#ifndef PRODUCT
void JvmtiFramePop::print() {
  tty->print_cr("_frame_number=%d", _frame_number);
}
#endif


///////////////////////////////////////////////////////////////
//
// class JvmtiFramePops - private methods
//

void
JvmtiFramePops::set(JvmtiFramePop& fp) {
  if (_pops->find(fp.frame_number()) < 0) {
    _pops->append(fp.frame_number());
  }
}


void
JvmtiFramePops::clear(JvmtiFramePop& fp) {
  assert(_pops->length() > 0, "No more frame pops");

  _pops->remove(fp.frame_number());
}


int
JvmtiFramePops::clear_to(JvmtiFramePop& fp) {
  int cleared = 0;
  int index = 0;
  while (index < _pops->length()) {
    JvmtiFramePop pop = JvmtiFramePop(_pops->at(index));
    if (pop.above_on_stack(fp)) {
      _pops->remove_at(index);
      ++cleared;
    } else {
      ++index;
    }
  }
  return cleared;
}


///////////////////////////////////////////////////////////////
//
// class JvmtiFramePops - public methods
//

JvmtiFramePops::JvmtiFramePops() {
  _pops = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<int> (2, true);
}

JvmtiFramePops::~JvmtiFramePops() {
  // return memory to c_heap.
  delete _pops;
}


#ifndef PRODUCT
void JvmtiFramePops::print() {
  ResourceMark rm;

  int n = _pops->length();
  for (int i=0; i<n; i++) {
    JvmtiFramePop fp = JvmtiFramePop(_pops->at(i));
    tty->print("%d: ", i);
    fp.print();
    tty->cr();
  }
}
#endif

///////////////////////////////////////////////////////////////
//
// class JvmtiEnvThreadState
//
// Instances of JvmtiEnvThreadState hang off of each JvmtiThreadState,
// one per JvmtiEnv.
//

JvmtiEnvThreadState::JvmtiEnvThreadState(JavaThread *thread, JvmtiEnvBase *env) :
  _event_enable() {
  _thread                 = thread;
  _env                    = (JvmtiEnv*)env;
  _next                   = NULL;
  _frame_pops             = NULL;
  _current_bci            = 0;
  _current_method_id      = NULL;
  _breakpoint_posted      = false;
  _single_stepping_posted = false;
  _agent_thread_local_storage_data = NULL;
}

JvmtiEnvThreadState::~JvmtiEnvThreadState()   {
  delete _frame_pops;
  _frame_pops = NULL;
}

// Given that a new (potential) event has come in,
// maintain the current JVMTI location on a per-thread per-env basis
// and use it to filter out duplicate events:
// - instruction rewrites
// - breakpoint followed by single step
// - single step at a breakpoint
void JvmtiEnvThreadState::compare_and_set_current_location(Method* new_method,
                                                           address new_location, jvmtiEvent event) {

  int new_bci = new_location - new_method->code_base();

  // The method is identified and stored as a jmethodID which is safe in this
  // case because the class cannot be unloaded while a method is executing.
  jmethodID new_method_id = new_method->jmethod_id();

  // the last breakpoint or single step was at this same location
  if (_current_bci == new_bci && _current_method_id == new_method_id) {
    switch (event) {
    case JVMTI_EVENT_BREAKPOINT:
      // Repeat breakpoint is complicated. If we previously posted a breakpoint
      // event at this location and if we also single stepped at this location
      // then we skip the duplicate breakpoint.
      _breakpoint_posted = _breakpoint_posted && _single_stepping_posted;
      break;
    case JVMTI_EVENT_SINGLE_STEP:
      // Repeat single step is easy: just don't post it again.
      // If step is pending for popframe then it may not be
      // a repeat step. The new_bci and method_id is same as current_bci
      // and current method_id after pop and step for recursive calls.
      // This has been handled by clearing the location
      _single_stepping_posted = true;
      break;
    default:
      assert(false, "invalid event value passed");
      break;
    }
    return;
  }

  set_current_location(new_method_id, new_bci);
  _breakpoint_posted = false;
  _single_stepping_posted = false;
}


JvmtiFramePops* JvmtiEnvThreadState::get_frame_pops() {
#ifdef ASSERT
  uint32_t debug_bits = 0;
#endif
  assert(get_thread() == Thread::current() || JvmtiEnv::is_thread_fully_suspended(get_thread(), false, &debug_bits),
         "frame pop data only accessible from same thread or while suspended");

  if (_frame_pops == NULL) {
    _frame_pops = new JvmtiFramePops();
    assert(_frame_pops != NULL, "_frame_pops != NULL");
  }
  return _frame_pops;
}


bool JvmtiEnvThreadState::has_frame_pops() {
  return _frame_pops == NULL? false : (_frame_pops->length() > 0);
}

void JvmtiEnvThreadState::set_frame_pop(int frame_number) {
#ifdef ASSERT
  uint32_t debug_bits = 0;
#endif
  assert(get_thread() == Thread::current() || JvmtiEnv::is_thread_fully_suspended(get_thread(), false, &debug_bits),
         "frame pop data only accessible from same thread or while suspended");
  JvmtiFramePop fpop(frame_number);
  JvmtiEventController::set_frame_pop(this, fpop);
}


void JvmtiEnvThreadState::clear_frame_pop(int frame_number) {
#ifdef ASSERT
  uint32_t debug_bits = 0;
#endif
  assert(get_thread() == Thread::current() || JvmtiEnv::is_thread_fully_suspended(get_thread(), false, &debug_bits),
         "frame pop data only accessible from same thread or while suspended");
  JvmtiFramePop fpop(frame_number);
  JvmtiEventController::clear_frame_pop(this, fpop);
}


void JvmtiEnvThreadState::clear_to_frame_pop(int frame_number)  {
#ifdef ASSERT
  uint32_t debug_bits = 0;
#endif
  assert(get_thread() == Thread::current() || JvmtiEnv::is_thread_fully_suspended(get_thread(), false, &debug_bits),
         "frame pop data only accessible from same thread or while suspended");
  JvmtiFramePop fpop(frame_number);
  JvmtiEventController::clear_to_frame_pop(this, fpop);
}


bool JvmtiEnvThreadState::is_frame_pop(int cur_frame_number) {
#ifdef ASSERT
  uint32_t debug_bits = 0;
#endif
  assert(get_thread() == Thread::current() || JvmtiEnv::is_thread_fully_suspended(get_thread(), false, &debug_bits),
         "frame pop data only accessible from same thread or while suspended");
  if (!get_thread()->is_interp_only_mode() || _frame_pops == NULL) {
    return false;
  }
  JvmtiFramePop fp(cur_frame_number);
  return get_frame_pops()->contains(fp);
}


class VM_GetCurrentLocation : public VM_Operation {
 private:
   JavaThread *_thread;
   jmethodID _method_id;
   int _bci;

 public:
  VM_GetCurrentLocation(JavaThread *thread) {
     _thread = thread;
   }
  VMOp_Type type() const { return VMOp_GetCurrentLocation; }
  void doit() {
    ResourceMark rmark; // _thread != Thread::current()
    RegisterMap rm(_thread, false);
    // There can be a race condition between a VM_Operation reaching a safepoint
    // and the target thread exiting from Java execution.
    // We must recheck the last Java frame still exists.
    if (!_thread->is_exiting() && _thread->has_last_Java_frame()) {
      javaVFrame* vf = _thread->last_java_vframe(&rm);
      assert(vf != NULL, "must have last java frame");
      Method* method = vf->method();
      _method_id = method->jmethod_id();
      _bci = vf->bci();
    } else {
      // Clear current location as the target thread has no Java frames anymore.
      _method_id = (jmethodID)NULL;
      _bci = 0;
    }
  }
  void get_current_location(jmethodID *method_id, int *bci) {
    *method_id = _method_id;
    *bci = _bci;
  }
};

void JvmtiEnvThreadState::reset_current_location(jvmtiEvent event_type, bool enabled) {
  assert(event_type == JVMTI_EVENT_SINGLE_STEP || event_type == JVMTI_EVENT_BREAKPOINT,
         "must be single-step or breakpoint event");

  // Current location is used to detect the following:
  // 1) a breakpoint event followed by single-stepping to the same bci
  // 2) single-step to a bytecode that will be transformed to a fast version
  // We skip to avoid posting the duplicate single-stepping event.

  // If single-stepping is disabled, clear current location so that
  // single-stepping to the same method and bcp at a later time will be
  // detected if single-stepping is enabled at that time (see 4388912).

  // If single-stepping is enabled, set the current location to the
  // current method and bcp. This covers the following type of case,
  // e.g., the debugger stepi command:
  // - bytecode single stepped
  // - SINGLE_STEP event posted and SINGLE_STEP event disabled
  // - SINGLE_STEP event reenabled
  // - bytecode rewritten to fast version

  // If breakpoint event is disabled, clear current location only if
  // single-stepping is not enabled.  Otherwise, keep the thread location
  // to detect any duplicate events.

  if (enabled) {
    // If enabling breakpoint, no need to reset.
    // Can't do anything if empty stack.
    if (event_type == JVMTI_EVENT_SINGLE_STEP && _thread->has_last_Java_frame()) {
      jmethodID method_id;
      int bci;
      // The java thread stack may not be walkable for a running thread
      // so get current location at safepoint.
      VM_GetCurrentLocation op(_thread);
      VMThread::execute(&op);
      op.get_current_location(&method_id, &bci);
      set_current_location(method_id, bci);
    }
  } else if (event_type == JVMTI_EVENT_SINGLE_STEP || !is_enabled(JVMTI_EVENT_SINGLE_STEP)) {
    // If this is to disable breakpoint, also check if single-step is not enabled
    clear_current_location();
  }
}
