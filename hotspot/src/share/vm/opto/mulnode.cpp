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

#include "precompiled.hpp"
#include "memory/allocation.inline.hpp"
#include "opto/addnode.hpp"
#include "opto/connode.hpp"
#include "opto/memnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/subnode.hpp"

// Portions of code courtesy of Clifford Click


//=============================================================================
//------------------------------hash-------------------------------------------
// Hash function over MulNodes.  Needs to be commutative; i.e., I swap
// (commute) inputs to MulNodes willy-nilly so the hash function must return
// the same value in the presence of edge swapping.
uint MulNode::hash() const {
  return (uintptr_t)in(1) + (uintptr_t)in(2) + Opcode();
}

//------------------------------Identity---------------------------------------
// Multiplying a one preserves the other argument
Node *MulNode::Identity( PhaseTransform *phase ) {
  register const Type *one = mul_id();  // The multiplicative identity
  if( phase->type( in(1) )->higher_equal( one ) ) return in(2);
  if( phase->type( in(2) )->higher_equal( one ) ) return in(1);

  return this;
}

//------------------------------Ideal------------------------------------------
// We also canonicalize the Node, moving constants to the right input,
// and flatten expressions (so that 1+x+2 becomes x+3).
Node *MulNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  Node *progress = NULL;        // Progress flag
  // We are OK if right is a constant, or right is a load and
  // left is a non-constant.
  if( !(t2->singleton() ||
        (in(2)->is_Load() && !(t1->singleton() || in(1)->is_Load())) ) ) {
    if( t1->singleton() ||       // Left input is a constant?
        // Otherwise, sort inputs (commutativity) to help value numbering.
        (in(1)->_idx > in(2)->_idx) ) {
      swap_edges(1, 2);
      const Type *t = t1;
      t1 = t2;
      t2 = t;
      progress = this;            // Made progress
    }
  }

  // If the right input is a constant, and the left input is a product of a
  // constant, flatten the expression tree.
  uint op = Opcode();
  if( t2->singleton() &&        // Right input is a constant?
      op != Op_MulF &&          // Float & double cannot reassociate
      op != Op_MulD ) {
    if( t2 == Type::TOP ) return NULL;
    Node *mul1 = in(1);
#ifdef ASSERT
    // Check for dead loop
    int   op1 = mul1->Opcode();
    if( phase->eqv( mul1, this ) || phase->eqv( in(2), this ) ||
        ( op1 == mul_opcode() || op1 == add_opcode() ) &&
        ( phase->eqv( mul1->in(1), this ) || phase->eqv( mul1->in(2), this ) ||
          phase->eqv( mul1->in(1), mul1 ) || phase->eqv( mul1->in(2), mul1 ) ) )
      assert(false, "dead loop in MulNode::Ideal");
#endif

    if( mul1->Opcode() == mul_opcode() ) {  // Left input is a multiply?
      // Mul of a constant?
      const Type *t12 = phase->type( mul1->in(2) );
      if( t12->singleton() && t12 != Type::TOP) { // Left input is an add of a constant?
        // Compute new constant; check for overflow
        const Type *tcon01 = ((MulNode*)mul1)->mul_ring(t2,t12);
        if( tcon01->singleton() ) {
          // The Mul of the flattened expression
          set_req(1, mul1->in(1));
          set_req(2, phase->makecon( tcon01 ));
          t2 = tcon01;
          progress = this;      // Made progress
        }
      }
    }
    // If the right input is a constant, and the left input is an add of a
    // constant, flatten the tree: (X+con1)*con0 ==> X*con0 + con1*con0
    const Node *add1 = in(1);
    if( add1->Opcode() == add_opcode() ) {      // Left input is an add?
      // Add of a constant?
      const Type *t12 = phase->type( add1->in(2) );
      if( t12->singleton() && t12 != Type::TOP ) { // Left input is an add of a constant?
        assert( add1->in(1) != add1, "dead loop in MulNode::Ideal" );
        // Compute new constant; check for overflow
        const Type *tcon01 = mul_ring(t2,t12);
        if( tcon01->singleton() ) {

        // Convert (X+con1)*con0 into X*con0
          Node *mul = clone();    // mul = ()*con0
          mul->set_req(1,add1->in(1));  // mul = X*con0
          mul = phase->transform(mul);

          Node *add2 = add1->clone();
          add2->set_req(1, mul);        // X*con0 + con0*con1
          add2->set_req(2, phase->makecon(tcon01) );
          progress = add2;
        }
      }
    } // End of is left input an add
  } // End of is right input a Mul

  return progress;
}

//------------------------------Value-----------------------------------------
const Type *MulNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  // Either input is TOP ==> the result is TOP
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Either input is ZERO ==> the result is ZERO.
  // Not valid for floats or doubles since +0.0 * -0.0 --> +0.0
  int op = Opcode();
  if( op == Op_MulI || op == Op_AndI || op == Op_MulL || op == Op_AndL ) {
    const Type *zero = add_id();        // The multiplicative zero
    if( t1->higher_equal( zero ) ) return zero;
    if( t2->higher_equal( zero ) ) return zero;
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  if( t1 == Type::BOTTOM || t2 == Type::BOTTOM )
    return bottom_type();

#if defined(IA32)
  // Can't trust native compilers to properly fold strict double
  // multiplication with round-to-zero on this platform.
  if (op == Op_MulD && phase->C->method()->is_strict()) {
    return TypeD::DOUBLE;
  }
#endif

  return mul_ring(t1,t2);            // Local flavor of type multiplication
}

//=============================================================================
//------------------------------Ideal------------------------------------------
// Check for power-of-2 multiply, then try the regular MulNode::Ideal
Node *MulINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Swap constant to right
  jint con;
  if ((con = in(1)->find_int_con(0)) != 0) {
    swap_edges(1, 2);
    // Finish rest of method to use info in 'con'
  } else if ((con = in(2)->find_int_con(0)) == 0) {
    return MulNode::Ideal(phase, can_reshape);
  }

  // Now we have a constant Node on the right and the constant in con
  if (con == 0) return NULL;   // By zero is handled by Value call
  if (con == 1) return NULL;   // By one  is handled by Identity call

  // Check for negative constant; if so negate the final result
  bool sign_flip = false;

  unsigned int abs_con = uabs(con);
  if (abs_con != (unsigned int)con) {
    sign_flip = true;
  }

  // Get low bit; check for being the only bit
  Node *res = NULL;
  unsigned int bit1 = abs_con & (0-abs_con);       // Extract low bit
  if (bit1 == abs_con) {           // Found a power of 2?
    res = new (phase->C) LShiftINode(in(1), phase->intcon(log2_uint(bit1)));
  } else {

    // Check for constant with 2 bits set
    unsigned int bit2 = abs_con-bit1;
    bit2 = bit2 & (0-bit2);          // Extract 2nd bit
    if (bit2 + bit1 == abs_con) {    // Found all bits in con?
      Node *n1 = phase->transform( new (phase->C) LShiftINode(in(1), phase->intcon(log2_uint(bit1))));
      Node *n2 = phase->transform( new (phase->C) LShiftINode(in(1), phase->intcon(log2_uint(bit2))));
      res = new (phase->C) AddINode(n2, n1);

    } else if (is_power_of_2(abs_con+1)) {
      // Sleezy: power-of-2 -1.  Next time be generic.
      unsigned int temp = abs_con + 1;
      Node *n1 = phase->transform(new (phase->C) LShiftINode(in(1), phase->intcon(log2_uint(temp))));
      res = new (phase->C) SubINode(n1, in(1));
    } else {
      return MulNode::Ideal(phase, can_reshape);
    }
  }

  if (sign_flip) {             // Need to negate result?
    res = phase->transform(res);// Transform, before making the zero con
    res = new (phase->C) SubINode(phase->intcon(0),res);
  }

  return res;                   // Return final result
}

