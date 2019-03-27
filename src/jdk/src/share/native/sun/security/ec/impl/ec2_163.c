/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * Use is subject to license terms.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/* *********************************************************************
 *
 * The Original Code is the elliptic curve math library for binary polynomial field curves.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Sheueling Chang-Shantz <sheueling.chang@sun.com>,
 *   Stephen Fung <fungstep@hotmail.com>, and
 *   Douglas Stebila <douglas@stebila.ca>, Sun Microsystems Laboratories.
 *
 *********************************************************************** */

#include "ec2.h"
#include "mp_gf2m.h"
#include "mp_gf2m-priv.h"
#include "mpi.h"
#include "mpi-priv.h"
#ifndef _KERNEL
#include <stdlib.h>
#endif

/* Fast reduction for polynomials over a 163-bit curve. Assumes reduction
 * polynomial with terms {163, 7, 6, 3, 0}. */
mp_err
ec_GF2m_163_mod(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit *u, z;

        if (a != r) {
                MP_CHECKOK(mp_copy(a, r));
        }
#ifdef ECL_SIXTY_FOUR_BIT
        if (MP_USED(r) < 6) {
                MP_CHECKOK(s_mp_pad(r, 6));
        }
        u = MP_DIGITS(r);
        MP_USED(r) = 6;

        /* u[5] only has 6 significant bits */
        z = u[5];
        u[2] ^= (z << 36) ^ (z << 35) ^ (z << 32) ^ (z << 29);
        z = u[4];
        u[2] ^= (z >> 28) ^ (z >> 29) ^ (z >> 32) ^ (z >> 35);
        u[1] ^= (z << 36) ^ (z << 35) ^ (z << 32) ^ (z << 29);
        z = u[3];
        u[1] ^= (z >> 28) ^ (z >> 29) ^ (z >> 32) ^ (z >> 35);
        u[0] ^= (z << 36) ^ (z << 35) ^ (z << 32) ^ (z << 29);
        z = u[2] >> 35;                         /* z only has 29 significant bits */
        u[0] ^= (z << 7) ^ (z << 6) ^ (z << 3) ^ z;
        /* clear bits above 163 */
        u[5] = u[4] = u[3] = 0;
        u[2] ^= z << 35;
#else
        if (MP_USED(r) < 11) {
                MP_CHECKOK(s_mp_pad(r, 11));
        }
        u = MP_DIGITS(r);
        MP_USED(r) = 11;

        /* u[11] only has 6 significant bits */
        z = u[10];
        u[5] ^= (z << 4) ^ (z << 3) ^ z ^ (z >> 3);
        u[4] ^= (z << 29);
        z = u[9];
        u[5] ^= (z >> 28) ^ (z >> 29);
        u[4] ^= (z << 4) ^ (z << 3) ^ z ^ (z >> 3);
        u[3] ^= (z << 29);
        z = u[8];
        u[4] ^= (z >> 28) ^ (z >> 29);
        u[3] ^= (z << 4) ^ (z << 3) ^ z ^ (z >> 3);
        u[2] ^= (z << 29);
        z = u[7];
        u[3] ^= (z >> 28) ^ (z >> 29);
        u[2] ^= (z << 4) ^ (z << 3) ^ z ^ (z >> 3);
        u[1] ^= (z << 29);
        z = u[6];
        u[2] ^= (z >> 28) ^ (z >> 29);
        u[1] ^= (z << 4) ^ (z << 3) ^ z ^ (z >> 3);
        u[0] ^= (z << 29);
        z = u[5] >> 3;                          /* z only has 29 significant bits */
        u[1] ^= (z >> 25) ^ (z >> 26);
        u[0] ^= (z << 7) ^ (z << 6) ^ (z << 3) ^ z;
        /* clear bits above 163 */
        u[11] = u[10] = u[9] = u[8] = u[7] = u[6] = 0;
        u[5] ^= z << 3;
#endif
        s_mp_clamp(r);

  CLEANUP:
        return res;
}

/* Fast squaring for polynomials over a 163-bit curve. Assumes reduction
 * polynomial with terms {163, 7, 6, 3, 0}. */
