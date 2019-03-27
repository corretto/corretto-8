/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_INTERPRETER_INTERPRETERRUNTIME_HPP
#define SHARE_VM_INTERPRETER_INTERPRETERRUNTIME_HPP

#include "interpreter/bytecode.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/universe.hpp"
#include "oops/method.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/signature.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/top.hpp"

// The InterpreterRuntime is called by the interpreter for everything
// that cannot/should not be dealt with in assembly and needs C support.

class InterpreterRuntime: AllStatic {
  friend class BytecodeClosure; // for method and bcp
  friend class PrintingClosure; // for method and bcp

 private:
  // Helper functions to access current interpreter state
  static frame     last_frame(JavaThread *thread)    { return thread->last_frame(); }
  static Method*   method(JavaThread *thread)        { return last_frame(thread).interpreter_frame_method(); }
  static address   bcp(JavaThread *thread)           { return last_frame(thread).interpreter_frame_bcp(); }
  static int       bci(JavaThread *thread)           { return last_frame(thread).interpreter_frame_bci(); }
  static void      set_bcp_and_mdp(address bcp, JavaThread*thread);
  static Bytecodes::Code code(JavaThread *thread)    {
    // pass method to avoid calling unsafe bcp_to_method (partial fix 4926272)
    return Bytecodes::code_at(method(thread), bcp(thread));
  }
  static bool      already_resolved(JavaThread *thread) { return cache_entry(thread)->is_resolved(code(thread)); }
  static Bytecode  bytecode(JavaThread *thread)      { return Bytecode(method(thread), bcp(thread)); }
  static int       get_index_u1(JavaThread *thread, Bytecodes::Code bc)
                                                        { return bytecode(thread).get_index_u1(bc); }
  static int       get_index_u2(JavaThread *thread, Bytecodes::Code bc)
                                                        { return bytecode(thread).get_index_u2(bc); }
  static int       get_index_u2_cpcache(JavaThread *thread, Bytecodes::Code bc)
                                                        { return bytecode(thread).get_index_u2_cpcache(bc); }
  static int       get_index_u4(JavaThread *thread, Bytecodes::Code bc)
                                                        { return bytecode(thread).get_index_u4(bc); }
  static int       number_of_dimensions(JavaThread *thread)  { return bcp(thread)[3]; }

  static ConstantPoolCacheEntry* cache_entry_at(JavaThread *thread, int i)  { return method(thread)->constants()->cache()->entry_at(i); }
  static ConstantPoolCacheEntry* cache_entry(JavaThread *thread)            { return cache_entry_at(thread, Bytes::get_native_u2(bcp(thread) + 1)); }
  static void      note_trap_inner(JavaThread* thread, int reason,
                                   methodHandle trap_method, int trap_bci, TRAPS);
  static void      note_trap(JavaThread *thread, int reason, TRAPS);
#ifdef CC_INTERP
  // Profile traps in C++ interpreter.
  static void      note_trap(JavaThread* thread, int reason, Method *method, int trap_bci);
#endif // CC_INTERP

  // Inner work method for Interpreter's frequency counter overflow.
  static nmethod* frequency_counter_overflow_inner(JavaThread* thread, address branch_bcp);

 public:
  // Constants
  static void    ldc           (JavaThread* thread, bool wide);
  static void    resolve_ldc   (JavaThread* thread, Bytecodes::Code bytecode);

  // Allocation
  static void    _new          (JavaThread* thread, ConstantPool* pool, int index);
  static void    newarray      (JavaThread* thread, BasicType type, jint size);
  static void    anewarray     (JavaThread* thread, ConstantPool* pool, int index, jint size);
  static void    multianewarray(JavaThread* thread, jint* first_size_address);
  static void    register_finalizer(JavaThread* thread, oopDesc* obj);

  // Quicken instance-of and check-cast bytecodes
  static void    quicken_io_cc(JavaThread* thread);

