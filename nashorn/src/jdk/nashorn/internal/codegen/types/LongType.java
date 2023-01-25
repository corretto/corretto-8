/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

package jdk.nashorn.internal.codegen.types;

import static jdk.internal.org.objectweb.asm.Opcodes.L2D;
import static jdk.internal.org.objectweb.asm.Opcodes.L2I;
import static jdk.internal.org.objectweb.asm.Opcodes.LADD;
import static jdk.internal.org.objectweb.asm.Opcodes.LCONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.LCONST_1;
import static jdk.internal.org.objectweb.asm.Opcodes.LLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.LRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.LSTORE;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.runtime.JSType.UNDEFINED_LONG;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.runtime.JSType;

/**
 * Type class: LONG
 */
class LongType extends Type {
    private static final long serialVersionUID = 1L;

    private static final CompilerConstants.Call VALUE_OF = staticCallNoLookup(Long.class, "valueOf", Long.class, long.class);

    protected LongType(final String name) {
        super(name, long.class, 3, 2);
    }

    protected LongType() {
        this("long");
    }

    @Override
    public Type nextWider() {
        return NUMBER;
    }

    @Override
    public Class<?> getBoxedType() {
        return Long.class;
    }

    @Override
    public char getBytecodeStackType() {
        return 'J';
    }

    @Override
    public Type load(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(LLOAD, slot);
        return LONG;
    }

    @Override
    public void store(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(LSTORE, slot);
    }

    @Override
    public Type ldc(final MethodVisitor method, final Object c) {
        assert c instanceof Long;

        final long value = (Long) c;

        if (value == 0L) {
            method.visitInsn(LCONST_0);
        } else if (value == 1L) {
            method.visitInsn(LCONST_1);
        } else {
            method.visitLdcInsn(c);
        }

        return Type.LONG;
    }

    @Override
    public Type convert(final MethodVisitor method, final Type to) {
        if (isEquivalentTo(to)) {
            return to;
        }

        if (to.isNumber()) {
            method.visitInsn(L2D);
        } else if (to.isInteger()) {
            invokestatic(method, JSType.TO_INT32_L);
        } else if (to.isBoolean()) {
            method.visitInsn(L2I);
        } else if (to.isObject()) {
            invokestatic(method, VALUE_OF);
        } else {
            assert false : "Illegal conversion " + this + " -> " + to;
        }

        return to;
    }

    @Override
    public Type add(final MethodVisitor method, final int programPoint) {
        if(programPoint == INVALID_PROGRAM_POINT) {
            method.visitInsn(LADD);
        } else {
            method.visitInvokeDynamicInsn("ladd", "(JJ)J", MATHBOOTSTRAP, programPoint);
        }
        return LONG;
    }

    @Override
    public void _return(final MethodVisitor method) {
        method.visitInsn(LRETURN);
    }

    @Override
    public Type loadUndefined(final MethodVisitor method) {
        method.visitLdcInsn(UNDEFINED_LONG);
        return LONG;
    }

    @Override
    public Type loadForcedInitializer(final MethodVisitor method) {
        method.visitInsn(LCONST_0);
        return LONG;
    }
}
