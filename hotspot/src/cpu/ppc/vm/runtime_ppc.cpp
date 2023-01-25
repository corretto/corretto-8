/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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
#ifdef COMPILER2
#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/vmreg.hpp"
#include "interpreter/interpreter.hpp"
#include "nativeInst_ppc.hpp"
#include "opto/runtime.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vmreg_ppc.inline.hpp"
#endif

#define __ masm->


#ifdef COMPILER2

// SP adjustment (must use unextended SP) for method handle call sites
// during exception handling.
static intptr_t adjust_SP_for_methodhandle_callsite(JavaThread *thread) {
  RegisterMap map(thread, false);
  // The frame constructor will do the correction for us (see frame::adjust_unextended_SP).
  frame mh_caller_frame = thread->last_frame().sender(&map);
  assert(mh_caller_frame.is_compiled_frame(), "Only may reach here for compiled MH call sites");
  return (intptr_t) mh_caller_frame.unextended_sp();
}

//------------------------------generate_exception_blob---------------------------
// Creates exception blob at the end.
// Using exception blob, this code is jumped from a compiled method.
//
// Given an exception pc at a call we call into the runtime for the
// handler in this method. This handler might merely restore state
// (i.e. callee save registers) unwind the frame and jump to the
// exception handler for the nmethod if there is no Java level handler
// for the nmethod.
//
// This code is entered with a jmp.
//
// Arguments:
//   R3_ARG1: exception oop
//   R4_ARG2: exception pc
//
// Results:
//   R3_ARG1: exception oop
//   R4_ARG2: exception pc in caller
//   destination: exception handler of caller
//
// Note: the exception pc MUST be at a call (precise debug information)
//
void OptoRuntime::generate_exception_blob() {
  // Allocate space for the code.
  ResourceMark rm;
  // Setup code generation tools.
  CodeBuffer buffer("exception_blob", 2048, 1024);
  InterpreterMacroAssembler* masm = new InterpreterMacroAssembler(&buffer);

  address start = __ pc();

  int frame_size_in_bytes = frame::abi_reg_args_size;
  OopMap* map = new OopMap(frame_size_in_bytes / sizeof(jint), 0);

  // Exception pc is 'return address' for stack walker.
  __ std(R4_ARG2/*exception pc*/, _abi(lr), R1_SP);

  // Store the exception in the Thread object.
  __ std(R3_ARG1/*exception oop*/, in_bytes(JavaThread::exception_oop_offset()), R16_thread);
  __ std(R4_ARG2/*exception pc*/,  in_bytes(JavaThread::exception_pc_offset()),  R16_thread);

  // Save callee-saved registers.
  // Push a C frame for the exception blob. It is needed for the C call later on.
  __ push_frame_reg_args(0, R11_scratch1);

  // This call does all the hard work. It checks if an exception handler
  // exists in the method.
  // If so, it returns the handler address.
  // If not, it prepares for stack-unwinding, restoring the callee-save
  // registers of the frame being removed.
  __ set_last_Java_frame(/*sp=*/R1_SP, noreg);

  __ mr(R3_ARG1, R16_thread);
#if defined(ABI_ELFv2)
  __ call_c((address) OptoRuntime::handle_exception_C, relocInfo::none);
#else
  __ call_c(CAST_FROM_FN_PTR(FunctionDescriptor*, OptoRuntime::handle_exception_C),
            relocInfo::none);
#endif
  address calls_return_pc = __ last_calls_return_pc();
# ifdef ASSERT
  __ cmpdi(CCR0, R3_RET, 0);
  __ asm_assert_ne("handle_exception_C must not return NULL", 0x601);
# endif

  // Set an oopmap for the call site. This oopmap will only be used if we
  // are unwinding the stack. Hence, all locations will be dead.
  // Callee-saved registers will be the same as the frame above (i.e.,
  // handle_exception_stub), since they were restored when we got the
  // exception.
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(calls_return_pc - start, map);

  // Get unextended_sp for method handle call sites.
  Label mh_callsite, mh_done; // Use a 2nd c call if it's a method handle call site.
  __ lwa(R4_ARG2, in_bytes(JavaThread::is_method_handle_return_offset()), R16_thread);
  __ cmpwi(CCR0, R4_ARG2, 0);
  __ bne(CCR0, mh_callsite);

  __ mtctr(R3_RET); // Move address of exception handler to SR_CTR.
  __ reset_last_Java_frame();
  __ pop_frame();

  __ bind(mh_done);
  // We have a handler in register SR_CTR (could be deopt blob).

  // Get the exception oop.
  __ ld(R3_ARG1, in_bytes(JavaThread::exception_oop_offset()), R16_thread);

  // Get the exception pc in case we are deoptimized.
  __ ld(R4_ARG2, in_bytes(JavaThread::exception_pc_offset()), R16_thread);

  // Reset thread values.
  __ li(R0, 0);
#ifdef ASSERT
  __ std(R0, in_bytes(JavaThread::exception_handler_pc_offset()), R16_thread);
  __ std(R0, in_bytes(JavaThread::exception_pc_offset()), R16_thread);
#endif
  // Clear the exception oop so GC no longer processes it as a root.
  __ std(R0, in_bytes(JavaThread::exception_oop_offset()), R16_thread);

  // Move exception pc into SR_LR.
  __ mtlr(R4_ARG2);
  __ bctr();


  // Same as above, but also set sp to unextended_sp.
  __ bind(mh_callsite);
  __ mr(R31, R3_RET); // Save branch address.
  __ mr(R3_ARG1, R16_thread);
#if defined(ABI_ELFv2)
  __ call_c((address) adjust_SP_for_methodhandle_callsite, relocInfo::none);
#else
  __ call_c(CAST_FROM_FN_PTR(FunctionDescriptor*, adjust_SP_for_methodhandle_callsite), relocInfo::none);
#endif
  // Returns unextended_sp in R3_RET.

  __ mtctr(R31); // Move address of exception handler to SR_CTR.
  __ reset_last_Java_frame();

  __ mr(R1_SP, R3_RET); // Set sp to unextended_sp.
  __ b(mh_done);


  // Make sure all code is generated.
  masm->flush();

  // Set exception blob.
  _exception_blob = ExceptionBlob::create(&buffer, oop_maps,
                                          frame_size_in_bytes/wordSize);
}

#endif // COMPILER2
