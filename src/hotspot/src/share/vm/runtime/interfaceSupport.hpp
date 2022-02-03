/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_INTERFACESUPPORT_HPP
#define SHARE_VM_RUNTIME_INTERFACESUPPORT_HPP

#include "memory/gcLocker.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/preserveException.hpp"
#include "utilities/top.hpp"

// Wrapper for all entry points to the virtual machine.
// The HandleMarkCleaner is a faster version of HandleMark.
// It relies on the fact that there is a HandleMark further
// down the stack (in JavaCalls::call_helper), and just resets
// to the saved values in that HandleMark.

class HandleMarkCleaner: public StackObj {
 private:
  Thread* _thread;
 public:
  HandleMarkCleaner(Thread* thread) {
    _thread = thread;
    _thread->last_handle_mark()->push();
  }
  ~HandleMarkCleaner() {
    _thread->last_handle_mark()->pop_and_restore();
  }

 private:
  inline void* operator new(size_t size, void* ptr) throw() {
    return ptr;
  }
};

// InterfaceSupport provides functionality used by the VM_LEAF_BASE and
// VM_ENTRY_BASE macros. These macros are used to guard entry points into
// the VM and perform checks upon leave of the VM.


class InterfaceSupport: AllStatic {
# ifdef ASSERT
 public:
  static long _scavenge_alot_counter;
  static long _fullgc_alot_counter;
  static long _number_of_calls;
  static long _fullgc_alot_invocation;

  // tracing
  static void trace(const char* result_type, const char* header);

  // Helper methods used to implement +ScavengeALot and +FullGCALot
  static void check_gc_alot() { if (ScavengeALot || FullGCALot) gc_alot(); }
  static void gc_alot();

  static void walk_stack_from(vframe* start_vf);
  static void walk_stack();

# ifdef ENABLE_ZAP_DEAD_LOCALS
  static void zap_dead_locals_old();
# endif

  static void zombieAll();
  static void unlinkSymbols();
  static void deoptimizeAll();
  static void stress_derived_pointers();
  static void verify_stack();
  static void verify_last_frame();
# endif

 public:
  // OS dependent stuff
#ifdef TARGET_OS_FAMILY_linux
# include "interfaceSupport_linux.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "interfaceSupport_solaris.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "interfaceSupport_windows.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "interfaceSupport_aix.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "interfaceSupport_bsd.hpp"
#endif

};


// Basic class for all thread transition classes.

class ThreadStateTransition : public StackObj {
 protected:
  JavaThread* _thread;
 public:
  ThreadStateTransition(JavaThread *thread) {
    _thread = thread;
    assert(thread != NULL && thread->is_Java_thread(), "must be Java thread");
  }

  // Change threadstate in a manner, so safepoint can detect changes.
  // Time-critical: called on exit from every runtime routine
  static inline void transition(JavaThread *thread, JavaThreadState from, JavaThreadState to) {
    assert(from != _thread_in_Java, "use transition_from_java");
    assert(from != _thread_in_native, "use transition_from_native");
    assert((from & 1) == 0 && (to & 1) == 0, "odd numbers are transitions states");
    assert(thread->thread_state() == from, "coming from wrong thread state");
    // Change to transition state (assumes total store ordering!  -Urs)
    thread->set_thread_state((JavaThreadState)(from + 1));

    // Make sure new state is seen by VM thread
    if (os::is_MP()) {
      if (UseMembar) {
        // Force a fence between the write above and read below
        OrderAccess::fence();
      } else {
        // store to serialize page so VM thread can do pseudo remote membar
        os::write_memory_serialize_page(thread);
      }
    }

    if (SafepointSynchronize::do_call_back()) {
      SafepointSynchronize::block(thread);
    }
    thread->set_thread_state(to);

    CHECK_UNHANDLED_OOPS_ONLY(thread->clear_unhandled_oops();)
  }

