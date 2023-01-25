/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_DEOPTIMIZATION_HPP
#define SHARE_VM_RUNTIME_DEOPTIMIZATION_HPP

#include "memory/allocation.hpp"
#include "runtime/frame.inline.hpp"

class ProfileData;
class vframeArray;
class MonitorValue;
class ObjectValue;

class Deoptimization : AllStatic {
  friend class VMStructs;

 public:
  // What condition caused the deoptimization?
  enum DeoptReason {
    Reason_many = -1,             // indicates presence of several reasons
    Reason_none = 0,              // indicates absence of a relevant deopt.
    // Next 7 reasons are recorded per bytecode in DataLayout::trap_bits
    Reason_null_check,            // saw unexpected null or zero divisor (@bci)
    Reason_null_assert,           // saw unexpected non-null or non-zero (@bci)
    Reason_range_check,           // saw unexpected array index (@bci)
    Reason_class_check,           // saw unexpected object class (@bci)
    Reason_array_check,           // saw unexpected array class (aastore @bci)
    Reason_intrinsic,             // saw unexpected operand to intrinsic (@bci)
    Reason_bimorphic,             // saw unexpected object class in bimorphic inlining (@bci)

    Reason_unloaded,              // unloaded class or constant pool entry
    Reason_uninitialized,         // bad class state (uninitialized)
    Reason_unreached,             // code is not reached, compiler
    Reason_unhandled,             // arbitrary compiler limitation
    Reason_constraint,            // arbitrary runtime constraint violated
    Reason_div0_check,            // a null_check due to division by zero
    Reason_age,                   // nmethod too old; tier threshold reached
    Reason_predicate,             // compiler generated predicate failed
    Reason_loop_limit_check,      // compiler generated loop limits check failed
    Reason_speculate_class_check, // saw unexpected object class from type speculation
    Reason_rtm_state_change,      // rtm state change detected
    Reason_unstable_if,           // a branch predicted always false was taken
    Reason_LIMIT,
    // Note:  Keep this enum in sync. with _trap_reason_name.
    Reason_RECORDED_LIMIT = Reason_bimorphic  // some are not recorded per bc
    // Note:  Reason_RECORDED_LIMIT should be < 8 to fit into 3 bits of
    // DataLayout::trap_bits.  This dependency is enforced indirectly
    // via asserts, to avoid excessive direct header-to-header dependencies.
    // See Deoptimization::trap_state_reason and class DataLayout.
  };

  // What action must be taken by the runtime?
  enum DeoptAction {
    Action_none,                  // just interpret, do not invalidate nmethod
    Action_maybe_recompile,       // recompile the nmethod; need not invalidate
    Action_reinterpret,           // invalidate the nmethod, reset IC, maybe recompile
    Action_make_not_entrant,      // invalidate the nmethod, recompile (probably)
    Action_make_not_compilable,   // invalidate the nmethod and do not compile
    Action_LIMIT
    // Note:  Keep this enum in sync. with _trap_action_name.
  };

  enum {
    _action_bits = 3,
    _reason_bits = 5,
    _action_shift = 0,
    _reason_shift = _action_shift+_action_bits,
    BC_CASE_LIMIT = PRODUCT_ONLY(1) NOT_PRODUCT(4) // for _deoptimization_hist
  };

  enum UnpackType {
    Unpack_deopt                = 0, // normal deoptimization, use pc computed in unpack_vframe_on_stack
    Unpack_exception            = 1, // exception is pending
    Unpack_uncommon_trap        = 2, // redo last byte code (C2 only)
    Unpack_reexecute            = 3  // reexecute bytecode (C1 only)
  };

  // Checks all compiled methods. Invalid methods are deleted and
  // corresponding activations are deoptimized.
  static int deoptimize_dependents();

  // Deoptimizes a frame lazily. nmethod gets patched deopt happens on return to the frame
  static void deoptimize(JavaThread* thread, frame fr, RegisterMap *reg_map);

  private:
  // Does the actual work for deoptimizing a single frame
  static void deoptimize_single_frame(JavaThread* thread, frame fr);

