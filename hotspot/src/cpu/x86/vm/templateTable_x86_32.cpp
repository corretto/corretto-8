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

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/templateTable.hpp"
#include "memory/universe.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "utilities/macros.hpp"

#ifndef CC_INTERP
#define __ _masm->

//----------------------------------------------------------------------------------------------------
// Platform-dependent initialization

void TemplateTable::pd_initialize() {
  // No i486 specific initialization
}

//----------------------------------------------------------------------------------------------------
// Address computation

// local variables
static inline Address iaddress(int n)            {
  return Address(rdi, Interpreter::local_offset_in_bytes(n));
}

static inline Address laddress(int n)            { return iaddress(n + 1); }
static inline Address haddress(int n)            { return iaddress(n + 0); }
static inline Address faddress(int n)            { return iaddress(n); }
static inline Address daddress(int n)            { return laddress(n); }
static inline Address aaddress(int n)            { return iaddress(n); }

static inline Address iaddress(Register r)       {
  return Address(rdi, r, Interpreter::stackElementScale());
}
static inline Address laddress(Register r)       {
  return Address(rdi, r, Interpreter::stackElementScale(), Interpreter::local_offset_in_bytes(1));
}
static inline Address haddress(Register r)       {
  return Address(rdi, r, Interpreter::stackElementScale(), Interpreter::local_offset_in_bytes(0));
}

static inline Address faddress(Register r)       { return iaddress(r); }
static inline Address daddress(Register r)       { return laddress(r); }
static inline Address aaddress(Register r)       { return iaddress(r); }

// expression stack
// (Note: Must not use symmetric equivalents at_rsp_m1/2 since they store
// data beyond the rsp which is potentially unsafe in an MT environment;
// an interrupt may overwrite that data.)
static inline Address at_rsp   () {
  return Address(rsp, 0);
}

// At top of Java expression stack which may be different than rsp().  It
// isn't for category 1 objects.
static inline Address at_tos   () {
  Address tos = Address(rsp,  Interpreter::expr_offset_in_bytes(0));
  return tos;
}

static inline Address at_tos_p1() {
  return Address(rsp,  Interpreter::expr_offset_in_bytes(1));
}

static inline Address at_tos_p2() {
  return Address(rsp,  Interpreter::expr_offset_in_bytes(2));
}

// Condition conversion
static Assembler::Condition j_not(TemplateTable::Condition cc) {
  switch (cc) {
    case TemplateTable::equal        : return Assembler::notEqual;
    case TemplateTable::not_equal    : return Assembler::equal;
    case TemplateTable::less         : return Assembler::greaterEqual;
    case TemplateTable::less_equal   : return Assembler::greater;
    case TemplateTable::greater      : return Assembler::lessEqual;
    case TemplateTable::greater_equal: return Assembler::less;
  }
  ShouldNotReachHere();
  return Assembler::zero;
}


//----------------------------------------------------------------------------------------------------
// Miscelaneous helper routines

// Store an oop (or NULL) at the address described by obj.
// If val == noreg this means store a NULL

static void do_oop_store(InterpreterMacroAssembler* _masm,
                         Address obj,
                         Register val,
                         BarrierSet::Name barrier,
                         bool precise) {
  assert(val == noreg || val == rax, "parameter is just for looks");
  switch (barrier) {
#if INCLUDE_ALL_GCS
    case BarrierSet::G1SATBCT:
    case BarrierSet::G1SATBCTLogging:
      {
        // flatten object address if needed
        // We do it regardless of precise because we need the registers
        if (obj.index() == noreg && obj.disp() == 0) {
          if (obj.base() != rdx) {
            __ movl(rdx, obj.base());
          }
        } else {
          __ leal(rdx, obj);
        }
        __ get_thread(rcx);
        __ save_bcp();
        __ g1_write_barrier_pre(rdx /* obj */,
                                rbx /* pre_val */,
                                rcx /* thread */,
                                rsi /* tmp */,
                                val != noreg /* tosca_live */,
                                false /* expand_call */);

        // Do the actual store
        // noreg means NULL
        if (val == noreg) {
          __ movptr(Address(rdx, 0), NULL_WORD);
          // No post barrier for NULL
        } else {
          __ movl(Address(rdx, 0), val);
          __ g1_write_barrier_post(rdx /* store_adr */,
                                   val /* new_val */,
                                   rcx /* thread */,
                                   rbx /* tmp */,
                                   rsi /* tmp2 */);
        }
        __ restore_bcp();

      }
      break;
#endif // INCLUDE_ALL_GCS
    case BarrierSet::CardTableModRef:
    case BarrierSet::CardTableExtension:
      {
        if (val == noreg) {
          __ movptr(obj, NULL_WORD);
        } else {
          __ movl(obj, val);
          // flatten object address if needed
          if (!precise || (obj.index() == noreg && obj.disp() == 0)) {
            __ store_check(obj.base());
          } else {
            __ leal(rdx, obj);
            __ store_check(rdx);
          }
        }
      }
      break;
    case BarrierSet::ModRef:
    case BarrierSet::Other:
      if (val == noreg) {
        __ movptr(obj, NULL_WORD);
      } else {
        __ movl(obj, val);
      }
      break;
    default      :
      ShouldNotReachHere();

  }
}

Address TemplateTable::at_bcp(int offset) {
  assert(_desc->uses_bcp(), "inconsistent uses_bcp information");
  return Address(rsi, offset);
}


void TemplateTable::patch_bytecode(Bytecodes::Code bc, Register bc_reg,
                                   Register temp_reg, bool load_bc_into_bc_reg/*=true*/,
                                   int byte_no) {
  if (!RewriteBytecodes)  return;
  Label L_patch_done;

  switch (bc) {
  case Bytecodes::_fast_aputfield:
  case Bytecodes::_fast_bputfield:
  case Bytecodes::_fast_zputfield:
  case Bytecodes::_fast_cputfield:
  case Bytecodes::_fast_dputfield:
  case Bytecodes::_fast_fputfield:
  case Bytecodes::_fast_iputfield:
  case Bytecodes::_fast_lputfield:
  case Bytecodes::_fast_sputfield:
    {
      // We skip bytecode quickening for putfield instructions when
      // the put_code written to the constant pool cache is zero.
      // This is required so that every execution of this instruction
      // calls out to InterpreterRuntime::resolve_get_put to do
      // additional, required work.
      assert(byte_no == f1_byte || byte_no == f2_byte, "byte_no out of range");
      assert(load_bc_into_bc_reg, "we use bc_reg as temp");
      __ get_cache_and_index_and_bytecode_at_bcp(bc_reg, temp_reg, temp_reg, byte_no, 1);
      __ movl(bc_reg, bc);
      __ cmpl(temp_reg, (int) 0);
      __ jcc(Assembler::zero, L_patch_done);  // don't patch
    }
    break;
  default:
    assert(byte_no == -1, "sanity");
    // the pair bytecodes have already done the load.
    if (load_bc_into_bc_reg) {
      __ movl(bc_reg, bc);
    }
  }

  if (JvmtiExport::can_post_breakpoint()) {
    Label L_fast_patch;
    // if a breakpoint is present we can't rewrite the stream directly
    __ movzbl(temp_reg, at_bcp(0));
    __ cmpl(temp_reg, Bytecodes::_breakpoint);
    __ jcc(Assembler::notEqual, L_fast_patch);
    __ get_method(temp_reg);
    // Let breakpoint table handling rewrite to quicker bytecode
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::set_original_bytecode_at), temp_reg, rsi, bc_reg);
#ifndef ASSERT
    __ jmpb(L_patch_done);
#else
    __ jmp(L_patch_done);
#endif
    __ bind(L_fast_patch);
  }

#ifdef ASSERT
  Label L_okay;
  __ load_unsigned_byte(temp_reg, at_bcp(0));
  __ cmpl(temp_reg, (int)Bytecodes::java_code(bc));
  __ jccb(Assembler::equal, L_okay);
  __ cmpl(temp_reg, bc_reg);
  __ jcc(Assembler::equal, L_okay);
  __ stop("patching the wrong bytecode");
  __ bind(L_okay);
#endif

  // patch bytecode
  __ movb(at_bcp(0), bc_reg);
  __ bind(L_patch_done);
}

//----------------------------------------------------------------------------------------------------
// Individual instructions

void TemplateTable::nop() {
  transition(vtos, vtos);
  // nothing to do
}

void TemplateTable::shouldnotreachhere() {
  transition(vtos, vtos);
  __ stop("shouldnotreachhere bytecode");
}



void TemplateTable::aconst_null() {
  transition(vtos, atos);
  __ xorptr(rax, rax);
}


void TemplateTable::iconst(int value) {
  transition(vtos, itos);
  if (value == 0) {
    __ xorptr(rax, rax);
  } else {
    __ movptr(rax, value);
  }
}


void TemplateTable::lconst(int value) {
  transition(vtos, ltos);
  if (value == 0) {
    __ xorptr(rax, rax);
  } else {
    __ movptr(rax, value);
  }
  assert(value >= 0, "check this code");
  __ xorptr(rdx, rdx);
}


void TemplateTable::fconst(int value) {
  transition(vtos, ftos);
         if (value == 0) { __ fldz();
  } else if (value == 1) { __ fld1();
  } else if (value == 2) { __ fld1(); __ fld1(); __ faddp(); // should do a better solution here
  } else                 { ShouldNotReachHere();
  }
}


void TemplateTable::dconst(int value) {
  transition(vtos, dtos);
         if (value == 0) { __ fldz();
  } else if (value == 1) { __ fld1();
  } else                 { ShouldNotReachHere();
  }
}


void TemplateTable::bipush() {
  transition(vtos, itos);
  __ load_signed_byte(rax, at_bcp(1));
}


void TemplateTable::sipush() {
  transition(vtos, itos);
  __ load_unsigned_short(rax, at_bcp(1));
  __ bswapl(rax);
  __ sarl(rax, 16);
}

void TemplateTable::ldc(bool wide) {
  transition(vtos, vtos);
  Label call_ldc, notFloat, notClass, Done;

  if (wide) {
    __ get_unsigned_2_byte_index_at_bcp(rbx, 1);
  } else {
    __ load_unsigned_byte(rbx, at_bcp(1));
  }
  __ get_cpool_and_tags(rcx, rax);
  const int base_offset = ConstantPool::header_size() * wordSize;
  const int tags_offset = Array<u1>::base_offset_in_bytes();

  // get type
  __ xorptr(rdx, rdx);
  __ movb(rdx, Address(rax, rbx, Address::times_1, tags_offset));

  // unresolved class - get the resolved class
  __ cmpl(rdx, JVM_CONSTANT_UnresolvedClass);
  __ jccb(Assembler::equal, call_ldc);

  // unresolved class in error (resolution failed) - call into runtime
  // so that the same error from first resolution attempt is thrown.
  __ cmpl(rdx, JVM_CONSTANT_UnresolvedClassInError);
  __ jccb(Assembler::equal, call_ldc);

  // resolved class - need to call vm to get java mirror of the class
  __ cmpl(rdx, JVM_CONSTANT_Class);
  __ jcc(Assembler::notEqual, notClass);

  __ bind(call_ldc);
  __ movl(rcx, wide);
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::ldc), rcx);
  __ push(atos);
  __ jmp(Done);

  __ bind(notClass);
  __ cmpl(rdx, JVM_CONSTANT_Float);
  __ jccb(Assembler::notEqual, notFloat);
  // ftos
  __ fld_s(    Address(rcx, rbx, Address::times_ptr, base_offset));
  __ push(ftos);
  __ jmp(Done);

  __ bind(notFloat);
#ifdef ASSERT
  { Label L;
    __ cmpl(rdx, JVM_CONSTANT_Integer);
    __ jcc(Assembler::equal, L);
    // String and Object are rewritten to fast_aldc
    __ stop("unexpected tag type in ldc");
    __ bind(L);
  }
#endif
  // itos JVM_CONSTANT_Integer only
  __ movl(rax, Address(rcx, rbx, Address::times_ptr, base_offset));
  __ push(itos);
  __ bind(Done);
}

// Fast path for caching oop constants.
void TemplateTable::fast_aldc(bool wide) {
  transition(vtos, atos);

  Register result = rax;
  Register tmp = rdx;
  int index_size = wide ? sizeof(u2) : sizeof(u1);

  Label resolved;

  // We are resolved if the resolved reference cache entry contains a
  // non-null object (String, MethodType, etc.)
  assert_different_registers(result, tmp);
  __ get_cache_index_at_bcp(tmp, 1, index_size);
  __ load_resolved_reference_at_index(result, tmp);
  __ testl(result, result);
  __ jcc(Assembler::notZero, resolved);

  address entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_ldc);

  // first time invocation - must resolve first
  __ movl(tmp, (int)bytecode());
  __ call_VM(result, entry, tmp);

  __ bind(resolved);

  if (VerifyOops) {
    __ verify_oop(result);
  }
}

void TemplateTable::ldc2_w() {
  transition(vtos, vtos);
  Label Long, Done;
  __ get_unsigned_2_byte_index_at_bcp(rbx, 1);

  __ get_cpool_and_tags(rcx, rax);
  const int base_offset = ConstantPool::header_size() * wordSize;
  const int tags_offset = Array<u1>::base_offset_in_bytes();

  // get type
  __ cmpb(Address(rax, rbx, Address::times_1, tags_offset), JVM_CONSTANT_Double);
  __ jccb(Assembler::notEqual, Long);
  // dtos
  __ fld_d(    Address(rcx, rbx, Address::times_ptr, base_offset));
  __ push(dtos);
  __ jmpb(Done);

  __ bind(Long);
  // ltos
  __ movptr(rax, Address(rcx, rbx, Address::times_ptr, base_offset + 0 * wordSize));
  NOT_LP64(__ movptr(rdx, Address(rcx, rbx, Address::times_ptr, base_offset + 1 * wordSize)));

  __ push(ltos);

  __ bind(Done);
}


void TemplateTable::locals_index(Register reg, int offset) {
  __ load_unsigned_byte(reg, at_bcp(offset));
  __ negptr(reg);
}


void TemplateTable::iload() {
  transition(vtos, itos);
  if (RewriteFrequentPairs) {
    Label rewrite, done;

    // get next byte
    __ load_unsigned_byte(rbx, at_bcp(Bytecodes::length_for(Bytecodes::_iload)));
    // if _iload, wait to rewrite to iload2.  We only want to rewrite the
    // last two iloads in a pair.  Comparing against fast_iload means that
    // the next bytecode is neither an iload or a caload, and therefore
    // an iload pair.
    __ cmpl(rbx, Bytecodes::_iload);
    __ jcc(Assembler::equal, done);

    __ cmpl(rbx, Bytecodes::_fast_iload);
    __ movl(rcx, Bytecodes::_fast_iload2);
    __ jccb(Assembler::equal, rewrite);

    // if _caload, rewrite to fast_icaload
    __ cmpl(rbx, Bytecodes::_caload);
    __ movl(rcx, Bytecodes::_fast_icaload);
    __ jccb(Assembler::equal, rewrite);

    // rewrite so iload doesn't check again.
    __ movl(rcx, Bytecodes::_fast_iload);

    // rewrite
    // rcx: fast bytecode
    __ bind(rewrite);
    patch_bytecode(Bytecodes::_iload, rcx, rbx, false);
    __ bind(done);
  }

  // Get the local value into tos
  locals_index(rbx);
  __ movl(rax, iaddress(rbx));
}


void TemplateTable::fast_iload2() {
  transition(vtos, itos);
  locals_index(rbx);
  __ movl(rax, iaddress(rbx));
  __ push(itos);
  locals_index(rbx, 3);
  __ movl(rax, iaddress(rbx));
}

void TemplateTable::fast_iload() {
  transition(vtos, itos);
  locals_index(rbx);
  __ movl(rax, iaddress(rbx));
}


void TemplateTable::lload() {
  transition(vtos, ltos);
  locals_index(rbx);
  __ movptr(rax, laddress(rbx));
  NOT_LP64(__ movl(rdx, haddress(rbx)));
}


