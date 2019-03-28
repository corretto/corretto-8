/*
 * Copyright (c) 2013, Red Hat Inc.
 * All rights reserved.
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
 */

#include <stdlib.h>
#include "decode_aarch64.hpp"
#include "immediate_aarch64.hpp"

// there are at most 2^13 possible logical immediate encodings
// however, some combinations of immr and imms are invalid
static const unsigned  LI_TABLE_SIZE = (1 << 13);

static int li_table_entry_count;

// for forward lookup we just use a direct array lookup
// and assume that the cient has supplied a valid encoding
// table[encoding] = immediate
static u_int64_t LITable[LI_TABLE_SIZE];

// for reverse lookup we need a sparse map so we store a table of
// immediate and encoding pairs sorted by immediate value

struct li_pair {
  u_int64_t immediate;
  u_int32_t encoding;
};

static struct li_pair InverseLITable[LI_TABLE_SIZE];

// comparator to sort entries in the inverse table
int compare_immediate_pair(const void *i1, const void *i2)
{
  struct li_pair *li1 = (struct li_pair *)i1;
  struct li_pair *li2 = (struct li_pair *)i2;
  if (li1->immediate < li2->immediate) {
    return -1;
  }
  if (li1->immediate > li2->immediate) {
    return 1;
  }
  return 0;
}

// helper functions used by expandLogicalImmediate

// for i = 1, ... N result<i-1> = 1 other bits are zero
static inline u_int64_t ones(int N)
{
  return (N == 64 ? (u_int64_t)-1UL : ((1UL << N) - 1));
}

// result<0> to val<N>
static inline u_int64_t pickbit(u_int64_t val, int N)
{
  return pickbits64(val, N, N);
}


// SPEC bits(M*N) Replicate(bits(M) x, integer N);
// this is just an educated guess

u_int64_t replicate(u_int64_t bits, int nbits, int count)
{
  u_int64_t result = 0;
  // nbits may be 64 in which case we want mask to be -1
  u_int64_t mask = ones(nbits);
  for (int i = 0; i < count ; i++) {
    result <<= nbits;
    result |= (bits & mask);
  }
  return result;
}

// this function writes the supplied bimm reference and returns a
// boolean to indicate success (1) or fail (0) because an illegal
// encoding must be treated as an UNALLOC instruction

// construct a 32 bit immediate value for a logical immediate operation
int expandLogicalImmediate(u_int32_t immN, u_int32_t immr,
			    u_int32_t imms, u_int64_t &bimm)
{
  int len;		    // ought to be <= 6
  u_int32_t levels;	    // 6 bits
  u_int32_t tmask_and;	    // 6 bits
  u_int32_t wmask_and;	    // 6 bits
  u_int32_t tmask_or;	    // 6 bits
  u_int32_t wmask_or;	    // 6 bits
  u_int64_t imm64;	    // 64 bits
  u_int64_t tmask, wmask;   // 64 bits
  u_int32_t S, R, diff;	    // 6 bits?

  if (immN == 1) {
    len = 6; // looks like 7 given the spec above but this cannot be!
  } else {
    len = 0;
    u_int32_t val = (~imms & 0x3f);
    for (int i = 5; i > 0; i--) {
      if (val & (1 << i)) {
	len = i;
	break;
      }
    }
    if (len < 1) {
      return 0;
    }
    // for valid inputs leading 1s in immr must be less than leading
    // zeros in imms
    int len2 = 0;		    // ought to be < len
    u_int32_t val2 = (~immr & 0x3f);
    for (int i = 5; i > 0; i--) {
      if (!(val2 & (1 << i))) {
	len2 = i;
	break;
      }
    }
    if (len2 >= len) {
      return 0;
    }
  }

  levels = (1 << len) - 1;

  if ((imms & levels) == levels) {
    return 0;
  }

  S = imms & levels;
  R = immr & levels;
  
 // 6 bit arithmetic!
  diff = S - R;
  tmask_and = (diff | ~levels) & 0x3f;
  tmask_or = (diff & levels) & 0x3f;
  tmask = 0xffffffffffffffffULL;

  for (int i = 0; i < 6; i++) {
    int nbits = 1 << i;
    u_int64_t and_bit = pickbit(tmask_and, i);
    u_int64_t or_bit = pickbit(tmask_or, i);
    u_int64_t and_bits_sub = replicate(and_bit, 1, nbits);
    u_int64_t or_bits_sub = replicate(or_bit, 1, nbits);
    u_int64_t and_bits_top = (and_bits_sub << nbits) | ones(nbits);
    u_int64_t or_bits_top = (0 << nbits) | or_bits_sub;

    tmask = ((tmask
	      & (replicate(and_bits_top, 2 * nbits, 32 / nbits)))
	     | replicate(or_bits_top, 2 * nbits, 32 / nbits));
  }

  wmask_and = (immr | ~levels) & 0x3f;
  wmask_or = (immr & levels) & 0x3f;

  wmask = 0;

  for (int i = 0; i < 6; i++) {
    int nbits = 1 << i;
    u_int64_t and_bit = pickbit(wmask_and, i);
    u_int64_t or_bit = pickbit(wmask_or, i);
    u_int64_t and_bits_sub = replicate(and_bit, 1, nbits);
    u_int64_t or_bits_sub = replicate(or_bit, 1, nbits);
    u_int64_t and_bits_top = (ones(nbits) << nbits) | and_bits_sub;
    u_int64_t or_bits_top = (or_bits_sub << nbits) | 0;

    wmask = ((wmask
	      & (replicate(and_bits_top, 2 * nbits, 32 / nbits)))
	     | replicate(or_bits_top, 2 * nbits, 32 / nbits));
  }

  if (diff & (1U << 6)) {
    imm64 = tmask & wmask;
  } else {
    imm64 = tmask | wmask;
  }


  bimm = imm64;
  return 1;
}

