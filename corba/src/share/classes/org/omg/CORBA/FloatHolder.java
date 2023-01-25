/*
 * Copyright (c) 1995, 2001, Oracle and/or its affiliates. All rights reserved.
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

package org.omg.CORBA;

import org.omg.CORBA.portable.Streamable;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;


/**
 * The Holder for <tt>Float</tt>.  For more information on
 * Holder files, see <a href="doc-files/generatedfiles.html#holder">
 * "Generated Files: Holder Files"</a>.<P>
 * A Holder class for a <code>float</code>
 * that is used to store "out" and "inout" parameters in IDL methods.
 * If an IDL method signature has an IDL <code>float</code> as an "out"
 * or "inout" parameter, the programmer must pass an instance of
 * <code>FloatHolder</code> as the corresponding
 * parameter in the method invocation; for "inout" parameters, the programmer
 * must also fill the "in" value to be sent to the server.
 * Before the method invocation returns, the ORB will fill in the
 * value corresponding to the "out" value returned from the server.
 * <P>
 * If <code>myFloatHolder</code> is an instance of <code>FloatHolder</code>,
 * the value stored in its <code>value</code> field can be accessed with
 * <code>myFloatHolder.value</code>.
 *
 * @since       JDK1.2
 */
public final class FloatHolder implements Streamable {
    /**
     * The <code>float</code> value held by this <code>FloatHolder</code>
     * object.
     */
    public float value;

    /**
     * Constructs a new <code>FloatHolder</code> object with its
     * <code>value</code> field initialized to 0.0.
     */
    public FloatHolder() {
    }

    /**
     * Constructs a new <code>FloatHolder</code> object for the given
     * <code>float</code>.
     * @param initial the <code>float</code> with which to initialize
     *                the <code>value</code> field of the new
     *                <code>FloatHolder</code> object
     */
    public FloatHolder(float initial) {
        value = initial;
    }

    /**
     * Read a float from an input stream and initialize the value
     * member with the float value.
     *
     * @param input the <code>InputStream</code> to read from.
     */
    public void _read(InputStream input) {
        value = input.read_float();
    }

    /**
     * Write the float value into an output stream.
     *
     * @param output the <code>OutputStream</code> to write into.
     */
    public void _write(OutputStream output) {
        output.write_float(value);
    }

    /**
     * Return the <code>TypeCode</code> of this Streamable.
     *
     * @return the <code>TypeCode</code> object.
     */
    public org.omg.CORBA.TypeCode _type() {
        return ORB.init().get_primitive_tc(TCKind.tk_float);
    }
}