void TemplateTable::fload() {
  transition(vtos, ftos);
  locals_index(rbx);
  __ fld_s(faddress(rbx));
}


void TemplateTable::dload() {
  transition(vtos, dtos);
  locals_index(rbx);
  __ fld_d(daddress(rbx));
}


void TemplateTable::aload() {
  transition(vtos, atos);
  locals_index(rbx);
  __ movptr(rax, aaddress(rbx));
}


void TemplateTable::locals_index_wide(Register reg) {
  __ load_unsigned_short(reg, at_bcp(2));
  __ bswapl(reg);
  __ shrl(reg, 16);
  __ negptr(reg);
}


void TemplateTable::wide_iload() {
  transition(vtos, itos);
  locals_index_wide(rbx);
  __ movl(rax, iaddress(rbx));
}


void TemplateTable::wide_lload() {
  transition(vtos, ltos);
  locals_index_wide(rbx);
  __ movptr(rax, laddress(rbx));
  NOT_LP64(__ movl(rdx, haddress(rbx)));
}


void TemplateTable::wide_fload() {
  transition(vtos, ftos);
  locals_index_wide(rbx);
  __ fld_s(faddress(rbx));
}


void TemplateTable::wide_dload() {
  transition(vtos, dtos);
  locals_index_wide(rbx);
  __ fld_d(daddress(rbx));
}


void TemplateTable::wide_aload() {
  transition(vtos, atos);
  locals_index_wide(rbx);
  __ movptr(rax, aaddress(rbx));
}

void TemplateTable::index_check(Register array, Register index) {
  // Pop ptr into array
  __ pop_ptr(array);
  index_check_without_pop(array, index);
}

void TemplateTable::index_check_without_pop(Register array, Register index) {
  // destroys rbx,
  // check array
  __ null_check(array, arrayOopDesc::length_offset_in_bytes());
  LP64_ONLY(__ movslq(index, index));
  // check index
  __ cmpl(index, Address(array, arrayOopDesc::length_offset_in_bytes()));
  if (index != rbx) {
    // ??? convention: move aberrant index into rbx, for exception message
    assert(rbx != array, "different registers");
    __ mov(rbx, index);
  }
  __ jump_cc(Assembler::aboveEqual,
             ExternalAddress(Interpreter::_throw_ArrayIndexOutOfBoundsException_entry));
}


void TemplateTable::iaload() {
  transition(itos, itos);
  // rdx: array
  index_check(rdx, rax);  // kills rbx,
  // rax,: index
  __ movl(rax, Address(rdx, rax, Address::times_4, arrayOopDesc::base_offset_in_bytes(T_INT)));
}


void TemplateTable::laload() {
  transition(itos, ltos);
  // rax,: index
  // rdx: array
  index_check(rdx, rax);
  __ mov(rbx, rax);
  // rbx,: index
  __ movptr(rax, Address(rdx, rbx, Address::times_8, arrayOopDesc::base_offset_in_bytes(T_LONG) + 0 * wordSize));
  NOT_LP64(__ movl(rdx, Address(rdx, rbx, Address::times_8, arrayOopDesc::base_offset_in_bytes(T_LONG) + 1 * wordSize)));
}


void TemplateTable::faload() {
  transition(itos, ftos);
  // rdx: array
  index_check(rdx, rax);  // kills rbx,
  // rax,: index
  __ fld_s(Address(rdx, rax, Address::times_4, arrayOopDesc::base_offset_in_bytes(T_FLOAT)));
}


void TemplateTable::daload() {
  transition(itos, dtos);
  // rdx: array
  index_check(rdx, rax);  // kills rbx,
  // rax,: index
  __ fld_d(Address(rdx, rax, Address::times_8, arrayOopDesc::base_offset_in_bytes(T_DOUBLE)));
}


void TemplateTable::aaload() {
  transition(itos, atos);
  // rdx: array
  index_check(rdx, rax);  // kills rbx,
  // rax,: index
  __ movptr(rax, Address(rdx, rax, Address::times_ptr, arrayOopDesc::base_offset_in_bytes(T_OBJECT)));
}


void TemplateTable::baload() {
  transition(itos, itos);
  // rdx: array
  index_check(rdx, rax);  // kills rbx,
  // rax,: index
  // can do better code for P5 - fix this at some point
  __ load_signed_byte(rbx, Address(rdx, rax, Address::times_1, arrayOopDesc::base_offset_in_bytes(T_BYTE)));
  __ mov(rax, rbx);
}


void TemplateTable::caload() {
  transition(itos, itos);
  // rdx: array
  index_check(rdx, rax);  // kills rbx,
  // rax,: index
  // can do better code for P5 - may want to improve this at some point
  __ load_unsigned_short(rbx, Address(rdx, rax, Address::times_2, arrayOopDesc::base_offset_in_bytes(T_CHAR)));
  __ mov(rax, rbx);
}

// iload followed by caload frequent pair
void TemplateTable::fast_icaload() {
  transition(vtos, itos);
  // load index out of locals
  locals_index(rbx);
  __ movl(rax, iaddress(rbx));

  // rdx: array
  index_check(rdx, rax);
  // rax,: index
  __ load_unsigned_short(rbx, Address(rdx, rax, Address::times_2, arrayOopDesc::base_offset_in_bytes(T_CHAR)));
  __ mov(rax, rbx);
}

void TemplateTable::saload() {
  transition(itos, itos);
  // rdx: array
  index_check(rdx, rax);  // kills rbx,
  // rax,: index
  // can do better code for P5 - may want to improve this at some point
  __ load_signed_short(rbx, Address(rdx, rax, Address::times_2, arrayOopDesc::base_offset_in_bytes(T_SHORT)));
  __ mov(rax, rbx);
}


void TemplateTable::iload(int n) {
  transition(vtos, itos);
  __ movl(rax, iaddress(n));
}


void TemplateTable::lload(int n) {
  transition(vtos, ltos);
  __ movptr(rax, laddress(n));
  NOT_LP64(__ movptr(rdx, haddress(n)));
}


void TemplateTable::fload(int n) {
  transition(vtos, ftos);
  __ fld_s(faddress(n));
}


void TemplateTable::dload(int n) {
  transition(vtos, dtos);
  __ fld_d(daddress(n));
}


void TemplateTable::aload(int n) {
  transition(vtos, atos);
  __ movptr(rax, aaddress(n));
}


void TemplateTable::aload_0() {
  transition(vtos, atos);
  // According to bytecode histograms, the pairs:
  //
  // _aload_0, _fast_igetfield
  // _aload_0, _fast_agetfield
  // _aload_0, _fast_fgetfield
  //
  // occur frequently. If RewriteFrequentPairs is set, the (slow) _aload_0
  // bytecode checks if the next bytecode is either _fast_igetfield,
  // _fast_agetfield or _fast_fgetfield and then rewrites the
  // current bytecode into a pair bytecode; otherwise it rewrites the current
  // bytecode into _fast_aload_0 that doesn't do the pair check anymore.
  //
  // Note: If the next bytecode is _getfield, the rewrite must be delayed,
  //       otherwise we may miss an opportunity for a pair.
  //
  // Also rewrite frequent pairs
  //   aload_0, aload_1
  //   aload_0, iload_1
  // These bytecodes with a small amount of code are most profitable to rewrite
  if (RewriteFrequentPairs) {
    Label rewrite, done;
    // get next byte
    __ load_unsigned_byte(rbx, at_bcp(Bytecodes::length_for(Bytecodes::_aload_0)));

    // do actual aload_0
    aload(0);

    // if _getfield then wait with rewrite
    __ cmpl(rbx, Bytecodes::_getfield);
    __ jcc(Assembler::equal, done);

    // if _igetfield then reqrite to _fast_iaccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_iaccess_0) == Bytecodes::_aload_0, "fix bytecode definition");
    __ cmpl(rbx, Bytecodes::_fast_igetfield);
    __ movl(rcx, Bytecodes::_fast_iaccess_0);
    __ jccb(Assembler::equal, rewrite);

    // if _agetfield then reqrite to _fast_aaccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_aaccess_0) == Bytecodes::_aload_0, "fix bytecode definition");
    __ cmpl(rbx, Bytecodes::_fast_agetfield);
    __ movl(rcx, Bytecodes::_fast_aaccess_0);
    __ jccb(Assembler::equal, rewrite);

    // if _fgetfield then reqrite to _fast_faccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_faccess_0) == Bytecodes::_aload_0, "fix bytecode definition");
    __ cmpl(rbx, Bytecodes::_fast_fgetfield);
    __ movl(rcx, Bytecodes::_fast_faccess_0);
    __ jccb(Assembler::equal, rewrite);

    // else rewrite to _fast_aload0
    assert(Bytecodes::java_code(Bytecodes::_fast_aload_0) == Bytecodes::_aload_0, "fix bytecode definition");
    __ movl(rcx, Bytecodes::_fast_aload_0);

    // rewrite
    // rcx: fast bytecode
    __ bind(rewrite);
    patch_bytecode(Bytecodes::_aload_0, rcx, rbx, false);

    __ bind(done);
  } else {
    aload(0);
  }
}

void TemplateTable::istore() {
  transition(itos, vtos);
  locals_index(rbx);
  __ movl(iaddress(rbx), rax);
}


void TemplateTable::lstore() {
  transition(ltos, vtos);
  locals_index(rbx);
  __ movptr(laddress(rbx), rax);
  NOT_LP64(__ movptr(haddress(rbx), rdx));
}


void TemplateTable::fstore() {
  transition(ftos, vtos);
  locals_index(rbx);
  __ fstp_s(faddress(rbx));
}


void TemplateTable::dstore() {
  transition(dtos, vtos);
  locals_index(rbx);
  __ fstp_d(daddress(rbx));
}


void TemplateTable::astore() {
  transition(vtos, vtos);
  __ pop_ptr(rax);
  locals_index(rbx);
  __ movptr(aaddress(rbx), rax);
}


void TemplateTable::wide_istore() {
  transition(vtos, vtos);
  __ pop_i(rax);
  locals_index_wide(rbx);
  __ movl(iaddress(rbx), rax);
}


void TemplateTable::wide_lstore() {
  transition(vtos, vtos);
  __ pop_l(rax, rdx);
  locals_index_wide(rbx);
  __ movptr(laddress(rbx), rax);
  NOT_LP64(__ movl(haddress(rbx), rdx));
}


void TemplateTable::wide_fstore() {
  wide_istore();
}


void TemplateTable::wide_dstore() {
  wide_lstore();
}


void TemplateTable::wide_astore() {
  transition(vtos, vtos);
  __ pop_ptr(rax);
  locals_index_wide(rbx);
  __ movptr(aaddress(rbx), rax);
}


void TemplateTable::iastore() {
  transition(itos, vtos);
  __ pop_i(rbx);
  // rax,: value
  // rdx: array
  index_check(rdx, rbx);  // prefer index in rbx,
  // rbx,: index
  __ movl(Address(rdx, rbx, Address::times_4, arrayOopDesc::base_offset_in_bytes(T_INT)), rax);
}


void TemplateTable::lastore() {
  transition(ltos, vtos);
  __ pop_i(rbx);
  // rax,: low(value)
  // rcx: array
  // rdx: high(value)
  index_check(rcx, rbx);  // prefer index in rbx,
  // rbx,: index
  __ movptr(Address(rcx, rbx, Address::times_8, arrayOopDesc::base_offset_in_bytes(T_LONG) + 0 * wordSize), rax);
  NOT_LP64(__ movl(Address(rcx, rbx, Address::times_8, arrayOopDesc::base_offset_in_bytes(T_LONG) + 1 * wordSize), rdx));
}


void TemplateTable::fastore() {
  transition(ftos, vtos);
  __ pop_i(rbx);
  // rdx: array
  // st0: value
  index_check(rdx, rbx);  // prefer index in rbx,
  // rbx,: index
  __ fstp_s(Address(rdx, rbx, Address::times_4, arrayOopDesc::base_offset_in_bytes(T_FLOAT)));
}


void TemplateTable::dastore() {
  transition(dtos, vtos);
  __ pop_i(rbx);
  // rdx: array
  // st0: value
  index_check(rdx, rbx);  // prefer index in rbx,
  // rbx,: index
  __ fstp_d(Address(rdx, rbx, Address::times_8, arrayOopDesc::base_offset_in_bytes(T_DOUBLE)));
}


void TemplateTable::aastore() {
  Label is_null, ok_is_subtype, done;
  transition(vtos, vtos);
  // stack: ..., array, index, value
  __ movptr(rax, at_tos());     // Value
  __ movl(rcx, at_tos_p1());  // Index
  __ movptr(rdx, at_tos_p2());  // Array

  Address element_address(rdx, rcx, Address::times_4, arrayOopDesc::base_offset_in_bytes(T_OBJECT));
  index_check_without_pop(rdx, rcx);      // kills rbx,
  // do array store check - check for NULL value first
  __ testptr(rax, rax);
  __ jcc(Assembler::zero, is_null);

  // Move subklass into EBX
  __ load_klass(rbx, rax);
  // Move superklass into EAX
  __ load_klass(rax, rdx);
  __ movptr(rax, Address(rax, ObjArrayKlass::element_klass_offset()));
  // Compress array+index*wordSize+12 into a single register.  Frees ECX.
  __ lea(rdx, element_address);

  // Generate subtype check.  Blows ECX.  Resets EDI to locals.
  // Superklass in EAX.  Subklass in EBX.
  __ gen_subtype_check( rbx, ok_is_subtype );

  // Come here on failure
  // object is at TOS
  __ jump(ExternalAddress(Interpreter::_throw_ArrayStoreException_entry));

  // Come here on success
  __ bind(ok_is_subtype);

  // Get the value to store
  __ movptr(rax, at_rsp());
  // and store it with appropriate barrier
  do_oop_store(_masm, Address(rdx, 0), rax, _bs->kind(), true);

  __ jmp(done);

  // Have a NULL in EAX, EDX=array, ECX=index.  Store NULL at ary[idx]
  __ bind(is_null);
  __ profile_null_seen(rbx);

  // Store NULL, (noreg means NULL to do_oop_store)
  do_oop_store(_masm, element_address, noreg, _bs->kind(), true);

  // Pop stack arguments
  __ bind(done);
  __ addptr(rsp, 3 * Interpreter::stackElementSize);
}


void TemplateTable::bastore() {
  transition(itos, vtos);
  __ pop_i(rbx);
  // rax: value
  // rbx: index
  // rdx: array
  index_check(rdx, rbx);  // prefer index in rbx
  // Need to check whether array is boolean or byte
  // since both types share the bastore bytecode.
  __ load_klass(rcx, rdx);
  __ movl(rcx, Address(rcx, Klass::layout_helper_offset()));
  int diffbit = Klass::layout_helper_boolean_diffbit();
  __ testl(rcx, diffbit);
  Label L_skip;
  __ jccb(Assembler::zero, L_skip);
  __ andl(rax, 1);  // if it is a T_BOOLEAN array, mask the stored value to 0/1
  __ bind(L_skip);
 __ movb(Address(rdx, rbx, Address::times_1, arrayOopDesc::base_offset_in_bytes(T_BYTE)), rax);
}


void TemplateTable::castore() {
  transition(itos, vtos);
  __ pop_i(rbx);
  // rax,: value
  // rdx: array
  index_check(rdx, rbx);  // prefer index in rbx,
  // rbx,: index
  __ movw(Address(rdx, rbx, Address::times_2, arrayOopDesc::base_offset_in_bytes(T_CHAR)), rax);
}


void TemplateTable::sastore() {
  castore();
}


void TemplateTable::istore(int n) {
  transition(itos, vtos);
  __ movl(iaddress(n), rax);
}


void TemplateTable::lstore(int n) {
  transition(ltos, vtos);
  __ movptr(laddress(n), rax);
  NOT_LP64(__ movptr(haddress(n), rdx));
}


void TemplateTable::fstore(int n) {
  transition(ftos, vtos);
  __ fstp_s(faddress(n));
}