//------------------------------mul_ring---------------------------------------
// Compute the product type of two integer ranges into this node.
const Type *MulINode::mul_ring(const Type *t0, const Type *t1) const {
  const TypeInt *r0 = t0->is_int(); // Handy access
  const TypeInt *r1 = t1->is_int();

  // Fetch endpoints of all ranges
  int32 lo0 = r0->_lo;
  double a = (double)lo0;
  int32 hi0 = r0->_hi;
  double b = (double)hi0;
  int32 lo1 = r1->_lo;
  double c = (double)lo1;
  int32 hi1 = r1->_hi;
  double d = (double)hi1;

  // Compute all endpoints & check for overflow
  int32 A = java_multiply(lo0, lo1);
  if( (double)A != a*c ) return TypeInt::INT; // Overflow?
  int32 B = java_multiply(lo0, hi1);
  if( (double)B != a*d ) return TypeInt::INT; // Overflow?
  int32 C = java_multiply(hi0, lo1);
  if( (double)C != b*c ) return TypeInt::INT; // Overflow?
  int32 D = java_multiply(hi0, hi1);
  if( (double)D != b*d ) return TypeInt::INT; // Overflow?

  if( A < B ) { lo0 = A; hi0 = B; } // Sort range endpoints
  else { lo0 = B; hi0 = A; }
  if( C < D ) {
    if( C < lo0 ) lo0 = C;
    if( D > hi0 ) hi0 = D;
  } else {
    if( D < lo0 ) lo0 = D;
    if( C > hi0 ) hi0 = C;
  }
  return TypeInt::make(lo0, hi0, MAX2(r0->_widen,r1->_widen));
}


//=============================================================================
//------------------------------Ideal------------------------------------------
// Check for power-of-2 multiply, then try the regular MulNode::Ideal
Node *MulLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Swap constant to right
  jlong con;
  if ((con = in(1)->find_long_con(0)) != 0) {
    swap_edges(1, 2);
    // Finish rest of method to use info in 'con'
  } else if ((con = in(2)->find_long_con(0)) == 0) {
    return MulNode::Ideal(phase, can_reshape);
  }

  // Now we have a constant Node on the right and the constant in con
  if (con == CONST64(0)) return NULL;  // By zero is handled by Value call
  if (con == CONST64(1)) return NULL;  // By one  is handled by Identity call

  // Check for negative constant; if so negate the final result
  bool sign_flip = false;
  julong abs_con = uabs(con);
  if (abs_con != (julong)con) {
    sign_flip = true;
  }

  // Get low bit; check for being the only bit
  Node *res = NULL;
  julong bit1 = abs_con & (0-abs_con);      // Extract low bit
  if (bit1 == abs_con) {           // Found a power of 2?
    res = new (phase->C) LShiftLNode(in(1), phase->intcon(log2_long(bit1)));
  } else {

    // Check for constant with 2 bits set
    julong bit2 = abs_con-bit1;
    bit2 = bit2 & (0-bit2);          // Extract 2nd bit
    if (bit2 + bit1 == abs_con) {    // Found all bits in con?
      Node *n1 = phase->transform(new (phase->C) LShiftLNode(in(1), phase->intcon(log2_long(bit1))));
      Node *n2 = phase->transform(new (phase->C) LShiftLNode(in(1), phase->intcon(log2_long(bit2))));
      res = new (phase->C) AddLNode(n2, n1);

    } else if (is_power_of_2_long(abs_con+1)) {
      // Sleezy: power-of-2 -1.  Next time be generic.
      julong temp = abs_con + 1;
      Node *n1 = phase->transform( new (phase->C) LShiftLNode(in(1), phase->intcon(log2_long(temp))));
      res = new (phase->C) SubLNode(n1, in(1));
    } else {
      return MulNode::Ideal(phase, can_reshape);
    }
  }

  if (sign_flip) {             // Need to negate result?
    res = phase->transform(res);// Transform, before making the zero con
    res = new (phase->C) SubLNode(phase->longcon(0),res);
  }

  return res;                   // Return final result
}

//------------------------------mul_ring---------------------------------------
// Compute the product type of two integer ranges into this node.
const Type *MulLNode::mul_ring(const Type *t0, const Type *t1) const {
  const TypeLong *r0 = t0->is_long(); // Handy access
  const TypeLong *r1 = t1->is_long();

  // Fetch endpoints of all ranges
  jlong lo0 = r0->_lo;
  double a = (double)lo0;
  jlong hi0 = r0->_hi;
  double b = (double)hi0;
  jlong lo1 = r1->_lo;
  double c = (double)lo1;
  jlong hi1 = r1->_hi;
  double d = (double)hi1;

  // Compute all endpoints & check for overflow
  jlong A = java_multiply(lo0, lo1);
  if( (double)A != a*c ) return TypeLong::LONG; // Overflow?
  jlong B = java_multiply(lo0, hi1);
  if( (double)B != a*d ) return TypeLong::LONG; // Overflow?
  jlong C = java_multiply(hi0, lo1);
  if( (double)C != b*c ) return TypeLong::LONG; // Overflow?
  jlong D = java_multiply(hi0, hi1);
  if( (double)D != b*d ) return TypeLong::LONG; // Overflow?

  if( A < B ) { lo0 = A; hi0 = B; } // Sort range endpoints
  else { lo0 = B; hi0 = A; }
  if( C < D ) {
    if( C < lo0 ) lo0 = C;
    if( D > hi0 ) hi0 = D;
  } else {
    if( D < lo0 ) lo0 = D;
    if( C > hi0 ) hi0 = C;
  }
  return TypeLong::make(lo0, hi0, MAX2(r0->_widen,r1->_widen));
}

//=============================================================================
//------------------------------mul_ring---------------------------------------
// Compute the product type of two double ranges into this node.
const Type *MulFNode::mul_ring(const Type *t0, const Type *t1) const {
  if( t0 == Type::FLOAT || t1 == Type::FLOAT ) return Type::FLOAT;
  return TypeF::make( t0->getf() * t1->getf() );
}