  // transition_and_fence must be used on any thread state transition
  // where there might not be a Java call stub on the stack, in
  // particular on Windows where the Structured Exception Handler is
  // set up in the call stub. os::write_memory_serialize_page() can
  // fault and we can't recover from it on Windows without a SEH in
  // place.
  static inline void transition_and_fence(JavaThread *thread, JavaThreadState from, JavaThreadState to) {
    assert(thread->thread_state() == from, "coming from wrong thread state");
    assert((from & 1) == 0 && (to & 1) == 0, "odd numbers are transitions states");
    // Change to transition state (assumes total store ordering!  -Urs)
    thread->set_thread_state((JavaThreadState)(from + 1));

    // Make sure new state is seen by VM thread
    if (os::is_MP()) {
      if (UseMembar) {
        // Force a fence between the write above and read below
        OrderAccess::fence();
      } else {
        // Must use this rather than serialization page in particular on Windows
        InterfaceSupport::serialize_memory(thread);
      }
    }

    if (SafepointSynchronize::do_call_back()) {
      SafepointSynchronize::block(thread);
    }
    thread->set_thread_state(to);

    CHECK_UNHANDLED_OOPS_ONLY(thread->clear_unhandled_oops();)
  }

  // Same as above, but assumes from = _thread_in_Java. This is simpler, since we
  // never block on entry to the VM. This will break the code, since e.g. preserve arguments
  // have not been setup.
  static inline void transition_from_java(JavaThread *thread, JavaThreadState to) {
    assert(thread->thread_state() == _thread_in_Java, "coming from wrong thread state");
    thread->set_thread_state(to);
  }

  static inline void transition_from_native(JavaThread *thread, JavaThreadState to) {
    assert((to & 1) == 0, "odd numbers are transitions states");
    assert(thread->thread_state() == _thread_in_native, "coming from wrong thread state");
    // Change to transition state (assumes total store ordering!  -Urs)
    thread->set_thread_state(_thread_in_native_trans);

    // Make sure new state is seen by GC thread
    if (os::is_MP()) {
      if (UseMembar) {
        // Force a fence between the write above and read below
        OrderAccess::fence();
      } else {
        // Must use this rather than serialization page in particular on Windows
        InterfaceSupport::serialize_memory(thread);
      }
    }

    // We never install asynchronous exceptions when coming (back) in
    // to the runtime from native code because the runtime is not set
    // up to handle exceptions floating around at arbitrary points.
    if (SafepointSynchronize::do_call_back() || thread->is_suspend_after_native()) {
      JavaThread::check_safepoint_and_suspend_for_native_trans(thread);

      // Clear unhandled oops anywhere where we could block, even if we don't.
      CHECK_UNHANDLED_OOPS_ONLY(thread->clear_unhandled_oops();)
    }

    thread->set_thread_state(to);
  }
 protected:
   void trans(JavaThreadState from, JavaThreadState to)  { transition(_thread, from, to); }
   void trans_from_java(JavaThreadState to)              { transition_from_java(_thread, to); }
   void trans_from_native(JavaThreadState to)            { transition_from_native(_thread, to); }
   void trans_and_fence(JavaThreadState from, JavaThreadState to) { transition_and_fence(_thread, from, to); }
};


class ThreadInVMfromJava : public ThreadStateTransition {
 public:
  ThreadInVMfromJava(JavaThread* thread) : ThreadStateTransition(thread) {
    trans_from_java(_thread_in_vm);
  }
  ~ThreadInVMfromJava()  {
    trans(_thread_in_vm, _thread_in_Java);
    // Check for pending. async. exceptions or suspends.
    if (_thread->has_special_runtime_exit_condition()) _thread->handle_special_runtime_exit_condition();
  }
};


class ThreadInVMfromUnknown {
 private:
  JavaThread* _thread;
 public:
  ThreadInVMfromUnknown() : _thread(NULL) {
    Thread* t = Thread::current();
    if (t->is_Java_thread()) {
      JavaThread* t2 = (JavaThread*) t;
      if (t2->thread_state() == _thread_in_native) {
        _thread = t2;
        ThreadStateTransition::transition_from_native(t2, _thread_in_vm);
        // Used to have a HandleMarkCleaner but that is dangerous as
        // it could free a handle in our (indirect, nested) caller.
        // We expect any handles will be short lived and figure we
        // don't need an actual HandleMark.
      }
    }
  }
  ~ThreadInVMfromUnknown()  {
    if (_thread) {
      ThreadStateTransition::transition_and_fence(_thread, _thread_in_vm, _thread_in_native);
    }
  }
};


