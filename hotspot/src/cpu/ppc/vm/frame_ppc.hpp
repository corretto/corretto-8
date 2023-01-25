/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_PPC_VM_FRAME_PPC_HPP
#define CPU_PPC_VM_FRAME_PPC_HPP

#include "runtime/synchronizer.hpp"
#include "utilities/top.hpp"

  //  C frame layout on PPC-64.
  //
  //  In this figure the stack grows upwards, while memory grows
  //  downwards. See "64-bit PowerPC ELF ABI Supplement Version 1.7",
  //  IBM Corp. (2003-10-29)
  //  (http://math-atlas.sourceforge.net/devel/assembly/PPC-elf64abi-1.7.pdf).
  //
  //  Square brackets denote stack regions possibly larger
  //  than a single 64 bit slot.
  //
  //  STACK:
  //    0       [C_FRAME]               <-- SP after prolog (mod 16 = 0)
  //            [C_FRAME]               <-- SP before prolog
  //            ...
  //            [C_FRAME]
  //
  //  C_FRAME:
  //    0       [ABI_REG_ARGS]
  //    112     CARG_9: outgoing arg 9 (arg_1 ... arg_8 via gpr_3 ... gpr_{10})
  //            ...
  //    40+M*8  CARG_M: outgoing arg M (M is the maximum of outgoing args taken over all call sites in the procedure)
  //            local 1
  //            ...
  //            local N
  //            spill slot for vector reg (16 bytes aligned)
  //            ...
  //            spill slot for vector reg
  //            alignment       (4 or 12 bytes)
  //    V       SR_VRSAVE
  //    V+4     spill slot for GR
  //    ...     ...
  //            spill slot for GR
  //            spill slot for FR
  //            ...
  //            spill slot for FR
  //
  //  ABI_48:
  //    0       caller's SP
  //    8       space for condition register (CR) for next call
  //    16      space for link register (LR) for next call
  //    24      reserved
  //    32      reserved
  //    40      space for TOC (=R2) register for next call
  //
  //  ABI_REG_ARGS:
  //    0       [ABI_48]
  //    48      CARG_1: spill slot for outgoing arg 1. used by next callee.
  //    ...     ...
  //    104     CARG_8: spill slot for outgoing arg 8. used by next callee.
  //

 public:

  // C frame layout

  enum {
    // stack alignment
    alignment_in_bytes = 16,
    // log_2(16*8 bits) = 7.
    log_2_of_alignment_in_bits = 7
  };

  // ABI_MINFRAME:
  struct abi_minframe {
    uint64_t callers_sp;
    uint64_t cr;                                  //_16
    uint64_t lr;
#if !defined(ABI_ELFv2)
    uint64_t reserved1;                           //_16
    uint64_t reserved2;
#endif
    uint64_t toc;                                 //_16
    // nothing to add here!
    // aligned to frame::alignment_in_bytes (16)
  };

  enum {
    abi_minframe_size = sizeof(abi_minframe)
  };

  struct abi_reg_args : abi_minframe {
    uint64_t carg_1;
    uint64_t carg_2;                              //_16
    uint64_t carg_3;
    uint64_t carg_4;                              //_16
    uint64_t carg_5;
    uint64_t carg_6;                              //_16
    uint64_t carg_7;
    uint64_t carg_8;                              //_16
    // aligned to frame::alignment_in_bytes (16)
  };

  enum {
    abi_reg_args_size = sizeof(abi_reg_args)
  };

  #define _abi(_component) \
          (offset_of(frame::abi_reg_args, _component))

  struct abi_reg_args_spill : abi_reg_args {
    // additional spill slots
    uint64_t spill_ret;
    uint64_t spill_fret;                          //_16
    // aligned to frame::alignment_in_bytes (16)
  };

  enum {
    abi_reg_args_spill_size = sizeof(abi_reg_args_spill)
  };

  #define _abi_reg_args_spill(_component) \
          (offset_of(frame::abi_reg_args_spill, _component))

  // non-volatile GPRs:

  struct spill_nonvolatiles {
    uint64_t r14;
    uint64_t r15;                                 //_16
    uint64_t r16;
    uint64_t r17;                                 //_16
    uint64_t r18;
    uint64_t r19;                                 //_16
    uint64_t r20;
    uint64_t r21;                                 //_16
    uint64_t r22;
    uint64_t r23;                                 //_16
    uint64_t r24;
    uint64_t r25;                                 //_16
    uint64_t r26;
    uint64_t r27;                                 //_16
    uint64_t r28;
    uint64_t r29;                                 //_16
    uint64_t r30;
    uint64_t r31;                                 //_16

    double f14;
    double f15;
    double f16;
    double f17;
    double f18;
    double f19;
    double f20;
    double f21;
    double f22;
    double f23;
    double f24;
    double f25;
    double f26;
    double f27;
    double f28;
    double f29;
    double f30;
    double f31;

    // aligned to frame::alignment_in_bytes (16)
  };

  enum {
    spill_nonvolatiles_size = sizeof(spill_nonvolatiles)
  };

  #define _spill_nonvolatiles_neg(_component) \
     (int)(-frame::spill_nonvolatiles_size + offset_of(frame::spill_nonvolatiles, _component))