//=============================================================================
//------------------------------mul_ring---------------------------------------
// Compute the product type of two double ranges into this node.
const Type *MulDNode::mul_ring(const Type *t0, const Type *t1) const {
  if( t0 == Type::DOUBLE || t1 == Type::DOUBLE ) return Type::DOUBLE;
  // We must be multiplying 2 double constants.
  return TypeD::make( t0->getd() * t1->getd() );
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *MulHiLNode::Value( PhaseTransform *phase ) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type *bot = bottom_type();
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  // It is not worth trying to constant fold this stuff!
  return TypeLong::LONG;
}

//=============================================================================
//------------------------------mul_ring---------------------------------------
// Supplied function returns the product of the inputs IN THE CURRENT RING.
// For the logical operations the ring's MUL is really a logical AND function.
// This also type-checks the inputs for sanity.  Guaranteed never to
// be passed a TOP or BOTTOM type, these are filtered out by pre-check.
const Type *AndINode::mul_ring( const Type *t0, const Type *t1 ) const {
  const TypeInt *r0 = t0->is_int(); // Handy access
  const TypeInt *r1 = t1->is_int();
  int widen = MAX2(r0->_widen,r1->_widen);

  // If either input is a constant, might be able to trim cases
  if( !r0->is_con() && !r1->is_con() )
    return TypeInt::INT;        // No constants to be had

  // Both constants?  Return bits
  if( r0->is_con() && r1->is_con() )
    return TypeInt::make( r0->get_con() & r1->get_con() );

  if( r0->is_con() && r0->get_con() > 0 )
    return TypeInt::make(0, r0->get_con(), widen);

  if( r1->is_con() && r1->get_con() > 0 )
    return TypeInt::make(0, r1->get_con(), widen);

  if( r0 == TypeInt::BOOL || r1 == TypeInt::BOOL ) {
    return TypeInt::BOOL;
  }

  return TypeInt::INT;          // No constants to be had
}

//------------------------------Identity---------------------------------------
// Masking off the high bits of an unsigned load is not required
Node *AndINode::Identity( PhaseTransform *phase ) {

  // x & x => x
  if (phase->eqv(in(1), in(2))) return in(1);

  Node* in1 = in(1);
  uint op = in1->Opcode();
  const TypeInt* t2 = phase->type(in(2))->isa_int();
  if (t2 && t2->is_con()) {
    int con = t2->get_con();
    // Masking off high bits which are always zero is useless.
    const TypeInt* t1 = phase->type( in(1) )->isa_int();
    if (t1 != NULL && t1->_lo >= 0) {
      jint t1_support = right_n_bits(1 + log2_jint(t1->_hi));
      if ((t1_support & con) == t1_support)
        return in1;
    }
    // Masking off the high bits of a unsigned-shift-right is not
    // needed either.
    if (op == Op_URShiftI) {
      const TypeInt* t12 = phase->type(in1->in(2))->isa_int();
      if (t12 && t12->is_con()) {  // Shift is by a constant
        int shift = t12->get_con();
        shift &= BitsPerJavaInteger - 1;  // semantics of Java shifts
        int mask = max_juint >> shift;
        if ((mask & con) == mask)  // If AND is useless, skip it
          return in1;
      }
    }
  }
  return MulNode::Identity(phase);
}

//------------------------------Ideal------------------------------------------
Node *AndINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Special case constant AND mask
  const TypeInt *t2 = phase->type( in(2) )->isa_int();
  if( !t2 || !t2->is_con() ) return MulNode::Ideal(phase, can_reshape);
  const int mask = t2->get_con();
  Node *load = in(1);
  uint lop = load->Opcode();

  // Masking bits off of a Character?  Hi bits are already zero.
  if( lop == Op_LoadUS &&
      (mask & 0xFFFF0000) )     // Can we make a smaller mask?
    return new (phase->C) AndINode(load,phase->intcon(mask&0xFFFF));

  // Masking bits off of a Short?  Loading a Character does some masking
  if (can_reshape &&
      load->outcnt() == 1 && load->unique_out() == this) {
    if (lop == Op_LoadS && (mask & 0xFFFF0000) == 0 ) {
      Node *ldus = new (phase->C) LoadUSNode(load->in(MemNode::Control),
                                             load->in(MemNode::Memory),
                                             load->in(MemNode::Address),
                                             load->adr_type(),
                                             TypeInt::CHAR, MemNode::unordered);
      ldus = phase->transform(ldus);
      return new (phase->C) AndINode(ldus, phase->intcon(mask & 0xFFFF));
    }

    // Masking sign bits off of a Byte?  Do an unsigned byte load plus
    // an and.
    if (lop == Op_LoadB && (mask & 0xFFFFFF00) == 0) {
      Node* ldub = new (phase->C) LoadUBNode(load->in(MemNode::Control),
                                             load->in(MemNode::Memory),
                                             load->in(MemNode::Address),
                                             load->adr_type(),
                                             TypeInt::UBYTE, MemNode::unordered);
      ldub = phase->transform(ldub);
      return new (phase->C) AndINode(ldub, phase->intcon(mask));
    }
  }

  // Masking off sign bits?  Dont make them!
  if( lop == Op_RShiftI ) {
    const TypeInt *t12 = phase->type(load->in(2))->isa_int();
    if( t12 && t12->is_con() ) { // Shift is by a constant
      int shift = t12->get_con();
      shift &= BitsPerJavaInteger-1;  // semantics of Java shifts
      const int sign_bits_mask = ~right_n_bits(BitsPerJavaInteger - shift);
      // If the AND'ing of the 2 masks has no bits, then only original shifted
      // bits survive.  NO sign-extension bits survive the maskings.
      if( (sign_bits_mask & mask) == 0 ) {
        // Use zero-fill shift instead
        Node *zshift = phase->transform(new (phase->C) URShiftINode(load->in(1),load->in(2)));
        return new (phase->C) AndINode( zshift, in(2) );
      }
    }
  }

  // Check for 'negate/and-1', a pattern emitted when someone asks for
  // 'mod 2'.  Negate leaves the low order bit unchanged (think: complement
  // plus 1) and the mask is of the low order bit.  Skip the negate.
  if( lop == Op_SubI && mask == 1 && load->in(1) &&
      phase->type(load->in(1)) == TypeInt::ZERO )
    return new (phase->C) AndINode( load->in(2), in(2) );

  return MulNode::Ideal(phase, can_reshape);
}

//=============================================================================
//------------------------------mul_ring---------------------------------------
// Supplied function returns the product of the inputs IN THE CURRENT RING.
// For the logical operations the ring's MUL is really a logical AND function.
// This also type-checks the inputs for sanity.  Guaranteed never to
// be passed a TOP or BOTTOM type, these are filtered out by pre-check.
const Type *AndLNode::mul_ring( const Type *t0, const Type *t1 ) const {
  const TypeLong *r0 = t0->is_long(); // Handy access
  const TypeLong *r1 = t1->is_long();
  int widen = MAX2(r0->_widen,r1->_widen);

  // If either input is a constant, might be able to trim cases
  if( !r0->is_con() && !r1->is_con() )
    return TypeLong::LONG;      // No constants to be had

  // Both constants?  Return bits
  if( r0->is_con() && r1->is_con() )
    return TypeLong::make( r0->get_con() & r1->get_con() );

  if( r0->is_con() && r0->get_con() > 0 )
    return TypeLong::make(CONST64(0), r0->get_con(), widen);

  if( r1->is_con() && r1->get_con() > 0 )
    return TypeLong::make(CONST64(0), r1->get_con(), widen);

  return TypeLong::LONG;        // No constants to be had
}

