/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2014 SAP AG. All rights reserved.
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

#ifndef CPU_PPC_VM_INTERP_MASM_PPC_64_HPP
#define CPU_PPC_VM_INTERP_MASM_PPC_64_HPP

#include "asm/macroAssembler.hpp"
#include "interpreter/invocationCounter.hpp"

// This file specializes the assembler with interpreter-specific macros.


class InterpreterMacroAssembler: public MacroAssembler {

 public:
  InterpreterMacroAssembler(CodeBuffer* code) : MacroAssembler(code) {}

  void null_check_throw(Register a, int offset, Register temp_reg);

  void branch_to_entry(address entry, Register Rscratch);

  // Handy address generation macros.
#define thread_(field_name) in_bytes(JavaThread::field_name ## _offset()), R16_thread
#define method_(field_name) in_bytes(Method::field_name ## _offset()), R19_method

#ifdef CC_INTERP
#define state_(field_name)  in_bytes(byte_offset_of(BytecodeInterpreter, field_name)), R14_state
#define prev_state_(field_name)  in_bytes(byte_offset_of(BytecodeInterpreter, field_name)), R15_prev_state
  void pop (TosState state) {};           // Not needed.
  void push(TosState state) {};           // Not needed.
#endif

#ifndef CC_INTERP
  virtual void check_and_handle_popframe(Register java_thread);
  virtual void check_and_handle_earlyret(Register java_thread);

  // Base routine for all dispatches.
  void dispatch_base(TosState state, address* table);

  void load_earlyret_value(TosState state, Register Rscratch1);

  static const Address l_tmp;
  static const Address d_tmp;

  // dispatch routines
  void dispatch_next(TosState state, int step = 0);
  void dispatch_via (TosState state, address* table);
  void load_dispatch_table(Register dst, address* table);
  void dispatch_Lbyte_code(TosState state, Register bytecode, address* table, bool verify = false);

  // Called by shared interpreter generator.
  void dispatch_prolog(TosState state, int step = 0);
  void dispatch_epilog(TosState state, int step = 0);

  // Super call_VM calls - correspond to MacroAssembler::call_VM(_leaf) calls.
  void super_call_VM_leaf(Register thread_cache, address entry_point, Register arg_1);
  void super_call_VM(Register thread_cache, Register oop_result, Register last_java_sp,
                     address entry_point, Register arg_1, Register arg_2, bool check_exception = true);

  // Generate a subtype check: branch to ok_is_subtype if sub_klass is
  // a subtype of super_klass.  Blows registers tmp1, tmp2 and tmp3.
  void gen_subtype_check(Register sub_klass, Register super_klass,
                         Register tmp1, Register tmp2, Register tmp3, Label &ok_is_subtype);

  // Load object from cpool->resolved_references(index).
  void load_resolved_reference_at_index(Register result, Register index);

  void generate_stack_overflow_check_with_compare_and_throw(Register Rmem_frame_size, Register Rscratch1);
  void load_receiver(Register Rparam_count, Register Rrecv_dst);

  // helpers for expression stack
  void pop_i(     Register r = R17_tos);
  void pop_ptr(   Register r = R17_tos);
  void pop_l(     Register r = R17_tos);
  void pop_f(FloatRegister f = F15_ftos);
  void pop_d(FloatRegister f = F15_ftos );

  void push_i(     Register r = R17_tos);
  void push_ptr(   Register r = R17_tos);
  void push_l(     Register r = R17_tos);
  void push_f(FloatRegister f = F15_ftos );
  void push_d(FloatRegister f = F15_ftos);

  void push_2ptrs(Register first, Register second);

  void push_l_pop_d(Register l = R17_tos, FloatRegister d = F15_ftos);
  void push_d_pop_l(FloatRegister d = F15_ftos, Register l = R17_tos);

  void pop (TosState state);           // transition vtos -> state
  void push(TosState state);           // transition state -> vtos
  void empty_expression_stack();       // Resets both Lesp and SP.

 public:
  // Load values from bytecode stream:

  enum signedOrNot { Signed, Unsigned };
  enum setCCOrNot  { set_CC, dont_set_CC };

  void get_2_byte_integer_at_bcp(int         bcp_offset,
                                 Register    Rdst,
                                 signedOrNot is_signed);

  void get_4_byte_integer_at_bcp(int         bcp_offset,
                                 Register    Rdst,
                                 signedOrNot is_signed = Unsigned);

  void get_cache_index_at_bcp(Register Rdst, int bcp_offset, size_t index_size);

  void get_cache_and_index_at_bcp(Register cache, int bcp_offset, size_t index_size = sizeof(u2));