#ifndef CC_INTERP
  //  Frame layout for the Java template interpreter on PPC64.
  //
  //  Diffs to the CC_INTERP are marked with 'X'.
  //
  //  TOP_IJAVA_FRAME:
  //
  //    0       [TOP_IJAVA_FRAME_ABI]
  //            alignment (optional)
  //            [operand stack]
  //            [monitors] (optional)
  //           X[IJAVA_STATE]
  //            note: own locals are located in the caller frame.
  //
  //  PARENT_IJAVA_FRAME:
  //
  //    0       [PARENT_IJAVA_FRAME_ABI]
  //            alignment (optional)
  //            [callee's Java result]
  //            [callee's locals w/o arguments]
  //            [outgoing arguments]
  //            [used part of operand stack w/o arguments]
  //            [monitors]      (optional)
  //           X[IJAVA_STATE]
  //

  struct parent_ijava_frame_abi : abi_minframe {
  };

  enum {
    parent_ijava_frame_abi_size = sizeof(parent_ijava_frame_abi)
  };

#define _parent_ijava_frame_abi(_component) \
        (offset_of(frame::parent_ijava_frame_abi, _component))

  struct top_ijava_frame_abi : abi_reg_args {
  };

  enum {
    top_ijava_frame_abi_size = sizeof(top_ijava_frame_abi)
  };

#define _top_ijava_frame_abi(_component) \
        (offset_of(frame::top_ijava_frame_abi, _component))

  struct ijava_state {
#ifdef ASSERT
    uint64_t ijava_reserved;  // Used for assertion.
    uint64_t ijava_reserved2; // Inserted for alignment.
#endif
    uint64_t method;
    uint64_t locals;
    uint64_t monitors;
    uint64_t cpoolCache;
    uint64_t bcp;
    uint64_t esp;
    uint64_t mdx;
    uint64_t top_frame_sp; // Maybe define parent_frame_abi and move there.
    uint64_t sender_sp;
    // Slots only needed for native calls. Maybe better to move elsewhere.
    uint64_t oop_tmp;
    uint64_t lresult;
    uint64_t fresult;
    // Aligned to frame::alignment_in_bytes (16).
  };

  enum {
    ijava_state_size = sizeof(ijava_state)
  };

#define _ijava_state_neg(_component) \
        (int) (-frame::ijava_state_size + offset_of(frame::ijava_state, _component))