//------------------------------Identity---------------------------------------
// Masking off the high bits of an unsigned load is not required
Node *AndLNode::Identity( PhaseTransform *phase ) {

  // x & x => x
  if (phase->eqv(in(1), in(2))) return in(1);

  Node *usr = in(1);
  const TypeLong *t2 = phase->type( in(2) )->isa_long();
  if( t2 && t2->is_con() ) {
    jlong con = t2->get_con();
    // Masking off high bits which are always zero is useless.
    const TypeLong* t1 = phase->type( in(1) )->isa_long();
    if (t1 != NULL && t1->_lo >= 0) {
      int bit_count = log2_long(t1->_hi) + 1;
      jlong t1_support = jlong(max_julong >> (BitsPerJavaLong - bit_count));
      if ((t1_support & con) == t1_support)
        return usr;
    }
    uint lop = usr->Opcode();
    // Masking off the high bits of a unsigned-shift-right is not
    // needed either.
    if( lop == Op_URShiftL ) {
      const TypeInt *t12 = phase->type( usr->in(2) )->isa_int();
      if( t12 && t12->is_con() ) {  // Shift is by a constant
        int shift = t12->get_con();
        shift &= BitsPerJavaLong - 1;  // semantics of Java shifts
        jlong mask = max_julong >> shift;
        if( (mask&con) == mask )  // If AND is useless, skip it
          return usr;
      }
    }
  }
  return MulNode::Identity(phase);
}

//------------------------------Ideal------------------------------------------
Node *AndLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Special case constant AND mask
  const TypeLong *t2 = phase->type( in(2) )->isa_long();
  if( !t2 || !t2->is_con() ) return MulNode::Ideal(phase, can_reshape);
  const jlong mask = t2->get_con();

  Node* in1 = in(1);
  uint op = in1->Opcode();

  // Are we masking a long that was converted from an int with a mask
  // that fits in 32-bits?  Commute them and use an AndINode.  Don't
  // convert masks which would cause a sign extension of the integer
  // value.  This check includes UI2L masks (0x00000000FFFFFFFF) which
  // would be optimized away later in Identity.
  if (op == Op_ConvI2L && (mask & CONST64(0xFFFFFFFF80000000)) == 0) {
    Node* andi = new (phase->C) AndINode(in1->in(1), phase->intcon(mask));
    andi = phase->transform(andi);
    return new (phase->C) ConvI2LNode(andi);
  }

  // Masking off sign bits?  Dont make them!
  if (op == Op_RShiftL) {
    const TypeInt* t12 = phase->type(in1->in(2))->isa_int();
    if( t12 && t12->is_con() ) { // Shift is by a constant
      int shift = t12->get_con();
      shift &= BitsPerJavaLong - 1;  // semantics of Java shifts
      const jlong sign_bits_mask = ~(((jlong)CONST64(1) << (jlong)(BitsPerJavaLong - shift)) -1);
      // If the AND'ing of the 2 masks has no bits, then only original shifted
      // bits survive.  NO sign-extension bits survive the maskings.
      if( (sign_bits_mask & mask) == 0 ) {
        // Use zero-fill shift instead
        Node *zshift = phase->transform(new (phase->C) URShiftLNode(in1->in(1), in1->in(2)));
        return new (phase->C) AndLNode(zshift, in(2));
      }
    }
  }

  return MulNode::Ideal(phase, can_reshape);
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node *LShiftINode::Identity( PhaseTransform *phase ) {
  const TypeInt *ti = phase->type( in(2) )->isa_int();  // shift count is an int
  return ( ti && ti->is_con() && ( ti->get_con() & ( BitsPerInt - 1 ) ) == 0 ) ? in(1) : this;
}

//------------------------------Ideal------------------------------------------
// If the right input is a constant, and the left input is an add of a
// constant, flatten the tree: (X+con1)<<con0 ==> X<<con0 + con1<<con0
Node *LShiftINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const Type *t  = phase->type( in(2) );
  if( t == Type::TOP ) return NULL;       // Right input is dead
  const TypeInt *t2 = t->isa_int();
  if( !t2 || !t2->is_con() ) return NULL; // Right input is a constant
  const int con = t2->get_con() & ( BitsPerInt - 1 );  // masked shift count

  if ( con == 0 )  return NULL; // let Identity() handle 0 shift count

  // Left input is an add of a constant?
  Node *add1 = in(1);
  int add1_op = add1->Opcode();
  if( add1_op == Op_AddI ) {    // Left input is an add?
    assert( add1 != add1->in(1), "dead loop in LShiftINode::Ideal" );
    const TypeInt *t12 = phase->type(add1->in(2))->isa_int();
    if( t12 && t12->is_con() ){ // Left input is an add of a con?
      // Transform is legal, but check for profit.  Avoid breaking 'i2s'
      // and 'i2b' patterns which typically fold into 'StoreC/StoreB'.
      if( con < 16 ) {
        // Compute X << con0
        Node *lsh = phase->transform( new (phase->C) LShiftINode( add1->in(1), in(2) ) );
        // Compute X<<con0 + (con1<<con0)
        return new (phase->C) AddINode( lsh, phase->intcon(t12->get_con() << con));
      }
    }
  }

  // Check for "(x>>c0)<<c0" which just masks off low bits
  if( (add1_op == Op_RShiftI || add1_op == Op_URShiftI ) &&
      add1->in(2) == in(2) )
    // Convert to "(x & -(1<<c0))"
    return new (phase->C) AndINode(add1->in(1),phase->intcon( -(1<<con)));

  // Check for "((x>>c0) & Y)<<c0" which just masks off more low bits
  if( add1_op == Op_AndI ) {
    Node *add2 = add1->in(1);
    int add2_op = add2->Opcode();
    if( (add2_op == Op_RShiftI || add2_op == Op_URShiftI ) &&
        add2->in(2) == in(2) ) {
      // Convert to "(x & (Y<<c0))"
      Node *y_sh = phase->transform( new (phase->C) LShiftINode( add1->in(2), in(2) ) );
      return new (phase->C) AndINode( add2->in(1), y_sh );
    }
  }

  // Check for ((x & ((1<<(32-c0))-1)) << c0) which ANDs off high bits
  // before shifting them away.
  const jint bits_mask = right_n_bits(BitsPerJavaInteger-con);
  if( add1_op == Op_AndI &&
      phase->type(add1->in(2)) == TypeInt::make( bits_mask ) )
    return new (phase->C) LShiftINode( add1->in(1), in(2) );

  return NULL;
}

