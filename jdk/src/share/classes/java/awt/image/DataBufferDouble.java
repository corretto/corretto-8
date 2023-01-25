/*
 * Copyright (c) 2000, 2007, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.image;

import static sun.java2d.StateTrackable.State.*;

/**
 * This class extends <code>DataBuffer</code> and stores data internally
 * in <code>double</code> form.
 * <p>
 * <a name="optimizations">
 * Note that some implementations may function more efficiently
 * if they can maintain control over how the data for an image is
 * stored.
 * For example, optimizations such as caching an image in video
 * memory require that the implementation track all modifications
 * to that data.
 * Other implementations may operate better if they can store the
 * data in locations other than a Java array.
 * To maintain optimum compatibility with various optimizations
 * it is best to avoid constructors and methods which expose the
 * underlying storage as a Java array as noted below in the
 * documentation for those methods.
 * </a>
 *
 * @since 1.4
 */

public final class DataBufferDouble extends DataBuffer {

    /** The array of data banks. */
    double bankdata[][];

    /** A reference to the default data bank. */
    double data[];

    /**
     * Constructs a <code>double</code>-based <code>DataBuffer</code>
     * with a specified size.
     *
     * @param size The number of elements in the <code>DataBuffer</code>.
     */
    public DataBufferDouble(int size) {
        super(STABLE, TYPE_DOUBLE, size);
        data = new double[size];
        bankdata = new double[1][];
        bankdata[0] = data;
    }

    /**
     * Constructs a <code>double</code>-based <code>DataBuffer</code>
     * with a specified number of banks, all of which are of a
     * specified size.
     *
     * @param size The number of elements in each bank of the
     *        <code>DataBuffer</code>.
     * @param numBanks The number of banks in the <code>DataBuffer</code>.
     */
    public DataBufferDouble(int size, int numBanks) {
        super(STABLE, TYPE_DOUBLE, size, numBanks);
        bankdata = new double[numBanks][];
        for (int i= 0; i < numBanks; i++) {
            bankdata[i] = new double[size];
        }
        data = bankdata[0];
    }

    /**
     * Constructs a <code>double</code>-based <code>DataBuffer</code>
     * with the specified data array.  Only the first
     * <code>size</code> elements are available for use by this
     * <code>DataBuffer</code>.  The array must be large enough to
     * hold <code>size</code> elements.
     * <p>
     * Note that {@code DataBuffer} objects created by this constructor
     * may be incompatible with <a href="#optimizations">performance
     * optimizations</a> used by some implementations (such as caching
     * an associated image in video memory).
     *
     * @param dataArray An array of <code>double</code>s to be used as the
     *                  first and only bank of this <code>DataBuffer</code>.
     * @param size The number of elements of the array to be used.
     */
    public DataBufferDouble(double dataArray[], int size) {
        super(UNTRACKABLE, TYPE_DOUBLE, size);
        data = dataArray;
        bankdata = new double[1][];
        bankdata[0] = data;
    }

    /**
     * Constructs a <code>double</code>-based <code>DataBuffer</code>
     * with the specified data array.  Only the elements between
     * <code>offset</code> and <code>offset + size - 1</code> are
     * available for use by this <code>DataBuffer</code>.  The array
     * must be large enough to hold <code>offset + size</code> elements.
     * <p>
     * Note that {@code DataBuffer} objects created by this constructor
     * may be incompatible with <a href="#optimizations">performance
     * optimizations</a> used by some implementations (such as caching
     * an associated image in video memory).
     *
     * @param dataArray An array of <code>double</code>s to be used as the
     *                  first and only bank of this <code>DataBuffer</code>.
     * @param size The number of elements of the array to be used.
     * @param offset The offset of the first element of the array
     *               that will be used.
     */
    public DataBufferDouble(double dataArray[], int size, int offset) {
        super(UNTRACKABLE, TYPE_DOUBLE, size, 1, offset);
        data = dataArray;
        bankdata = new double[1][];
        bankdata[0] = data;
    }

