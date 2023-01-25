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

import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Iterator over a NativeArray
 */
class ScriptArrayIterator extends ArrayLikeIterator<Object> {

    /** Array {@link ScriptObject} to iterate over */
    protected final ScriptObject array;

    /** length of array */
    protected final long length;

    /**
     * Constructor
     * @param array array to iterate over
     * @param includeUndefined should undefined elements be included in iteration
     */
    protected ScriptArrayIterator(final ScriptObject array, final boolean includeUndefined) {
        super(includeUndefined);
        this.array = array;
        this.length = array.getArray().length();
    }

    /**
     * Is the current index still inside the array
     * @return true if inside the array
     */
    protected boolean indexInArray() {
        return index < length;
    }

    @Override
    public Object next() {
        return array.get(bumpIndex());
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public boolean hasNext() {
        if (!includeUndefined) {
            while (indexInArray()) {
                if (array.has(index)) {
                    break;
                }
                bumpIndex();
            }
        }

        return indexInArray();
    }

    @Override
    public void remove() {
        array.delete(index, false);
    }
}