// constructor to initialise the lookup tables

static void initLITables() __attribute__ ((constructor));
static void initLITables()
{
  li_table_entry_count = 0;
  for (unsigned index = 0; index < LI_TABLE_SIZE; index++) {
    u_int32_t N = uimm(index, 12, 12);
    u_int32_t immr = uimm(index, 11, 6);
    u_int32_t imms = uimm(index, 5, 0);
    if (expandLogicalImmediate(N, immr, imms, LITable[index])) {
      InverseLITable[li_table_entry_count].immediate = LITable[index];
      InverseLITable[li_table_entry_count].encoding = index;
      li_table_entry_count++;
    }
  }
  // now sort the inverse table
  qsort(InverseLITable, li_table_entry_count,
	sizeof(InverseLITable[0]), compare_immediate_pair);
}

// public APIs provided for logical immediate lookup and reverse lookup

u_int64_t logical_immediate_for_encoding(u_int32_t encoding)
{
  return LITable[encoding];
}

u_int32_t encoding_for_logical_immediate(u_int64_t immediate)
{
  struct li_pair pair;
  struct li_pair *result;

  pair.immediate = immediate;

  result = (struct li_pair *)
    bsearch(&pair, InverseLITable, li_table_entry_count,
	    sizeof(InverseLITable[0]), compare_immediate_pair);

  if (result) {
    return result->encoding;
  }

  return 0xffffffff;
}

// floating point immediates are encoded in 8 bits
// fpimm[7] = sign bit
// fpimm[6:4] = signed exponent
// fpimm[3:0] = fraction (assuming leading 1)
// i.e. F = s * 1.f * 2^(e - b)

u_int64_t fp_immediate_for_encoding(u_int32_t imm8, int is_dp)
{
  union {
    float fpval;
    double dpval;
    u_int64_t val;
  };

  u_int32_t s, e, f;
  s = (imm8 >> 7 ) & 0x1;
  e = (imm8 >> 4) & 0x7;
  f = imm8 & 0xf;
  // the fp value is s * n/16 * 2r where n is 16+e
  fpval = (16.0 + f) / 16.0;
  // n.b. exponent is signed
  if (e < 4) {
    int epos = e;
    for (int i = 0; i <= epos; i++) {
      fpval *= 2.0;
    }
  } else {
    int eneg = 7 - e;
    for (int i = 0; i < eneg; i++) {
      fpval /= 2.0;
    }
  }

  if (s) {
    fpval = -fpval;
  }
  if (is_dp) {
    dpval = (double)fpval;
  }
  return val;
}

u_int32_t encoding_for_fp_immediate(float immediate)
{
  // given a float which is of the form
  //
  //     s * n/16 * 2r
  //
  // where n is 16+f and imm1:s, imm4:f, simm3:r
  // return the imm8 result [s:r:f]
  //

  union {
    float fpval;
    u_int32_t val;
  };
  fpval = immediate;
  u_int32_t s, r, f, res;
  // sign bit is 31
  s = (val >> 31) & 0x1;
  // exponent is bits 30-23 but we only want the bottom 3 bits
  // strictly we ought to check that the bits bits 30-25 are
  // either all 1s or all 0s
  r = (val >> 23) & 0x7;
  // fraction is bits 22-0
  f = (val >> 19) & 0xf;
  res = (s << 7) | (r << 4) | f;
  return res;
}

