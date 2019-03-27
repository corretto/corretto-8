/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/abstractCompiler.hpp"
#include "compiler/disassembler.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/oopMapCache.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/markOop.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.inline2.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/monitorChunk.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/decoder.hpp"

#ifdef TARGET_ARCH_x86
# include "nativeInst_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "nativeInst_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "nativeInst_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "nativeInst_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "nativeInst_ppc.hpp"
#endif

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

RegisterMap::RegisterMap(JavaThread *thread, bool update_map) {
  _thread         = thread;
  _update_map     = update_map;
  clear();
  debug_only(_update_for_id = NULL;)
#ifndef PRODUCT
  for (int i = 0; i < reg_count ; i++ ) _location[i] = NULL;
#endif /* PRODUCT */
}

RegisterMap::RegisterMap(const RegisterMap* map) {
  assert(map != this, "bad initialization parameter");
  assert(map != NULL, "RegisterMap must be present");
  _thread                = map->thread();
  _update_map            = map->update_map();
  _include_argument_oops = map->include_argument_oops();
  debug_only(_update_for_id = map->_update_for_id;)
  pd_initialize_from(map);
  if (update_map()) {
    for(int i = 0; i < location_valid_size; i++) {
      LocationValidType bits = !update_map() ? 0 : map->_location_valid[i];
      _location_valid[i] = bits;
      // for whichever bits are set, pull in the corresponding map->_location
      int j = i*location_valid_type_size;
      while (bits != 0) {
        if ((bits & 1) != 0) {
          assert(0 <= j && j < reg_count, "range check");
          _location[j] = map->_location[j];
        }
        bits >>= 1;
        j += 1;
      }
    }
  }
}

void RegisterMap::clear() {
  set_include_argument_oops(true);
  if (_update_map) {
    for(int i = 0; i < location_valid_size; i++) {
      _location_valid[i] = 0;
    }
    pd_clear();
  } else {
    pd_initialize();
  }
}

#ifndef PRODUCT

void RegisterMap::print_on(outputStream* st) const {
  st->print_cr("Register map");
  for(int i = 0; i < reg_count; i++) {

    VMReg r = VMRegImpl::as_VMReg(i);
    intptr_t* src = (intptr_t*) location(r);
    if (src != NULL) {

      r->print_on(st);
      st->print(" [" INTPTR_FORMAT "] = ", src);
      if (((uintptr_t)src & (sizeof(*src)-1)) != 0) {
        st->print_cr("<misaligned>");
      } else {
        st->print_cr(INTPTR_FORMAT, *src);
      }
    }
  }
}

void RegisterMap::print() const {
  print_on(tty);
}

#endif
// This returns the pc that if you were in the debugger you'd see. Not
// the idealized value in the frame object. This undoes the magic conversion
// that happens for deoptimized frames. In addition it makes the value the
// hardware would want to see in the native frame. The only user (at this point)
// is deoptimization. It likely no one else should ever use it.

address frame::raw_pc() const {
  if (is_deoptimized_frame()) {
    nmethod* nm = cb()->as_nmethod_or_null();
    if (nm->is_method_handle_return(pc()))
      return nm->deopt_mh_handler_begin() - pc_return_offset;
    else
      return nm->deopt_handler_begin() - pc_return_offset;
  } else {
    return (pc() - pc_return_offset);
  }
}

// Change the pc in a frame object. This does not change the actual pc in
// actual frame. To do that use patch_pc.
//
void frame::set_pc(address   newpc ) {
#ifdef ASSERT
  if (_cb != NULL && _cb->is_nmethod()) {
    assert(!((nmethod*)_cb)->is_deopt_pc(_pc), "invariant violation");
  }
#endif // ASSERT

  // Unsafe to use the is_deoptimzed tester after changing pc
  _deopt_state = unknown;
  _pc = newpc;
  _cb = CodeCache::find_blob_unsafe(_pc);

}

// type testers
bool frame::is_ignored_frame() const {
  return false;  // FIXME: some LambdaForm frames should be ignored
}
bool frame::is_deoptimized_frame() const {
  assert(_deopt_state != unknown, "not answerable");
  return _deopt_state == is_deoptimized;
}

bool frame::is_native_frame() const {
  return (_cb != NULL &&
          _cb->is_nmethod() &&
          ((nmethod*)_cb)->is_native_method());
}

bool frame::is_java_frame() const {
  if (is_interpreted_frame()) return true;
  if (is_compiled_frame())    return true;
  return false;
}


bool frame::is_compiled_frame() const {
  if (_cb != NULL &&
      _cb->is_nmethod() &&
      ((nmethod*)_cb)->is_java_method()) {
    return true;
  }
  return false;
}


bool frame::is_runtime_frame() const {
  return (_cb != NULL && _cb->is_runtime_stub());
}

bool frame::is_safepoint_blob_frame() const {
  return (_cb != NULL && _cb->is_safepoint_stub());
}

// testers

bool frame::is_first_java_frame() const {
  RegisterMap map(JavaThread::current(), false); // No update
  frame s;
  for (s = sender(&map); !(s.is_java_frame() || s.is_first_frame()); s = s.sender(&map));
  return s.is_first_frame();
}


bool frame::entry_frame_is_first() const {
  return entry_frame_call_wrapper()->is_first_frame();
}

JavaCallWrapper* frame::entry_frame_call_wrapper_if_safe(JavaThread* thread) const {
  JavaCallWrapper** jcw = entry_frame_call_wrapper_addr();
  address addr = (address) jcw;

  // addr must be within the usable part of the stack
  if (thread->is_in_usable_stack(addr)) {
    return *jcw;
  }

  return NULL;
}

bool frame::should_be_deoptimized() const {
  if (_deopt_state == is_deoptimized ||
      !is_compiled_frame() ) return false;
  assert(_cb != NULL && _cb->is_nmethod(), "must be an nmethod");
  nmethod* nm = (nmethod *)_cb;
  if (TraceDependencies) {
    tty->print("checking (%s) ", nm->is_marked_for_deoptimization() ? "true" : "false");
    nm->print_value_on(tty);
    tty->cr();
  }

  if( !nm->is_marked_for_deoptimization() )
    return false;

  // If at the return point, then the frame has already been popped, and
  // only the return needs to be executed. Don't deoptimize here.
  return !nm->is_at_poll_return(pc());
}

bool frame::can_be_deoptimized() const {
  if (!is_compiled_frame()) return false;
  nmethod* nm = (nmethod*)_cb;

  if( !nm->can_be_deoptimized() )
    return false;

  return !nm->is_at_poll_return(pc());
}