//------------------------------Value------------------------------------------
// A LShiftINode shifts its input2 left by input1 amount.
const Type *LShiftINode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  // Either input is TOP ==> the result is TOP
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Left input is ZERO ==> the result is ZERO.
  if( t1 == TypeInt::ZERO ) return TypeInt::ZERO;
  // Shift by zero does nothing
  if( t2 == TypeInt::ZERO ) return t1;

  // Either input is BOTTOM ==> the result is BOTTOM
  if( (t1 == TypeInt::INT) || (t2 == TypeInt::INT) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return TypeInt::INT;

  const TypeInt *r1 = t1->is_int(); // Handy access
  const TypeInt *r2 = t2->is_int(); // Handy access

  if (!r2->is_con())
    return TypeInt::INT;

  uint shift = r2->get_con();
  shift &= BitsPerJavaInteger-1;  // semantics of Java shifts
  // Shift by a multiple of 32 does nothing:
  if (shift == 0)  return t1;

  // If the shift is a constant, shift the bounds of the type,
  // unless this could lead to an overflow.
  if (!r1->is_con()) {
    jint lo = r1->_lo, hi = r1->_hi;
    if (((lo << shift) >> shift) == lo &&
        ((hi << shift) >> shift) == hi) {
      // No overflow.  The range shifts up cleanly.
      return TypeInt::make((jint)lo << (jint)shift,
                           (jint)hi << (jint)shift,
                           MAX2(r1->_widen,r2->_widen));
    }
    return TypeInt::INT;
  }

  return TypeInt::make( (jint)r1->get_con() << (jint)shift );
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node *LShiftLNode::Identity( PhaseTransform *phase ) {
  const TypeInt *ti = phase->type( in(2) )->isa_int(); // shift count is an int
  return ( ti && ti->is_con() && ( ti->get_con() & ( BitsPerLong - 1 ) ) == 0 ) ? in(1) : this;
}

//------------------------------Ideal------------------------------------------
// If the right input is a constant, and the left input is an add of a
// constant, flatten the tree: (X+con1)<<con0 ==> X<<con0 + con1<<con0
Node *LShiftLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const Type *t  = phase->type( in(2) );
  if( t == Type::TOP ) return NULL;       // Right input is dead
  const TypeInt *t2 = t->isa_int();
  if( !t2 || !t2->is_con() ) return NULL; // Right input is a constant
  const int con = t2->get_con() & ( BitsPerLong - 1 );  // masked shift count

  if ( con == 0 ) return NULL;  // let Identity() handle 0 shift count

  // Left input is an add of a constant?
  Node *add1 = in(1);
  int add1_op = add1->Opcode();
  if( add1_op == Op_AddL ) {    // Left input is an add?
    // Avoid dead data cycles from dead loops
    assert( add1 != add1->in(1), "dead loop in LShiftLNode::Ideal" );
    const TypeLong *t12 = phase->type(add1->in(2))->isa_long();
    if( t12 && t12->is_con() ){ // Left input is an add of a con?
      // Compute X << con0
      Node *lsh = phase->transform( new (phase->C) LShiftLNode( add1->in(1), in(2) ) );
      // Compute X<<con0 + (con1<<con0)
      return new (phase->C) AddLNode( lsh, phase->longcon(t12->get_con() << con));
    }
  }

  // Check for "(x>>c0)<<c0" which just masks off low bits
  if( (add1_op == Op_RShiftL || add1_op == Op_URShiftL ) &&
      add1->in(2) == in(2) )
    // Convert to "(x & -(1<<c0))"
    return new (phase->C) AndLNode(add1->in(1),phase->longcon( -(CONST64(1)<<con)));

  // Check for "((x>>c0) & Y)<<c0" which just masks off more low bits
  if( add1_op == Op_AndL ) {
    Node *add2 = add1->in(1);
    int add2_op = add2->Opcode();
    if( (add2_op == Op_RShiftL || add2_op == Op_URShiftL ) &&
        add2->in(2) == in(2) ) {
      // Convert to "(x & (Y<<c0))"
      Node *y_sh = phase->transform( new (phase->C) LShiftLNode( add1->in(2), in(2) ) );
      return new (phase->C) AndLNode( add2->in(1), y_sh );
    }
  }

  // Check for ((x & ((CONST64(1)<<(64-c0))-1)) << c0) which ANDs off high bits
  // before shifting them away.
  const jlong bits_mask = jlong(max_julong >> con);
  if( add1_op == Op_AndL &&
      phase->type(add1->in(2)) == TypeLong::make( bits_mask ) )
    return new (phase->C) LShiftLNode( add1->in(1), in(2) );

  return NULL;
}