  void get_u4(Register Rdst, Register Rsrc, int offset, signedOrNot is_signed);

  // common code

  void field_offset_at(int n, Register tmp, Register dest, Register base);
  int  field_offset_at(Register object, address bcp, int offset);
  void fast_iaaccess(int n, address bcp);
  void fast_iagetfield(address bcp);
  void fast_iaputfield(address bcp, bool do_store_check);

  void index_check(Register array, Register index, int index_shift, Register tmp, Register res);
  void index_check_without_pop(Register array, Register index, int index_shift, Register tmp, Register res);

  void get_const(Register Rdst);
  void get_constant_pool(Register Rdst);
  void get_constant_pool_cache(Register Rdst);
  void get_cpool_and_tags(Register Rcpool, Register Rtags);
  void is_a(Label& L);

  void narrow(Register result);

  // Java Call Helpers
  void call_from_interpreter(Register Rtarget_method, Register Rret_addr, Register Rscratch1, Register Rscratch2);

  // --------------------------------------------------

  void unlock_if_synchronized_method(TosState state, bool throw_monitor_exception = true,
                                     bool install_monitor_exception = true);

  // Removes the current activation (incl. unlocking of monitors).
  // Additionally this code is used for earlyReturn in which case we
  // want to skip throwing an exception and installing an exception.
  void remove_activation(TosState state,
                         bool throw_monitor_exception = true,
                         bool install_monitor_exception = true);
  void merge_frames(Register Rtop_frame_sp, Register return_pc, Register Rscratch1, Register Rscratch2); // merge top frames

  void add_monitor_to_stack(bool stack_is_empty, Register Rtemp1, Register Rtemp2);

  // Local variable access helpers
  void load_local_int(Register Rdst_value, Register Rdst_address, Register Rindex);
  void load_local_long(Register Rdst_value, Register Rdst_address, Register Rindex);
  void load_local_ptr(Register Rdst_value, Register Rdst_address, Register Rindex);
  void load_local_float(FloatRegister Rdst_value, Register Rdst_address, Register Rindex);
  void load_local_double(FloatRegister Rdst_value, Register Rdst_address, Register Rindex);
  void store_local_int(Register Rvalue, Register Rindex);
  void store_local_long(Register Rvalue, Register Rindex);
  void store_local_ptr(Register Rvalue, Register Rindex);
  void store_local_float(FloatRegister Rvalue, Register Rindex);
  void store_local_double(FloatRegister Rvalue, Register Rindex);

  // Call VM for std frames
  // Special call VM versions that check for exceptions and forward exception
  // via short cut (not via expensive forward exception stub).
  void check_and_forward_exception(Register Rscratch1, Register Rscratch2);
  void call_VM(Register oop_result, address entry_point, bool check_exceptions = true);
  void call_VM(Register oop_result, address entry_point, Register arg_1, bool check_exceptions = true);
  void call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, bool check_exceptions = true);
  void call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions = true);
  // Should not be used:
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, bool check_exceptions = true) {ShouldNotReachHere();}
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, bool check_exceptions = true) {ShouldNotReachHere();}
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, bool check_exceptions = true) {ShouldNotReachHere();}
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions = true) {ShouldNotReachHere();}

  Address first_local_in_stack();

  enum LoadOrStore { load, store };
  void static_iload_or_store(int which_local, LoadOrStore direction, Register Rtmp);
  void static_aload_or_store(int which_local, LoadOrStore direction, Register Rtmp);
  void static_dload_or_store(int which_local, LoadOrStore direction);

  void save_interpreter_state(Register scratch);
  void restore_interpreter_state(Register scratch, bool bcp_and_mdx_only = false);

  void increment_backedge_counter(const Register Rcounters, Register Rtmp, Register Rtmp2, Register Rscratch);
  void test_backedge_count_for_osr(Register backedge_count, Register branch_bcp, Register Rtmp);

  void record_static_call_in_profile(Register Rentry, Register Rtmp);
  void record_receiver_call_in_profile(Register Rklass, Register Rentry, Register Rtmp);
#endif // !CC_INTERP

  void get_method_counters(Register method, Register Rcounters, Label& skip);
  void increment_invocation_counter(Register iv_be_count, Register Rtmp1, Register Rtmp2_r0);

  // Object locking
  void lock_object  (Register lock_reg, Register obj_reg);
  void unlock_object(Register lock_reg, bool check_for_exceptions = true);