void frame::deoptimize(JavaThread* thread) {
  // Schedule deoptimization of an nmethod activation with this frame.
  assert(_cb != NULL && _cb->is_nmethod(), "must be");
  nmethod* nm = (nmethod*)_cb;

  // This is a fix for register window patching race
  if (NeedsDeoptSuspend && Thread::current() != thread) {
    assert(SafepointSynchronize::is_at_safepoint(),
           "patching other threads for deopt may only occur at a safepoint");

    // It is possible especially with DeoptimizeALot/DeoptimizeRandom that
    // we could see the frame again and ask for it to be deoptimized since
    // it might move for a long time. That is harmless and we just ignore it.
    if (id() == thread->must_deopt_id()) {
      assert(thread->is_deopt_suspend(), "lost suspension");
      return;
    }

    // We are at a safepoint so the target thread can only be
    // in 4 states:
    //     blocked - no problem
    //     blocked_trans - no problem (i.e. could have woken up from blocked
    //                                 during a safepoint).
    //     native - register window pc patching race
    //     native_trans - momentary state
    //
    // We could just wait out a thread in native_trans to block.
    // Then we'd have all the issues that the safepoint code has as to
    // whether to spin or block. It isn't worth it. Just treat it like
    // native and be done with it.
    //
    // Examine the state of the thread at the start of safepoint since
    // threads that were in native at the start of the safepoint could
    // come to a halt during the safepoint, changing the current value
    // of the safepoint_state.
    JavaThreadState state = thread->safepoint_state()->orig_thread_state();
    if (state == _thread_in_native || state == _thread_in_native_trans) {
      // Since we are at a safepoint the target thread will stop itself
      // before it can return to java as long as we remain at the safepoint.
      // Therefore we can put an additional request for the thread to stop
      // no matter what no (like a suspend). This will cause the thread
      // to notice it needs to do the deopt on its own once it leaves native.
      //
      // The only reason we must do this is because on machine with register
      // windows we have a race with patching the return address and the
      // window coming live as the thread returns to the Java code (but still
      // in native mode) and then blocks. It is only this top most frame
      // that is at risk. So in truth we could add an additional check to
      // see if this frame is one that is at risk.
      RegisterMap map(thread, false);
      frame at_risk =  thread->last_frame().sender(&map);
      if (id() == at_risk.id()) {
        thread->set_must_deopt_id(id());
        thread->set_deopt_suspend();
        return;
      }
    }
  } // NeedsDeoptSuspend


  // If the call site is a MethodHandle call site use the MH deopt
  // handler.
  address deopt = nm->is_method_handle_return(pc()) ?
    nm->deopt_mh_handler_begin() :
    nm->deopt_handler_begin();

  // Save the original pc before we patch in the new one
  nm->set_original_pc(this, pc());
  patch_pc(thread, deopt);

#ifdef ASSERT
  {
    RegisterMap map(thread, false);
    frame check = thread->last_frame();
    while (id() != check.id()) {
      check = check.sender(&map);
    }
    assert(check.is_deoptimized_frame(), "missed deopt");
  }
#endif // ASSERT
}

frame frame::java_sender() const {
  RegisterMap map(JavaThread::current(), false);
  frame s;
  for (s = sender(&map); !(s.is_java_frame() || s.is_first_frame()); s = s.sender(&map)) ;
  guarantee(s.is_java_frame(), "tried to get caller of first java frame");
  return s;
}

frame frame::real_sender(RegisterMap* map) const {
  frame result = sender(map);
  while (result.is_runtime_frame() ||
         result.is_ignored_frame()) {
    result = result.sender(map);
  }
  return result;
}

// Note: called by profiler - NOT for current thread
frame frame::profile_find_Java_sender_frame(JavaThread *thread) {
// If we don't recognize this frame, walk back up the stack until we do
  RegisterMap map(thread, false);
  frame first_java_frame = frame();

  // Find the first Java frame on the stack starting with input frame
  if (is_java_frame()) {
    // top frame is compiled frame or deoptimized frame
    first_java_frame = *this;
  } else if (safe_for_sender(thread)) {
    for (frame sender_frame = sender(&map);
      sender_frame.safe_for_sender(thread) && !sender_frame.is_first_frame();
      sender_frame = sender_frame.sender(&map)) {
      if (sender_frame.is_java_frame()) {
        first_java_frame = sender_frame;
        break;
      }
    }
  }
  return first_java_frame;
}

// Interpreter frames


void frame::interpreter_frame_set_locals(intptr_t* locs)  {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  *interpreter_frame_locals_addr() = locs;
}

Method* frame::interpreter_frame_method() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  Method* m = *interpreter_frame_method_addr();
  assert(m->is_method(), "not a Method*");
  return m;
}

void frame::interpreter_frame_set_method(Method* method) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  *interpreter_frame_method_addr() = method;
}

void frame::interpreter_frame_set_bcx(intptr_t bcx) {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  if (ProfileInterpreter) {
    bool formerly_bci = is_bci(interpreter_frame_bcx());
    bool is_now_bci = is_bci(bcx);
    *interpreter_frame_bcx_addr() = bcx;

    intptr_t mdx = interpreter_frame_mdx();

    if (mdx != 0) {
      if (formerly_bci) {
        if (!is_now_bci) {
          // The bcx was just converted from bci to bcp.
          // Convert the mdx in parallel.
          MethodData* mdo = interpreter_frame_method()->method_data();
          assert(mdo != NULL, "");
          int mdi = mdx - 1; // We distinguish valid mdi from zero by adding one.
          address mdp = mdo->di_to_dp(mdi);
          interpreter_frame_set_mdx((intptr_t)mdp);
        }
      } else {
        if (is_now_bci) {
          // The bcx was just converted from bcp to bci.
          // Convert the mdx in parallel.
          MethodData* mdo = interpreter_frame_method()->method_data();
          assert(mdo != NULL, "");
          int mdi = mdo->dp_to_di((address)mdx);
          interpreter_frame_set_mdx((intptr_t)mdi + 1); // distinguish valid from 0.
        }
      }
    }
  } else {
    *interpreter_frame_bcx_addr() = bcx;
  }
}

jint frame::interpreter_frame_bci() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  intptr_t bcx = interpreter_frame_bcx();
  return is_bci(bcx) ? bcx : interpreter_frame_method()->bci_from((address)bcx);
}

void frame::interpreter_frame_set_bci(jint bci) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  assert(!is_bci(interpreter_frame_bcx()), "should not set bci during GC");
  interpreter_frame_set_bcx((intptr_t)interpreter_frame_method()->bcp_from(bci));
}

address frame::interpreter_frame_bcp() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  intptr_t bcx = interpreter_frame_bcx();
  return is_bci(bcx) ? interpreter_frame_method()->bcp_from(bcx) : (address)bcx;
}