//------------------------------Value------------------------------------------
// A LShiftLNode shifts its input2 left by input1 amount.
const Type *LShiftLNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  // Either input is TOP ==> the result is TOP
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Left input is ZERO ==> the result is ZERO.
  if( t1 == TypeLong::ZERO ) return TypeLong::ZERO;
  // Shift by zero does nothing
  if( t2 == TypeInt::ZERO ) return t1;

  // Either input is BOTTOM ==> the result is BOTTOM
  if( (t1 == TypeLong::LONG) || (t2 == TypeInt::INT) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return TypeLong::LONG;

  const TypeLong *r1 = t1->is_long(); // Handy access
  const TypeInt  *r2 = t2->is_int();  // Handy access

  if (!r2->is_con())
    return TypeLong::LONG;

  uint shift = r2->get_con();
  shift &= BitsPerJavaLong - 1;  // semantics of Java shifts
  // Shift by a multiple of 64 does nothing:
  if (shift == 0)  return t1;

  // If the shift is a constant, shift the bounds of the type,
  // unless this could lead to an overflow.
  if (!r1->is_con()) {
    jlong lo = r1->_lo, hi = r1->_hi;
    if (((lo << shift) >> shift) == lo &&
        ((hi << shift) >> shift) == hi) {
      // No overflow.  The range shifts up cleanly.
      return TypeLong::make((jlong)lo << (jint)shift,
                            (jlong)hi << (jint)shift,
                            MAX2(r1->_widen,r2->_widen));
    }
    return TypeLong::LONG;
  }

  return TypeLong::make( (jlong)r1->get_con() << (jint)shift );
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node *RShiftINode::Identity( PhaseTransform *phase ) {
  const TypeInt *t2 = phase->type(in(2))->isa_int();
  if( !t2 ) return this;
  if ( t2->is_con() && ( t2->get_con() & ( BitsPerInt - 1 ) ) == 0 )
    return in(1);

  // Check for useless sign-masking
  if( in(1)->Opcode() == Op_LShiftI &&
      in(1)->req() == 3 &&
      in(1)->in(2) == in(2) &&
      t2->is_con() ) {
    uint shift = t2->get_con();
    shift &= BitsPerJavaInteger-1; // semantics of Java shifts
    // Compute masks for which this shifting doesn't change
    int lo = (-1 << (BitsPerJavaInteger - shift-1)); // FFFF8000
    int hi = ~lo;               // 00007FFF
    const TypeInt *t11 = phase->type(in(1)->in(1))->isa_int();
    if( !t11 ) return this;
    // Does actual value fit inside of mask?
    if( lo <= t11->_lo && t11->_hi <= hi )
      return in(1)->in(1);      // Then shifting is a nop
  }

  return this;
}

//------------------------------Ideal------------------------------------------
Node *RShiftINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Inputs may be TOP if they are dead.
  const TypeInt *t1 = phase->type( in(1) )->isa_int();
  if( !t1 ) return NULL;        // Left input is an integer
  const TypeInt *t2 = phase->type( in(2) )->isa_int();
  if( !t2 || !t2->is_con() ) return NULL; // Right input is a constant
  const TypeInt *t3;  // type of in(1).in(2)
  int shift = t2->get_con();
  shift &= BitsPerJavaInteger-1;  // semantics of Java shifts

  if ( shift == 0 ) return NULL;  // let Identity() handle 0 shift count

  // Check for (x & 0xFF000000) >> 24, whose mask can be made smaller.
  // Such expressions arise normally from shift chains like (byte)(x >> 24).
  const Node *mask = in(1);
  if( mask->Opcode() == Op_AndI &&
      (t3 = phase->type(mask->in(2))->isa_int()) &&
      t3->is_con() ) {
    Node *x = mask->in(1);
    jint maskbits = t3->get_con();
    // Convert to "(x >> shift) & (mask >> shift)"
    Node *shr_nomask = phase->transform( new (phase->C) RShiftINode(mask->in(1), in(2)) );
    return new (phase->C) AndINode(shr_nomask, phase->intcon( maskbits >> shift));
  }

  // Check for "(short[i] <<16)>>16" which simply sign-extends
  const Node *shl = in(1);
  if( shl->Opcode() != Op_LShiftI ) return NULL;

  if( shift == 16 &&
      (t3 = phase->type(shl->in(2))->isa_int()) &&
      t3->is_con(16) ) {
    Node *ld = shl->in(1);
    if( ld->Opcode() == Op_LoadS ) {
      // Sign extension is just useless here.  Return a RShiftI of zero instead
      // returning 'ld' directly.  We cannot return an old Node directly as
      // that is the job of 'Identity' calls and Identity calls only work on
      // direct inputs ('ld' is an extra Node removed from 'this').  The
      // combined optimization requires Identity only return direct inputs.
      set_req(1, ld);
      set_req(2, phase->intcon(0));
      return this;
    }
    else if( can_reshape &&
             ld->Opcode() == Op_LoadUS &&
             ld->outcnt() == 1 && ld->unique_out() == shl)
      // Replace zero-extension-load with sign-extension-load
      return new (phase->C) LoadSNode( ld->in(MemNode::Control),
                                       ld->in(MemNode::Memory),
                                       ld->in(MemNode::Address),
                                       ld->adr_type(), TypeInt::SHORT,
                                       MemNode::unordered);
  }

  // Check for "(byte[i] <<24)>>24" which simply sign-extends
  if( shift == 24 &&
      (t3 = phase->type(shl->in(2))->isa_int()) &&
      t3->is_con(24) ) {
    Node *ld = shl->in(1);
    if( ld->Opcode() == Op_LoadB ) {
      // Sign extension is just useless here
      set_req(1, ld);
      set_req(2, phase->intcon(0));
      return this;
    }
  }

  return NULL;
}

//------------------------------Value------------------------------------------
// A RShiftINode shifts its input2 right by input1 amount.
const Type *RShiftINode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  // Either input is TOP ==> the result is TOP
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Left input is ZERO ==> the result is ZERO.
  if( t1 == TypeInt::ZERO ) return TypeInt::ZERO;
  // Shift by zero does nothing
  if( t2 == TypeInt::ZERO ) return t1;

  // Either input is BOTTOM ==> the result is BOTTOM
  if (t1 == Type::BOTTOM || t2 == Type::BOTTOM)
    return TypeInt::INT;

  if (t2 == TypeInt::INT)
    return TypeInt::INT;

  const TypeInt *r1 = t1->is_int(); // Handy access
  const TypeInt *r2 = t2->is_int(); // Handy access

  // If the shift is a constant, just shift the bounds of the type.
  // For example, if the shift is 31, we just propagate sign bits.
  if (r2->is_con()) {
    uint shift = r2->get_con();
    shift &= BitsPerJavaInteger-1;  // semantics of Java shifts
    // Shift by a multiple of 32 does nothing:
    if (shift == 0)  return t1;
    // Calculate reasonably aggressive bounds for the result.
    // This is necessary if we are to correctly type things
    // like (x<<24>>24) == ((byte)x).
    jint lo = (jint)r1->_lo >> (jint)shift;
    jint hi = (jint)r1->_hi >> (jint)shift;
    assert(lo <= hi, "must have valid bounds");
    const TypeInt* ti = TypeInt::make(lo, hi, MAX2(r1->_widen,r2->_widen));
#ifdef ASSERT
    // Make sure we get the sign-capture idiom correct.
    if (shift == BitsPerJavaInteger-1) {
      if (r1->_lo >= 0) assert(ti == TypeInt::ZERO,    ">>31 of + is  0");
      if (r1->_hi <  0) assert(ti == TypeInt::MINUS_1, ">>31 of - is -1");
    }
#endif
    return ti;
  }

  if( !r1->is_con() || !r2->is_con() )
    return TypeInt::INT;

  // Signed shift right
  return TypeInt::make( r1->get_con() >> (r2->get_con()&31) );
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node *RShiftLNode::Identity( PhaseTransform *phase ) {
  const TypeInt *ti = phase->type( in(2) )->isa_int(); // shift count is an int
  return ( ti && ti->is_con() && ( ti->get_con() & ( BitsPerLong - 1 ) ) == 0 ) ? in(1) : this;
}