class ThreadInVMfromNative : public ThreadStateTransition {
 public:
  ThreadInVMfromNative(JavaThread* thread) : ThreadStateTransition(thread) {
    trans_from_native(_thread_in_vm);
  }
  ~ThreadInVMfromNative() {
    trans_and_fence(_thread_in_vm, _thread_in_native);
  }
};


class ThreadToNativeFromVM : public ThreadStateTransition {
 public:
  ThreadToNativeFromVM(JavaThread *thread) : ThreadStateTransition(thread) {
    // We are leaving the VM at this point and going directly to native code.
    // Block, if we are in the middle of a safepoint synchronization.
    assert(!thread->owns_locks(), "must release all locks when leaving VM");
    thread->frame_anchor()->make_walkable(thread);
    trans_and_fence(_thread_in_vm, _thread_in_native);
    // Check for pending. async. exceptions or suspends.
    if (_thread->has_special_runtime_exit_condition()) _thread->handle_special_runtime_exit_condition(false);
  }

  ~ThreadToNativeFromVM() {
    trans_from_native(_thread_in_vm);
    // We don't need to clear_walkable because it will happen automagically when we return to java
  }
};


class ThreadBlockInVM : public ThreadStateTransition {
 public:
  ThreadBlockInVM(JavaThread *thread)
  : ThreadStateTransition(thread) {
    // Once we are blocked vm expects stack to be walkable
    thread->frame_anchor()->make_walkable(thread);
    trans_and_fence(_thread_in_vm, _thread_blocked);
  }
  ~ThreadBlockInVM() {
    trans_and_fence(_thread_blocked, _thread_in_vm);
    // We don't need to clear_walkable because it will happen automagically when we return to java
  }
};


// This special transition class is only used to prevent asynchronous exceptions
// from being installed on vm exit in situations where we can't tolerate them.
// See bugs: 4324348, 4854693, 4998314, 5040492, 5050705.
class ThreadInVMfromJavaNoAsyncException : public ThreadStateTransition {
  Thread::WXWriteVerifier _wx_write;
 public:
  ThreadInVMfromJavaNoAsyncException(JavaThread* thread) : ThreadStateTransition(thread), _wx_write() {
    trans_from_java(_thread_in_vm);
  }
  ~ThreadInVMfromJavaNoAsyncException()  {
    trans(_thread_in_vm, _thread_in_Java);
    // NOTE: We do not check for pending. async. exceptions.
    // If we did and moved the pending async exception over into the
    // pending exception field, we would need to deopt (currently C2
    // only). However, to do so would require that we transition back
    // to the _thread_in_vm state. Instead we postpone the handling of
    // the async exception.

    // Check for pending. suspends only.
    if (_thread->has_special_runtime_exit_condition())
      _thread->handle_special_runtime_exit_condition(false);
  }
};

// Debug class instantiated in JRT_ENTRY and ITR_ENTRY macro.
// Can be used to verify properties on enter/exit of the VM.

#ifdef ASSERT
class VMEntryWrapper {
 public:
  VMEntryWrapper() {
    if (VerifyLastFrame) {
      InterfaceSupport::verify_last_frame();
    }
  }

  ~VMEntryWrapper() {
    InterfaceSupport::check_gc_alot();
    if (WalkStackALot) {
      InterfaceSupport::walk_stack();
    }
#ifdef ENABLE_ZAP_DEAD_LOCALS
    if (ZapDeadLocalsOld) {
      InterfaceSupport::zap_dead_locals_old();
    }
#endif
#ifdef COMPILER2
    // This option is not used by Compiler 1
    if (StressDerivedPointers) {
      InterfaceSupport::stress_derived_pointers();
    }
#endif
    if (DeoptimizeALot || DeoptimizeRandom) {
      InterfaceSupport::deoptimizeAll();
    }
    if (ZombieALot) {
      InterfaceSupport::zombieAll();
    }
    if (UnlinkSymbolsALot) {
      InterfaceSupport::unlinkSymbols();
    }
    // do verification AFTER potential deoptimization
    if (VerifyStack) {
      InterfaceSupport::verify_stack();
    }

  }
};


class VMNativeEntryWrapper {
 public:
  VMNativeEntryWrapper() {
    if (GCALotAtAllSafepoints) InterfaceSupport::check_gc_alot();
  }