    /**
     * Constructs a <code>double</code>-based <code>DataBuffer</code>
     * with the specified data arrays.  Only the first
     * <code>size</code> elements of each array are available for use
     * by this <code>DataBuffer</code>.  The number of banks will be
     * equal <code>to dataArray.length</code>.
     * <p>
     * Note that {@code DataBuffer} objects created by this constructor
     * may be incompatible with <a href="#optimizations">performance
     * optimizations</a> used by some implementations (such as caching
     * an associated image in video memory).
     *
     * @param dataArray An array of arrays of <code>double</code>s to be
     *        used as the banks of this <code>DataBuffer</code>.
     * @param size The number of elements of each array to be used.
     */
    public DataBufferDouble(double dataArray[][], int size) {
        super(UNTRACKABLE, TYPE_DOUBLE, size, dataArray.length);
        bankdata = (double[][]) dataArray.clone();
        data = bankdata[0];
    }

    /**
     * Constructs a <code>double</code>-based <code>DataBuffer</code>
     * with the specified data arrays, size, and per-bank offsets.
     * The number of banks is equal to dataArray.length.  Each array
     * must be at least as large as <code>size</code> plus the
     * corresponding offset.  There must be an entry in the
     * <code>offsets</code> array for each data array.
     * <p>
     * Note that {@code DataBuffer} objects created by this constructor
     * may be incompatible with <a href="#optimizations">performance
     * optimizations</a> used by some implementations (such as caching
     * an associated image in video memory).
     *
     * @param dataArray An array of arrays of <code>double</code>s to be
     *        used as the banks of this <code>DataBuffer</code>.
     * @param size The number of elements of each array to be used.
     * @param offsets An array of integer offsets, one for each bank.
     */
    public DataBufferDouble(double dataArray[][], int size, int offsets[]) {
        super(UNTRACKABLE, TYPE_DOUBLE, size, dataArray.length, offsets);
        bankdata = (double[][]) dataArray.clone();
        data = bankdata[0];
    }

    /**
     * Returns the default (first) <code>double</code> data array.
     * <p>
     * Note that calling this method may cause this {@code DataBuffer}
     * object to be incompatible with <a href="#optimizations">performance
     * optimizations</a> used by some implementations (such as caching
     * an associated image in video memory).
     *
     * @return the first double data array.
     */
    public double[] getData() {
        theTrackable.setUntrackable();
        return data;
    }

    /**
     * Returns the data array for the specified bank.
     * <p>
     * Note that calling this method may cause this {@code DataBuffer}
     * object to be incompatible with <a href="#optimizations">performance
     * optimizations</a> used by some implementations (such as caching
     * an associated image in video memory).
     *
     * @param bank the data array
     * @return the data array specified by <code>bank</code>.
     */
    public double[] getData(int bank) {
        theTrackable.setUntrackable();
        return bankdata[bank];
    }

    /**
     * Returns the data array for all banks.
     * <p>
     * Note that calling this method may cause this {@code DataBuffer}
     * object to be incompatible with <a href="#optimizations">performance
     * optimizations</a> used by some implementations (such as caching
     * an associated image in video memory).
     *
     * @return all data arrays from this data buffer.
     */
    public double[][] getBankData() {
        theTrackable.setUntrackable();
        return (double[][]) bankdata.clone();
    }

    /**
     * Returns the requested data array element from the first
     * (default) bank as an <code>int</code>.
     *
     * @param i The desired data array element.
     * @return The data entry as an <code>int</code>.
     * @see #setElem(int, int)
     * @see #setElem(int, int, int)
     */
    public int getElem(int i) {
        return (int)(data[i+offset]);
    }

    /**
     * Returns the requested data array element from the specified
     * bank as an <code>int</code>.
     *
     * @param bank The bank number.
     * @param i The desired data array element.
     *
     * @return The data entry as an <code>int</code>.
     * @see #setElem(int, int)
     * @see #setElem(int, int, int)
     */
    public int getElem(int bank, int i) {
        return (int)(bankdata[bank][i+offsets[bank]]);
    }