void TemplateTable::dstore(int n) {
  transition(dtos, vtos);
  __ fstp_d(daddress(n));
}


void TemplateTable::astore(int n) {
  transition(vtos, vtos);
  __ pop_ptr(rax);
  __ movptr(aaddress(n), rax);
}


void TemplateTable::pop() {
  transition(vtos, vtos);
  __ addptr(rsp, Interpreter::stackElementSize);
}


void TemplateTable::pop2() {
  transition(vtos, vtos);
  __ addptr(rsp, 2*Interpreter::stackElementSize);
}


void TemplateTable::dup() {
  transition(vtos, vtos);
  // stack: ..., a
  __ load_ptr(0, rax);
  __ push_ptr(rax);
  // stack: ..., a, a
}


void TemplateTable::dup_x1() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ load_ptr( 0, rax);  // load b
  __ load_ptr( 1, rcx);  // load a
  __ store_ptr(1, rax);  // store b
  __ store_ptr(0, rcx);  // store a
  __ push_ptr(rax);      // push b
  // stack: ..., b, a, b
}


void TemplateTable::dup_x2() {
  transition(vtos, vtos);
  // stack: ..., a, b, c
  __ load_ptr( 0, rax);  // load c
  __ load_ptr( 2, rcx);  // load a
  __ store_ptr(2, rax);  // store c in a
  __ push_ptr(rax);      // push c
  // stack: ..., c, b, c, c
  __ load_ptr( 2, rax);  // load b
  __ store_ptr(2, rcx);  // store a in b
  // stack: ..., c, a, c, c
  __ store_ptr(1, rax);  // store b in c
  // stack: ..., c, a, b, c
}


void TemplateTable::dup2() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ load_ptr(1, rax);  // load a
  __ push_ptr(rax);     // push a
  __ load_ptr(1, rax);  // load b
  __ push_ptr(rax);     // push b
  // stack: ..., a, b, a, b
}


void TemplateTable::dup2_x1() {
  transition(vtos, vtos);
  // stack: ..., a, b, c
  __ load_ptr( 0, rcx);  // load c
  __ load_ptr( 1, rax);  // load b
  __ push_ptr(rax);      // push b
  __ push_ptr(rcx);      // push c
  // stack: ..., a, b, c, b, c
  __ store_ptr(3, rcx);  // store c in b
  // stack: ..., a, c, c, b, c
  __ load_ptr( 4, rcx);  // load a
  __ store_ptr(2, rcx);  // store a in 2nd c
  // stack: ..., a, c, a, b, c
  __ store_ptr(4, rax);  // store b in a
  // stack: ..., b, c, a, b, c
  // stack: ..., b, c, a, b, c
}


void TemplateTable::dup2_x2() {
  transition(vtos, vtos);
  // stack: ..., a, b, c, d
  __ load_ptr( 0, rcx);  // load d
  __ load_ptr( 1, rax);  // load c
  __ push_ptr(rax);      // push c
  __ push_ptr(rcx);      // push d
  // stack: ..., a, b, c, d, c, d
  __ load_ptr( 4, rax);  // load b
  __ store_ptr(2, rax);  // store b in d
  __ store_ptr(4, rcx);  // store d in b
  // stack: ..., a, d, c, b, c, d
  __ load_ptr( 5, rcx);  // load a
  __ load_ptr( 3, rax);  // load c
  __ store_ptr(3, rcx);  // store a in c
  __ store_ptr(5, rax);  // store c in a
  // stack: ..., c, d, a, b, c, d
  // stack: ..., c, d, a, b, c, d
}


void TemplateTable::swap() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ load_ptr( 1, rcx);  // load a
  __ load_ptr( 0, rax);  // load b
  __ store_ptr(0, rcx);  // store a in b
  __ store_ptr(1, rax);  // store b in a
  // stack: ..., b, a
}


void TemplateTable::iop2(Operation op) {
  transition(itos, itos);
  switch (op) {
    case add  :                   __ pop_i(rdx); __ addl (rax, rdx); break;
    case sub  : __ mov(rdx, rax); __ pop_i(rax); __ subl (rax, rdx); break;
    case mul  :                   __ pop_i(rdx); __ imull(rax, rdx); break;
    case _and :                   __ pop_i(rdx); __ andl (rax, rdx); break;
    case _or  :                   __ pop_i(rdx); __ orl  (rax, rdx); break;
    case _xor :                   __ pop_i(rdx); __ xorl (rax, rdx); break;
    case shl  : __ mov(rcx, rax); __ pop_i(rax); __ shll (rax);      break; // implicit masking of lower 5 bits by Intel shift instr.
    case shr  : __ mov(rcx, rax); __ pop_i(rax); __ sarl (rax);      break; // implicit masking of lower 5 bits by Intel shift instr.
    case ushr : __ mov(rcx, rax); __ pop_i(rax); __ shrl (rax);      break; // implicit masking of lower 5 bits by Intel shift instr.
    default   : ShouldNotReachHere();
  }
}


void TemplateTable::lop2(Operation op) {
  transition(ltos, ltos);
  __ pop_l(rbx, rcx);
  switch (op) {
    case add  : __ addl(rax, rbx); __ adcl(rdx, rcx); break;
    case sub  : __ subl(rbx, rax); __ sbbl(rcx, rdx);
                __ mov (rax, rbx); __ mov (rdx, rcx); break;
    case _and : __ andl(rax, rbx); __ andl(rdx, rcx); break;
    case _or  : __ orl (rax, rbx); __ orl (rdx, rcx); break;
    case _xor : __ xorl(rax, rbx); __ xorl(rdx, rcx); break;
    default   : ShouldNotReachHere();
  }
}


void TemplateTable::idiv() {
  transition(itos, itos);
  __ mov(rcx, rax);
  __ pop_i(rax);
  // Note: could xor rax, and rcx and compare with (-1 ^ min_int). If
  //       they are not equal, one could do a normal division (no correction
  //       needed), which may speed up this implementation for the common case.
  //       (see also JVM spec., p.243 & p.271)
  __ corrected_idivl(rcx);
}


void TemplateTable::irem() {
  transition(itos, itos);
  __ mov(rcx, rax);
  __ pop_i(rax);
  // Note: could xor rax, and rcx and compare with (-1 ^ min_int). If
  //       they are not equal, one could do a normal division (no correction
  //       needed), which may speed up this implementation for the common case.
  //       (see also JVM spec., p.243 & p.271)
  __ corrected_idivl(rcx);
  __ mov(rax, rdx);
}


void TemplateTable::lmul() {
  transition(ltos, ltos);
  __ pop_l(rbx, rcx);
  __ push(rcx); __ push(rbx);
  __ push(rdx); __ push(rax);
  __ lmul(2 * wordSize, 0);
  __ addptr(rsp, 4 * wordSize);  // take off temporaries
}


void TemplateTable::ldiv() {
  transition(ltos, ltos);
  __ pop_l(rbx, rcx);
  __ push(rcx); __ push(rbx);
  __ push(rdx); __ push(rax);
  // check if y = 0
  __ orl(rax, rdx);
  __ jump_cc(Assembler::zero,
             ExternalAddress(Interpreter::_throw_ArithmeticException_entry));
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::ldiv));
  __ addptr(rsp, 4 * wordSize);  // take off temporaries
}


void TemplateTable::lrem() {
  transition(ltos, ltos);
  __ pop_l(rbx, rcx);
  __ push(rcx); __ push(rbx);
  __ push(rdx); __ push(rax);
  // check if y = 0
  __ orl(rax, rdx);
  __ jump_cc(Assembler::zero,
             ExternalAddress(Interpreter::_throw_ArithmeticException_entry));
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::lrem));
  __ addptr(rsp, 4 * wordSize);
}


void TemplateTable::lshl() {
  transition(itos, ltos);
  __ movl(rcx, rax);                             // get shift count
  __ pop_l(rax, rdx);                            // get shift value
  __ lshl(rdx, rax);
}


void TemplateTable::lshr() {
  transition(itos, ltos);
  __ mov(rcx, rax);                              // get shift count
  __ pop_l(rax, rdx);                            // get shift value
  __ lshr(rdx, rax, true);
}


void TemplateTable::lushr() {
  transition(itos, ltos);
  __ mov(rcx, rax);                              // get shift count
  __ pop_l(rax, rdx);                            // get shift value
  __ lshr(rdx, rax);
}


void TemplateTable::fop2(Operation op) {
  transition(ftos, ftos);
  switch (op) {
    case add: __ fadd_s (at_rsp());                break;
    case sub: __ fsubr_s(at_rsp());                break;
    case mul: __ fmul_s (at_rsp());                break;
    case div: __ fdivr_s(at_rsp());                break;
    case rem: __ fld_s  (at_rsp()); __ fremr(rax); break;
    default : ShouldNotReachHere();
  }
  __ f2ieee();
  __ pop(rax);  // pop float thing off
}


void TemplateTable::dop2(Operation op) {
  transition(dtos, dtos);

  switch (op) {
    case add: __ fadd_d (at_rsp());                break;
    case sub: __ fsubr_d(at_rsp());                break;
    case mul: {
      Label L_strict;
      Label L_join;
      const Address access_flags      (rcx, Method::access_flags_offset());
      __ get_method(rcx);
      __ movl(rcx, access_flags);
      __ testl(rcx, JVM_ACC_STRICT);
      __ jccb(Assembler::notZero, L_strict);
      __ fmul_d (at_rsp());
      __ jmpb(L_join);
      __ bind(L_strict);
      __ fld_x(ExternalAddress(StubRoutines::addr_fpu_subnormal_bias1()));
      __ fmulp();
      __ fmul_d (at_rsp());
      __ fld_x(ExternalAddress(StubRoutines::addr_fpu_subnormal_bias2()));
      __ fmulp();
      __ bind(L_join);
      break;
    }
    case div: {
      Label L_strict;
      Label L_join;
      const Address access_flags      (rcx, Method::access_flags_offset());
      __ get_method(rcx);
      __ movl(rcx, access_flags);
      __ testl(rcx, JVM_ACC_STRICT);
      __ jccb(Assembler::notZero, L_strict);
      __ fdivr_d(at_rsp());
      __ jmp(L_join);
      __ bind(L_strict);
      __ fld_x(ExternalAddress(StubRoutines::addr_fpu_subnormal_bias1()));
      __ fmul_d (at_rsp());
      __ fdivrp();
      __ fld_x(ExternalAddress(StubRoutines::addr_fpu_subnormal_bias2()));
      __ fmulp();
      __ bind(L_join);
      break;
    }
    case rem: __ fld_d  (at_rsp()); __ fremr(rax); break;
    default : ShouldNotReachHere();
  }
  __ d2ieee();
  // Pop double precision number from rsp.
  __ pop(rax);
  __ pop(rdx);
}


void TemplateTable::ineg() {
  transition(itos, itos);
  __ negl(rax);
}


void TemplateTable::lneg() {
  transition(ltos, ltos);
  __ lneg(rdx, rax);
}


void TemplateTable::fneg() {
  transition(ftos, ftos);
  __ fchs();
}


void TemplateTable::dneg() {
  transition(dtos, dtos);
  __ fchs();
}


void TemplateTable::iinc() {
  transition(vtos, vtos);
  __ load_signed_byte(rdx, at_bcp(2));           // get constant
  locals_index(rbx);
  __ addl(iaddress(rbx), rdx);
}


void TemplateTable::wide_iinc() {
  transition(vtos, vtos);
  __ movl(rdx, at_bcp(4));                       // get constant
  locals_index_wide(rbx);
  __ bswapl(rdx);                                 // swap bytes & sign-extend constant
  __ sarl(rdx, 16);
  __ addl(iaddress(rbx), rdx);
  // Note: should probably use only one movl to get both
  //       the index and the constant -> fix this
}


void TemplateTable::convert() {
  // Checking
#ifdef ASSERT
  { TosState tos_in  = ilgl;
    TosState tos_out = ilgl;
    switch (bytecode()) {
      case Bytecodes::_i2l: // fall through
      case Bytecodes::_i2f: // fall through
      case Bytecodes::_i2d: // fall through
      case Bytecodes::_i2b: // fall through
      case Bytecodes::_i2c: // fall through
      case Bytecodes::_i2s: tos_in = itos; break;
      case Bytecodes::_l2i: // fall through
      case Bytecodes::_l2f: // fall through
      case Bytecodes::_l2d: tos_in = ltos; break;
      case Bytecodes::_f2i: // fall through
      case Bytecodes::_f2l: // fall through
      case Bytecodes::_f2d: tos_in = ftos; break;
      case Bytecodes::_d2i: // fall through
      case Bytecodes::_d2l: // fall through
      case Bytecodes::_d2f: tos_in = dtos; break;
      default             : ShouldNotReachHere();
    }
    switch (bytecode()) {
      case Bytecodes::_l2i: // fall through
      case Bytecodes::_f2i: // fall through
      case Bytecodes::_d2i: // fall through
      case Bytecodes::_i2b: // fall through
      case Bytecodes::_i2c: // fall through
      case Bytecodes::_i2s: tos_out = itos; break;
      case Bytecodes::_i2l: // fall through
      case Bytecodes::_f2l: // fall through
      case Bytecodes::_d2l: tos_out = ltos; break;
      case Bytecodes::_i2f: // fall through
      case Bytecodes::_l2f: // fall through
      case Bytecodes::_d2f: tos_out = ftos; break;
      case Bytecodes::_i2d: // fall through
      case Bytecodes::_l2d: // fall through
      case Bytecodes::_f2d: tos_out = dtos; break;
      default             : ShouldNotReachHere();
    }
    transition(tos_in, tos_out);
  }
#endif // ASSERT

  // Conversion
  // (Note: use push(rcx)/pop(rcx) for 1/2-word stack-ptr manipulation)
  switch (bytecode()) {
    case Bytecodes::_i2l:
      __ extend_sign(rdx, rax);
      break;
    case Bytecodes::_i2f:
      __ push(rax);          // store int on tos
      __ fild_s(at_rsp());   // load int to ST0
      __ f2ieee();           // truncate to float size
      __ pop(rcx);           // adjust rsp
      break;
    case Bytecodes::_i2d:
      __ push(rax);          // add one slot for d2ieee()
      __ push(rax);          // store int on tos
      __ fild_s(at_rsp());   // load int to ST0
      __ d2ieee();           // truncate to double size
      __ pop(rcx);           // adjust rsp
      __ pop(rcx);
      break;
    case Bytecodes::_i2b:
      __ shll(rax, 24);      // truncate upper 24 bits
      __ sarl(rax, 24);      // and sign-extend byte
      LP64_ONLY(__ movsbl(rax, rax));
      break;
    case Bytecodes::_i2c:
      __ andl(rax, 0xFFFF);  // truncate upper 16 bits
      LP64_ONLY(__ movzwl(rax, rax));
      break;
    case Bytecodes::_i2s:
      __ shll(rax, 16);      // truncate upper 16 bits
      __ sarl(rax, 16);      // and sign-extend short
      LP64_ONLY(__ movswl(rax, rax));
      break;
    case Bytecodes::_l2i:
      /* nothing to do */
      break;
    case Bytecodes::_l2f:
      __ push(rdx);          // store long on tos
      __ push(rax);
      __ fild_d(at_rsp());   // load long to ST0
      __ f2ieee();           // truncate to float size
      __ pop(rcx);           // adjust rsp
      __ pop(rcx);
      break;
    case Bytecodes::_l2d:
      __ push(rdx);          // store long on tos
      __ push(rax);
      __ fild_d(at_rsp());   // load long to ST0
      __ d2ieee();           // truncate to double size
      __ pop(rcx);           // adjust rsp
      __ pop(rcx);
      break;
    case Bytecodes::_f2i:
      __ push(rcx);          // reserve space for argument
      __ fstp_s(at_rsp());   // pass float argument on stack
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::f2i), 1);
      break;
    case Bytecodes::_f2l:
      __ push(rcx);          // reserve space for argument
      __ fstp_s(at_rsp());   // pass float argument on stack
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::f2l), 1);
      break;
    case Bytecodes::_f2d:
      /* nothing to do */
      break;
    case Bytecodes::_d2i:
      __ push(rcx);          // reserve space for argument
      __ push(rcx);
      __ fstp_d(at_rsp());   // pass double argument on stack
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::d2i), 2);
      break;
    case Bytecodes::_d2l:
      __ push(rcx);          // reserve space for argument
      __ push(rcx);
      __ fstp_d(at_rsp());   // pass double argument on stack
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::d2l), 2);
      break;
    case Bytecodes::_d2f:
      __ push(rcx);          // reserve space for f2ieee()
      __ f2ieee();           // truncate to float size
      __ pop(rcx);           // adjust rsp
      break;
    default             :
      ShouldNotReachHere();
  }
}


