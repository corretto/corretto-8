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

package jdk.nashorn.internal.runtime.arrays;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.PropertyDescriptor;

/**
 * ArrayData after the array has been sealed by Object.seal call.
 */
class SealedArrayFilter extends ArrayFilter {
    SealedArrayFilter(final ArrayData underlying) {
        super(underlying);
    }

    @Override
    public ArrayData copy() {
        return new SealedArrayFilter(underlying.copy());
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        return getUnderlying().slice(from, to);
    }

    @Override
    public boolean canDelete(final int index, final boolean strict) {
        return canDelete(ArrayIndex.toLongIndex(index), strict);
    }

    @Override
    public boolean canDelete(final long longIndex, final boolean strict) {
        if (strict) {
            throw typeError("cant.delete.property", Long.toString(longIndex), "sealed array");
        }
        return false;
    }

    @Override
    public PropertyDescriptor getDescriptor(final Global global, final int index) {
        return global.newDataDescriptor(getObject(index), false, true, true);
    }
}