//------------------------------Value------------------------------------------
// A RShiftLNode shifts its input2 right by input1 amount.
const Type *RShiftLNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  // Either input is TOP ==> the result is TOP
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Left input is ZERO ==> the result is ZERO.
  if( t1 == TypeLong::ZERO ) return TypeLong::ZERO;
  // Shift by zero does nothing
  if( t2 == TypeInt::ZERO ) return t1;

  // Either input is BOTTOM ==> the result is BOTTOM
  if (t1 == Type::BOTTOM || t2 == Type::BOTTOM)
    return TypeLong::LONG;

  if (t2 == TypeInt::INT)
    return TypeLong::LONG;

  const TypeLong *r1 = t1->is_long(); // Handy access
  const TypeInt  *r2 = t2->is_int (); // Handy access

  // If the shift is a constant, just shift the bounds of the type.
  // For example, if the shift is 63, we just propagate sign bits.
  if (r2->is_con()) {
    uint shift = r2->get_con();
    shift &= (2*BitsPerJavaInteger)-1;  // semantics of Java shifts
    // Shift by a multiple of 64 does nothing:
    if (shift == 0)  return t1;
    // Calculate reasonably aggressive bounds for the result.
    // This is necessary if we are to correctly type things
    // like (x<<24>>24) == ((byte)x).
    jlong lo = (jlong)r1->_lo >> (jlong)shift;
    jlong hi = (jlong)r1->_hi >> (jlong)shift;
    assert(lo <= hi, "must have valid bounds");
    const TypeLong* tl = TypeLong::make(lo, hi, MAX2(r1->_widen,r2->_widen));
    #ifdef ASSERT
    // Make sure we get the sign-capture idiom correct.
    if (shift == (2*BitsPerJavaInteger)-1) {
      if (r1->_lo >= 0) assert(tl == TypeLong::ZERO,    ">>63 of + is 0");
      if (r1->_hi < 0)  assert(tl == TypeLong::MINUS_1, ">>63 of - is -1");
    }
    #endif
    return tl;
  }

  return TypeLong::LONG;                // Give up
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node *URShiftINode::Identity( PhaseTransform *phase ) {
  const TypeInt *ti = phase->type( in(2) )->isa_int();
  if ( ti && ti->is_con() && ( ti->get_con() & ( BitsPerInt - 1 ) ) == 0 ) return in(1);

  // Check for "((x << LogBytesPerWord) + (wordSize-1)) >> LogBytesPerWord" which is just "x".
  // Happens during new-array length computation.
  // Safe if 'x' is in the range [0..(max_int>>LogBytesPerWord)]
  Node *add = in(1);
  if( add->Opcode() == Op_AddI ) {
    const TypeInt *t2  = phase->type(add->in(2))->isa_int();
    if( t2 && t2->is_con(wordSize - 1) &&
        add->in(1)->Opcode() == Op_LShiftI ) {
      // Check that shift_counts are LogBytesPerWord
      Node          *lshift_count   = add->in(1)->in(2);
      const TypeInt *t_lshift_count = phase->type(lshift_count)->isa_int();
      if( t_lshift_count && t_lshift_count->is_con(LogBytesPerWord) &&
          t_lshift_count == phase->type(in(2)) ) {
        Node          *x   = add->in(1)->in(1);
        const TypeInt *t_x = phase->type(x)->isa_int();
        if( t_x != NULL && 0 <= t_x->_lo && t_x->_hi <= (max_jint>>LogBytesPerWord) ) {
          return x;
        }
      }
    }
  }

  return (phase->type(in(2))->higher_equal(TypeInt::ZERO)) ? in(1) : this;
}

//------------------------------Ideal------------------------------------------
Node *URShiftINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const TypeInt *t2 = phase->type( in(2) )->isa_int();
  if( !t2 || !t2->is_con() ) return NULL; // Right input is a constant
  const int con = t2->get_con() & 31; // Shift count is always masked
  if ( con == 0 ) return NULL;  // let Identity() handle a 0 shift count
  // We'll be wanting the right-shift amount as a mask of that many bits
  const int mask = right_n_bits(BitsPerJavaInteger - con);

  int in1_op = in(1)->Opcode();

  // Check for ((x>>>a)>>>b) and replace with (x>>>(a+b)) when a+b < 32
  if( in1_op == Op_URShiftI ) {
    const TypeInt *t12 = phase->type( in(1)->in(2) )->isa_int();
    if( t12 && t12->is_con() ) { // Right input is a constant
      assert( in(1) != in(1)->in(1), "dead loop in URShiftINode::Ideal" );
      const int con2 = t12->get_con() & 31; // Shift count is always masked
      const int con3 = con+con2;
      if( con3 < 32 )           // Only merge shifts if total is < 32
        return new (phase->C) URShiftINode( in(1)->in(1), phase->intcon(con3) );
    }
  }

  // Check for ((x << z) + Y) >>> z.  Replace with x + con>>>z
  // The idiom for rounding to a power of 2 is "(Q+(2^z-1)) >>> z".
  // If Q is "X << z" the rounding is useless.  Look for patterns like
  // ((X<<Z) + Y) >>> Z  and replace with (X + Y>>>Z) & Z-mask.
  Node *add = in(1);
  if( in1_op == Op_AddI ) {
    Node *lshl = add->in(1);
    if( lshl->Opcode() == Op_LShiftI &&
        phase->type(lshl->in(2)) == t2 ) {
      Node *y_z = phase->transform( new (phase->C) URShiftINode(add->in(2),in(2)) );
      Node *sum = phase->transform( new (phase->C) AddINode( lshl->in(1), y_z ) );
      return new (phase->C) AndINode( sum, phase->intcon(mask) );
    }
  }

  // Check for (x & mask) >>> z.  Replace with (x >>> z) & (mask >>> z)
  // This shortens the mask.  Also, if we are extracting a high byte and
  // storing it to a buffer, the mask will be removed completely.
  Node *andi = in(1);
  if( in1_op == Op_AndI ) {
    const TypeInt *t3 = phase->type( andi->in(2) )->isa_int();
    if( t3 && t3->is_con() ) { // Right input is a constant
      jint mask2 = t3->get_con();
      mask2 >>= con;  // *signed* shift downward (high-order zeroes do not help)
      Node *newshr = phase->transform( new (phase->C) URShiftINode(andi->in(1), in(2)) );
      return new (phase->C) AndINode(newshr, phase->intcon(mask2));
      // The negative values are easier to materialize than positive ones.
      // A typical case from address arithmetic is ((x & ~15) >> 4).
      // It's better to change that to ((x >> 4) & ~0) versus
      // ((x >> 4) & 0x0FFFFFFF).  The difference is greatest in LP64.
    }
  }

  // Check for "(X << z ) >>> z" which simply zero-extends
  Node *shl = in(1);
  if( in1_op == Op_LShiftI &&
      phase->type(shl->in(2)) == t2 )
    return new (phase->C) AndINode( shl->in(1), phase->intcon(mask) );

  return NULL;
}