void TemplateTable::lcmp() {
  transition(ltos, itos);
  // y = rdx:rax
  __ pop_l(rbx, rcx);             // get x = rcx:rbx
  __ lcmp2int(rcx, rbx, rdx, rax);// rcx := cmp(x, y)
  __ mov(rax, rcx);
}


void TemplateTable::float_cmp(bool is_float, int unordered_result) {
  if (is_float) {
    __ fld_s(at_rsp());
  } else {
    __ fld_d(at_rsp());
    __ pop(rdx);
  }
  __ pop(rcx);
  __ fcmp2int(rax, unordered_result < 0);
}


void TemplateTable::branch(bool is_jsr, bool is_wide) {
  __ get_method(rcx);           // ECX holds method
  __ profile_taken_branch(rax,rbx); // EAX holds updated MDP, EBX holds bumped taken count

  const ByteSize be_offset = MethodCounters::backedge_counter_offset() +
                             InvocationCounter::counter_offset();
  const ByteSize inv_offset = MethodCounters::invocation_counter_offset() +
                              InvocationCounter::counter_offset();

  // Load up EDX with the branch displacement
  if (is_wide) {
    __ movl(rdx, at_bcp(1));
  } else {
    __ load_signed_short(rdx, at_bcp(1));
  }
  __ bswapl(rdx);
  if (!is_wide) __ sarl(rdx, 16);
  LP64_ONLY(__ movslq(rdx, rdx));


  // Handle all the JSR stuff here, then exit.
  // It's much shorter and cleaner than intermingling with the
  // non-JSR normal-branch stuff occurring below.
  if (is_jsr) {
    // Pre-load the next target bytecode into EBX
    __ load_unsigned_byte(rbx, Address(rsi, rdx, Address::times_1, 0));

    // compute return address as bci in rax,
    __ lea(rax, at_bcp((is_wide ? 5 : 3) - in_bytes(ConstMethod::codes_offset())));
    __ subptr(rax, Address(rcx, Method::const_offset()));
    // Adjust the bcp in RSI by the displacement in EDX
    __ addptr(rsi, rdx);
    // Push return address
    __ push_i(rax);
    // jsr returns vtos
    __ dispatch_only_noverify(vtos);
    return;
  }

  // Normal (non-jsr) branch handling

  // Adjust the bcp in RSI by the displacement in EDX
  __ addptr(rsi, rdx);

  assert(UseLoopCounter || !UseOnStackReplacement, "on-stack-replacement requires loop counters");
  Label backedge_counter_overflow;
  Label profile_method;
  Label dispatch;
  if (UseLoopCounter) {
    // increment backedge counter for backward branches
    // rax,: MDO
    // rbx,: MDO bumped taken-count
    // rcx: method
    // rdx: target offset
    // rsi: target bcp
    // rdi: locals pointer
    __ testl(rdx, rdx);             // check if forward or backward branch
    __ jcc(Assembler::positive, dispatch); // count only if backward branch

    // check if MethodCounters exists
    Label has_counters;
    __ movptr(rax, Address(rcx, Method::method_counters_offset()));
    __ testptr(rax, rax);
    __ jcc(Assembler::notZero, has_counters);
    __ push(rdx);
    __ push(rcx);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::build_method_counters),
               rcx);
    __ pop(rcx);
    __ pop(rdx);
    __ movptr(rax, Address(rcx, Method::method_counters_offset()));
    __ testptr(rax, rax);
    __ jcc(Assembler::zero, dispatch);
    __ bind(has_counters);

    if (TieredCompilation) {
      Label no_mdo;
      int increment = InvocationCounter::count_increment;
      int mask = ((1 << Tier0BackedgeNotifyFreqLog) - 1) << InvocationCounter::count_shift;
      if (ProfileInterpreter) {
        // Are we profiling?
        __ movptr(rbx, Address(rcx, in_bytes(Method::method_data_offset())));
        __ testptr(rbx, rbx);
        __ jccb(Assembler::zero, no_mdo);
        // Increment the MDO backedge counter
        const Address mdo_backedge_counter(rbx, in_bytes(MethodData::backedge_counter_offset()) +
                                                in_bytes(InvocationCounter::counter_offset()));
        __ increment_mask_and_jump(mdo_backedge_counter, increment, mask, rax, false, Assembler::zero,
                                   UseOnStackReplacement ? &backedge_counter_overflow : NULL);
        __ jmp(dispatch);
      }
      __ bind(no_mdo);
      // Increment backedge counter in MethodCounters*
      __ movptr(rcx, Address(rcx, Method::method_counters_offset()));
      __ increment_mask_and_jump(Address(rcx, be_offset), increment, mask,
                                 rax, false, Assembler::zero,
                                 UseOnStackReplacement ? &backedge_counter_overflow : NULL);
    } else {
      // increment counter
      __ movptr(rcx, Address(rcx, Method::method_counters_offset()));
      __ movl(rax, Address(rcx, be_offset));        // load backedge counter
      __ incrementl(rax, InvocationCounter::count_increment); // increment counter
      __ movl(Address(rcx, be_offset), rax);        // store counter

      __ movl(rax, Address(rcx, inv_offset));    // load invocation counter

      __ andl(rax, InvocationCounter::count_mask_value);     // and the status bits
      __ addl(rax, Address(rcx, be_offset));        // add both counters

      if (ProfileInterpreter) {
        // Test to see if we should create a method data oop
        __ cmp32(rax,
                 ExternalAddress((address) &InvocationCounter::InterpreterProfileLimit));
        __ jcc(Assembler::less, dispatch);

        // if no method data exists, go to profile method
        __ test_method_data_pointer(rax, profile_method);

        if (UseOnStackReplacement) {
          // check for overflow against rbx, which is the MDO taken count
          __ cmp32(rbx,
                   ExternalAddress((address) &InvocationCounter::InterpreterBackwardBranchLimit));
          __ jcc(Assembler::below, dispatch);

          // When ProfileInterpreter is on, the backedge_count comes from the
          // MethodData*, which value does not get reset on the call to
          // frequency_counter_overflow().  To avoid excessive calls to the overflow
          // routine while the method is being compiled, add a second test to make
          // sure the overflow function is called only once every overflow_frequency.
          const int overflow_frequency = 1024;
          __ andptr(rbx, overflow_frequency-1);
          __ jcc(Assembler::zero, backedge_counter_overflow);
        }
      } else {
        if (UseOnStackReplacement) {
          // check for overflow against rax, which is the sum of the counters
          __ cmp32(rax,
                   ExternalAddress((address) &InvocationCounter::InterpreterBackwardBranchLimit));
          __ jcc(Assembler::aboveEqual, backedge_counter_overflow);

        }
      }
    }
    __ bind(dispatch);
  }

  // Pre-load the next target bytecode into EBX
  __ load_unsigned_byte(rbx, Address(rsi, 0));

  // continue with the bytecode @ target
  // rax,: return bci for jsr's, unused otherwise
  // rbx,: target bytecode
  // rsi: target bcp
  __ dispatch_only(vtos);

  if (UseLoopCounter) {
    if (ProfileInterpreter) {
      // Out-of-line code to allocate method data oop.
      __ bind(profile_method);
      __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::profile_method));
      __ load_unsigned_byte(rbx, Address(rsi, 0));  // restore target bytecode
      __ set_method_data_pointer_for_bcp();
      __ jmp(dispatch);
    }

    if (UseOnStackReplacement) {

      // invocation counter overflow
      __ bind(backedge_counter_overflow);
      __ negptr(rdx);
      __ addptr(rdx, rsi);        // branch bcp
      call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow), rdx);
      __ load_unsigned_byte(rbx, Address(rsi, 0));  // restore target bytecode

      // rax,: osr nmethod (osr ok) or NULL (osr not possible)
      // rbx,: target bytecode
      // rdx: scratch
      // rdi: locals pointer
      // rsi: bcp
      __ testptr(rax, rax);                      // test result
      __ jcc(Assembler::zero, dispatch);         // no osr if null
      // nmethod may have been invalidated (VM may block upon call_VM return)
      __ movl(rcx, Address(rax, nmethod::entry_bci_offset()));
      __ cmpl(rcx, InvalidOSREntryBci);
      __ jcc(Assembler::equal, dispatch);

      // We have the address of an on stack replacement routine in rax,
      // We need to prepare to execute the OSR method. First we must
      // migrate the locals and monitors off of the stack.

      __ mov(rbx, rax);                             // save the nmethod

      const Register thread = rcx;
      __ get_thread(thread);
      call_VM(noreg, CAST_FROM_FN_PTR(address, SharedRuntime::OSR_migration_begin));
      // rax, is OSR buffer, move it to expected parameter location
      __ mov(rcx, rax);

      // pop the interpreter frame
      __ movptr(rdx, Address(rbp, frame::interpreter_frame_sender_sp_offset * wordSize)); // get sender sp
      __ leave();                                // remove frame anchor
      __ pop(rdi);                               // get return address
      __ mov(rsp, rdx);                          // set sp to sender sp

      // Align stack pointer for compiled code (note that caller is
      // responsible for undoing this fixup by remembering the old SP
      // in an rbp,-relative location)
      __ andptr(rsp, -(StackAlignmentInBytes));

      // push the (possibly adjusted) return address
      __ push(rdi);

      // and begin the OSR nmethod
      __ jmp(Address(rbx, nmethod::osr_entry_point_offset()));
    }
  }
}