  // Helper function to revoke biases of all monitors in frame if UseBiasedLocking
  // is enabled
  static void revoke_biases_of_monitors(JavaThread* thread, frame fr, RegisterMap* map);
  // Helper function to revoke biases of all monitors in frames
  // executing in a particular CodeBlob if UseBiasedLocking is enabled
  static void revoke_biases_of_monitors(CodeBlob* cb);

#ifdef COMPILER2
  // Support for restoring non-escaping objects
  static bool realloc_objects(JavaThread* thread, frame* fr, GrowableArray<ScopeValue*>* objects, TRAPS);
  static void reassign_type_array_elements(frame* fr, RegisterMap* reg_map, ObjectValue* sv, typeArrayOop obj, BasicType type);
  static void reassign_object_array_elements(frame* fr, RegisterMap* reg_map, ObjectValue* sv, objArrayOop obj);
  static void reassign_fields(frame* fr, RegisterMap* reg_map, GrowableArray<ScopeValue*>* objects, bool realloc_failures);
  static void relock_objects(GrowableArray<MonitorInfo*>* monitors, JavaThread* thread, bool realloc_failures);
  static void pop_frames_failed_reallocs(JavaThread* thread, vframeArray* array);
  NOT_PRODUCT(static void print_objects(GrowableArray<ScopeValue*>* objects, bool realloc_failures);)
#endif // COMPILER2

  public:
  static vframeArray* create_vframeArray(JavaThread* thread, frame fr, RegisterMap *reg_map, GrowableArray<compiledVFrame*>* chunk, bool realloc_failures);

  // Interface used for unpacking deoptimized frames

  // UnrollBlock is returned by fetch_unroll_info() to the deoptimization handler (blob).
  // This is only a CheapObj to ease debugging after a deopt failure
  class UnrollBlock : public CHeapObj<mtCompiler> {
   private:
    int       _size_of_deoptimized_frame; // Size, in bytes, of current deoptimized frame
    int       _caller_adjustment;         // Adjustment, in bytes, to caller's SP by initial interpreted frame
    int       _number_of_frames;          // Number frames to unroll
    int       _total_frame_sizes;         // Total of number*sizes frames
    intptr_t* _frame_sizes;               // Array of frame sizes, in bytes, for unrolling the stack
    address*  _frame_pcs;                 // Array of frame pc's, in bytes, for unrolling the stack
    intptr_t* _register_block;            // Block for storing callee-saved registers.
    BasicType _return_type;               // Tells if we have to restore double or long return value
    intptr_t  _initial_info;              // Platform dependent data for the sender frame (was FP on x86)
    int       _caller_actual_parameters;  // The number of actual arguments at the
                                          // interpreted caller of the deoptimized frame

    // The following fields are used as temps during the unpacking phase
    // (which is tight on registers, especially on x86). They really ought
    // to be PD variables but that involves moving this class into its own
    // file to use the pd include mechanism. Maybe in a later cleanup ...
    intptr_t  _counter_temp;              // SHOULD BE PD VARIABLE (x86 frame count temp)
    intptr_t  _unpack_kind;               // SHOULD BE PD VARIABLE (x86 unpack kind)
    intptr_t  _sender_sp_temp;            // SHOULD BE PD VARIABLE (x86 sender_sp)
   public:
    // Constructor
    UnrollBlock(int  size_of_deoptimized_frame,
                int  caller_adjustment,
                int  caller_actual_parameters,
                int  number_of_frames,
                intptr_t* frame_sizes,
                address* frames_pcs,
                BasicType return_type);
    ~UnrollBlock();

    // Returns where a register is located.
    intptr_t* value_addr_at(int register_number) const;

    // Accessors
    intptr_t* frame_sizes()  const { return _frame_sizes; }
    int number_of_frames()  const { return _number_of_frames; }
    address*  frame_pcs()   const { return _frame_pcs ; }

    // Returns the total size of frames
    int size_of_frames() const;

    void set_initial_info(intptr_t info) { _initial_info = info; }

    int caller_actual_parameters() const { return _caller_actual_parameters; }