  // Exceptions thrown by the interpreter
  static void    throw_AbstractMethodError(JavaThread* thread);
  static void    throw_IncompatibleClassChangeError(JavaThread* thread);
  static void    throw_StackOverflowError(JavaThread* thread);
  static void    throw_ArrayIndexOutOfBoundsException(JavaThread* thread, char* name, jint index);
  static void    throw_ClassCastException(JavaThread* thread, oopDesc* obj);
  static void    create_exception(JavaThread* thread, char* name, char* message);
  static void    create_klass_exception(JavaThread* thread, char* name, oopDesc* obj);
  static address exception_handler_for_exception(JavaThread* thread, oopDesc* exception);
#if INCLUDE_JVMTI
  static void    member_name_arg_or_null(JavaThread* thread, address dmh, Method* m, address bcp);
#endif
  static void    throw_pending_exception(JavaThread* thread);

#ifdef CC_INTERP
  // Profile traps in C++ interpreter.
  static void    note_nullCheck_trap (JavaThread* thread, Method *method, int trap_bci);
  static void    note_div0Check_trap (JavaThread* thread, Method *method, int trap_bci);
  static void    note_rangeCheck_trap(JavaThread* thread, Method *method, int trap_bci);
  static void    note_classCheck_trap(JavaThread* thread, Method *method, int trap_bci);
  static void    note_arrayCheck_trap(JavaThread* thread, Method *method, int trap_bci);
  // A dummy for makros that shall not profile traps.
  static void    note_no_trap(JavaThread* thread, Method *method, int trap_bci) {}
#endif // CC_INTERP

  // Statics & fields
  static void    resolve_get_put(JavaThread* thread, Bytecodes::Code bytecode);

  // Synchronization
  static void    monitorenter(JavaThread* thread, BasicObjectLock* elem);
  static void    monitorexit (JavaThread* thread, BasicObjectLock* elem);

  static void    throw_illegal_monitor_state_exception(JavaThread* thread);
  static void    new_illegal_monitor_state_exception(JavaThread* thread);

  // Calls
  static void    resolve_invoke       (JavaThread* thread, Bytecodes::Code bytecode);
  static void    resolve_invokehandle (JavaThread* thread);
  static void    resolve_invokedynamic(JavaThread* thread);

  // Breakpoints
  static void _breakpoint(JavaThread* thread, Method* method, address bcp);
  static Bytecodes::Code get_original_bytecode_at(JavaThread* thread, Method* method, address bcp);
  static void            set_original_bytecode_at(JavaThread* thread, Method* method, address bcp, Bytecodes::Code new_code);
  static bool is_breakpoint(JavaThread *thread) { return Bytecodes::code_or_bp_at(bcp(thread)) == Bytecodes::_breakpoint; }

  // Safepoints
  static void    at_safepoint(JavaThread* thread);

  // Debugger support
  static void post_field_access(JavaThread *thread, oopDesc* obj,
    ConstantPoolCacheEntry *cp_entry);
  static void post_field_modification(JavaThread *thread, oopDesc* obj,
    ConstantPoolCacheEntry *cp_entry, jvalue *value);
  static void post_method_entry(JavaThread *thread);
  static void post_method_exit (JavaThread *thread);
  static int  interpreter_contains(address pc);

  // Native signature handlers
  static void prepare_native_call(JavaThread* thread, Method* method);
  static address slow_signature_handler(JavaThread* thread,
                                        Method* method,
                                        intptr_t* from, intptr_t* to);

#if defined(IA32) || defined(AMD64) || defined(ARM)
  // Popframe support (only needed on x86, AMD64 and ARM)
  static void popframe_move_outgoing_args(JavaThread* thread, void* src_address, void* dest_address);
#endif

  // Platform dependent stuff
#ifdef TARGET_ARCH_x86
# include "interpreterRT_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "interpreterRT_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "interpreterRT_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "interpreterRT_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "interpreterRT_ppc.hpp"
#endif


  // Interpreter's frequency counter overflow
  static nmethod* frequency_counter_overflow(JavaThread* thread, address branch_bcp);

  // Interpreter profiling support
  static jint    bcp_to_di(Method* method, address cur_bcp);
  static void    profile_method(JavaThread* thread);
  static void    update_mdp_for_ret(JavaThread* thread, int bci);
#ifdef ASSERT
  static void    verify_mdp(Method* method, address bcp, address mdp);
#endif // ASSERT
  static MethodCounters* build_method_counters(JavaThread* thread, Method* m);
};


class SignatureHandlerLibrary: public AllStatic {
 public:
  enum { buffer_size =  1*K }; // the size of the temporary code buffer
  enum { blob_size   = 32*K }; // the size of a handler code blob.

 private:
  static BufferBlob*              _handler_blob; // the current buffer blob containing the generated handlers
  static address                  _handler;      // next available address within _handler_blob;
  static GrowableArray<uint64_t>* _fingerprints; // the fingerprint collection
  static GrowableArray<address>*  _handlers;     // the corresponding handlers
  static address                  _buffer;       // the temporary code buffer

  static address set_handler_blob();
  static void initialize();
  static address set_handler(CodeBuffer* buffer);
  static void pd_set_handler(address handler);

 public:
  static void add(methodHandle method);
};

#endif // SHARE_VM_INTERPRETER_INTERPRETERRUNTIME_HPP