    /**
     * Sets the requested data array element in the first (default)
     * bank to the given <code>int</code>.
     *
     * @param i The desired data array element.
     * @param val The value to be set.
     * @see #getElem(int)
     * @see #getElem(int, int)
     */
    public void setElem(int i, int val) {
        data[i+offset] = (double)val;
        theTrackable.markDirty();
    }

    /**
     * Sets the requested data array element in the specified bank
     * to the given <code>int</code>.
     *
     * @param bank The bank number.
     * @param i The desired data array element.
     * @param val The value to be set.
     * @see #getElem(int)
     * @see #getElem(int, int)
     */
    public void setElem(int bank, int i, int val) {
        bankdata[bank][i+offsets[bank]] = (double)val;
        theTrackable.markDirty();
    }

    /**
     * Returns the requested data array element from the first
     * (default) bank as a <code>float</code>.
     *
     * @param i The desired data array element.
     *
     * @return The data entry as a <code>float</code>.
     * @see #setElemFloat(int, float)
     * @see #setElemFloat(int, int, float)
     */
    public float getElemFloat(int i) {
        return (float)data[i+offset];
    }

    /**
     * Returns the requested data array element from the specified
     * bank as a <code>float</code>.
     *
     * @param bank The bank number.
     * @param i The desired data array element.
     *
     * @return The data entry as a <code>float</code>.
     * @see #setElemFloat(int, float)
     * @see #setElemFloat(int, int, float)
     */
    public float getElemFloat(int bank, int i) {
        return (float)bankdata[bank][i+offsets[bank]];
    }

    /**
     * Sets the requested data array element in the first (default)
     * bank to the given <code>float</code>.
     *
     * @param i The desired data array element.
     * @param val The value to be set.
     * @see #getElemFloat(int)
     * @see #getElemFloat(int, int)
     */
    public void setElemFloat(int i, float val) {
        data[i+offset] = (double)val;
        theTrackable.markDirty();
    }

    /**
     * Sets the requested data array element in the specified bank to
     * the given <code>float</code>.
     *
     * @param bank The bank number.
     * @param i The desired data array element.
     * @param val The value to be set.
     * @see #getElemFloat(int)
     * @see #getElemFloat(int, int)
     */
    public void setElemFloat(int bank, int i, float val) {
        bankdata[bank][i+offsets[bank]] = (double)val;
        theTrackable.markDirty();
    }

    /**
     * Returns the requested data array element from the first
     * (default) bank as a <code>double</code>.
     *
     * @param i The desired data array element.
     *
     * @return The data entry as a <code>double</code>.
     * @see #setElemDouble(int, double)
     * @see #setElemDouble(int, int, double)
     */
    public double getElemDouble(int i) {
        return data[i+offset];
    }

    /**
     * Returns the requested data array element from the specified
     * bank as a <code>double</code>.
     *
     * @param bank The bank number.
     * @param i The desired data array element.
     *
     * @return The data entry as a <code>double</code>.
     * @see #setElemDouble(int, double)
     * @see #setElemDouble(int, int, double)
     */
    public double getElemDouble(int bank, int i) {
        return bankdata[bank][i+offsets[bank]];
    }

    /**
     * Sets the requested data array element in the first (default)
     * bank to the given <code>double</code>.
     *
     * @param i The desired data array element.
     * @param val The value to be set.
     * @see #getElemDouble(int)
     * @see #getElemDouble(int, int)
     */
    public void setElemDouble(int i, double val) {
        data[i+offset] = val;
        theTrackable.markDirty();
    }

    /**
     * Sets the requested data array element in the specified bank to
     * the given <code>double</code>.
     *
     * @param bank The bank number.
     * @param i The desired data array element.
     * @param val The value to be set.
     * @see #getElemDouble(int)
     * @see #getElemDouble(int, int)
     */
    public void setElemDouble(int bank, int i, double val) {
        bankdata[bank][i+offsets[bank]] = val;
        theTrackable.markDirty();
    }
}