void TemplateTable::if_0cmp(Condition cc) {
  transition(itos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ testl(rax, rax);
  __ jcc(j_not(cc), not_taken);
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(rax);
}


void TemplateTable::if_icmp(Condition cc) {
  transition(itos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ pop_i(rdx);
  __ cmpl(rdx, rax);
  __ jcc(j_not(cc), not_taken);
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(rax);
}


void TemplateTable::if_nullcmp(Condition cc) {
  transition(atos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ testptr(rax, rax);
  __ jcc(j_not(cc), not_taken);
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(rax);
}


void TemplateTable::if_acmp(Condition cc) {
  transition(atos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ pop_ptr(rdx);
  __ cmpptr(rdx, rax);
  __ jcc(j_not(cc), not_taken);
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(rax);
}


void TemplateTable::ret() {
  transition(vtos, vtos);
  locals_index(rbx);
  __ movptr(rbx, iaddress(rbx));                   // get return bci, compute return bcp
  __ profile_ret(rbx, rcx);
  __ get_method(rax);
  __ movptr(rsi, Address(rax, Method::const_offset()));
  __ lea(rsi, Address(rsi, rbx, Address::times_1,
                      ConstMethod::codes_offset()));
  __ dispatch_next(vtos);
}


void TemplateTable::wide_ret() {
  transition(vtos, vtos);
  locals_index_wide(rbx);
  __ movptr(rbx, iaddress(rbx));                   // get return bci, compute return bcp
  __ profile_ret(rbx, rcx);
  __ get_method(rax);
  __ movptr(rsi, Address(rax, Method::const_offset()));
  __ lea(rsi, Address(rsi, rbx, Address::times_1, ConstMethod::codes_offset()));
  __ dispatch_next(vtos);
}


void TemplateTable::tableswitch() {
  Label default_case, continue_execution;
  transition(itos, vtos);
  // align rsi
  __ lea(rbx, at_bcp(wordSize));
  __ andptr(rbx, -wordSize);
  // load lo & hi
  __ movl(rcx, Address(rbx, 1 * wordSize));
  __ movl(rdx, Address(rbx, 2 * wordSize));
  __ bswapl(rcx);
  __ bswapl(rdx);
  // check against lo & hi
  __ cmpl(rax, rcx);
  __ jccb(Assembler::less, default_case);
  __ cmpl(rax, rdx);
  __ jccb(Assembler::greater, default_case);
  // lookup dispatch offset
  __ subl(rax, rcx);
  __ movl(rdx, Address(rbx, rax, Address::times_4, 3 * BytesPerInt));
  __ profile_switch_case(rax, rbx, rcx);
  // continue execution
  __ bind(continue_execution);
  __ bswapl(rdx);
  __ load_unsigned_byte(rbx, Address(rsi, rdx, Address::times_1));
  __ addptr(rsi, rdx);
  __ dispatch_only(vtos);
  // handle default
  __ bind(default_case);
  __ profile_switch_default(rax);
  __ movl(rdx, Address(rbx, 0));
  __ jmp(continue_execution);
}


void TemplateTable::lookupswitch() {
  transition(itos, itos);
  __ stop("lookupswitch bytecode should have been rewritten");
}


void TemplateTable::fast_linearswitch() {
  transition(itos, vtos);
  Label loop_entry, loop, found, continue_execution;
  // bswapl rax, so we can avoid bswapping the table entries
  __ bswapl(rax);
  // align rsi
  __ lea(rbx, at_bcp(wordSize));                // btw: should be able to get rid of this instruction (change offsets below)
  __ andptr(rbx, -wordSize);
  // set counter
  __ movl(rcx, Address(rbx, wordSize));
  __ bswapl(rcx);
  __ jmpb(loop_entry);
  // table search
  __ bind(loop);
  __ cmpl(rax, Address(rbx, rcx, Address::times_8, 2 * wordSize));
  __ jccb(Assembler::equal, found);
  __ bind(loop_entry);
  __ decrementl(rcx);
  __ jcc(Assembler::greaterEqual, loop);
  // default case
  __ profile_switch_default(rax);
  __ movl(rdx, Address(rbx, 0));
  __ jmpb(continue_execution);
  // entry found -> get offset
  __ bind(found);
  __ movl(rdx, Address(rbx, rcx, Address::times_8, 3 * wordSize));
  __ profile_switch_case(rcx, rax, rbx);
  // continue execution
  __ bind(continue_execution);
  __ bswapl(rdx);
  __ load_unsigned_byte(rbx, Address(rsi, rdx, Address::times_1));
  __ addptr(rsi, rdx);
  __ dispatch_only(vtos);
}


void TemplateTable::fast_binaryswitch() {
  transition(itos, vtos);
  // Implementation using the following core algorithm:
  //
  // int binary_search(int key, LookupswitchPair* array, int n) {
  //   // Binary search according to "Methodik des Programmierens" by
  //   // Edsger W. Dijkstra and W.H.J. Feijen, Addison Wesley Germany 1985.
  //   int i = 0;
  //   int j = n;
  //   while (i+1 < j) {
  //     // invariant P: 0 <= i < j <= n and (a[i] <= key < a[j] or Q)
  //     // with      Q: for all i: 0 <= i < n: key < a[i]
  //     // where a stands for the array and assuming that the (inexisting)
  //     // element a[n] is infinitely big.
  //     int h = (i + j) >> 1;
  //     // i < h < j
  //     if (key < array[h].fast_match()) {
  //       j = h;
  //     } else {
  //       i = h;
  //     }
  //   }
  //   // R: a[i] <= key < a[i+1] or Q
  //   // (i.e., if key is within array, i is the correct index)
  //   return i;
  // }

  // register allocation
  const Register key   = rax;                    // already set (tosca)
  const Register array = rbx;
  const Register i     = rcx;
  const Register j     = rdx;
  const Register h     = rdi;                    // needs to be restored
  const Register temp  = rsi;
  // setup array
  __ save_bcp();

  __ lea(array, at_bcp(3*wordSize));             // btw: should be able to get rid of this instruction (change offsets below)
  __ andptr(array, -wordSize);
  // initialize i & j
  __ xorl(i, i);                                 // i = 0;
  __ movl(j, Address(array, -wordSize));         // j = length(array);
  // Convert j into native byteordering
  __ bswapl(j);
  // and start
  Label entry;
  __ jmp(entry);

  // binary search loop
  { Label loop;
    __ bind(loop);
    // int h = (i + j) >> 1;
    __ leal(h, Address(i, j, Address::times_1)); // h = i + j;
    __ sarl(h, 1);                               // h = (i + j) >> 1;
    // if (key < array[h].fast_match()) {
    //   j = h;
    // } else {
    //   i = h;
    // }
    // Convert array[h].match to native byte-ordering before compare
    __ movl(temp, Address(array, h, Address::times_8, 0*wordSize));
    __ bswapl(temp);
    __ cmpl(key, temp);
    // j = h if (key <  array[h].fast_match())
    __ cmov32(Assembler::less        , j, h);
    // i = h if (key >= array[h].fast_match())
    __ cmov32(Assembler::greaterEqual, i, h);
    // while (i+1 < j)
    __ bind(entry);
    __ leal(h, Address(i, 1));                   // i+1
    __ cmpl(h, j);                               // i+1 < j
    __ jcc(Assembler::less, loop);
  }

  // end of binary search, result index is i (must check again!)
  Label default_case;
  // Convert array[i].match to native byte-ordering before compare
  __ movl(temp, Address(array, i, Address::times_8, 0*wordSize));
  __ bswapl(temp);
  __ cmpl(key, temp);
  __ jcc(Assembler::notEqual, default_case);

  // entry found -> j = offset
  __ movl(j , Address(array, i, Address::times_8, 1*wordSize));
  __ profile_switch_case(i, key, array);
  __ bswapl(j);
  LP64_ONLY(__ movslq(j, j));
  __ restore_bcp();
  __ restore_locals();                           // restore rdi
  __ load_unsigned_byte(rbx, Address(rsi, j, Address::times_1));

  __ addptr(rsi, j);
  __ dispatch_only(vtos);

  // default case -> j = default offset
  __ bind(default_case);
  __ profile_switch_default(i);
  __ movl(j, Address(array, -2*wordSize));
  __ bswapl(j);
  LP64_ONLY(__ movslq(j, j));
  __ restore_bcp();
  __ restore_locals();                           // restore rdi
  __ load_unsigned_byte(rbx, Address(rsi, j, Address::times_1));
  __ addptr(rsi, j);
  __ dispatch_only(vtos);
}


void TemplateTable::_return(TosState state) {
  transition(state, state);
  assert(_desc->calls_vm(), "inconsistent calls_vm information"); // call in remove_activation

  if (_desc->bytecode() == Bytecodes::_return_register_finalizer) {
    assert(state == vtos, "only valid state");
    __ movptr(rax, aaddress(0));
    __ load_klass(rdi, rax);
    __ movl(rdi, Address(rdi, Klass::access_flags_offset()));
    __ testl(rdi, JVM_ACC_HAS_FINALIZER);
    Label skip_register_finalizer;
    __ jcc(Assembler::zero, skip_register_finalizer);

    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::register_finalizer), rax);

    __ bind(skip_register_finalizer);
  }

  // Narrow result if state is itos but result type is smaller.
  // Need to narrow in the return bytecode rather than in generate_return_entry
  // since compiled code callers expect the result to already be narrowed.
  if (state == itos) {
    __ narrow(rax);
  }
  __ remove_activation(state, rsi);

  __ jmp(rsi);
}


// ----------------------------------------------------------------------------
// Volatile variables demand their effects be made known to all CPU's in
// order.  Store buffers on most chips allow reads & writes to reorder; the
// JMM's ReadAfterWrite.java test fails in -Xint mode without some kind of
// memory barrier (i.e., it's not sufficient that the interpreter does not
// reorder volatile references, the hardware also must not reorder them).
//
// According to the new Java Memory Model (JMM):
// (1) All volatiles are serialized wrt to each other.
// ALSO reads & writes act as aquire & release, so:
// (2) A read cannot let unrelated NON-volatile memory refs that happen after
// the read float up to before the read.  It's OK for non-volatile memory refs
// that happen before the volatile read to float down below it.
// (3) Similar a volatile write cannot let unrelated NON-volatile memory refs
// that happen BEFORE the write float down to after the write.  It's OK for
// non-volatile memory refs that happen after the volatile write to float up
// before it.
//
// We only put in barriers around volatile refs (they are expensive), not
// _between_ memory refs (that would require us to track the flavor of the
// previous memory refs).  Requirements (2) and (3) require some barriers
// before volatile stores and after volatile loads.  These nearly cover
// requirement (1) but miss the volatile-store-volatile-load case.  This final
// case is placed after volatile-stores although it could just as well go
// before volatile-loads.
void TemplateTable::volatile_barrier(Assembler::Membar_mask_bits order_constraint ) {
  // Helper function to insert a is-volatile test and memory barrier
  if( !os::is_MP() ) return;    // Not needed on single CPU
  __ membar(order_constraint);
}

void TemplateTable::resolve_cache_and_index(int byte_no,
                                            Register Rcache,
                                            Register index,
                                            size_t index_size) {
  const Register temp = rbx;
  assert_different_registers(Rcache, index, temp);

  Label resolved;
    assert(byte_no == f1_byte || byte_no == f2_byte, "byte_no out of range");
    __ get_cache_and_index_and_bytecode_at_bcp(Rcache, index, temp, byte_no, 1, index_size);
    __ cmpl(temp, (int) bytecode());  // have we resolved this bytecode?
    __ jcc(Assembler::equal, resolved);

  // resolve first time through
  address entry;
  switch (bytecode()) {
    case Bytecodes::_getstatic      : // fall through
    case Bytecodes::_putstatic      : // fall through
    case Bytecodes::_getfield       : // fall through
    case Bytecodes::_putfield       : entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_get_put);        break;
    case Bytecodes::_invokevirtual  : // fall through
    case Bytecodes::_invokespecial  : // fall through
    case Bytecodes::_invokestatic   : // fall through
    case Bytecodes::_invokeinterface: entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_invoke);         break;
    case Bytecodes::_invokehandle   : entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_invokehandle);   break;
    case Bytecodes::_invokedynamic  : entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_invokedynamic);  break;
    default:
      fatal(err_msg("unexpected bytecode: %s", Bytecodes::name(bytecode())));
      break;
  }
  __ movl(temp, (int)bytecode());
  __ call_VM(noreg, entry, temp);
  // Update registers with resolved info
  __ get_cache_and_index_at_bcp(Rcache, index, 1, index_size);
  __ bind(resolved);
}


// The cache and index registers must be set before call
void TemplateTable::load_field_cp_cache_entry(Register obj,
                                              Register cache,
                                              Register index,
                                              Register off,
                                              Register flags,
                                              bool is_static = false) {
  assert_different_registers(cache, index, flags, off);

  ByteSize cp_base_offset = ConstantPoolCache::base_offset();
  // Field offset
  __ movptr(off, Address(cache, index, Address::times_ptr,
                         in_bytes(cp_base_offset + ConstantPoolCacheEntry::f2_offset())));
  // Flags
  __ movl(flags, Address(cache, index, Address::times_ptr,
           in_bytes(cp_base_offset + ConstantPoolCacheEntry::flags_offset())));

  // klass overwrite register
  if (is_static) {
    __ movptr(obj, Address(cache, index, Address::times_ptr,
                           in_bytes(cp_base_offset + ConstantPoolCacheEntry::f1_offset())));
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());
    __ movptr(obj, Address(obj, mirror_offset));
  }
}

void TemplateTable::load_invoke_cp_cache_entry(int byte_no,
                                               Register method,
                                               Register itable_index,
                                               Register flags,
                                               bool is_invokevirtual,
                                               bool is_invokevfinal, /*unused*/
                                               bool is_invokedynamic) {
  // setup registers
  const Register cache = rcx;
  const Register index = rdx;
  assert_different_registers(method, flags);
  assert_different_registers(method, cache, index);
  assert_different_registers(itable_index, flags);
  assert_different_registers(itable_index, cache, index);
  // determine constant pool cache field offsets
  assert(is_invokevirtual == (byte_no == f2_byte), "is_invokevirtual flag redundant");
  const int method_offset = in_bytes(
    ConstantPoolCache::base_offset() +
      ((byte_no == f2_byte)
       ? ConstantPoolCacheEntry::f2_offset()
       : ConstantPoolCacheEntry::f1_offset()));
  const int flags_offset = in_bytes(ConstantPoolCache::base_offset() +
                                    ConstantPoolCacheEntry::flags_offset());
  // access constant pool cache fields
  const int index_offset = in_bytes(ConstantPoolCache::base_offset() +
                                    ConstantPoolCacheEntry::f2_offset());

  size_t index_size = (is_invokedynamic ? sizeof(u4) : sizeof(u2));
  resolve_cache_and_index(byte_no, cache, index, index_size);
    __ movptr(method, Address(cache, index, Address::times_ptr, method_offset));

  if (itable_index != noreg) {
    __ movptr(itable_index, Address(cache, index, Address::times_ptr, index_offset));
  }
  __ movl(flags, Address(cache, index, Address::times_ptr, flags_offset));
}


// The registers cache and index expected to be set before call.
// Correct values of the cache and index registers are preserved.
void TemplateTable::jvmti_post_field_access(Register cache,
                                            Register index,
                                            bool is_static,
                                            bool has_tos) {
  if (JvmtiExport::can_post_field_access()) {
    // Check to see if a field access watch has been set before we take
    // the time to call into the VM.
    Label L1;
    assert_different_registers(cache, index, rax);
    __ mov32(rax, ExternalAddress((address) JvmtiExport::get_field_access_count_addr()));
    __ testl(rax,rax);
    __ jcc(Assembler::zero, L1);

    // cache entry pointer
    __ addptr(cache, in_bytes(ConstantPoolCache::base_offset()));
    __ shll(index, LogBytesPerWord);
    __ addptr(cache, index);
    if (is_static) {
      __ xorptr(rax, rax);      // NULL object reference
    } else {
      __ pop(atos);         // Get the object
      __ verify_oop(rax);
      __ push(atos);        // Restore stack state
    }
    // rax,:   object pointer or NULL
    // cache: cache entry pointer
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_field_access),
               rax, cache);
    __ get_cache_and_index_at_bcp(cache, index, 1);
    __ bind(L1);
  }
}

void TemplateTable::pop_and_check_object(Register r) {
  __ pop_ptr(r);
  __ null_check(r);  // for field access must check obj.
  __ verify_oop(r);
}

void TemplateTable::getfield_or_static(int byte_no, bool is_static) {
  transition(vtos, vtos);

  const Register cache = rcx;
  const Register index = rdx;
  const Register obj   = rcx;
  const Register off   = rbx;
  const Register flags = rax;

  resolve_cache_and_index(byte_no, cache, index, sizeof(u2));
  jvmti_post_field_access(cache, index, is_static, false);
  load_field_cp_cache_entry(obj, cache, index, off, flags, is_static);

  if (!is_static) pop_and_check_object(obj);

  const Address lo(obj, off, Address::times_1, 0*wordSize);
  const Address hi(obj, off, Address::times_1, 1*wordSize);

  Label Done, notByte, notBool, notInt, notShort, notChar, notLong, notFloat, notObj, notDouble;

  __ shrl(flags, ConstantPoolCacheEntry::tos_state_shift);
  assert(btos == 0, "change code, btos != 0");
  // btos
  __ andptr(flags, ConstantPoolCacheEntry::tos_state_mask);
  __ jcc(Assembler::notZero, notByte);

  __ load_signed_byte(rax, lo );
  __ push(btos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_bgetfield, rcx, rbx);
  }
  __ jmp(Done);

  __ bind(notByte);

  __ cmpl(flags, ztos);
  __ jcc(Assembler::notEqual, notBool);

  // ztos (same code as btos)
  __ load_signed_byte(rax, lo);
  __ push(ztos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    // use btos rewriting, no truncating to t/f bit is needed for getfield.
    patch_bytecode(Bytecodes::_fast_bgetfield, rcx, rbx);
  }
  __ jmp(Done);

  __ bind(notBool);

  // itos
  __ cmpl(flags, itos );
  __ jcc(Assembler::notEqual, notInt);

  __ movl(rax, lo );
  __ push(itos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_igetfield, rcx, rbx);
  }
  __ jmp(Done);

  __ bind(notInt);
  // atos
  __ cmpl(flags, atos );
  __ jcc(Assembler::notEqual, notObj);

  __ movl(rax, lo );
  __ push(atos);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_agetfield, rcx, rbx);
  }
  __ jmp(Done);

  __ bind(notObj);
  // ctos
  __ cmpl(flags, ctos );
  __ jcc(Assembler::notEqual, notChar);

  __ load_unsigned_short(rax, lo );
  __ push(ctos);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_cgetfield, rcx, rbx);
  }
  __ jmp(Done);

  __ bind(notChar);
  // stos
  __ cmpl(flags, stos );
  __ jcc(Assembler::notEqual, notShort);

  __ load_signed_short(rax, lo );
  __ push(stos);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_sgetfield, rcx, rbx);
  }
  __ jmp(Done);

  __ bind(notShort);
  // ltos
  __ cmpl(flags, ltos );
  __ jcc(Assembler::notEqual, notLong);

  // Generate code as if volatile.  There just aren't enough registers to
  // save that information and this code is faster than the test.
  __ fild_d(lo);                // Must load atomically
  __ subptr(rsp,2*wordSize);    // Make space for store
  __ fistp_d(Address(rsp,0));
  __ pop(rax);
  __ pop(rdx);

  __ push(ltos);
  // Don't rewrite to _fast_lgetfield for potential volatile case.
  __ jmp(Done);

  __ bind(notLong);
  // ftos
  __ cmpl(flags, ftos );
  __ jcc(Assembler::notEqual, notFloat);

  __ fld_s(lo);
  __ push(ftos);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_fgetfield, rcx, rbx);
  }
  __ jmp(Done);

  __ bind(notFloat);
  // dtos
  __ cmpl(flags, dtos );
  __ jcc(Assembler::notEqual, notDouble);

  __ fld_d(lo);
  __ push(dtos);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_dgetfield, rcx, rbx);
  }
  __ jmpb(Done);

  __ bind(notDouble);

  __ stop("Bad state");

  __ bind(Done);
  // Doug Lea believes this is not needed with current Sparcs (TSO) and Intel (PSO).
  // volatile_barrier( );
}


void TemplateTable::getfield(int byte_no) {
  getfield_or_static(byte_no, false);
}


void TemplateTable::getstatic(int byte_no) {
  getfield_or_static(byte_no, true);
}