void frame::interpreter_frame_set_bcp(address bcp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  assert(!is_bci(interpreter_frame_bcx()), "should not set bcp during GC");
  interpreter_frame_set_bcx((intptr_t)bcp);
}

void frame::interpreter_frame_set_mdx(intptr_t mdx) {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  assert(ProfileInterpreter, "must be profiling interpreter");
  *interpreter_frame_mdx_addr() = mdx;
}

address frame::interpreter_frame_mdp() const {
  assert(ProfileInterpreter, "must be profiling interpreter");
  assert(is_interpreted_frame(), "interpreted frame expected");
  intptr_t bcx = interpreter_frame_bcx();
  intptr_t mdx = interpreter_frame_mdx();

  assert(!is_bci(bcx), "should not access mdp during GC");
  return (address)mdx;
}

void frame::interpreter_frame_set_mdp(address mdp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  if (mdp == NULL) {
    // Always allow the mdp to be cleared.
    interpreter_frame_set_mdx((intptr_t)mdp);
  }
  intptr_t bcx = interpreter_frame_bcx();
  assert(!is_bci(bcx), "should not set mdp during GC");
  interpreter_frame_set_mdx((intptr_t)mdp);
}

BasicObjectLock* frame::next_monitor_in_interpreter_frame(BasicObjectLock* current) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
#ifdef ASSERT
  interpreter_frame_verify_monitor(current);
#endif
  BasicObjectLock* next = (BasicObjectLock*) (((intptr_t*) current) + interpreter_frame_monitor_size());
  return next;
}

BasicObjectLock* frame::previous_monitor_in_interpreter_frame(BasicObjectLock* current) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
#ifdef ASSERT
//   // This verification needs to be checked before being enabled
//   interpreter_frame_verify_monitor(current);
#endif
  BasicObjectLock* previous = (BasicObjectLock*) (((intptr_t*) current) - interpreter_frame_monitor_size());
  return previous;
}

// Interpreter locals and expression stack locations.

intptr_t* frame::interpreter_frame_local_at(int index) const {
  const int n = Interpreter::local_offset_in_bytes(index)/wordSize;
  return &((*interpreter_frame_locals_addr())[n]);
}

intptr_t* frame::interpreter_frame_expression_stack_at(jint offset) const {
  const int i = offset * interpreter_frame_expression_stack_direction();
  const int n = i * Interpreter::stackElementWords;
  return &(interpreter_frame_expression_stack()[n]);
}

jint frame::interpreter_frame_expression_stack_size() const {
  // Number of elements on the interpreter expression stack
  // Callers should span by stackElementWords
  int element_size = Interpreter::stackElementWords;
  size_t stack_size = 0;
  if (frame::interpreter_frame_expression_stack_direction() < 0) {
    stack_size = (interpreter_frame_expression_stack() -
                  interpreter_frame_tos_address() + 1)/element_size;
  } else {
    stack_size = (interpreter_frame_tos_address() -
                  interpreter_frame_expression_stack() + 1)/element_size;
  }
  assert( stack_size <= (size_t)max_jint, "stack size too big");
  return ((jint)stack_size);
}


// (frame::interpreter_frame_sender_sp accessor is in frame_<arch>.cpp)

const char* frame::print_name() const {
  if (is_native_frame())      return "Native";
  if (is_interpreted_frame()) return "Interpreted";
  if (is_compiled_frame()) {
    if (is_deoptimized_frame()) return "Deoptimized";
    return "Compiled";
  }
  if (sp() == NULL)            return "Empty";
  return "C";
}

void frame::print_value_on(outputStream* st, JavaThread *thread) const {
  NOT_PRODUCT(address begin = pc()-40;)
  NOT_PRODUCT(address end   = NULL;)

  st->print("%s frame (sp=" INTPTR_FORMAT " unextended sp=" INTPTR_FORMAT, print_name(), sp(), unextended_sp());
  if (sp() != NULL)
    st->print(", fp=" INTPTR_FORMAT ", real_fp=" INTPTR_FORMAT ", pc=" INTPTR_FORMAT, fp(), real_fp(), pc());

  if (StubRoutines::contains(pc())) {
    st->print_cr(")");
    st->print("(");
    StubCodeDesc* desc = StubCodeDesc::desc_for(pc());
    st->print("~Stub::%s", desc->name());
    NOT_PRODUCT(begin = desc->begin(); end = desc->end();)
  } else if (Interpreter::contains(pc())) {
    st->print_cr(")");
    st->print("(");
    InterpreterCodelet* desc = Interpreter::codelet_containing(pc());
    if (desc != NULL) {
      st->print("~");
      desc->print_on(st);
      NOT_PRODUCT(begin = desc->code_begin(); end = desc->code_end();)
    } else {
      st->print("~interpreter");
    }
  }
  st->print_cr(")");

  if (_cb != NULL) {
    st->print("     ");
    _cb->print_value_on(st);
    st->cr();
#ifndef PRODUCT
    if (end == NULL) {
      begin = _cb->code_begin();
      end   = _cb->code_end();
    }
#endif
  }
  NOT_PRODUCT(if (WizardMode && Verbose) Disassembler::decode(begin, end);)
}


void frame::print_on(outputStream* st) const {
  print_value_on(st,NULL);
  if (is_interpreted_frame()) {
    interpreter_frame_print_on(st);
  }
}


void frame::interpreter_frame_print_on(outputStream* st) const {
#ifndef PRODUCT
  assert(is_interpreted_frame(), "Not an interpreted frame");
  jint i;
  for (i = 0; i < interpreter_frame_method()->max_locals(); i++ ) {
    intptr_t x = *interpreter_frame_local_at(i);
    st->print(" - local  [" INTPTR_FORMAT "]", x);
    st->fill_to(23);
    st->print_cr("; #%d", i);
  }
  for (i = interpreter_frame_expression_stack_size() - 1; i >= 0; --i ) {
    intptr_t x = *interpreter_frame_expression_stack_at(i);
    st->print(" - stack  [" INTPTR_FORMAT "]", x);
    st->fill_to(23);
    st->print_cr("; #%d", i);
  }
  // locks for synchronization
  for (BasicObjectLock* current = interpreter_frame_monitor_end();
       current < interpreter_frame_monitor_begin();
       current = next_monitor_in_interpreter_frame(current)) {
    st->print(" - obj    [");
    current->obj()->print_value_on(st);
    st->print_cr("]");
    st->print(" - lock   [");
    current->lock()->print_on(st);
    st->print_cr("]");
  }
  // monitor
  st->print_cr(" - monitor[" INTPTR_FORMAT "]", interpreter_frame_monitor_begin());
  // bcp
  st->print(" - bcp    [" INTPTR_FORMAT "]", interpreter_frame_bcp());
  st->fill_to(23);
  st->print_cr("; @%d", interpreter_frame_bci());
  // locals
  st->print_cr(" - locals [" INTPTR_FORMAT "]", interpreter_frame_local_at(0));
  // method
  st->print(" - method [" INTPTR_FORMAT "]", (address)interpreter_frame_method());
  st->fill_to(23);
  st->print("; ");
  interpreter_frame_method()->print_name(st);
  st->cr();
#endif
}