#ifndef CC_INTERP

  // Interpreter profiling operations
  void set_method_data_pointer_for_bcp();
  void test_method_data_pointer(Label& zero_continue);
  void verify_method_data_pointer();
  void test_invocation_counter_for_mdp(Register invocation_count, Register Rscratch, Label &profile_continue);

  void set_mdp_data_at(int constant, Register value);

  void increment_mdp_data_at(int constant, Register counter_addr, Register Rbumped_count, bool decrement = false);

  void increment_mdp_data_at(Register counter_addr, Register Rbumped_count, bool decrement = false);
  void increment_mdp_data_at(Register reg, int constant, Register scratch, Register Rbumped_count, bool decrement = false);

  void set_mdp_flag_at(int flag_constant, Register scratch);
  void test_mdp_data_at(int offset, Register value, Label& not_equal_continue, Register test_out);

  void update_mdp_by_offset(int offset_of_disp, Register scratch);
  void update_mdp_by_offset(Register reg, int offset_of_disp,
                            Register scratch);
  void update_mdp_by_constant(int constant);
  void update_mdp_for_ret(TosState state, Register return_bci);

  void profile_taken_branch(Register scratch, Register bumped_count);
  void profile_not_taken_branch(Register scratch1, Register scratch2);
  void profile_call(Register scratch1, Register scratch2);
  void profile_final_call(Register scratch1, Register scratch2);
  void profile_virtual_call(Register Rreceiver, Register Rscratch1, Register Rscratch2,  bool receiver_can_be_null);
  void profile_typecheck(Register Rklass, Register Rscratch1, Register Rscratch2);
  void profile_typecheck_failed(Register Rscratch1, Register Rscratch2);
  void profile_ret(TosState state, Register return_bci, Register scratch1, Register scratch2);
  void profile_switch_default(Register scratch1, Register scratch2);
  void profile_switch_case(Register index, Register scratch1,Register scratch2, Register scratch3);
  void profile_null_seen(Register Rscratch1, Register Rscratch2);
  void record_klass_in_profile(Register receiver, Register scratch1, Register scratch2, bool is_virtual_call);
  void record_klass_in_profile_helper(Register receiver, Register scratch1, Register scratch2, int start_row, Label& done, bool is_virtual_call);

  // Argument and return type profiling.
  void profile_obj_type(Register obj, Register mdo_addr_base, RegisterOrConstant mdo_addr_offs, Register tmp, Register tmp2);
  void profile_arguments_type(Register callee, Register tmp1, Register tmp2, bool is_virtual);
  void profile_return_type(Register ret, Register tmp1, Register tmp2);
  void profile_parameters_type(Register tmp1, Register tmp2, Register tmp3, Register tmp4);

#endif // !CC_INTERP

  // Debugging
  void verify_oop(Register reg, TosState state = atos);    // only if +VerifyOops && state == atos
#ifndef CC_INTERP
  void verify_oop_or_return_address(Register reg, Register rtmp); // for astore
  void verify_FPU(int stack_depth, TosState state = ftos);
#endif // !CC_INTERP

  typedef enum { NotifyJVMTI, SkipNotifyJVMTI } NotifyMethodExitMode;

  // Support for jvmdi/jvmpi.
  void notify_method_entry();
  void notify_method_exit(bool is_native_method, TosState state,
                          NotifyMethodExitMode mode, bool check_exceptions);

#ifdef CC_INTERP
  // Convert the current TOP_IJAVA_FRAME into a PARENT_IJAVA_FRAME
  // (using parent_frame_resize) and push a new interpreter
  // TOP_IJAVA_FRAME (using frame_size).
  void push_interpreter_frame(Register top_frame_size, Register parent_frame_resize,
                              Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register pc=noreg);

  // Pop the topmost TOP_IJAVA_FRAME and convert the previous
  // PARENT_IJAVA_FRAME back into a TOP_IJAVA_FRAME.
  void pop_interpreter_frame(Register tmp1, Register tmp2, Register tmp3, Register tmp4);

  // Turn state's interpreter frame into the current TOP_IJAVA_FRAME.
  void pop_interpreter_frame_to_state(Register state, Register tmp1, Register tmp2, Register tmp3);

  // Set SP to initial caller's sp, but before fix the back chain.
  void resize_frame_to_initial_caller(Register tmp1, Register tmp2);

  // Pop the current interpreter state (without popping the
  // correspoding frame) and restore R14_state and R15_prev_state
  // accordingly. Use prev_state_may_be_0 to indicate whether
  // prev_state may be 0 in order to generate an extra check before
  // retrieving prev_state_(_prev_link).
  void pop_interpreter_state(bool prev_state_may_be_0);

  void restore_prev_state();
#endif
};

#endif // CPU_PPC_VM_INTERP_MASM_PPC_64_HPP