#else // CC_INTERP:

  //  Frame layout for the Java C++ interpreter on PPC64.
  //
  //  This frame layout provides a C-like frame for every Java frame.
  //
  //  In these figures the stack grows upwards, while memory grows
  //  downwards. Square brackets denote regions possibly larger than
  //  single 64 bit slots.
  //
  //  STACK (no JNI, no compiled code, no library calls,
  //         interpreter-loop is active):
  //    0       [InterpretMethod]
  //            [TOP_IJAVA_FRAME]
  //            [PARENT_IJAVA_FRAME]
  //            ...
  //            [PARENT_IJAVA_FRAME]
  //            [ENTRY_FRAME]
  //            [C_FRAME]
  //            ...
  //            [C_FRAME]
  //
  //  TOP_IJAVA_FRAME:
  //    0       [TOP_IJAVA_FRAME_ABI]
  //            alignment (optional)
  //            [operand stack]
  //            [monitors] (optional)
  //            [cInterpreter object]
  //            result, locals, and arguments are in parent frame!
  //
  //  PARENT_IJAVA_FRAME:
  //    0       [PARENT_IJAVA_FRAME_ABI]
  //            alignment (optional)
  //            [callee's Java result]
  //            [callee's locals w/o arguments]
  //            [outgoing arguments]
  //            [used part of operand stack w/o arguments]
  //            [monitors] (optional)
  //            [cInterpreter object]
  //
  //  ENTRY_FRAME:
  //    0       [PARENT_IJAVA_FRAME_ABI]
  //            alignment (optional)
  //            [callee's Java result]
  //            [callee's locals w/o arguments]
  //            [outgoing arguments]
  //            [ENTRY_FRAME_LOCALS]
  //
  //  PARENT_IJAVA_FRAME_ABI:
  //    0       [ABI_MINFRAME]
  //            top_frame_sp
  //            initial_caller_sp
  //
  //  TOP_IJAVA_FRAME_ABI:
  //    0       [PARENT_IJAVA_FRAME_ABI]
  //            carg_3_unused
  //            carg_4_unused
  //            carg_5_unused
  //            carg_6_unused
  //            carg_7_unused
  //            frame_manager_lr
  //

  // PARENT_IJAVA_FRAME_ABI

  struct parent_ijava_frame_abi : abi_minframe {
    // SOE registers.
    // C2i adapters spill their top-frame stack-pointer here.
    uint64_t top_frame_sp;                        //      carg_1
    // Sp of calling compiled frame before it was resized by the c2i
    // adapter or sp of call stub. Does not contain a valid value for
    // non-initial frames.
    uint64_t initial_caller_sp;                   //      carg_2
    // aligned to frame::alignment_in_bytes (16)
  };

  enum {
    parent_ijava_frame_abi_size = sizeof(parent_ijava_frame_abi)
  };

  #define _parent_ijava_frame_abi(_component) \
          (offset_of(frame::parent_ijava_frame_abi, _component))

  // TOP_IJAVA_FRAME_ABI

  struct top_ijava_frame_abi : parent_ijava_frame_abi {
    uint64_t carg_3_unused;                       //      carg_3
    uint64_t card_4_unused;                       //_16   carg_4
    uint64_t carg_5_unused;                       //      carg_5
    uint64_t carg_6_unused;                       //_16   carg_6
    uint64_t carg_7_unused;                       //      carg_7
    // Use arg8 for storing frame_manager_lr. The size of
    // top_ijava_frame_abi must match abi_reg_args.
    uint64_t frame_manager_lr;                    //_16   carg_8
    // nothing to add here!
    // aligned to frame::alignment_in_bytes (16)
  };

  enum {
    top_ijava_frame_abi_size = sizeof(top_ijava_frame_abi)
  };

  #define _top_ijava_frame_abi(_component) \
          (offset_of(frame::top_ijava_frame_abi, _component))

