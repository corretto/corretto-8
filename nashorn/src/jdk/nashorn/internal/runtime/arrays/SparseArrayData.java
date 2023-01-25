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

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Handle arrays where the index is very large.
 */
class SparseArrayData extends ArrayData {
    /** Maximum size for dense arrays */
    static final int MAX_DENSE_LENGTH = 128 * 1024;

    /** Underlying array. */
    private ArrayData underlying;

    /** Maximum length to be stored in the array. */
    private final long maxDenseLength;

    /** Sparse elements. */
    private TreeMap<Long, Object> sparseMap;

    SparseArrayData(final ArrayData underlying, final long length) {
        this(underlying, length, new TreeMap<Long, Object>());
    }

    private SparseArrayData(final ArrayData underlying, final long length, final TreeMap<Long, Object> sparseMap) {
        super(length);
        assert underlying.length() <= length;
        this.underlying = underlying;
        this.maxDenseLength = underlying.length();
        this.sparseMap = sparseMap;
    }

    @Override
    public ArrayData copy() {
        return new SparseArrayData(underlying.copy(), length(), new TreeMap<>(sparseMap));
    }

    @Override
    public Object[] asObjectArray() {
        final int len = (int)Math.min(length(), Integer.MAX_VALUE);
        final int underlyingLength = (int)Math.min(len, underlying.length());
        final Object[] objArray = new Object[len];

        for (int i = 0; i < underlyingLength; i++) {
            objArray[i] = underlying.getObject(i);
        }

        Arrays.fill(objArray, underlyingLength, len, ScriptRuntime.UNDEFINED);

        for (final Map.Entry<Long, Object> entry : sparseMap.entrySet()) {
            final long key = entry.getKey();
            if (key < Integer.MAX_VALUE) {
                objArray[(int)key] = entry.getValue();
            } else {
                break; // ascending key order
            }
        }

        return objArray;
    }

    @Override
    public ArrayData shiftLeft(final int by) {
        underlying = underlying.shiftLeft(by);

        final TreeMap<Long, Object> newSparseMap = new TreeMap<>();

        for (final Map.Entry<Long, Object> entry : sparseMap.entrySet()) {
            final long newIndex = entry.getKey().longValue() - by;
            if (newIndex >= 0) {
                if (newIndex < maxDenseLength) {
                    final long oldLength = underlying.length();
                    underlying = underlying.ensure(newIndex)
                            .set((int) newIndex, entry.getValue(), false)
                            .safeDelete(oldLength, newIndex - 1, false);
                } else {
                    newSparseMap.put(Long.valueOf(newIndex), entry.getValue());
                }
            }
        }

        sparseMap = newSparseMap;
        setLength(Math.max(length() - by, 0));

        return sparseMap.isEmpty() ? underlying : this;
    }

    @Override
    public ArrayData shiftRight(final int by) {
        final TreeMap<Long, Object> newSparseMap = new TreeMap<>();
        // Move elements from underlying to sparse map if necessary
        final long len = underlying.length();
        if (len + by > maxDenseLength) {
            // Length of underlying array after shrinking, before right-shifting
            final long tempLength = Math.max(0, maxDenseLength - by);
            for (long i = tempLength; i < len; i++) {
                if (underlying.has((int) i)) {
                    newSparseMap.put(Long.valueOf(i + by), underlying.getObject((int) i));
                }
            }
            underlying = underlying.shrink((int) tempLength);
            underlying.setLength(tempLength);
        }

        underlying = underlying.shiftRight(by);

        for (final Map.Entry<Long, Object> entry : sparseMap.entrySet()) {
            final long newIndex = entry.getKey().longValue() + by;
            newSparseMap.put(Long.valueOf(newIndex), entry.getValue());
        }

        sparseMap = newSparseMap;
        setLength(length() + by);

        return this;
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        if (safeIndex >= length()) {
            setLength(safeIndex + 1);
        }
        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        if (newLength < underlying.length()) {
            underlying = underlying.shrink(newLength);
            underlying.setLength(newLength);
            sparseMap.clear();
            setLength(newLength);
        }

        sparseMap.subMap(Long.valueOf(newLength), Long.MAX_VALUE).clear();
        setLength(newLength);
        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        if (index >= 0 && index < maxDenseLength) {
            final long oldLength = underlying.length();
            underlying = underlying.ensure(index).set(index, value, strict).safeDelete(oldLength, index - 1, strict);
            setLength(Math.max(underlying.length(), length()));
        } else {
            final Long longIndex = indexToKey(index);
            sparseMap.put(longIndex, value);
            setLength(Math.max(longIndex + 1, length()));
        }

        return this;
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        if (index >= 0 && index < maxDenseLength) {
            final long oldLength = underlying.length();
            underlying = underlying.ensure(index).set(index, value, strict).safeDelete(oldLength, index - 1, strict);
            setLength(Math.max(underlying.length(), length()));
        } else {
            final Long longIndex = indexToKey(index);
            sparseMap.put(longIndex, value);
            setLength(Math.max(longIndex + 1, length()));
        }
        return this;
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        if (index >= 0 && index < maxDenseLength) {
            final long oldLength = underlying.length();
            underlying = underlying.ensure(index).set(index, value, strict).safeDelete(oldLength, index - 1, strict);
            setLength(Math.max(underlying.length(), length()));
        } else {
            final Long longIndex = indexToKey(index);
            sparseMap.put(longIndex, value);
            setLength(Math.max(longIndex + 1, length()));
        }
        return this;
    }