// The registers cache and index expected to be set before call.
// The function may destroy various registers, just not the cache and index registers.
void TemplateTable::jvmti_post_field_mod(Register cache, Register index, bool is_static) {

  ByteSize cp_base_offset = ConstantPoolCache::base_offset();

  if (JvmtiExport::can_post_field_modification()) {
    // Check to see if a field modification watch has been set before we take
    // the time to call into the VM.
    Label L1;
    assert_different_registers(cache, index, rax);
    __ mov32(rax, ExternalAddress((address)JvmtiExport::get_field_modification_count_addr()));
    __ testl(rax, rax);
    __ jcc(Assembler::zero, L1);

    // The cache and index registers have been already set.
    // This allows to eliminate this call but the cache and index
    // registers have to be correspondingly used after this line.
    __ get_cache_and_index_at_bcp(rax, rdx, 1);

    if (is_static) {
      // Life is simple.  Null out the object pointer.
      __ xorptr(rbx, rbx);
    } else {
      // Life is harder. The stack holds the value on top, followed by the object.
      // We don't know the size of the value, though; it could be one or two words
      // depending on its type. As a result, we must find the type to determine where
      // the object is.
      Label two_word, valsize_known;
      __ movl(rcx, Address(rax, rdx, Address::times_ptr, in_bytes(cp_base_offset +
                                   ConstantPoolCacheEntry::flags_offset())));
      __ mov(rbx, rsp);
      __ shrl(rcx, ConstantPoolCacheEntry::tos_state_shift);
      // Make sure we don't need to mask rcx after the above shift
      ConstantPoolCacheEntry::verify_tos_state_shift();
      __ cmpl(rcx, ltos);
      __ jccb(Assembler::equal, two_word);
      __ cmpl(rcx, dtos);
      __ jccb(Assembler::equal, two_word);
      __ addptr(rbx, Interpreter::expr_offset_in_bytes(1)); // one word jvalue (not ltos, dtos)
      __ jmpb(valsize_known);

      __ bind(two_word);
      __ addptr(rbx, Interpreter::expr_offset_in_bytes(2)); // two words jvalue

      __ bind(valsize_known);
      // setup object pointer
      __ movptr(rbx, Address(rbx, 0));
    }
    // cache entry pointer
    __ addptr(rax, in_bytes(cp_base_offset));
    __ shll(rdx, LogBytesPerWord);
    __ addptr(rax, rdx);
    // object (tos)
    __ mov(rcx, rsp);
    // rbx,: object pointer set up above (NULL if static)
    // rax,: cache entry pointer
    // rcx: jvalue object on the stack
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_field_modification),
               rbx, rax, rcx);
    __ get_cache_and_index_at_bcp(cache, index, 1);
    __ bind(L1);
  }
}


void TemplateTable::putfield_or_static(int byte_no, bool is_static) {
  transition(vtos, vtos);

  const Register cache = rcx;
  const Register index = rdx;
  const Register obj   = rcx;
  const Register off   = rbx;
  const Register flags = rax;

  resolve_cache_and_index(byte_no, cache, index, sizeof(u2));
  jvmti_post_field_mod(cache, index, is_static);
  load_field_cp_cache_entry(obj, cache, index, off, flags, is_static);

  // Doug Lea believes this is not needed with current Sparcs (TSO) and Intel (PSO).
  // volatile_barrier( );

  Label notVolatile, Done;
  __ movl(rdx, flags);
  __ shrl(rdx, ConstantPoolCacheEntry::is_volatile_shift);
  __ andl(rdx, 0x1);

  // field addresses
  const Address lo(obj, off, Address::times_1, 0*wordSize);
  const Address hi(obj, off, Address::times_1, 1*wordSize);

  Label notByte, notBool, notInt, notShort, notChar, notLong, notFloat, notObj, notDouble;

  __ shrl(flags, ConstantPoolCacheEntry::tos_state_shift);
  assert(btos == 0, "change code, btos != 0");
  __ andl(flags, ConstantPoolCacheEntry::tos_state_mask);
  __ jcc(Assembler::notZero, notByte);

  // btos
  {
    __ pop(btos);
    if (!is_static) pop_and_check_object(obj);
    __ movb(lo, rax);
    if (!is_static) {
      patch_bytecode(Bytecodes::_fast_bputfield, rcx, rbx, true, byte_no);
    }
    __ jmp(Done);
  }

  __ bind(notByte);
  __ cmpl(flags, ztos);
  __ jcc(Assembler::notEqual, notBool);

  // ztos
  {
    __ pop(ztos);
    if (!is_static) pop_and_check_object(obj);
    __ andl(rax, 0x1);
    __ movb(lo, rax);
    if (!is_static) {
      patch_bytecode(Bytecodes::_fast_zputfield, rcx, rbx, true, byte_no);
    }
    __ jmp(Done);
  }

  __ bind(notBool);
  __ cmpl(flags, itos);
  __ jcc(Assembler::notEqual, notInt);

  // itos
  {
    __ pop(itos);
    if (!is_static) pop_and_check_object(obj);
    __ movl(lo, rax);
    if (!is_static) {
      patch_bytecode(Bytecodes::_fast_iputfield, rcx, rbx, true, byte_no);
    }
    __ jmp(Done);
  }

  __ bind(notInt);
  __ cmpl(flags, atos);
  __ jcc(Assembler::notEqual, notObj);

  // atos
  {
    __ pop(atos);
    if (!is_static) pop_and_check_object(obj);
    do_oop_store(_masm, lo, rax, _bs->kind(), false);
    if (!is_static) {
      patch_bytecode(Bytecodes::_fast_aputfield, rcx, rbx, true, byte_no);
    }
    __ jmp(Done);
  }

  __ bind(notObj);
  __ cmpl(flags, ctos);
  __ jcc(Assembler::notEqual, notChar);

  // ctos
  {
    __ pop(ctos);
    if (!is_static) pop_and_check_object(obj);
    __ movw(lo, rax);
    if (!is_static) {
      patch_bytecode(Bytecodes::_fast_cputfield, rcx, rbx, true, byte_no);
    }
    __ jmp(Done);
  }

  __ bind(notChar);
  __ cmpl(flags, stos);
  __ jcc(Assembler::notEqual, notShort);

  // stos
  {
    __ pop(stos);
    if (!is_static) pop_and_check_object(obj);
    __ movw(lo, rax);
    if (!is_static) {
      patch_bytecode(Bytecodes::_fast_sputfield, rcx, rbx, true, byte_no);
    }
    __ jmp(Done);
  }

  __ bind(notShort);
  __ cmpl(flags, ltos);
  __ jcc(Assembler::notEqual, notLong);

  // ltos
  {
    Label notVolatileLong;
    __ testl(rdx, rdx);
    __ jcc(Assembler::zero, notVolatileLong);

    __ pop(ltos);  // overwrites rdx, do this after testing volatile.
    if (!is_static) pop_and_check_object(obj);

    // Replace with real volatile test
    __ push(rdx);
    __ push(rax);                 // Must update atomically with FIST
    __ fild_d(Address(rsp,0));    // So load into FPU register
    __ fistp_d(lo);               // and put into memory atomically
    __ addptr(rsp, 2*wordSize);
    // volatile_barrier();
    volatile_barrier(Assembler::Membar_mask_bits(Assembler::StoreLoad |
                                                 Assembler::StoreStore));
    // Don't rewrite volatile version
    __ jmp(notVolatile);

    __ bind(notVolatileLong);

    __ pop(ltos);  // overwrites rdx
    if (!is_static) pop_and_check_object(obj);
    NOT_LP64(__ movptr(hi, rdx));
    __ movptr(lo, rax);
    if (!is_static) {
      patch_bytecode(Bytecodes::_fast_lputfield, rcx, rbx, true, byte_no);
    }
    __ jmp(notVolatile);
  }

  __ bind(notLong);
  __ cmpl(flags, ftos);
  __ jcc(Assembler::notEqual, notFloat);

  // ftos
  {
    __ pop(ftos);
    if (!is_static) pop_and_check_object(obj);
    __ fstp_s(lo);
    if (!is_static) {
      patch_bytecode(Bytecodes::_fast_fputfield, rcx, rbx, true, byte_no);
    }
    __ jmp(Done);
  }

  __ bind(notFloat);
#ifdef ASSERT
  __ cmpl(flags, dtos);
  __ jcc(Assembler::notEqual, notDouble);
#endif

  // dtos
  {
    __ pop(dtos);
    if (!is_static) pop_and_check_object(obj);
    __ fstp_d(lo);
    if (!is_static) {
      patch_bytecode(Bytecodes::_fast_dputfield, rcx, rbx, true, byte_no);
    }
    __ jmp(Done);
  }

#ifdef ASSERT
  __ bind(notDouble);
  __ stop("Bad state");
#endif

  __ bind(Done);

  // Check for volatile store
  __ testl(rdx, rdx);
  __ jcc(Assembler::zero, notVolatile);
  volatile_barrier(Assembler::Membar_mask_bits(Assembler::StoreLoad |
                                               Assembler::StoreStore));
  __ bind(notVolatile);
}


void TemplateTable::putfield(int byte_no) {
  putfield_or_static(byte_no, false);
}


void TemplateTable::putstatic(int byte_no) {
  putfield_or_static(byte_no, true);
}

void TemplateTable::jvmti_post_fast_field_mod() {
  if (JvmtiExport::can_post_field_modification()) {
    // Check to see if a field modification watch has been set before we take
    // the time to call into the VM.
    Label L2;
     __ mov32(rcx, ExternalAddress((address)JvmtiExport::get_field_modification_count_addr()));
     __ testl(rcx,rcx);
     __ jcc(Assembler::zero, L2);
     __ pop_ptr(rbx);               // copy the object pointer from tos
     __ verify_oop(rbx);
     __ push_ptr(rbx);              // put the object pointer back on tos

     // Save tos values before call_VM() clobbers them. Since we have
     // to do it for every data type, we use the saved values as the
     // jvalue object.
     switch (bytecode()) {          // load values into the jvalue object
     case Bytecodes::_fast_aputfield: __ push_ptr(rax); break;
     case Bytecodes::_fast_bputfield: // fall through
     case Bytecodes::_fast_zputfield: // fall through
     case Bytecodes::_fast_sputfield: // fall through
     case Bytecodes::_fast_cputfield: // fall through
     case Bytecodes::_fast_iputfield: __ push_i(rax); break;
     case Bytecodes::_fast_dputfield: __ push_d(); break;
     case Bytecodes::_fast_fputfield: __ push_f(); break;
     case Bytecodes::_fast_lputfield: __ push_l(rax); break;

     default:
       ShouldNotReachHere();
     }
     __ mov(rcx, rsp);              // points to jvalue on the stack
     // access constant pool cache entry
     __ get_cache_entry_pointer_at_bcp(rax, rdx, 1);
     __ verify_oop(rbx);
     // rbx,: object pointer copied above
     // rax,: cache entry pointer
     // rcx: jvalue object on the stack
     __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_field_modification), rbx, rax, rcx);

     switch (bytecode()) {             // restore tos values
     case Bytecodes::_fast_aputfield: __ pop_ptr(rax); break;
     case Bytecodes::_fast_bputfield: // fall through
     case Bytecodes::_fast_zputfield: // fall through
     case Bytecodes::_fast_sputfield: // fall through
     case Bytecodes::_fast_cputfield: // fall through
     case Bytecodes::_fast_iputfield: __ pop_i(rax); break;
     case Bytecodes::_fast_dputfield: __ pop_d(); break;
     case Bytecodes::_fast_fputfield: __ pop_f(); break;
     case Bytecodes::_fast_lputfield: __ pop_l(rax); break;
     }
     __ bind(L2);
  }
}

void TemplateTable::fast_storefield(TosState state) {
  transition(state, vtos);

  ByteSize base = ConstantPoolCache::base_offset();

  jvmti_post_fast_field_mod();

  // access constant pool cache
  __ get_cache_and_index_at_bcp(rcx, rbx, 1);

  // test for volatile with rdx but rdx is tos register for lputfield.
  if (bytecode() == Bytecodes::_fast_lputfield) __ push(rdx);
  __ movl(rdx, Address(rcx, rbx, Address::times_ptr, in_bytes(base +
                       ConstantPoolCacheEntry::flags_offset())));

  // replace index with field offset from cache entry
  __ movptr(rbx, Address(rcx, rbx, Address::times_ptr, in_bytes(base + ConstantPoolCacheEntry::f2_offset())));

  // Doug Lea believes this is not needed with current Sparcs (TSO) and Intel (PSO).
  // volatile_barrier( );

  Label notVolatile, Done;
  __ shrl(rdx, ConstantPoolCacheEntry::is_volatile_shift);
  __ andl(rdx, 0x1);
  // Check for volatile store
  __ testl(rdx, rdx);
  __ jcc(Assembler::zero, notVolatile);

  if (bytecode() == Bytecodes::_fast_lputfield) __ pop(rdx);

  // Get object from stack
  pop_and_check_object(rcx);

  // field addresses
  const Address lo(rcx, rbx, Address::times_1, 0*wordSize);
  const Address hi(rcx, rbx, Address::times_1, 1*wordSize);

  // access field
  switch (bytecode()) {
    case Bytecodes::_fast_zputfield: __ andl(rax, 0x1);  // boolean is true if LSB is 1
    // fall through to bputfield
    case Bytecodes::_fast_bputfield: __ movb(lo, rax); break;
    case Bytecodes::_fast_sputfield: // fall through
    case Bytecodes::_fast_cputfield: __ movw(lo, rax); break;
    case Bytecodes::_fast_iputfield: __ movl(lo, rax); break;
    case Bytecodes::_fast_lputfield:
      NOT_LP64(__ movptr(hi, rdx));
      __ movptr(lo, rax);
      break;
    case Bytecodes::_fast_fputfield: __ fstp_s(lo); break;
    case Bytecodes::_fast_dputfield: __ fstp_d(lo); break;
    case Bytecodes::_fast_aputfield: {
      do_oop_store(_masm, lo, rax, _bs->kind(), false);
      break;
    }
    default:
      ShouldNotReachHere();
  }

  Label done;
  volatile_barrier(Assembler::Membar_mask_bits(Assembler::StoreLoad |
                                               Assembler::StoreStore));
  // Barriers are so large that short branch doesn't reach!
  __ jmp(done);

  // Same code as above, but don't need rdx to test for volatile.
  __ bind(notVolatile);

  if (bytecode() == Bytecodes::_fast_lputfield) __ pop(rdx);

  // Get object from stack
  pop_and_check_object(rcx);

  // access field
  switch (bytecode()) {
    case Bytecodes::_fast_zputfield: __ andl(rax, 0x1);  // boolean is true if LSB is 1
    // fall through to bputfield
    case Bytecodes::_fast_bputfield: __ movb(lo, rax); break;
    case Bytecodes::_fast_sputfield: // fall through
    case Bytecodes::_fast_cputfield: __ movw(lo, rax); break;
    case Bytecodes::_fast_iputfield: __ movl(lo, rax); break;
    case Bytecodes::_fast_lputfield:
      NOT_LP64(__ movptr(hi, rdx));
      __ movptr(lo, rax);
      break;
    case Bytecodes::_fast_fputfield: __ fstp_s(lo); break;
    case Bytecodes::_fast_dputfield: __ fstp_d(lo); break;
    case Bytecodes::_fast_aputfield: {
      do_oop_store(_masm, lo, rax, _bs->kind(), false);
      break;
    }
    default:
      ShouldNotReachHere();
  }
  __ bind(done);
}


void TemplateTable::fast_accessfield(TosState state) {
  transition(atos, state);

  // do the JVMTI work here to avoid disturbing the register state below
  if (JvmtiExport::can_post_field_access()) {
    // Check to see if a field access watch has been set before we take
    // the time to call into the VM.
    Label L1;
    __ mov32(rcx, ExternalAddress((address) JvmtiExport::get_field_access_count_addr()));
    __ testl(rcx,rcx);
    __ jcc(Assembler::zero, L1);
    // access constant pool cache entry
    __ get_cache_entry_pointer_at_bcp(rcx, rdx, 1);
    __ push_ptr(rax);  // save object pointer before call_VM() clobbers it
    __ verify_oop(rax);
    // rax,: object pointer copied above
    // rcx: cache entry pointer
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_field_access), rax, rcx);
    __ pop_ptr(rax);   // restore object pointer
    __ bind(L1);
  }

  // access constant pool cache
  __ get_cache_and_index_at_bcp(rcx, rbx, 1);
  // replace index with field offset from cache entry
  __ movptr(rbx, Address(rcx,
                         rbx,
                         Address::times_ptr,
                         in_bytes(ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::f2_offset())));


  // rax,: object
  __ verify_oop(rax);
  __ null_check(rax);
  // field addresses
  const Address lo = Address(rax, rbx, Address::times_1, 0*wordSize);
  const Address hi = Address(rax, rbx, Address::times_1, 1*wordSize);

  // access field
  switch (bytecode()) {
    case Bytecodes::_fast_bgetfield: __ movsbl(rax, lo );                 break;
    case Bytecodes::_fast_sgetfield: __ load_signed_short(rax, lo );      break;
    case Bytecodes::_fast_cgetfield: __ load_unsigned_short(rax, lo );    break;
    case Bytecodes::_fast_igetfield: __ movl(rax, lo);                    break;
    case Bytecodes::_fast_lgetfield: __ stop("should not be rewritten");  break;
    case Bytecodes::_fast_fgetfield: __ fld_s(lo);                        break;
    case Bytecodes::_fast_dgetfield: __ fld_d(lo);                        break;
    case Bytecodes::_fast_agetfield: __ movptr(rax, lo); __ verify_oop(rax); break;
    default:
      ShouldNotReachHere();
  }

  // Doug Lea believes this is not needed with current Sparcs(TSO) and Intel(PSO)
  // volatile_barrier( );
}