//------------------------------Value------------------------------------------
// A URShiftINode shifts its input2 right by input1 amount.
const Type *URShiftINode::Value( PhaseTransform *phase ) const {
  // (This is a near clone of RShiftINode::Value.)
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  // Either input is TOP ==> the result is TOP
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Left input is ZERO ==> the result is ZERO.
  if( t1 == TypeInt::ZERO ) return TypeInt::ZERO;
  // Shift by zero does nothing
  if( t2 == TypeInt::ZERO ) return t1;

  // Either input is BOTTOM ==> the result is BOTTOM
  if (t1 == Type::BOTTOM || t2 == Type::BOTTOM)
    return TypeInt::INT;

  if (t2 == TypeInt::INT)
    return TypeInt::INT;

  const TypeInt *r1 = t1->is_int();     // Handy access
  const TypeInt *r2 = t2->is_int();     // Handy access

  if (r2->is_con()) {
    uint shift = r2->get_con();
    shift &= BitsPerJavaInteger-1;  // semantics of Java shifts
    // Shift by a multiple of 32 does nothing:
    if (shift == 0)  return t1;
    // Calculate reasonably aggressive bounds for the result.
    jint lo = (juint)r1->_lo >> (juint)shift;
    jint hi = (juint)r1->_hi >> (juint)shift;
    if (r1->_hi >= 0 && r1->_lo < 0) {
      // If the type has both negative and positive values,
      // there are two separate sub-domains to worry about:
      // The positive half and the negative half.
      jint neg_lo = lo;
      jint neg_hi = (juint)-1 >> (juint)shift;
      jint pos_lo = (juint) 0 >> (juint)shift;
      jint pos_hi = hi;
      lo = MIN2(neg_lo, pos_lo);  // == 0
      hi = MAX2(neg_hi, pos_hi);  // == -1 >>> shift;
    }
    assert(lo <= hi, "must have valid bounds");
    const TypeInt* ti = TypeInt::make(lo, hi, MAX2(r1->_widen,r2->_widen));
    #ifdef ASSERT
    // Make sure we get the sign-capture idiom correct.
    if (shift == BitsPerJavaInteger-1) {
      if (r1->_lo >= 0) assert(ti == TypeInt::ZERO, ">>>31 of + is 0");
      if (r1->_hi < 0)  assert(ti == TypeInt::ONE,  ">>>31 of - is +1");
    }
    #endif
    return ti;
  }

  //
  // Do not support shifted oops in info for GC
  //
  // else if( t1->base() == Type::InstPtr ) {
  //
  //   const TypeInstPtr *o = t1->is_instptr();
  //   if( t1->singleton() )
  //     return TypeInt::make( ((uint32)o->const_oop() + o->_offset) >> shift );
  // }
  // else if( t1->base() == Type::KlassPtr ) {
  //   const TypeKlassPtr *o = t1->is_klassptr();
  //   if( t1->singleton() )
  //     return TypeInt::make( ((uint32)o->const_oop() + o->_offset) >> shift );
  // }

  return TypeInt::INT;
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node *URShiftLNode::Identity( PhaseTransform *phase ) {
  const TypeInt *ti = phase->type( in(2) )->isa_int(); // shift count is an int
  return ( ti && ti->is_con() && ( ti->get_con() & ( BitsPerLong - 1 ) ) == 0 ) ? in(1) : this;
}

//------------------------------Ideal------------------------------------------
Node *URShiftLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const TypeInt *t2 = phase->type( in(2) )->isa_int();
  if( !t2 || !t2->is_con() ) return NULL; // Right input is a constant
  const int con = t2->get_con() & ( BitsPerLong - 1 ); // Shift count is always masked
  if ( con == 0 ) return NULL;  // let Identity() handle a 0 shift count
                              // note: mask computation below does not work for 0 shift count
  // We'll be wanting the right-shift amount as a mask of that many bits
  const jlong mask = jlong(max_julong >> con);

  // Check for ((x << z) + Y) >>> z.  Replace with x + con>>>z
  // The idiom for rounding to a power of 2 is "(Q+(2^z-1)) >>> z".
  // If Q is "X << z" the rounding is useless.  Look for patterns like
  // ((X<<Z) + Y) >>> Z  and replace with (X + Y>>>Z) & Z-mask.
  Node *add = in(1);
  if( add->Opcode() == Op_AddL ) {
    Node *lshl = add->in(1);
    if( lshl->Opcode() == Op_LShiftL &&
        phase->type(lshl->in(2)) == t2 ) {
      Node *y_z = phase->transform( new (phase->C) URShiftLNode(add->in(2),in(2)) );
      Node *sum = phase->transform( new (phase->C) AddLNode( lshl->in(1), y_z ) );
      return new (phase->C) AndLNode( sum, phase->longcon(mask) );
    }
  }

  // Check for (x & mask) >>> z.  Replace with (x >>> z) & (mask >>> z)
  // This shortens the mask.  Also, if we are extracting a high byte and
  // storing it to a buffer, the mask will be removed completely.
  Node *andi = in(1);
  if( andi->Opcode() == Op_AndL ) {
    const TypeLong *t3 = phase->type( andi->in(2) )->isa_long();
    if( t3 && t3->is_con() ) { // Right input is a constant
      jlong mask2 = t3->get_con();
      mask2 >>= con;  // *signed* shift downward (high-order zeroes do not help)
      Node *newshr = phase->transform( new (phase->C) URShiftLNode(andi->in(1), in(2)) );
      return new (phase->C) AndLNode(newshr, phase->longcon(mask2));
    }
  }

  // Check for "(X << z ) >>> z" which simply zero-extends
  Node *shl = in(1);
  if( shl->Opcode() == Op_LShiftL &&
      phase->type(shl->in(2)) == t2 )
    return new (phase->C) AndLNode( shl->in(1), phase->longcon(mask) );

  return NULL;
}

//------------------------------Value------------------------------------------
// A URShiftINode shifts its input2 right by input1 amount.
const Type *URShiftLNode::Value( PhaseTransform *phase ) const {
  // (This is a near clone of RShiftLNode::Value.)
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  // Either input is TOP ==> the result is TOP
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Left input is ZERO ==> the result is ZERO.
  if( t1 == TypeLong::ZERO ) return TypeLong::ZERO;
  // Shift by zero does nothing
  if( t2 == TypeInt::ZERO ) return t1;

  // Either input is BOTTOM ==> the result is BOTTOM
  if (t1 == Type::BOTTOM || t2 == Type::BOTTOM)
    return TypeLong::LONG;

  if (t2 == TypeInt::INT)
    return TypeLong::LONG;

  const TypeLong *r1 = t1->is_long(); // Handy access
  const TypeInt  *r2 = t2->is_int (); // Handy access

  if (r2->is_con()) {
    uint shift = r2->get_con();
    shift &= BitsPerJavaLong - 1;  // semantics of Java shifts
    // Shift by a multiple of 64 does nothing:
    if (shift == 0)  return t1;
    // Calculate reasonably aggressive bounds for the result.
    jlong lo = (julong)r1->_lo >> (juint)shift;
    jlong hi = (julong)r1->_hi >> (juint)shift;
    if (r1->_hi >= 0 && r1->_lo < 0) {
      // If the type has both negative and positive values,
      // there are two separate sub-domains to worry about:
      // The positive half and the negative half.
      jlong neg_lo = lo;
      jlong neg_hi = (julong)-1 >> (juint)shift;
      jlong pos_lo = (julong) 0 >> (juint)shift;
      jlong pos_hi = hi;
      //lo = MIN2(neg_lo, pos_lo);  // == 0
      lo = neg_lo < pos_lo ? neg_lo : pos_lo;
      //hi = MAX2(neg_hi, pos_hi);  // == -1 >>> shift;
      hi = neg_hi > pos_hi ? neg_hi : pos_hi;
    }
    assert(lo <= hi, "must have valid bounds");
    const TypeLong* tl = TypeLong::make(lo, hi, MAX2(r1->_widen,r2->_widen));
    #ifdef ASSERT
    // Make sure we get the sign-capture idiom correct.
    if (shift == BitsPerJavaLong - 1) {
      if (r1->_lo >= 0) assert(tl == TypeLong::ZERO, ">>>63 of + is 0");
      if (r1->_hi < 0)  assert(tl == TypeLong::ONE,  ">>>63 of - is +1");
    }
    #endif
    return tl;
  }

  return TypeLong::LONG;                // Give up
}