#endif // CC_INTERP

  // ENTRY_FRAME

  struct entry_frame_locals {
    uint64_t call_wrapper_address;
    uint64_t result_address;                      //_16
    uint64_t result_type;
    uint64_t arguments_tos_address;               //_16
    // aligned to frame::alignment_in_bytes (16)
    uint64_t r[spill_nonvolatiles_size/sizeof(uint64_t)];
  };

  enum {
    entry_frame_locals_size = sizeof(entry_frame_locals)
  };

  #define _entry_frame_locals_neg(_component) \
    (int)(-frame::entry_frame_locals_size + offset_of(frame::entry_frame_locals, _component))


  //  Frame layout for JIT generated methods
  //
  //  In these figures the stack grows upwards, while memory grows
  //  downwards. Square brackets denote regions possibly larger than single
  //  64 bit slots.
  //
  //  STACK (interpreted Java calls JIT generated Java):
  //          [JIT_FRAME]                                <-- SP (mod 16 = 0)
  //          [TOP_IJAVA_FRAME]
  //         ...
  //
  //  JIT_FRAME (is a C frame according to PPC-64 ABI):
  //          [out_preserve]
  //          [out_args]
  //          [spills]
  //          [pad_1]
  //          [monitor] (optional)
  //       ...
  //          [monitor] (optional)
  //          [pad_2]
  //          [in_preserve] added / removed by prolog / epilog
  //

  // JIT_ABI (TOP and PARENT)

  struct jit_abi {
    uint64_t callers_sp;
    uint64_t cr;
    uint64_t lr;
    uint64_t toc;
    // Nothing to add here!
    // NOT ALIGNED to frame::alignment_in_bytes (16).
  };

  struct jit_out_preserve : jit_abi {
    // Nothing to add here!
  };

  struct jit_in_preserve {
    // Nothing to add here!
  };

  enum {
    jit_out_preserve_size = sizeof(jit_out_preserve),
    jit_in_preserve_size  = sizeof(jit_in_preserve)
  };

  struct jit_monitor {
    uint64_t monitor[1];
  };

  enum {
    jit_monitor_size = sizeof(jit_monitor),
  };

 private:

  //  STACK:
  //            ...
  //            [THIS_FRAME]             <-- this._sp (stack pointer for this frame)
  //            [CALLER_FRAME]           <-- this.fp() (_sp of caller's frame)
  //            ...
  //

  // frame pointer for this frame
  intptr_t* _fp;

  // The frame's stack pointer before it has been extended by a c2i adapter;
  // needed by deoptimization
  intptr_t* _unextended_sp;
  void adjust_unextended_sp();

 public:

  // Accessors for fields
  intptr_t* fp() const { return _fp; }

  // Accessors for ABIs
  inline abi_minframe* own_abi()     const { return (abi_minframe*) _sp; }
  inline abi_minframe* callers_abi() const { return (abi_minframe*) _fp; }

 private:

  // Find codeblob and set deopt_state.
  inline void find_codeblob_and_set_pc_and_deopt_state(address pc);

 public:

  // Constructors
  inline frame(intptr_t* sp);
  frame(intptr_t* sp, address pc);
  inline frame(intptr_t* sp, address pc, intptr_t* unextended_sp);

 private:

  intptr_t* compiled_sender_sp(CodeBlob* cb) const;
  address*  compiled_sender_pc_addr(CodeBlob* cb) const;
  address*  sender_pc_addr(void) const;

 public:

#ifdef CC_INTERP
  // Additional interface for interpreter frames:
  inline interpreterState get_interpreterState() const;
#else
  inline ijava_state* get_ijava_state() const;
  // Some convenient register frame setters/getters for deoptimization.
  inline intptr_t* interpreter_frame_esp() const;
  inline void interpreter_frame_set_cpcache(ConstantPoolCache* cp);
  inline void interpreter_frame_set_esp(intptr_t* esp);
  inline void interpreter_frame_set_top_frame_sp(intptr_t* top_frame_sp);
  inline void interpreter_frame_set_sender_sp(intptr_t* sender_sp);
#endif // CC_INTERP

  // Size of a monitor in bytes.
  static int interpreter_frame_monitor_size_in_bytes();

  // The size of a cInterpreter object.
  static inline int interpreter_frame_cinterpreterstate_size_in_bytes();

 private:

  ConstantPoolCache** interpreter_frame_cpoolcache_addr() const;

 public:

  // Additional interface for entry frames:
  inline entry_frame_locals* get_entry_frame_locals() const {
    return (entry_frame_locals*) (((address) fp()) - entry_frame_locals_size);
  }

  enum {
    // normal return address is 1 bundle past PC
    pc_return_offset = 0
  };

#endif // CPU_PPC_VM_FRAME_PPC_HPP