// Return whether the frame is in the VM or os indicating a Hotspot problem.
// Otherwise, it's likely a bug in the native library that the Java code calls,
// hopefully indicating where to submit bugs.
void frame::print_C_frame(outputStream* st, char* buf, int buflen, address pc) {
  // C/C++ frame
  bool in_vm = os::address_is_in_vm(pc);
  st->print(in_vm ? "V" : "C");

  int offset;
  bool found;

  // libname
  found = os::dll_address_to_library_name(pc, buf, buflen, &offset);
  if (found) {
    // skip directory names
    const char *p1, *p2;
    p1 = buf;
    int len = (int)strlen(os::file_separator());
    while ((p2 = strstr(p1, os::file_separator())) != NULL) p1 = p2 + len;
    st->print("  [%s+0x%x]", p1, offset);
  } else {
    st->print("  " PTR_FORMAT, pc);
  }

  // function name - os::dll_address_to_function_name() may return confusing
  // names if pc is within jvm.dll or libjvm.so, because JVM only has
  // JVM_xxxx and a few other symbols in the dynamic symbol table. Do this
  // only for native libraries.
  if (!in_vm || Decoder::can_decode_C_frame_in_vm()) {
    found = os::dll_address_to_function_name(pc, buf, buflen, &offset);

    if (found) {
      st->print("  %s+0x%x", buf, offset);
    }
  }
}

// frame::print_on_error() is called by fatal error handler. Notice that we may
// crash inside this function if stack frame is corrupted. The fatal error
// handler can catch and handle the crash. Here we assume the frame is valid.
//
// First letter indicates type of the frame:
//    J: Java frame (compiled)
//    j: Java frame (interpreted)
//    V: VM frame (C/C++)
//    v: Other frames running VM generated code (e.g. stubs, adapters, etc.)
//    C: C/C++ frame
//
// We don't need detailed frame type as that in frame::print_name(). "C"
// suggests the problem is in user lib; everything else is likely a VM bug.

void frame::print_on_error(outputStream* st, char* buf, int buflen, bool verbose) const {
  if (_cb != NULL) {
    if (Interpreter::contains(pc())) {
      Method* m = this->interpreter_frame_method();
      if (m != NULL) {
        m->name_and_sig_as_C_string(buf, buflen);
        st->print("j  %s", buf);
        st->print("+%d", this->interpreter_frame_bci());
      } else {
        st->print("j  " PTR_FORMAT, pc());
      }
    } else if (StubRoutines::contains(pc())) {
      StubCodeDesc* desc = StubCodeDesc::desc_for(pc());
      if (desc != NULL) {
        st->print("v  ~StubRoutines::%s", desc->name());
      } else {
        st->print("v  ~StubRoutines::" PTR_FORMAT, pc());
      }
    } else if (_cb->is_buffer_blob()) {
      st->print("v  ~BufferBlob::%s", ((BufferBlob *)_cb)->name());
    } else if (_cb->is_nmethod()) {
      nmethod* nm = (nmethod*)_cb;
      Method* m = nm->method();
      if (m != NULL) {
        m->name_and_sig_as_C_string(buf, buflen);
        st->print("J %d%s %s %s (%d bytes) @ " PTR_FORMAT " [" PTR_FORMAT "+0x%x]",
                  nm->compile_id(), (nm->is_osr_method() ? "%" : ""),
                  ((nm->compiler() != NULL) ? nm->compiler()->name() : ""),
                  buf, m->code_size(), _pc, _cb->code_begin(), _pc - _cb->code_begin());
      } else {
        st->print("J  " PTR_FORMAT, pc());
      }
    } else if (_cb->is_runtime_stub()) {
      st->print("v  ~RuntimeStub::%s", ((RuntimeStub *)_cb)->name());
    } else if (_cb->is_deoptimization_stub()) {
      st->print("v  ~DeoptimizationBlob");
    } else if (_cb->is_exception_stub()) {
      st->print("v  ~ExceptionBlob");
    } else if (_cb->is_safepoint_stub()) {
      st->print("v  ~SafepointBlob");
    } else {
      st->print("v  blob " PTR_FORMAT, pc());
    }
  } else {
    print_C_frame(st, buf, buflen, pc());
  }
}


/*
  The interpreter_frame_expression_stack_at method in the case of SPARC needs the
  max_stack value of the method in order to compute the expression stack address.
  It uses the Method* in order to get the max_stack value but during GC this
  Method* value saved on the frame is changed by reverse_and_push and hence cannot
  be used. So we save the max_stack value in the FrameClosure object and pass it
  down to the interpreter_frame_expression_stack_at method
*/
class InterpreterFrameClosure : public OffsetClosure {
 private:
  frame* _fr;
  OopClosure* _f;
  int    _max_locals;
  int    _max_stack;

 public:
  InterpreterFrameClosure(frame* fr, int max_locals, int max_stack,
                          OopClosure* f) {
    _fr         = fr;
    _max_locals = max_locals;
    _max_stack  = max_stack;
    _f          = f;
  }

  void offset_do(int offset) {
    oop* addr;
    if (offset < _max_locals) {
      addr = (oop*) _fr->interpreter_frame_local_at(offset);
      assert((intptr_t*)addr >= _fr->sp(), "must be inside the frame");
      _f->do_oop(addr);
    } else {
      addr = (oop*) _fr->interpreter_frame_expression_stack_at((offset - _max_locals));
      // In case of exceptions, the expression stack is invalid and the esp will be reset to express
      // this condition. Therefore, we call f only if addr is 'inside' the stack (i.e., addr >= esp for Intel).
      bool in_stack;
      if (frame::interpreter_frame_expression_stack_direction() > 0) {
        in_stack = (intptr_t*)addr <= _fr->interpreter_frame_tos_address();
      } else {
        in_stack = (intptr_t*)addr >= _fr->interpreter_frame_tos_address();
      }
      if (in_stack) {
        _f->do_oop(addr);
      }
    }
  }

  int max_locals()  { return _max_locals; }
  frame* fr()       { return _fr; }
};


class InterpretedArgumentOopFinder: public SignatureInfo {
 private:
  OopClosure* _f;        // Closure to invoke
  int    _offset;        // TOS-relative offset, decremented with each argument
  bool   _has_receiver;  // true if the callee has a receiver
  frame* _fr;

  void set(int size, BasicType type) {
    _offset -= size;
    if (type == T_OBJECT || type == T_ARRAY) oop_offset_do();
  }