  ~VMNativeEntryWrapper() {
    if (GCALotAtAllSafepoints) InterfaceSupport::check_gc_alot();
  }
};

#endif


// VM-internal runtime interface support

#ifdef ASSERT

class RuntimeHistogramElement : public HistogramElement {
  public:
   RuntimeHistogramElement(const char* name);
};

#define TRACE_CALL(result_type, header)                            \
  InterfaceSupport::_number_of_calls++;                            \
  if (TraceRuntimeCalls)                                           \
    InterfaceSupport::trace(#result_type, #header);                \
  if (CountRuntimeCalls) {                                         \
    static RuntimeHistogramElement* e = new RuntimeHistogramElement(#header); \
    if (e != NULL) e->increment_count();                           \
  }
#else
#define TRACE_CALL(result_type, header)                            \
  /* do nothing */
#endif


// LEAF routines do not lock, GC or throw exceptions

#define VM_LEAF_BASE(result_type, header)                            \
  TRACE_CALL(result_type, header)                                    \
  debug_only(NoHandleMark __hm;)                                     \
  os::verify_stack_alignment();                                      \
  /* begin of body */

#define VM_ENTRY_BASE_FROM_LEAF(result_type, header, thread)         \
  TRACE_CALL(result_type, header)                                    \
  debug_only(ResetNoHandleMark __rnhm;)                              \
  HandleMarkCleaner __hm(thread);                                    \
  Thread* THREAD = thread;                                           \
  os::verify_stack_alignment();                                      \
  /* begin of body */


// ENTRY routines may lock, GC and throw exceptions

#define VM_ENTRY_BASE(result_type, header, thread)                   \
  TRACE_CALL(result_type, header)                                    \
  HandleMarkCleaner __hm(thread);                                    \
  Thread* THREAD = thread;                                           \
  os::verify_stack_alignment();                                      \
  /* begin of body */


// QUICK_ENTRY routines behave like ENTRY but without a handle mark

#define VM_QUICK_ENTRY_BASE(result_type, header, thread)             \
  TRACE_CALL(result_type, header)                                    \
  debug_only(NoHandleMark __hm;)                                     \
  Thread* THREAD = thread;                                           \
  os::verify_stack_alignment();                                      \
  /* begin of body */


// Definitions for IRT (Interpreter Runtime)
// (thread is an argument passed in to all these routines)

#define IRT_ENTRY(result_type, header)                               \
  result_type header {                                               \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    ThreadInVMfromJava __tiv(thread);                                \
    VM_ENTRY_BASE(result_type, header, thread)                       \
    debug_only(VMEntryWrapper __vew;)


#define IRT_LEAF(result_type, header)                                \
  result_type header {                                               \
    VM_LEAF_BASE(result_type, header)                                \
    debug_only(No_Safepoint_Verifier __nspv(true);)


#define IRT_ENTRY_NO_ASYNC(result_type, header)                      \
  result_type header {                                               \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    ThreadInVMfromJavaNoAsyncException __tiv(thread);                \
    VM_ENTRY_BASE(result_type, header, thread)                       \
    debug_only(VMEntryWrapper __vew;)

#define IRT_END }


// Definitions for JRT (Java (Compiler/Shared) Runtime)

#define JRT_ENTRY(result_type, header)                               \
  result_type header {                                               \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    ThreadInVMfromJava __tiv(thread);                                \
    VM_ENTRY_BASE(result_type, header, thread)                       \
    debug_only(VMEntryWrapper __vew;)


#define JRT_LEAF(result_type, header)                                \
  result_type header {                                               \
  VM_LEAF_BASE(result_type, header)                                  \
  debug_only(JRT_Leaf_Verifier __jlv;)


#define JRT_ENTRY_NO_ASYNC(result_type, header)                      \
  result_type header {                                               \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    ThreadInVMfromJavaNoAsyncException __tiv(thread);                \
    VM_ENTRY_BASE(result_type, header, thread)                       \
    debug_only(VMEntryWrapper __vew;)

// Same as JRT Entry but allows for return value after the safepoint
// to get back into Java from the VM
#define JRT_BLOCK_ENTRY(result_type, header)                         \
  result_type header {                                               \
    TRACE_CALL(result_type, header)                                  \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    HandleMarkCleaner __hm(thread);

#define JRT_BLOCK                                                    \
    {                                                                \
    ThreadInVMfromJava __tiv(thread);                                \
    Thread* THREAD = thread;                                         \
    debug_only(VMEntryWrapper __vew;)

#define JRT_BLOCK_END }

#define JRT_END }

// Definitions for JNI

#define JNI_ENTRY(result_type, header)                               \
    JNI_ENTRY_NO_PRESERVE(result_type, header)                       \
    WeakPreserveExceptionMark __wem(thread);

#define JNI_ENTRY_NO_PRESERVE(result_type, header)             \
extern "C" {                                                         \
  result_type JNICALL header {                                \
    JavaThread* thread=JavaThread::thread_from_jni_environment(env); \
    assert( !VerifyJNIEnvThread || (thread == Thread::current()), "JNIEnv is only valid in same thread"); \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    ThreadInVMfromNative __tiv(thread);                              \
    debug_only(VMNativeEntryWrapper __vew;)                          \
    VM_ENTRY_BASE(result_type, header, thread)


// Ensure that the VMNativeEntryWrapper constructor, which can cause
// a GC, is called outside the NoHandleMark (set via VM_QUICK_ENTRY_BASE).
#define JNI_QUICK_ENTRY(result_type, header)                         \
extern "C" {                                                         \
  result_type JNICALL header {                                \
    JavaThread* thread=JavaThread::thread_from_jni_environment(env); \
    assert( !VerifyJNIEnvThread || (thread == Thread::current()), "JNIEnv is only valid in same thread"); \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    ThreadInVMfromNative __tiv(thread);                              \
    debug_only(VMNativeEntryWrapper __vew;)                          \
    VM_QUICK_ENTRY_BASE(result_type, header, thread)


#define JNI_LEAF(result_type, header)                                \
extern "C" {                                                         \
  result_type JNICALL header {                                \
    JavaThread* thread=JavaThread::thread_from_jni_environment(env); \
    assert( !VerifyJNIEnvThread || (thread == Thread::current()), "JNIEnv is only valid in same thread"); \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    VM_LEAF_BASE(result_type, header)


// Close the routine and the extern "C"
#define JNI_END } }



// Definitions for JVM

#define JVM_ENTRY(result_type, header)                               \
extern "C" {                                                         \
  result_type JNICALL header {                                       \
    JavaThread* thread=JavaThread::thread_from_jni_environment(env); \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    ThreadInVMfromNative __tiv(thread);                              \
    debug_only(VMNativeEntryWrapper __vew;)                          \
    VM_ENTRY_BASE(result_type, header, thread)


#define JVM_ENTRY_NO_ENV(result_type, header)                        \
extern "C" {                                                         \
  result_type JNICALL header {                                       \
    JavaThread* thread = (JavaThread*)ThreadLocalStorage::thread();  \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    ThreadInVMfromNative __tiv(thread);                              \
    debug_only(VMNativeEntryWrapper __vew;)                          \
    VM_ENTRY_BASE(result_type, header, thread)


#define JVM_QUICK_ENTRY(result_type, header)                         \
extern "C" {                                                         \
  result_type JNICALL header {                                       \
    JavaThread* thread=JavaThread::thread_from_jni_environment(env); \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    ThreadInVMfromNative __tiv(thread);                              \
    debug_only(VMNativeEntryWrapper __vew;)                          \
    VM_QUICK_ENTRY_BASE(result_type, header, thread)


#define JVM_LEAF(result_type, header)                                \
extern "C" {                                                         \
  result_type JNICALL header {                                       \
    VM_Exit::block_if_vm_exited();                                   \
    Thread::WXWriteFromExecSetter __wx_write;                        \
    VM_LEAF_BASE(result_type, header)


#define JVM_ENTRY_FROM_LEAF(env, result_type, header)                \
  { {                                                                \
    JavaThread* thread=JavaThread::thread_from_jni_environment(env); \
    ThreadInVMfromNative __tiv(thread);                              \
    debug_only(VMNativeEntryWrapper __vew;)                          \
    VM_ENTRY_BASE_FROM_LEAF(result_type, header, thread)


#define JVM_END } }

#endif // SHARE_VM_RUNTIME_INTERFACESUPPORT_HPP
