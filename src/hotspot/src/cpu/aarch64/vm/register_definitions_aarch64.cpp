/*
 * Copyright (c) 2013, Red Hat Inc.
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "asm/assembler.hpp"
#include "asm/register.hpp"
#include "register_aarch64.hpp"
# include "interp_masm_aarch64.hpp"

REGISTER_DEFINITION(Register, r0);
REGISTER_DEFINITION(Register, r1);
REGISTER_DEFINITION(Register, r2);
REGISTER_DEFINITION(Register, r3);
REGISTER_DEFINITION(Register, r4);
REGISTER_DEFINITION(Register, r5);
REGISTER_DEFINITION(Register, r6);
REGISTER_DEFINITION(Register, r7);
REGISTER_DEFINITION(Register, r8);
REGISTER_DEFINITION(Register, r9);
REGISTER_DEFINITION(Register, r10);
REGISTER_DEFINITION(Register, r11);
REGISTER_DEFINITION(Register, r12);
REGISTER_DEFINITION(Register, r13);
REGISTER_DEFINITION(Register, r14);
REGISTER_DEFINITION(Register, r15);
REGISTER_DEFINITION(Register, r16);
REGISTER_DEFINITION(Register, r17);
REGISTER_DEFINITION(Register, r18);
REGISTER_DEFINITION(Register, r19);
REGISTER_DEFINITION(Register, r20);
REGISTER_DEFINITION(Register, r21);
REGISTER_DEFINITION(Register, r22);
REGISTER_DEFINITION(Register, r23);
REGISTER_DEFINITION(Register, r24);
REGISTER_DEFINITION(Register, r25);
REGISTER_DEFINITION(Register, r26);
REGISTER_DEFINITION(Register, r27);
REGISTER_DEFINITION(Register, r28);
REGISTER_DEFINITION(Register, r29);
REGISTER_DEFINITION(Register, r30);
REGISTER_DEFINITION(Register, sp);

REGISTER_DEFINITION(FloatRegister, v0);
REGISTER_DEFINITION(FloatRegister, v1);
REGISTER_DEFINITION(FloatRegister, v2);
REGISTER_DEFINITION(FloatRegister, v3);
REGISTER_DEFINITION(FloatRegister, v4);
REGISTER_DEFINITION(FloatRegister, v5);
REGISTER_DEFINITION(FloatRegister, v6);
REGISTER_DEFINITION(FloatRegister, v7);
REGISTER_DEFINITION(FloatRegister, v8);
REGISTER_DEFINITION(FloatRegister, v9);
REGISTER_DEFINITION(FloatRegister, v10);
REGISTER_DEFINITION(FloatRegister, v11);
REGISTER_DEFINITION(FloatRegister, v12);
REGISTER_DEFINITION(FloatRegister, v13);
REGISTER_DEFINITION(FloatRegister, v14);
REGISTER_DEFINITION(FloatRegister, v15);
REGISTER_DEFINITION(FloatRegister, v16);
REGISTER_DEFINITION(FloatRegister, v17);
REGISTER_DEFINITION(FloatRegister, v18);
REGISTER_DEFINITION(FloatRegister, v19);
REGISTER_DEFINITION(FloatRegister, v20);
REGISTER_DEFINITION(FloatRegister, v21);
REGISTER_DEFINITION(FloatRegister, v22);
REGISTER_DEFINITION(FloatRegister, v23);
REGISTER_DEFINITION(FloatRegister, v24);
REGISTER_DEFINITION(FloatRegister, v25);
REGISTER_DEFINITION(FloatRegister, v26);
REGISTER_DEFINITION(FloatRegister, v27);
REGISTER_DEFINITION(FloatRegister, v28);
REGISTER_DEFINITION(FloatRegister, v29);
REGISTER_DEFINITION(FloatRegister, v30);
REGISTER_DEFINITION(FloatRegister, v31);

REGISTER_DEFINITION(Register, zr);

REGISTER_DEFINITION(Register, c_rarg0);
REGISTER_DEFINITION(Register, c_rarg1);
REGISTER_DEFINITION(Register, c_rarg2);
REGISTER_DEFINITION(Register, c_rarg3);
REGISTER_DEFINITION(Register, c_rarg4);
REGISTER_DEFINITION(Register, c_rarg5);
REGISTER_DEFINITION(Register, c_rarg6);
REGISTER_DEFINITION(Register, c_rarg7);

REGISTER_DEFINITION(FloatRegister, c_farg0);
REGISTER_DEFINITION(FloatRegister, c_farg1);
REGISTER_DEFINITION(FloatRegister, c_farg2);
REGISTER_DEFINITION(FloatRegister, c_farg3);
REGISTER_DEFINITION(FloatRegister, c_farg4);
REGISTER_DEFINITION(FloatRegister, c_farg5);
REGISTER_DEFINITION(FloatRegister, c_farg6);
REGISTER_DEFINITION(FloatRegister, c_farg7);

REGISTER_DEFINITION(Register, j_rarg0);
REGISTER_DEFINITION(Register, j_rarg1);
REGISTER_DEFINITION(Register, j_rarg2);
REGISTER_DEFINITION(Register, j_rarg3);
REGISTER_DEFINITION(Register, j_rarg4);
REGISTER_DEFINITION(Register, j_rarg5);
REGISTER_DEFINITION(Register, j_rarg6);
REGISTER_DEFINITION(Register, j_rarg7);

REGISTER_DEFINITION(FloatRegister, j_farg0);
REGISTER_DEFINITION(FloatRegister, j_farg1);
REGISTER_DEFINITION(FloatRegister, j_farg2);
REGISTER_DEFINITION(FloatRegister, j_farg3);
REGISTER_DEFINITION(FloatRegister, j_farg4);
REGISTER_DEFINITION(FloatRegister, j_farg5);
REGISTER_DEFINITION(FloatRegister, j_farg6);
REGISTER_DEFINITION(FloatRegister, j_farg7);

REGISTER_DEFINITION(Register, rscratch1);
REGISTER_DEFINITION(Register, rscratch2);
REGISTER_DEFINITION(Register, esp);
REGISTER_DEFINITION(Register, rdispatch);
REGISTER_DEFINITION(Register, rcpool);
REGISTER_DEFINITION(Register, rmonitors);
REGISTER_DEFINITION(Register, rlocals);
REGISTER_DEFINITION(Register, rmethod);
REGISTER_DEFINITION(Register, rbcp);

REGISTER_DEFINITION(Register, lr);
REGISTER_DEFINITION(Register, rfp);
REGISTER_DEFINITION(Register, rthread);
REGISTER_DEFINITION(Register, rheapbase);

REGISTER_DEFINITION(Register, r31_sp);

// TODO : x86 uses rbp to save SP in method handle code
// we may need to do the same with fp
// REGISTER_DEFINITION(Register, rbp_mh_SP_save)