  void oop_offset_do() {
    oop* addr;
    addr = (oop*)_fr->interpreter_frame_tos_at(_offset);
    _f->do_oop(addr);
  }

 public:
  InterpretedArgumentOopFinder(Symbol* signature, bool has_receiver, frame* fr, OopClosure* f) : SignatureInfo(signature), _has_receiver(has_receiver) {
    // compute size of arguments
    int args_size = ArgumentSizeComputer(signature).size() + (has_receiver ? 1 : 0);
    assert(!fr->is_interpreted_frame() ||
           args_size <= fr->interpreter_frame_expression_stack_size(),
            "args cannot be on stack anymore");
    // initialize InterpretedArgumentOopFinder
    _f         = f;
    _fr        = fr;
    _offset    = args_size;
  }

  void oops_do() {
    if (_has_receiver) {
      --_offset;
      oop_offset_do();
    }
    iterate_parameters();
  }
};


// Entry frame has following form (n arguments)
//         +-----------+
//   sp -> |  last arg |
//         +-----------+
//         :    :::    :
//         +-----------+
// (sp+n)->|  first arg|
//         +-----------+



// visits and GC's all the arguments in entry frame
class EntryFrameOopFinder: public SignatureInfo {
 private:
  bool   _is_static;
  int    _offset;
  frame* _fr;
  OopClosure* _f;

  void set(int size, BasicType type) {
    assert (_offset >= 0, "illegal offset");
    if (type == T_OBJECT || type == T_ARRAY) oop_at_offset_do(_offset);
    _offset -= size;
  }

  void oop_at_offset_do(int offset) {
    assert (offset >= 0, "illegal offset");
    oop* addr = (oop*) _fr->entry_frame_argument_at(offset);
    _f->do_oop(addr);
  }

 public:
   EntryFrameOopFinder(frame* frame, Symbol* signature, bool is_static) : SignatureInfo(signature) {
     _f = NULL; // will be set later
     _fr = frame;
     _is_static = is_static;
     _offset = ArgumentSizeComputer(signature).size() - 1; // last parameter is at index 0
   }

  void arguments_do(OopClosure* f) {
    _f = f;
    if (!_is_static) oop_at_offset_do(_offset+1); // do the receiver
    iterate_parameters();
  }

};

oop* frame::interpreter_callee_receiver_addr(Symbol* signature) {
  ArgumentSizeComputer asc(signature);
  int size = asc.size();
  return (oop *)interpreter_frame_tos_at(size);
}


void frame::oops_interpreted_do(OopClosure* f, CLDClosure* cld_f,
    const RegisterMap* map, bool query_oop_map_cache) {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  assert(map != NULL, "map must be set");
  Thread *thread = Thread::current();
  methodHandle m (thread, interpreter_frame_method());
  jint      bci = interpreter_frame_bci();

  assert(!Universe::heap()->is_in(m()),
          "must be valid oop");
  assert(m->is_method(), "checking frame value");
  assert((m->is_native() && bci == 0)  ||
         (!m->is_native() && bci >= 0 && bci < m->code_size()),
         "invalid bci value");

  // Handle the monitor elements in the activation
  for (
    BasicObjectLock* current = interpreter_frame_monitor_end();
    current < interpreter_frame_monitor_begin();
    current = next_monitor_in_interpreter_frame(current)
  ) {
#ifdef ASSERT
    interpreter_frame_verify_monitor(current);
#endif
    current->oops_do(f);
  }

  // process fixed part
  if (cld_f != NULL) {
    // The method pointer in the frame might be the only path to the method's
    // klass, and the klass needs to be kept alive while executing. The GCs
    // don't trace through method pointers, so typically in similar situations
    // the mirror or the class loader of the klass are installed as a GC root.
    // To minimze the overhead of doing that here, we ask the GC to pass down a
    // closure that knows how to keep klasses alive given a ClassLoaderData.
    cld_f->do_cld(m->method_holder()->class_loader_data());
  }

  if (m->is_native() PPC32_ONLY(&& m->is_static())) {
    f->do_oop(interpreter_frame_temp_oop_addr());
  }

  int max_locals = m->is_native() ? m->size_of_parameters() : m->max_locals();

  Symbol* signature = NULL;
  bool has_receiver = false;

  // Process a callee's arguments if we are at a call site
  // (i.e., if we are at an invoke bytecode)
  // This is used sometimes for calling into the VM, not for another
  // interpreted or compiled frame.
  if (!m->is_native()) {
    Bytecode_invoke call = Bytecode_invoke_check(m, bci);
    if (call.is_valid()) {
      signature = call.signature();
      has_receiver = call.has_receiver();
      if (map->include_argument_oops() &&
          interpreter_frame_expression_stack_size() > 0) {
        ResourceMark rm(thread);  // is this right ???
        // we are at a call site & the expression stack is not empty
        // => process callee's arguments
        //
        // Note: The expression stack can be empty if an exception
        //       occurred during method resolution/execution. In all
        //       cases we empty the expression stack completely be-
        //       fore handling the exception (the exception handling
        //       code in the interpreter calls a blocking runtime
        //       routine which can cause this code to be executed).
        //       (was bug gri 7/27/98)
        oops_interpreted_arguments_do(signature, has_receiver, f);
      }
    }
  }

  InterpreterFrameClosure blk(this, max_locals, m->max_stack(), f);

  // process locals & expression stack
  InterpreterOopMap mask;
  if (query_oop_map_cache) {
    m->mask_for(bci, &mask);
  } else {
    OopMapCache::compute_one_oop_map(m, bci, &mask);
  }
  mask.iterate_oop(&blk);
}


void frame::oops_interpreted_arguments_do(Symbol* signature, bool has_receiver, OopClosure* f) {
  InterpretedArgumentOopFinder finder(signature, has_receiver, this, f);
  finder.oops_do();
}

void frame::oops_code_blob_do(OopClosure* f, CodeBlobClosure* cf, const RegisterMap* reg_map) {
  assert(_cb != NULL, "sanity check");
  if (_cb->oop_maps() != NULL) {
    OopMapSet::oops_do(this, reg_map, f);

    // Preserve potential arguments for a callee. We handle this by dispatching
    // on the codeblob. For c2i, we do
    if (reg_map->include_argument_oops()) {
      _cb->preserve_callee_argument_oops(*this, reg_map, f);
    }
  }
  // In cases where perm gen is collected, GC will want to mark
  // oops referenced from nmethods active on thread stacks so as to
  // prevent them from being collected. However, this visit should be
  // restricted to certain phases of the collection only. The
  // closure decides how it wants nmethods to be traced.
  if (cf != NULL)
    cf->do_code_blob(_cb);
}