    // Accessors used by the code generator for the unpack stub.
    static int size_of_deoptimized_frame_offset_in_bytes() { return offset_of(UnrollBlock, _size_of_deoptimized_frame); }
    static int caller_adjustment_offset_in_bytes()         { return offset_of(UnrollBlock, _caller_adjustment);         }
    static int number_of_frames_offset_in_bytes()          { return offset_of(UnrollBlock, _number_of_frames);          }
    static int frame_sizes_offset_in_bytes()               { return offset_of(UnrollBlock, _frame_sizes);               }
    static int total_frame_sizes_offset_in_bytes()         { return offset_of(UnrollBlock, _total_frame_sizes);         }
    static int frame_pcs_offset_in_bytes()                 { return offset_of(UnrollBlock, _frame_pcs);                 }
    static int register_block_offset_in_bytes()            { return offset_of(UnrollBlock, _register_block);            }
    static int return_type_offset_in_bytes()               { return offset_of(UnrollBlock, _return_type);               }
    static int counter_temp_offset_in_bytes()              { return offset_of(UnrollBlock, _counter_temp);              }
    static int initial_info_offset_in_bytes()              { return offset_of(UnrollBlock, _initial_info);              }
    static int unpack_kind_offset_in_bytes()               { return offset_of(UnrollBlock, _unpack_kind);               }
    static int sender_sp_temp_offset_in_bytes()            { return offset_of(UnrollBlock, _sender_sp_temp);            }

    BasicType return_type() const { return _return_type; }
    void print();
  };

  //** Returns an UnrollBlock continuing information
  // how to make room for the resulting interpreter frames.
  // Called by assembly stub after execution has returned to
  // deoptimized frame.
  // @argument thread.     Thread where stub_frame resides.
  // @see OptoRuntime::deoptimization_fetch_unroll_info_C
  static UnrollBlock* fetch_unroll_info(JavaThread* thread);

  //** Unpacks vframeArray onto execution stack
  // Called by assembly stub after execution has returned to
  // deoptimized frame and after the stack unrolling.
  // @argument thread.     Thread where stub_frame resides.
  // @argument exec_mode.  Determines how execution should be continuted in top frame.
  //                       0 means continue after current byte code
  //                       1 means exception has happened, handle exception
  //                       2 means reexecute current bytecode (for uncommon traps).
  // @see OptoRuntime::deoptimization_unpack_frames_C
  // Return BasicType of call return type, if any
  static BasicType unpack_frames(JavaThread* thread, int exec_mode);

  // Cleans up deoptimization bits on thread after unpacking or in the
  // case of an exception.
  static void cleanup_deopt_info(JavaThread  *thread,
                                 vframeArray * array);

  // Restores callee saved values from deoptimized frame into oldest interpreter frame
  // so caller of the deoptimized frame will get back the values it expects.
  static void unwind_callee_save_values(frame* f, vframeArray* vframe_array);

  //** Performs an uncommon trap for compiled code.
  // The top most compiler frame is converted into interpreter frames
  static UnrollBlock* uncommon_trap(JavaThread* thread, jint unloaded_class_index);
  // Helper routine that enters the VM and may block
  static void uncommon_trap_inner(JavaThread* thread, jint unloaded_class_index);

  //** Deoptimizes the frame identified by id.
  // Only called from VMDeoptimizeFrame
  // @argument thread.     Thread where stub_frame resides.
  // @argument id.         id of frame that should be deoptimized.
  static void deoptimize_frame_internal(JavaThread* thread, intptr_t* id);

  // If thread is not the current thread then execute
  // VM_DeoptimizeFrame otherwise deoptimize directly.
  static void deoptimize_frame(JavaThread* thread, intptr_t* id);

  // Statistics
  static void gather_statistics(DeoptReason reason, DeoptAction action,
                                Bytecodes::Code bc = Bytecodes::_illegal);
  static void print_statistics();

  // How much room to adjust the last frame's SP by, to make space for
  // the callee's interpreter frame (which expects locals to be next to
  // incoming arguments)
  static int last_frame_adjust(int callee_parameters, int callee_locals);

  // trap_request codes
  static DeoptReason trap_request_reason(int trap_request) {
    if (trap_request < 0)
      return (DeoptReason)
        ((~(trap_request) >> _reason_shift) & right_n_bits(_reason_bits));
    else
      // standard reason for unloaded CP entry
      return Reason_unloaded;
  }
  static DeoptAction trap_request_action(int trap_request) {
    if (trap_request < 0)
      return (DeoptAction)
        ((~(trap_request) >> _action_shift) & right_n_bits(_action_bits));
    else
      // standard action for unloaded CP entry
      return _unloaded_action;
  }
  static int trap_request_index(int trap_request) {
    if (trap_request < 0)
      return -1;
    else
      return trap_request;
  }
  static int make_trap_request(DeoptReason reason, DeoptAction action,
                               int index = -1) {
    assert((1 << _reason_bits) >= Reason_LIMIT, "enough bits");
    assert((1 << _action_bits) >= Action_LIMIT, "enough bits");
    int trap_request;
    if (index != -1)
      trap_request = index;
    else
      trap_request = (~(((reason) << _reason_shift)
                        + ((action) << _action_shift)));
    assert(reason == trap_request_reason(trap_request), "valid reason");
    assert(action == trap_request_action(trap_request), "valid action");
    assert(index  == trap_request_index(trap_request),  "valid index");
    return trap_request;
  }