mp_err
ec_GF2m_163_sqr(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit *u, *v;

        v = MP_DIGITS(a);

#ifdef ECL_SIXTY_FOUR_BIT
        if (MP_USED(a) < 3) {
                return mp_bsqrmod(a, meth->irr_arr, r);
        }
        if (MP_USED(r) < 6) {
                MP_CHECKOK(s_mp_pad(r, 6));
        }
        MP_USED(r) = 6;
#else
        if (MP_USED(a) < 6) {
                return mp_bsqrmod(a, meth->irr_arr, r);
        }
        if (MP_USED(r) < 12) {
                MP_CHECKOK(s_mp_pad(r, 12));
        }
        MP_USED(r) = 12;
#endif
        u = MP_DIGITS(r);

#ifdef ECL_THIRTY_TWO_BIT
        u[11] = gf2m_SQR1(v[5]);
        u[10] = gf2m_SQR0(v[5]);
        u[9] = gf2m_SQR1(v[4]);
        u[8] = gf2m_SQR0(v[4]);
        u[7] = gf2m_SQR1(v[3]);
        u[6] = gf2m_SQR0(v[3]);
#endif
        u[5] = gf2m_SQR1(v[2]);
        u[4] = gf2m_SQR0(v[2]);
        u[3] = gf2m_SQR1(v[1]);
        u[2] = gf2m_SQR0(v[1]);
        u[1] = gf2m_SQR1(v[0]);
        u[0] = gf2m_SQR0(v[0]);
        return ec_GF2m_163_mod(r, r, meth);

  CLEANUP:
        return res;
}

/* Fast multiplication for polynomials over a 163-bit curve. Assumes
 * reduction polynomial with terms {163, 7, 6, 3, 0}. */
mp_err
ec_GF2m_163_mul(const mp_int *a, const mp_int *b, mp_int *r,
                                const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit a2 = 0, a1 = 0, a0, b2 = 0, b1 = 0, b0;

#ifdef ECL_THIRTY_TWO_BIT
        mp_digit a5 = 0, a4 = 0, a3 = 0, b5 = 0, b4 = 0, b3 = 0;
        mp_digit rm[6];
#endif

        if (a == b) {
                return ec_GF2m_163_sqr(a, r, meth);
        } else {
                switch (MP_USED(a)) {
#ifdef ECL_THIRTY_TWO_BIT
                case 6:
                        a5 = MP_DIGIT(a, 5);
                case 5:
                        a4 = MP_DIGIT(a, 4);
                case 4:
                        a3 = MP_DIGIT(a, 3);
#endif
                case 3:
                        a2 = MP_DIGIT(a, 2);
                case 2:
                        a1 = MP_DIGIT(a, 1);
                default:
                        a0 = MP_DIGIT(a, 0);
                }
                switch (MP_USED(b)) {
#ifdef ECL_THIRTY_TWO_BIT
                case 6:
                        b5 = MP_DIGIT(b, 5);
                case 5:
                        b4 = MP_DIGIT(b, 4);
                case 4:
                        b3 = MP_DIGIT(b, 3);
#endif
                case 3:
                        b2 = MP_DIGIT(b, 2);
                case 2:
                        b1 = MP_DIGIT(b, 1);
                default:
                        b0 = MP_DIGIT(b, 0);
                }
#ifdef ECL_SIXTY_FOUR_BIT
                MP_CHECKOK(s_mp_pad(r, 6));
                s_bmul_3x3(MP_DIGITS(r), a2, a1, a0, b2, b1, b0);
                MP_USED(r) = 6;
                s_mp_clamp(r);
#else
                MP_CHECKOK(s_mp_pad(r, 12));
                s_bmul_3x3(MP_DIGITS(r) + 6, a5, a4, a3, b5, b4, b3);
                s_bmul_3x3(MP_DIGITS(r), a2, a1, a0, b2, b1, b0);
                s_bmul_3x3(rm, a5 ^ a2, a4 ^ a1, a3 ^ a0, b5 ^ b2, b4 ^ b1,
                                   b3 ^ b0);
                rm[5] ^= MP_DIGIT(r, 5) ^ MP_DIGIT(r, 11);
                rm[4] ^= MP_DIGIT(r, 4) ^ MP_DIGIT(r, 10);
                rm[3] ^= MP_DIGIT(r, 3) ^ MP_DIGIT(r, 9);
                rm[2] ^= MP_DIGIT(r, 2) ^ MP_DIGIT(r, 8);
                rm[1] ^= MP_DIGIT(r, 1) ^ MP_DIGIT(r, 7);
                rm[0] ^= MP_DIGIT(r, 0) ^ MP_DIGIT(r, 6);
                MP_DIGIT(r, 8) ^= rm[5];
                MP_DIGIT(r, 7) ^= rm[4];
                MP_DIGIT(r, 6) ^= rm[3];
                MP_DIGIT(r, 5) ^= rm[2];
                MP_DIGIT(r, 4) ^= rm[1];
                MP_DIGIT(r, 3) ^= rm[0];
                MP_USED(r) = 12;
                s_mp_clamp(r);
#endif
                return ec_GF2m_163_mod(r, r, meth);
        }

  CLEANUP:
        return res;
}

/* Wire in fast field arithmetic for 163-bit curves. */
mp_err
ec_group_set_gf2m163(ECGroup *group, ECCurveName name)
{
        group->meth->field_mod = &ec_GF2m_163_mod;
        group->meth->field_mul = &ec_GF2m_163_mul;
        group->meth->field_sqr = &ec_GF2m_163_sqr;
        return MP_OKAY;
}