class CompiledArgumentOopFinder: public SignatureInfo {
 protected:
  OopClosure*     _f;
  int             _offset;        // the current offset, incremented with each argument
  bool            _has_receiver;  // true if the callee has a receiver
  bool            _has_appendix;  // true if the call has an appendix
  frame           _fr;
  RegisterMap*    _reg_map;
  int             _arg_size;
  VMRegPair*      _regs;        // VMReg list of arguments

  void set(int size, BasicType type) {
    if (type == T_OBJECT || type == T_ARRAY) handle_oop_offset();
    _offset += size;
  }

  virtual void handle_oop_offset() {
    // Extract low order register number from register array.
    // In LP64-land, the high-order bits are valid but unhelpful.
    VMReg reg = _regs[_offset].first();
    oop *loc = _fr.oopmapreg_to_location(reg, _reg_map);
    _f->do_oop(loc);
  }

 public:
  CompiledArgumentOopFinder(Symbol* signature, bool has_receiver, bool has_appendix, OopClosure* f, frame fr,  const RegisterMap* reg_map)
    : SignatureInfo(signature) {

    // initialize CompiledArgumentOopFinder
    _f         = f;
    _offset    = 0;
    _has_receiver = has_receiver;
    _has_appendix = has_appendix;
    _fr        = fr;
    _reg_map   = (RegisterMap*)reg_map;
    _arg_size  = ArgumentSizeComputer(signature).size() + (has_receiver ? 1 : 0) + (has_appendix ? 1 : 0);

    int arg_size;
    _regs = SharedRuntime::find_callee_arguments(signature, has_receiver, has_appendix, &arg_size);
    assert(arg_size == _arg_size, "wrong arg size");
  }

  void oops_do() {
    if (_has_receiver) {
      handle_oop_offset();
      _offset++;
    }
    iterate_parameters();
    if (_has_appendix) {
      handle_oop_offset();
      _offset++;
    }
  }
};

void frame::oops_compiled_arguments_do(Symbol* signature, bool has_receiver, bool has_appendix, const RegisterMap* reg_map, OopClosure* f) {
  ResourceMark rm;
  CompiledArgumentOopFinder finder(signature, has_receiver, has_appendix, f, *this, reg_map);
  finder.oops_do();
}


// Get receiver out of callers frame, i.e. find parameter 0 in callers
// frame.  Consult ADLC for where parameter 0 is to be found.  Then
// check local reg_map for it being a callee-save register or argument
// register, both of which are saved in the local frame.  If not found
// there, it must be an in-stack argument of the caller.
// Note: caller.sp() points to callee-arguments
oop frame::retrieve_receiver(RegisterMap* reg_map) {
  frame caller = *this;

  // First consult the ADLC on where it puts parameter 0 for this signature.
  VMReg reg = SharedRuntime::name_for_receiver();
  oop* oop_adr = caller.oopmapreg_to_location(reg, reg_map);
  if (oop_adr == NULL) {
    guarantee(oop_adr != NULL, "bad register save location");
    return NULL;
  }
  oop r = *oop_adr;
  assert(Universe::heap()->is_in_or_null(r), err_msg("bad receiver: " INTPTR_FORMAT " (" INTX_FORMAT ")", (void *) r, (void *) r));
  return r;
}


oop* frame::oopmapreg_to_location(VMReg reg, const RegisterMap* reg_map) const {
  if(reg->is_reg()) {
    // If it is passed in a register, it got spilled in the stub frame.
    return (oop *)reg_map->location(reg);
  } else {
    int sp_offset_in_bytes = reg->reg2stack() * VMRegImpl::stack_slot_size;
    return (oop*)(((address)unextended_sp()) + sp_offset_in_bytes);
  }
}

BasicLock* frame::get_native_monitor() {
  nmethod* nm = (nmethod*)_cb;
  assert(_cb != NULL && _cb->is_nmethod() && nm->method()->is_native(),
         "Should not call this unless it's a native nmethod");
  int byte_offset = in_bytes(nm->native_basic_lock_sp_offset());
  assert(byte_offset >= 0, "should not see invalid offset");
  return (BasicLock*) &sp()[byte_offset / wordSize];
}

oop frame::get_native_receiver() {
  nmethod* nm = (nmethod*)_cb;
  assert(_cb != NULL && _cb->is_nmethod() && nm->method()->is_native(),
         "Should not call this unless it's a native nmethod");
  int byte_offset = in_bytes(nm->native_receiver_sp_offset());
  assert(byte_offset >= 0, "should not see invalid offset");
  oop owner = ((oop*) sp())[byte_offset / wordSize];
  assert( Universe::heap()->is_in(owner), "bad receiver" );
  return owner;
}

void frame::oops_entry_do(OopClosure* f, const RegisterMap* map) {
  assert(map != NULL, "map must be set");
  if (map->include_argument_oops()) {
    // must collect argument oops, as nobody else is doing it
    Thread *thread = Thread::current();
    methodHandle m (thread, entry_frame_call_wrapper()->callee_method());
    EntryFrameOopFinder finder(this, m->signature(), m->is_static());
    finder.arguments_do(f);
  }
  // Traverse the Handle Block saved in the entry frame
  entry_frame_call_wrapper()->oops_do(f);
}


void frame::oops_do_internal(OopClosure* f, CLDClosure* cld_f, CodeBlobClosure* cf, RegisterMap* map, bool use_interpreter_oop_map_cache) {
#ifndef PRODUCT
  // simulate GC crash here to dump java thread in error report
  if (CrashGCForDumpingJavaThread) {
    char *t = NULL;
    *t = 'c';
  }
#endif
  if (is_interpreted_frame()) {
    oops_interpreted_do(f, cld_f, map, use_interpreter_oop_map_cache);
  } else if (is_entry_frame()) {
    oops_entry_do(f, map);
  } else if (CodeCache::contains(pc())) {
    oops_code_blob_do(f, cf, map);
#ifdef SHARK
  } else if (is_fake_stub_frame()) {
    // nothing to do
#endif // SHARK
  } else {
    ShouldNotReachHere();
  }
}

void frame::nmethods_do(CodeBlobClosure* cf) {
  if (_cb != NULL && _cb->is_nmethod()) {
    cf->do_code_blob(_cb);
  }
}


// call f() on the interpreted Method*s in the stack.
// Have to walk the entire code cache for the compiled frames Yuck.
void frame::metadata_do(void f(Metadata*)) {
  if (_cb != NULL && Interpreter::contains(pc())) {
    Method* m = this->interpreter_frame_method();
    assert(m != NULL, "huh?");
    f(m);
  }
}

void frame::gc_prologue() {
  if (is_interpreted_frame()) {
    // set bcx to bci to become Method* position independent during GC
    interpreter_frame_set_bcx(interpreter_frame_bci());
  }
}