void TemplateTable::fast_xaccess(TosState state) {
  transition(vtos, state);
  // get receiver
  __ movptr(rax, aaddress(0));
  // access constant pool cache
  __ get_cache_and_index_at_bcp(rcx, rdx, 2);
  __ movptr(rbx, Address(rcx,
                         rdx,
                         Address::times_ptr,
                         in_bytes(ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::f2_offset())));
  // make sure exception is reported in correct bcp range (getfield is next instruction)
  __ increment(rsi);
  __ null_check(rax);
  const Address lo = Address(rax, rbx, Address::times_1, 0*wordSize);
  if (state == itos) {
    __ movl(rax, lo);
  } else if (state == atos) {
    __ movptr(rax, lo);
    __ verify_oop(rax);
  } else if (state == ftos) {
    __ fld_s(lo);
  } else {
    ShouldNotReachHere();
  }
  __ decrement(rsi);
}



//----------------------------------------------------------------------------------------------------
// Calls

void TemplateTable::count_calls(Register method, Register temp) {
  // implemented elsewhere
  ShouldNotReachHere();
}


void TemplateTable::prepare_invoke(int byte_no,
                                   Register method,  // linked method (or i-klass)
                                   Register index,   // itable index, MethodType, etc.
                                   Register recv,    // if caller wants to see it
                                   Register flags    // if caller wants to test it
                                   ) {
  // determine flags
  const Bytecodes::Code code = bytecode();
  const bool is_invokeinterface  = code == Bytecodes::_invokeinterface;
  const bool is_invokedynamic    = code == Bytecodes::_invokedynamic;
  const bool is_invokehandle     = code == Bytecodes::_invokehandle;
  const bool is_invokevirtual    = code == Bytecodes::_invokevirtual;
  const bool is_invokespecial    = code == Bytecodes::_invokespecial;
  const bool load_receiver       = (recv  != noreg);
  const bool save_flags          = (flags != noreg);
  assert(load_receiver == (code != Bytecodes::_invokestatic && code != Bytecodes::_invokedynamic), "");
  assert(save_flags    == (is_invokeinterface || is_invokevirtual), "need flags for vfinal");
  assert(flags == noreg || flags == rdx, "");
  assert(recv  == noreg || recv  == rcx, "");

  // setup registers & access constant pool cache
  if (recv  == noreg)  recv  = rcx;
  if (flags == noreg)  flags = rdx;
  assert_different_registers(method, index, recv, flags);

  // save 'interpreter return address'
  __ save_bcp();

  load_invoke_cp_cache_entry(byte_no, method, index, flags, is_invokevirtual, false, is_invokedynamic);

  // maybe push appendix to arguments (just before return address)
  if (is_invokedynamic || is_invokehandle) {
    Label L_no_push;
    __ testl(flags, (1 << ConstantPoolCacheEntry::has_appendix_shift));
    __ jccb(Assembler::zero, L_no_push);
    // Push the appendix as a trailing parameter.
    // This must be done before we get the receiver,
    // since the parameter_size includes it.
    __ push(rbx);
    __ mov(rbx, index);
    assert(ConstantPoolCacheEntry::_indy_resolved_references_appendix_offset == 0, "appendix expected at index+0");
    __ load_resolved_reference_at_index(index, rbx);
    __ pop(rbx);
    __ push(index);  // push appendix (MethodType, CallSite, etc.)
    __ bind(L_no_push);
  }

  // load receiver if needed (note: no return address pushed yet)
  if (load_receiver) {
    __ movl(recv, flags);
    __ andl(recv, ConstantPoolCacheEntry::parameter_size_mask);
    const int no_return_pc_pushed_yet = -1;  // argument slot correction before we push return address
    const int receiver_is_at_end      = -1;  // back off one slot to get receiver
    Address recv_addr = __ argument_address(recv, no_return_pc_pushed_yet + receiver_is_at_end);
    __ movptr(recv, recv_addr);
    __ verify_oop(recv);
  }

  if (save_flags) {
    __ mov(rsi, flags);
  }

  // compute return type
  __ shrl(flags, ConstantPoolCacheEntry::tos_state_shift);
  // Make sure we don't need to mask flags after the above shift
  ConstantPoolCacheEntry::verify_tos_state_shift();
  // load return address
  {
    const address table_addr = (address) Interpreter::invoke_return_entry_table_for(code);
    ExternalAddress table(table_addr);
    __ movptr(flags, ArrayAddress(table, Address(noreg, flags, Address::times_ptr)));
  }

  // push return address
  __ push(flags);

  // Restore flags value from the constant pool cache, and restore rsi
  // for later null checks.  rsi is the bytecode pointer
  if (save_flags) {
    __ mov(flags, rsi);
    __ restore_bcp();
  }
}


void TemplateTable::invokevirtual_helper(Register index,
                                         Register recv,
                                         Register flags) {
  // Uses temporary registers rax, rdx
  assert_different_registers(index, recv, rax, rdx);
  assert(index == rbx, "");
  assert(recv  == rcx, "");

  // Test for an invoke of a final method
  Label notFinal;
  __ movl(rax, flags);
  __ andl(rax, (1 << ConstantPoolCacheEntry::is_vfinal_shift));
  __ jcc(Assembler::zero, notFinal);

  const Register method = index;  // method must be rbx
  assert(method == rbx,
         "Method* must be rbx for interpreter calling convention");

  // do the call - the index is actually the method to call
  // that is, f2 is a vtable index if !is_vfinal, else f2 is a Method*

  // It's final, need a null check here!
  __ null_check(recv);

  // profile this call
  __ profile_final_call(rax);
  __ profile_arguments_type(rax, method, rsi, true);

  __ jump_from_interpreted(method, rax);

  __ bind(notFinal);

  // get receiver klass
  __ null_check(recv, oopDesc::klass_offset_in_bytes());
  __ load_klass(rax, recv);

  // profile this call
  __ profile_virtual_call(rax, rdi, rdx);

  // get target Method* & entry point
  __ lookup_virtual_method(rax, index, method);
  __ profile_arguments_type(rdx, method, rsi, true);
  __ jump_from_interpreted(method, rdx);
}


void TemplateTable::invokevirtual(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f2_byte, "use this argument");
  prepare_invoke(byte_no,
                 rbx,    // method or vtable index
                 noreg,  // unused itable index
                 rcx, rdx); // recv, flags

  // rbx: index
  // rcx: receiver
  // rdx: flags

  invokevirtual_helper(rbx, rcx, rdx);
}


void TemplateTable::invokespecial(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");
  prepare_invoke(byte_no, rbx, noreg,  // get f1 Method*
                 rcx);  // get receiver also for null check
  __ verify_oop(rcx);
  __ null_check(rcx);
  // do the call
  __ profile_call(rax);
  __ profile_arguments_type(rax, rbx, rsi, false);
  __ jump_from_interpreted(rbx, rax);
}


void TemplateTable::invokestatic(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");
  prepare_invoke(byte_no, rbx);  // get f1 Method*
  // do the call
  __ profile_call(rax);
  __ profile_arguments_type(rax, rbx, rsi, false);
  __ jump_from_interpreted(rbx, rax);
}


void TemplateTable::fast_invokevfinal(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f2_byte, "use this argument");
  __ stop("fast_invokevfinal not used on x86");
}


void TemplateTable::invokeinterface(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");
  prepare_invoke(byte_no, rax, rbx,  // get f1 Klass*, f2 Method*
                 rcx, rdx); // recv, flags

  // rax: reference klass (from f1)
  // rbx: method (from f2)
  // rcx: receiver
  // rdx: flags

  // Special case of invokeinterface called for virtual method of
  // java.lang.Object.  See cpCacheOop.cpp for details.
  // This code isn't produced by javac, but could be produced by
  // another compliant java compiler.
  Label notMethod;
  __ movl(rdi, rdx);
  __ andl(rdi, (1 << ConstantPoolCacheEntry::is_forced_virtual_shift));
  __ jcc(Assembler::zero, notMethod);

  invokevirtual_helper(rbx, rcx, rdx);
  __ bind(notMethod);

  // Get receiver klass into rdx - also a null check
  __ restore_locals();  // restore rdi
  __ null_check(rcx, oopDesc::klass_offset_in_bytes());
  __ load_klass(rdx, rcx);

  Label no_such_interface, no_such_method;

  // Receiver subtype check against REFC.
  // Superklass in rax. Subklass in rdx. Blows rcx, rdi.
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             rdx, rax, noreg,
                             // outputs: scan temp. reg, scan temp. reg
                             rsi, rdi,
                             no_such_interface,
                             /*return_method=*/false);


  // profile this call
  __ restore_bcp(); // rbcp was destroyed by receiver type check
  __ profile_virtual_call(rdx, rsi, rdi);

  // Get declaring interface class from method, and itable index
  __ movptr(rax, Address(rbx, Method::const_offset()));
  __ movptr(rax, Address(rax, ConstMethod::constants_offset()));
  __ movptr(rax, Address(rax, ConstantPool::pool_holder_offset_in_bytes()));
  __ movl(rbx, Address(rbx, Method::itable_index_offset()));
  __ subl(rbx, Method::itable_index_max);
  __ negl(rbx);

  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             rdx, rax, rbx,
                             // outputs: method, scan temp. reg
                             rbx, rsi,
                             no_such_interface);

  // rbx: Method* to call
  // rcx: receiver
  // Check for abstract method error
  // Note: This should be done more efficiently via a throw_abstract_method_error
  //       interpreter entry point and a conditional jump to it in case of a null
  //       method.
  __ testptr(rbx, rbx);
  __ jcc(Assembler::zero, no_such_method);

  __ profile_arguments_type(rdx, rbx, rsi, true);

  // do the call
  // rcx: receiver
  // rbx,: Method*
  __ jump_from_interpreted(rbx, rdx);
  __ should_not_reach_here();

  // exception handling code follows...
  // note: must restore interpreter registers to canonical
  //       state for exception handling to work correctly!

  __ bind(no_such_method);
  // throw exception
  __ pop(rbx);           // pop return address (pushed by prepare_invoke)
  __ restore_bcp();      // rsi must be correct for exception handler   (was destroyed)
  __ restore_locals();   // make sure locals pointer is correct as well (was destroyed)
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_AbstractMethodError));
  // the call_VM checks for exception, so we should never return here.
  __ should_not_reach_here();

  __ bind(no_such_interface);
  // throw exception
  __ pop(rbx);           // pop return address (pushed by prepare_invoke)
  __ restore_bcp();      // rsi must be correct for exception handler   (was destroyed)
  __ restore_locals();   // make sure locals pointer is correct as well (was destroyed)
  __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                   InterpreterRuntime::throw_IncompatibleClassChangeError));
  // the call_VM checks for exception, so we should never return here.
  __ should_not_reach_here();
}

void TemplateTable::invokehandle(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");
  const Register rbx_method = rbx;
  const Register rax_mtype  = rax;
  const Register rcx_recv   = rcx;
  const Register rdx_flags  = rdx;

  if (!EnableInvokeDynamic) {
    // rewriter does not generate this bytecode
    __ should_not_reach_here();
    return;
  }

  prepare_invoke(byte_no, rbx_method, rax_mtype, rcx_recv);
  __ verify_method_ptr(rbx_method);
  __ verify_oop(rcx_recv);
  __ null_check(rcx_recv);

  // rax: MethodType object (from cpool->resolved_references[f1], if necessary)
  // rbx: MH.invokeExact_MT method (from f2)

  // Note:  rax_mtype is already pushed (if necessary) by prepare_invoke

  // FIXME: profile the LambdaForm also
  __ profile_final_call(rax);
  __ profile_arguments_type(rdx, rbx_method, rsi, true);

  __ jump_from_interpreted(rbx_method, rdx);
}


void TemplateTable::invokedynamic(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  if (!EnableInvokeDynamic) {
    // We should not encounter this bytecode if !EnableInvokeDynamic.
    // The verifier will stop it.  However, if we get past the verifier,
    // this will stop the thread in a reasonable way, without crashing the JVM.
    __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                     InterpreterRuntime::throw_IncompatibleClassChangeError));
    // the call_VM checks for exception, so we should never return here.
    __ should_not_reach_here();
    return;
  }

  const Register rbx_method   = rbx;
  const Register rax_callsite = rax;

  prepare_invoke(byte_no, rbx_method, rax_callsite);

  // rax: CallSite object (from cpool->resolved_references[f1])
  // rbx: MH.linkToCallSite method (from f2)

  // Note:  rax_callsite is already pushed by prepare_invoke

  // %%% should make a type profile for any invokedynamic that takes a ref argument
  // profile this call
  __ profile_call(rsi);
  __ profile_arguments_type(rdx, rbx, rsi, false);

  __ verify_oop(rax_callsite);

  __ jump_from_interpreted(rbx_method, rdx);
}

//----------------------------------------------------------------------------------------------------
// Allocation