  // The trap_state stored in a MDO is decoded here.
  // It records two items of information.
  //  reason:  If a deoptimization happened here, what its reason was,
  //           or if there were multiple deopts with differing reasons.
  //  recompiled: If a deoptimization here triggered a recompilation.
  // Note that not all reasons are recorded per-bci.
  static DeoptReason trap_state_reason(int trap_state);
  static int  trap_state_has_reason(int trap_state, int reason);
  static int  trap_state_add_reason(int trap_state, int reason);
  static bool trap_state_is_recompiled(int trap_state);
  static int  trap_state_set_recompiled(int trap_state, bool z);
  static const char* format_trap_state(char* buf, size_t buflen,
                                       int trap_state);

  static bool reason_is_recorded_per_bytecode(DeoptReason reason) {
    return reason > Reason_none && reason <= Reason_RECORDED_LIMIT;
  }

  static DeoptReason reason_recorded_per_bytecode_if_any(DeoptReason reason) {
    if (reason_is_recorded_per_bytecode(reason))
      return reason;
    else if (reason == Reason_div0_check) // null check due to divide-by-zero?
      return Reason_null_check;           // recorded per BCI as a null check
    else if (reason == Reason_speculate_class_check)
      return Reason_class_check;
    else if (reason == Reason_unstable_if)
      return Reason_intrinsic;
    else
      return Reason_none;
  }

  static bool reason_is_speculate(int reason) {
    if (reason == Reason_speculate_class_check) {
      return true;
    }
    return false;
  }

  static uint per_method_trap_limit(int reason) {
    return reason_is_speculate(reason) ? (uint)PerMethodSpecTrapLimit : (uint)PerMethodTrapLimit;
  }

  static const char* trap_reason_name(int reason);
  static const char* trap_action_name(int action);
  // Format like reason='foo' action='bar' index='123'.
  // This is suitable both for XML and for tty output.
  static const char* format_trap_request(char* buf, size_t buflen,
                                         int trap_request);

  static jint total_deoptimization_count();
  static jint deoptimization_count(DeoptReason reason);

  // JVMTI PopFrame support

  // Preserves incoming arguments to the popped frame when it is
  // returning to a deoptimized caller
  static void popframe_preserve_args(JavaThread* thread, int bytes_to_save, void* start_address);

 private:
  static MethodData* get_method_data(JavaThread* thread, methodHandle m, bool create_if_missing);
  // Update the mdo's count and per-BCI reason bits, returning previous state:
  static ProfileData* query_update_method_data(MethodData* trap_mdo,
                                               int trap_bci,
                                               DeoptReason reason,
                                               Method* compiled_method,
                                               //outputs:
                                               uint& ret_this_trap_count,
                                               bool& ret_maybe_prior_trap,
                                               bool& ret_maybe_prior_recompile);
  // class loading support for uncommon trap
  static void load_class_by_index(constantPoolHandle constant_pool, int index, TRAPS);
  static void load_class_by_index(constantPoolHandle constant_pool, int index);

  static UnrollBlock* fetch_unroll_info_helper(JavaThread* thread);

  static DeoptAction _unloaded_action; // == Action_reinterpret;
  static const char* _trap_reason_name[Reason_LIMIT];
  static const char* _trap_action_name[Action_LIMIT];

  static juint _deoptimization_hist[Reason_LIMIT][1+Action_LIMIT][BC_CASE_LIMIT];
  // Note:  Histogram array size is 1-2 Kb.

 public:
  static void update_method_data_from_interpreter(MethodData* trap_mdo, int trap_bci, int reason);
};

class DeoptimizationMarker : StackObj {  // for profiling
  static bool _is_active;
public:
  DeoptimizationMarker()  { _is_active = true; }
  ~DeoptimizationMarker() { _is_active = false; }
  static bool is_active() { return _is_active; }
};

#endif // SHARE_VM_RUNTIME_DEOPTIMIZATION_HPP