void frame::gc_epilogue() {
  if (is_interpreted_frame()) {
    // set bcx back to bcp for interpreter
    interpreter_frame_set_bcx((intptr_t)interpreter_frame_bcp());
  }
  // call processor specific epilog function
  pd_gc_epilog();
}


# ifdef ENABLE_ZAP_DEAD_LOCALS

void frame::CheckValueClosure::do_oop(oop* p) {
  if (CheckOopishValues && Universe::heap()->is_in_reserved(*p)) {
    warning("value @ " INTPTR_FORMAT " looks oopish (" INTPTR_FORMAT ") (thread = " INTPTR_FORMAT ")", p, (address)*p, Thread::current());
  }
}
frame::CheckValueClosure frame::_check_value;


void frame::CheckOopClosure::do_oop(oop* p) {
  if (*p != NULL && !(*p)->is_oop()) {
    warning("value @ " INTPTR_FORMAT " should be an oop (" INTPTR_FORMAT ") (thread = " INTPTR_FORMAT ")", p, (address)*p, Thread::current());
 }
}
frame::CheckOopClosure frame::_check_oop;

void frame::check_derived_oop(oop* base, oop* derived) {
  _check_oop.do_oop(base);
}


void frame::ZapDeadClosure::do_oop(oop* p) {
  if (TraceZapDeadLocals) tty->print_cr("zapping @ " INTPTR_FORMAT " containing " INTPTR_FORMAT, p, (address)*p);
  *p = cast_to_oop<intptr_t>(0xbabebabe);
}
frame::ZapDeadClosure frame::_zap_dead;

void frame::zap_dead_locals(JavaThread* thread, const RegisterMap* map) {
  assert(thread == Thread::current(), "need to synchronize to do this to another thread");
  // Tracing - part 1
  if (TraceZapDeadLocals) {
    ResourceMark rm(thread);
    tty->print_cr("--------------------------------------------------------------------------------");
    tty->print("Zapping dead locals in ");
    print_on(tty);
    tty->cr();
  }
  // Zapping
       if (is_entry_frame      ()) zap_dead_entry_locals      (thread, map);
  else if (is_interpreted_frame()) zap_dead_interpreted_locals(thread, map);
  else if (is_compiled_frame()) zap_dead_compiled_locals   (thread, map);

  else
    // could be is_runtime_frame
    // so remove error: ShouldNotReachHere();
    ;
  // Tracing - part 2
  if (TraceZapDeadLocals) {
    tty->cr();
  }
}


void frame::zap_dead_interpreted_locals(JavaThread *thread, const RegisterMap* map) {
  // get current interpreter 'pc'
  assert(is_interpreted_frame(), "Not an interpreted frame");
  Method* m   = interpreter_frame_method();
  int       bci = interpreter_frame_bci();

  int max_locals = m->is_native() ? m->size_of_parameters() : m->max_locals();

  // process dynamic part
  InterpreterFrameClosure value_blk(this, max_locals, m->max_stack(),
                                    &_check_value);
  InterpreterFrameClosure   oop_blk(this, max_locals, m->max_stack(),
                                    &_check_oop  );
  InterpreterFrameClosure  dead_blk(this, max_locals, m->max_stack(),
                                    &_zap_dead   );

  // get frame map
  InterpreterOopMap mask;
  m->mask_for(bci, &mask);
  mask.iterate_all( &oop_blk, &value_blk, &dead_blk);
}


void frame::zap_dead_compiled_locals(JavaThread* thread, const RegisterMap* reg_map) {

  ResourceMark rm(thread);
  assert(_cb != NULL, "sanity check");
  if (_cb->oop_maps() != NULL) {
    OopMapSet::all_do(this, reg_map, &_check_oop, check_derived_oop, &_check_value);
  }
}


void frame::zap_dead_entry_locals(JavaThread*, const RegisterMap*) {
  if (TraceZapDeadLocals) warning("frame::zap_dead_entry_locals unimplemented");
}


void frame::zap_dead_deoptimized_locals(JavaThread*, const RegisterMap*) {
  if (TraceZapDeadLocals) warning("frame::zap_dead_deoptimized_locals unimplemented");
}

# endif // ENABLE_ZAP_DEAD_LOCALS

void frame::verify(const RegisterMap* map) {
  // for now make sure receiver type is correct
  if (is_interpreted_frame()) {
    Method* method = interpreter_frame_method();
    guarantee(method->is_method(), "method is wrong in frame::verify");
    if (!method->is_static()) {
      // fetch the receiver
      oop* p = (oop*) interpreter_frame_local_at(0);
      // make sure we have the right receiver type
    }
  }
  COMPILER2_PRESENT(assert(DerivedPointerTable::is_empty(), "must be empty before verify");)
  oops_do_internal(&VerifyOopClosure::verify_oop, NULL, NULL, (RegisterMap*)map, false);
}


#ifdef ASSERT
bool frame::verify_return_pc(address x) {
  if (StubRoutines::returns_to_call_stub(x)) {
    return true;
  }
  if (CodeCache::contains(x)) {
    return true;
  }
  if (Interpreter::contains(x)) {
    return true;
  }
  return false;
}
#endif

#ifdef ASSERT
void frame::interpreter_frame_verify_monitor(BasicObjectLock* value) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  // verify that the value is in the right part of the frame
  address low_mark  = (address) interpreter_frame_monitor_end();
  address high_mark = (address) interpreter_frame_monitor_begin();
  address current   = (address) value;

  const int monitor_size = frame::interpreter_frame_monitor_size();
  guarantee((high_mark - current) % monitor_size  ==  0         , "Misaligned top of BasicObjectLock*");
  guarantee( high_mark > current                                , "Current BasicObjectLock* higher than high_mark");

  guarantee((current - low_mark) % monitor_size  ==  0         , "Misaligned bottom of BasicObjectLock*");
  guarantee( current >= low_mark                               , "Current BasicObjectLock* below than low_mark");
}
#endif