void TemplateTable::_new() {
  transition(vtos, atos);
  __ get_unsigned_2_byte_index_at_bcp(rdx, 1);
  Label slow_case;
  Label slow_case_no_pop;
  Label done;
  Label initialize_header;
  Label initialize_object;  // including clearing the fields
  Label allocate_shared;

  __ get_cpool_and_tags(rcx, rax);

  // Make sure the class we're about to instantiate has been resolved.
  // This is done before loading InstanceKlass to be consistent with the order
  // how Constant Pool is updated (see ConstantPool::klass_at_put)
  const int tags_offset = Array<u1>::base_offset_in_bytes();
  __ cmpb(Address(rax, rdx, Address::times_1, tags_offset), JVM_CONSTANT_Class);
  __ jcc(Assembler::notEqual, slow_case_no_pop);

  // get InstanceKlass
  __ movptr(rcx, Address(rcx, rdx, Address::times_ptr, sizeof(ConstantPool)));
  __ push(rcx);  // save the contexts of klass for initializing the header

  // make sure klass is initialized & doesn't have finalizer
  // make sure klass is fully initialized
  __ cmpb(Address(rcx, InstanceKlass::init_state_offset()), InstanceKlass::fully_initialized);
  __ jcc(Assembler::notEqual, slow_case);

  // get instance_size in InstanceKlass (scaled to a count of bytes)
  __ movl(rdx, Address(rcx, Klass::layout_helper_offset()));
  // test to see if it has a finalizer or is malformed in some way
  __ testl(rdx, Klass::_lh_instance_slow_path_bit);
  __ jcc(Assembler::notZero, slow_case);

  //
  // Allocate the instance
  // 1) Try to allocate in the TLAB
  // 2) if fail and the object is large allocate in the shared Eden
  // 3) if the above fails (or is not applicable), go to a slow case
  // (creates a new TLAB, etc.)

  const bool allow_shared_alloc =
    Universe::heap()->supports_inline_contig_alloc() && !CMSIncrementalMode;

  const Register thread = rcx;
  if (UseTLAB || allow_shared_alloc) {
    __ get_thread(thread);
  }

  if (UseTLAB) {
    __ movptr(rax, Address(thread, in_bytes(JavaThread::tlab_top_offset())));
    __ lea(rbx, Address(rax, rdx, Address::times_1));
    __ cmpptr(rbx, Address(thread, in_bytes(JavaThread::tlab_end_offset())));
    __ jcc(Assembler::above, allow_shared_alloc ? allocate_shared : slow_case);
    __ movptr(Address(thread, in_bytes(JavaThread::tlab_top_offset())), rbx);
    if (ZeroTLAB) {
      // the fields have been already cleared
      __ jmp(initialize_header);
    } else {
      // initialize both the header and fields
      __ jmp(initialize_object);
    }
  }

  // Allocation in the shared Eden, if allowed.
  //
  // rdx: instance size in bytes
  if (allow_shared_alloc) {
    __ bind(allocate_shared);

    ExternalAddress heap_top((address)Universe::heap()->top_addr());

    Label retry;
    __ bind(retry);
    __ movptr(rax, heap_top);
    __ lea(rbx, Address(rax, rdx, Address::times_1));
    __ cmpptr(rbx, ExternalAddress((address)Universe::heap()->end_addr()));
    __ jcc(Assembler::above, slow_case);

    // Compare rax, with the top addr, and if still equal, store the new
    // top addr in rbx, at the address of the top addr pointer. Sets ZF if was
    // equal, and clears it otherwise. Use lock prefix for atomicity on MPs.
    //
    // rax,: object begin
    // rbx,: object end
    // rdx: instance size in bytes
    __ locked_cmpxchgptr(rbx, heap_top);

    // if someone beat us on the allocation, try again, otherwise continue
    __ jcc(Assembler::notEqual, retry);

    __ incr_allocated_bytes(thread, rdx, 0);
  }

  if (UseTLAB || Universe::heap()->supports_inline_contig_alloc()) {
    // The object is initialized before the header.  If the object size is
    // zero, go directly to the header initialization.
    __ bind(initialize_object);
    __ decrement(rdx, sizeof(oopDesc));
    __ jcc(Assembler::zero, initialize_header);

    // Initialize topmost object field, divide rdx by 8, check if odd and
    // test if zero.
    __ xorl(rcx, rcx);    // use zero reg to clear memory (shorter code)
    __ shrl(rdx, LogBytesPerLong); // divide by 2*oopSize and set carry flag if odd

    // rdx must have been multiple of 8
#ifdef ASSERT
    // make sure rdx was multiple of 8
    Label L;
    // Ignore partial flag stall after shrl() since it is debug VM
    __ jccb(Assembler::carryClear, L);
    __ stop("object size is not multiple of 2 - adjust this code");
    __ bind(L);
    // rdx must be > 0, no extra check needed here
#endif

    // initialize remaining object fields: rdx was a multiple of 8
    { Label loop;
    __ bind(loop);
    __ movptr(Address(rax, rdx, Address::times_8, sizeof(oopDesc) - 1*oopSize), rcx);
    NOT_LP64(__ movptr(Address(rax, rdx, Address::times_8, sizeof(oopDesc) - 2*oopSize), rcx));
    __ decrement(rdx);
    __ jcc(Assembler::notZero, loop);
    }

    // initialize object header only.
    __ bind(initialize_header);
    if (UseBiasedLocking) {
      __ pop(rcx);   // get saved klass back in the register.
      __ movptr(rbx, Address(rcx, Klass::prototype_header_offset()));
      __ movptr(Address(rax, oopDesc::mark_offset_in_bytes ()), rbx);
    } else {
      __ movptr(Address(rax, oopDesc::mark_offset_in_bytes ()),
                (int32_t)markOopDesc::prototype()); // header
      __ pop(rcx);   // get saved klass back in the register.
    }
    __ store_klass(rax, rcx);  // klass

    {
      SkipIfEqual skip_if(_masm, &DTraceAllocProbes, 0);
      // Trigger dtrace event for fastpath
      __ push(atos);
      __ call_VM_leaf(
           CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_object_alloc), rax);
      __ pop(atos);
    }

    __ jmp(done);
  }

  // slow case
  __ bind(slow_case);
  __ pop(rcx);   // restore stack pointer to what it was when we came in.
  __ bind(slow_case_no_pop);
  __ get_constant_pool(rax);
  __ get_unsigned_2_byte_index_at_bcp(rdx, 1);
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::_new), rax, rdx);

  // continue
  __ bind(done);
}


void TemplateTable::newarray() {
  transition(itos, atos);
  __ push_i(rax);                                 // make sure everything is on the stack
  __ load_unsigned_byte(rdx, at_bcp(1));
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::newarray), rdx, rax);
  __ pop_i(rdx);                                  // discard size
}


void TemplateTable::anewarray() {
  transition(itos, atos);
  __ get_unsigned_2_byte_index_at_bcp(rdx, 1);
  __ get_constant_pool(rcx);
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::anewarray), rcx, rdx, rax);
}


void TemplateTable::arraylength() {
  transition(atos, itos);
  __ null_check(rax, arrayOopDesc::length_offset_in_bytes());
  __ movl(rax, Address(rax, arrayOopDesc::length_offset_in_bytes()));
}


void TemplateTable::checkcast() {
  transition(atos, atos);
  Label done, is_null, ok_is_subtype, quicked, resolved;
  __ testptr(rax, rax);   // Object is in EAX
  __ jcc(Assembler::zero, is_null);

  // Get cpool & tags index
  __ get_cpool_and_tags(rcx, rdx); // ECX=cpool, EDX=tags array
  __ get_unsigned_2_byte_index_at_bcp(rbx, 1); // EBX=index
  // See if bytecode has already been quicked
  __ cmpb(Address(rdx, rbx, Address::times_1, Array<u1>::base_offset_in_bytes()), JVM_CONSTANT_Class);
  __ jcc(Assembler::equal, quicked);

  __ push(atos);
  call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::quicken_io_cc) );
  // vm_result_2 has metadata result
  // borrow rdi from locals
  __ get_thread(rdi);
  __ get_vm_result_2(rax, rdi);
  __ restore_locals();
  __ pop_ptr(rdx);
  __ jmpb(resolved);

  // Get superklass in EAX and subklass in EBX
  __ bind(quicked);
  __ mov(rdx, rax);          // Save object in EDX; EAX needed for subtype check
  __ movptr(rax, Address(rcx, rbx, Address::times_ptr, sizeof(ConstantPool)));

  __ bind(resolved);
  __ load_klass(rbx, rdx);

  // Generate subtype check.  Blows ECX.  Resets EDI.  Object in EDX.
  // Superklass in EAX.  Subklass in EBX.
  __ gen_subtype_check( rbx, ok_is_subtype );

  // Come here on failure
  __ push(rdx);
  // object is at TOS
  __ jump(ExternalAddress(Interpreter::_throw_ClassCastException_entry));

  // Come here on success
  __ bind(ok_is_subtype);
  __ mov(rax,rdx);           // Restore object in EDX

  // Collect counts on whether this check-cast sees NULLs a lot or not.
  if (ProfileInterpreter) {
    __ jmp(done);
    __ bind(is_null);
    __ profile_null_seen(rcx);
  } else {
    __ bind(is_null);   // same as 'done'
  }
  __ bind(done);
}


void TemplateTable::instanceof() {
  transition(atos, itos);
  Label done, is_null, ok_is_subtype, quicked, resolved;
  __ testptr(rax, rax);
  __ jcc(Assembler::zero, is_null);

  // Get cpool & tags index
  __ get_cpool_and_tags(rcx, rdx); // ECX=cpool, EDX=tags array
  __ get_unsigned_2_byte_index_at_bcp(rbx, 1); // EBX=index
  // See if bytecode has already been quicked
  __ cmpb(Address(rdx, rbx, Address::times_1, Array<u1>::base_offset_in_bytes()), JVM_CONSTANT_Class);
  __ jcc(Assembler::equal, quicked);

  __ push(atos);
  call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::quicken_io_cc) );
  // vm_result_2 has metadata result
  // borrow rdi from locals
  __ get_thread(rdi);
  __ get_vm_result_2(rax, rdi);
  __ restore_locals();
  __ pop_ptr(rdx);
  __ load_klass(rdx, rdx);
  __ jmp(resolved);

  // Get superklass in EAX and subklass in EDX
  __ bind(quicked);
  __ load_klass(rdx, rax);
  __ movptr(rax, Address(rcx, rbx, Address::times_ptr, sizeof(ConstantPool)));

  __ bind(resolved);

  // Generate subtype check.  Blows ECX.  Resets EDI.
  // Superklass in EAX.  Subklass in EDX.
  __ gen_subtype_check( rdx, ok_is_subtype );

  // Come here on failure
  __ xorl(rax,rax);
  __ jmpb(done);
  // Come here on success
  __ bind(ok_is_subtype);
  __ movl(rax, 1);

  // Collect counts on whether this test sees NULLs a lot or not.
  if (ProfileInterpreter) {
    __ jmp(done);
    __ bind(is_null);
    __ profile_null_seen(rcx);
  } else {
    __ bind(is_null);   // same as 'done'
  }
  __ bind(done);
  // rax, = 0: obj == NULL or  obj is not an instanceof the specified klass
  // rax, = 1: obj != NULL and obj is     an instanceof the specified klass
}


//----------------------------------------------------------------------------------------------------
// Breakpoints
void TemplateTable::_breakpoint() {

  // Note: We get here even if we are single stepping..
  // jbug inists on setting breakpoints at every bytecode
  // even if we are in single step mode.

  transition(vtos, vtos);

  // get the unpatched byte code
  __ get_method(rcx);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::get_original_bytecode_at), rcx, rsi);
  __ mov(rbx, rax);

  // post the breakpoint event
  __ get_method(rcx);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::_breakpoint), rcx, rsi);

  // complete the execution of original bytecode
  __ dispatch_only_normal(vtos);
}


//----------------------------------------------------------------------------------------------------
// Exceptions

void TemplateTable::athrow() {
  transition(atos, vtos);
  __ null_check(rax);
  __ jump(ExternalAddress(Interpreter::throw_exception_entry()));
}


//----------------------------------------------------------------------------------------------------
// Synchronization
//
// Note: monitorenter & exit are symmetric routines; which is reflected
//       in the assembly code structure as well
//
// Stack layout:
//
// [expressions  ] <--- rsp               = expression stack top
// ..
// [expressions  ]
// [monitor entry] <--- monitor block top = expression stack bot
// ..
// [monitor entry]
// [frame data   ] <--- monitor block bot
// ...
// [saved rbp,    ] <--- rbp,


void TemplateTable::monitorenter() {
  transition(atos, vtos);

  // check for NULL object
  __ null_check(rax);

  const Address monitor_block_top(rbp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
  const Address monitor_block_bot(rbp, frame::interpreter_frame_initial_sp_offset        * wordSize);
  const int entry_size =         (     frame::interpreter_frame_monitor_size()           * wordSize);
  Label allocated;

  // initialize entry pointer
  __ xorl(rdx, rdx);                             // points to free slot or NULL

  // find a free slot in the monitor block (result in rdx)
  { Label entry, loop, exit;
    __ movptr(rcx, monitor_block_top);           // points to current entry, starting with top-most entry

    __ lea(rbx, monitor_block_bot);              // points to word before bottom of monitor block
    __ jmpb(entry);

    __ bind(loop);
    __ cmpptr(Address(rcx, BasicObjectLock::obj_offset_in_bytes()), (int32_t)NULL_WORD);  // check if current entry is used
    __ cmovptr(Assembler::equal, rdx, rcx);      // if not used then remember entry in rdx
    __ cmpptr(rax, Address(rcx, BasicObjectLock::obj_offset_in_bytes()));   // check if current entry is for same object
    __ jccb(Assembler::equal, exit);             // if same object then stop searching
    __ addptr(rcx, entry_size);                  // otherwise advance to next entry
    __ bind(entry);
    __ cmpptr(rcx, rbx);                         // check if bottom reached
    __ jcc(Assembler::notEqual, loop);           // if not at bottom then check this entry
    __ bind(exit);
  }

  __ testptr(rdx, rdx);                          // check if a slot has been found
  __ jccb(Assembler::notZero, allocated);        // if found, continue with that one

  // allocate one if there's no free slot
  { Label entry, loop;
    // 1. compute new pointers                   // rsp: old expression stack top
    __ movptr(rdx, monitor_block_bot);           // rdx: old expression stack bottom
    __ subptr(rsp, entry_size);                  // move expression stack top
    __ subptr(rdx, entry_size);                  // move expression stack bottom
    __ mov(rcx, rsp);                            // set start value for copy loop
    __ movptr(monitor_block_bot, rdx);           // set new monitor block top
    __ jmp(entry);
    // 2. move expression stack contents
    __ bind(loop);
    __ movptr(rbx, Address(rcx, entry_size));    // load expression stack word from old location
    __ movptr(Address(rcx, 0), rbx);             // and store it at new location
    __ addptr(rcx, wordSize);                    // advance to next word
    __ bind(entry);
    __ cmpptr(rcx, rdx);                         // check if bottom reached
    __ jcc(Assembler::notEqual, loop);           // if not at bottom then copy next word
  }

  // call run-time routine
  // rdx: points to monitor entry
  __ bind(allocated);

  // Increment bcp to point to the next bytecode, so exception handling for async. exceptions work correctly.
  // The object has already been poped from the stack, so the expression stack looks correct.
  __ increment(rsi);

  __ movptr(Address(rdx, BasicObjectLock::obj_offset_in_bytes()), rax);     // store object
  __ lock_object(rdx);

  // check to make sure this monitor doesn't cause stack overflow after locking
  __ save_bcp();  // in case of exception
  __ generate_stack_overflow_check(0);

  // The bcp has already been incremented. Just need to dispatch to next instruction.
  __ dispatch_next(vtos);
}


void TemplateTable::monitorexit() {
  transition(atos, vtos);

  // check for NULL object
  __ null_check(rax);

  const Address monitor_block_top(rbp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
  const Address monitor_block_bot(rbp, frame::interpreter_frame_initial_sp_offset        * wordSize);
  const int entry_size =         (     frame::interpreter_frame_monitor_size()           * wordSize);
  Label found;

  // find matching slot
  { Label entry, loop;
    __ movptr(rdx, monitor_block_top);           // points to current entry, starting with top-most entry
    __ lea(rbx, monitor_block_bot);             // points to word before bottom of monitor block
    __ jmpb(entry);

    __ bind(loop);
    __ cmpptr(rax, Address(rdx, BasicObjectLock::obj_offset_in_bytes()));   // check if current entry is for same object
    __ jcc(Assembler::equal, found);             // if same object then stop searching
    __ addptr(rdx, entry_size);                  // otherwise advance to next entry
    __ bind(entry);
    __ cmpptr(rdx, rbx);                         // check if bottom reached
    __ jcc(Assembler::notEqual, loop);           // if not at bottom then check this entry
  }

  // error handling. Unlocking was not block-structured
  Label end;
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_illegal_monitor_state_exception));
  __ should_not_reach_here();

  // call run-time routine
  // rcx: points to monitor entry
  __ bind(found);
  __ push_ptr(rax);                                 // make sure object is on stack (contract with oopMaps)
  __ unlock_object(rdx);
  __ pop_ptr(rax);                                  // discard object
  __ bind(end);
}


//----------------------------------------------------------------------------------------------------
// Wide instructions

void TemplateTable::wide() {
  transition(vtos, vtos);
  __ load_unsigned_byte(rbx, at_bcp(1));
  ExternalAddress wtable((address)Interpreter::_wentry_point);
  __ jump(ArrayAddress(wtable, Address(noreg, rbx, Address::times_ptr)));
  // Note: the rsi increment step is part of the individual wide bytecode implementations
}


//----------------------------------------------------------------------------------------------------
// Multi arrays

void TemplateTable::multianewarray() {
  transition(vtos, atos);
  __ load_unsigned_byte(rax, at_bcp(3)); // get number of dimensions
  // last dim is on top of stack; we want address of first one:
  // first_addr = last_addr + (ndims - 1) * stackElementSize - 1*wordsize
  // the latter wordSize to point to the beginning of the array.
  __ lea(  rax, Address(rsp, rax, Interpreter::stackElementScale(), -wordSize));
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::multianewarray), rax);     // pass in rax,
  __ load_unsigned_byte(rbx, at_bcp(3));
  __ lea(rsp, Address(rsp, rbx, Interpreter::stackElementScale()));  // get rid of counts
}

#endif /* !CC_INTERP */