    @Override
    public ArrayData setEmpty(final int index) {
        underlying.setEmpty(index);
        return this;
    }

    @Override
    public ArrayData setEmpty(final long lo, final long hi) {
        underlying.setEmpty(lo, hi);
        return this;
    }

    @Override
    public Type getOptimisticType() {
        return underlying.getOptimisticType();
    }

    @Override
    public int getInt(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            return underlying.getInt(index);
        }
        return JSType.toInt32(sparseMap.get(indexToKey(index)));
    }

    @Override
    public int getIntOptimistic(final int index, final int programPoint) {
        if (index >= 0 && index < maxDenseLength) {
            return underlying.getIntOptimistic(index, programPoint);
        }
        return JSType.toInt32Optimistic(sparseMap.get(indexToKey(index)), programPoint);
    }

    @Override
    public double getDouble(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            return underlying.getDouble(index);
        }
        return JSType.toNumber(sparseMap.get(indexToKey(index)));
    }

    @Override
    public double getDoubleOptimistic(final int index, final int programPoint) {
        if (index >= 0 && index < maxDenseLength) {
            return underlying.getDouble(index);
        }
        return JSType.toNumberOptimistic(sparseMap.get(indexToKey(index)), programPoint);
    }

    @Override
    public Object getObject(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            return underlying.getObject(index);
        }

        final Long key = indexToKey(index);
        if (sparseMap.containsKey(key)) {
            return sparseMap.get(key);
        }

        return ScriptRuntime.UNDEFINED;
    }

    @Override
    public boolean has(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            return index < underlying.length() && underlying.has(index);
        }

        return sparseMap.containsKey(indexToKey(index));
    }

    @Override
    public ArrayData delete(final int index) {
        if (index >= 0 && index < maxDenseLength) {
            if (index < underlying.length()) {
                underlying = underlying.delete(index);
            }
        } else {
            sparseMap.remove(indexToKey(index));
        }

        return this;
    }

    @Override
    public ArrayData delete(final long fromIndex, final long toIndex) {
        if (fromIndex < maxDenseLength && fromIndex < underlying.length()) {
            underlying = underlying.delete(fromIndex, Math.min(toIndex, underlying.length() - 1));
        }
        if (toIndex >= maxDenseLength) {
            sparseMap.subMap(fromIndex, true, toIndex, true).clear();
        }
        return this;
    }

    private static Long indexToKey(final int index) {
        return Long.valueOf(ArrayIndex.toLongIndex(index));
    }

    @Override
    public ArrayData convert(final Class<?> type) {
        underlying = underlying.convert(type);
        return this;
    }

    @Override
    public Object pop() {
        final long len = length();
        final long underlyingLen = underlying.length();
        if (len == 0) {
            return ScriptRuntime.UNDEFINED;
        }
        if (len == underlyingLen) {
            final Object result = underlying.pop();
            setLength(underlying.length());
            return result;
        }
        setLength(len - 1);
        final Long key = Long.valueOf(len - 1);
        return sparseMap.containsKey(key) ? sparseMap.remove(key) : ScriptRuntime.UNDEFINED;
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        assert to <= length();
        final long start = from < 0 ? (from + length()) : from;
        final long newLength = to - start;

        final long underlyingLength = underlying.length();

        if (start >= 0 && to <= maxDenseLength) {
            if (newLength <= underlyingLength) {
                return underlying.slice(from, to);
            }
            return underlying.slice(from, to).ensure(newLength - 1).delete(underlyingLength, newLength);
        }

        ArrayData sliced = EMPTY_ARRAY;
        sliced = sliced.ensure(newLength - 1);
        for (long i = start; i < to; i = nextIndex(i)) {
            if (has((int)i)) {
                sliced = sliced.set((int)(i - start), getObject((int)i), false);
            }
        }
        assert sliced.length() == newLength;
        return sliced;
    }

    @Override
    public long nextIndex(final long index) {
        if (index < underlying.length() - 1) {
            return underlying.nextIndex(index);
        }

        final Long nextKey = sparseMap.higherKey(index);
        if (nextKey != null) {
            return nextKey;
        }

        return length();
    }
}