#ifndef PRODUCT
void frame::describe(FrameValues& values, int frame_no) {
  // boundaries: sp and the 'real' frame pointer
  values.describe(-1, sp(), err_msg("sp for #%d", frame_no), 1);
  intptr_t* frame_pointer = real_fp(); // Note: may differ from fp()

  // print frame info at the highest boundary
  intptr_t* info_address = MAX2(sp(), frame_pointer);

  if (info_address != frame_pointer) {
    // print frame_pointer explicitly if not marked by the frame info
    values.describe(-1, frame_pointer, err_msg("frame pointer for #%d", frame_no), 1);
  }

  if (is_entry_frame() || is_compiled_frame() || is_interpreted_frame() || is_native_frame()) {
    // Label values common to most frames
    values.describe(-1, unextended_sp(), err_msg("unextended_sp for #%d", frame_no));
  }

  if (is_interpreted_frame()) {
    Method* m = interpreter_frame_method();
    int bci = interpreter_frame_bci();

    // Label the method and current bci
    values.describe(-1, info_address,
                    FormatBuffer<1024>("#%d method %s @ %d", frame_no, m->name_and_sig_as_C_string(), bci), 2);
    values.describe(-1, info_address,
                    err_msg("- %d locals %d max stack", m->max_locals(), m->max_stack()), 1);
    if (m->max_locals() > 0) {
      intptr_t* l0 = interpreter_frame_local_at(0);
      intptr_t* ln = interpreter_frame_local_at(m->max_locals() - 1);
      values.describe(-1, MAX2(l0, ln), err_msg("locals for #%d", frame_no), 1);
      // Report each local and mark as owned by this frame
      for (int l = 0; l < m->max_locals(); l++) {
        intptr_t* l0 = interpreter_frame_local_at(l);
        values.describe(frame_no, l0, err_msg("local %d", l));
      }
    }

    // Compute the actual expression stack size
    InterpreterOopMap mask;
    OopMapCache::compute_one_oop_map(m, bci, &mask);
    intptr_t* tos = NULL;
    // Report each stack element and mark as owned by this frame
    for (int e = 0; e < mask.expression_stack_size(); e++) {
      tos = MAX2(tos, interpreter_frame_expression_stack_at(e));
      values.describe(frame_no, interpreter_frame_expression_stack_at(e),
                      err_msg("stack %d", e));
    }
    if (tos != NULL) {
      values.describe(-1, tos, err_msg("expression stack for #%d", frame_no), 1);
    }
    if (interpreter_frame_monitor_begin() != interpreter_frame_monitor_end()) {
      values.describe(frame_no, (intptr_t*)interpreter_frame_monitor_begin(), "monitors begin");
      values.describe(frame_no, (intptr_t*)interpreter_frame_monitor_end(), "monitors end");
    }
  } else if (is_entry_frame()) {
    // For now just label the frame
    values.describe(-1, info_address, err_msg("#%d entry frame", frame_no), 2);
  } else if (is_compiled_frame()) {
    // For now just label the frame
    nmethod* nm = cb()->as_nmethod_or_null();
    values.describe(-1, info_address,
                    FormatBuffer<1024>("#%d nmethod " INTPTR_FORMAT " for method %s%s", frame_no,
                                       nm, nm->method()->name_and_sig_as_C_string(),
                                       (_deopt_state == is_deoptimized) ?
                                       " (deoptimized)" :
                                       ((_deopt_state == unknown) ? " (state unknown)" : "")),
                    2);
  } else if (is_native_frame()) {
    // For now just label the frame
    nmethod* nm = cb()->as_nmethod_or_null();
    values.describe(-1, info_address,
                    FormatBuffer<1024>("#%d nmethod " INTPTR_FORMAT " for native method %s", frame_no,
                                       nm, nm->method()->name_and_sig_as_C_string()), 2);
  } else {
    // provide default info if not handled before
    char *info = (char *) "special frame";
    if ((_cb != NULL) &&
        (_cb->name() != NULL)) {
      info = (char *)_cb->name();
    }
    values.describe(-1, info_address, err_msg("#%d <%s>", frame_no, info), 2);
  }

  // platform dependent additional data
  describe_pd(values, frame_no);
}

#endif


//-----------------------------------------------------------------------------------
// StackFrameStream implementation

StackFrameStream::StackFrameStream(JavaThread *thread, bool update) : _reg_map(thread, update) {
  assert(thread->has_last_Java_frame(), "sanity check");
  _fr = thread->last_frame();
  _is_done = false;
}


#ifndef PRODUCT

void FrameValues::describe(int owner, intptr_t* location, const char* description, int priority) {
  FrameValue fv;
  fv.location = location;
  fv.owner = owner;
  fv.priority = priority;
  fv.description = NEW_RESOURCE_ARRAY(char, strlen(description) + 1);
  strcpy(fv.description, description);
  _values.append(fv);
}


#ifdef ASSERT
void FrameValues::validate() {
  _values.sort(compare);
  bool error = false;
  FrameValue prev;
  prev.owner = -1;
  for (int i = _values.length() - 1; i >= 0; i--) {
    FrameValue fv = _values.at(i);
    if (fv.owner == -1) continue;
    if (prev.owner == -1) {
      prev = fv;
      continue;
    }
    if (prev.location == fv.location) {
      if (fv.owner != prev.owner) {
        tty->print_cr("overlapping storage");
        tty->print_cr(" " INTPTR_FORMAT ": " INTPTR_FORMAT " %s", prev.location, *prev.location, prev.description);
        tty->print_cr(" " INTPTR_FORMAT ": " INTPTR_FORMAT " %s", fv.location, *fv.location, fv.description);
        error = true;
      }
    } else {
      prev = fv;
    }
  }
  assert(!error, "invalid layout");
}
#endif // ASSERT

void FrameValues::print(JavaThread* thread) {
  _values.sort(compare);

  // Sometimes values like the fp can be invalid values if the
  // register map wasn't updated during the walk.  Trim out values
  // that aren't actually in the stack of the thread.
  int min_index = 0;
  int max_index = _values.length() - 1;
  intptr_t* v0 = _values.at(min_index).location;
  intptr_t* v1 = _values.at(max_index).location;

  if (thread == Thread::current()) {
    while (!thread->is_in_stack((address)v0)) {
      v0 = _values.at(++min_index).location;
    }
    while (!thread->is_in_stack((address)v1)) {
      v1 = _values.at(--max_index).location;
    }
  } else {
    while (!thread->on_local_stack((address)v0)) {
      v0 = _values.at(++min_index).location;
    }
    while (!thread->on_local_stack((address)v1)) {
      v1 = _values.at(--max_index).location;
    }
  }
  intptr_t* min = MIN2(v0, v1);
  intptr_t* max = MAX2(v0, v1);
  intptr_t* cur = max;
  intptr_t* last = NULL;
  for (int i = max_index; i >= min_index; i--) {
    FrameValue fv = _values.at(i);
    while (cur > fv.location) {
      tty->print_cr(" " INTPTR_FORMAT ": " INTPTR_FORMAT, cur, *cur);
      cur--;
    }
    if (last == fv.location) {
      const char* spacer = "          " LP64_ONLY("        ");
      tty->print_cr(" %s  %s %s", spacer, spacer, fv.description);
    } else {
      tty->print_cr(" " INTPTR_FORMAT ": " INTPTR_FORMAT " %s", fv.location, *fv.location, fv.description);
      last = fv.location;
      cur--;
    }
  }
}

#endif // ndef PRODUCT
