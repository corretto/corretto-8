/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (C) 2004-2011
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sun.xml.internal.rngom.parse;

import com.sun.xml.internal.rngom.ast.builder.*;
import com.sun.xml.internal.rngom.ast.om.*;

/**
 * An input that can be turned into a RELAX NG pattern.
 *
 * <p>
 * This is either a RELAX NG schema in the XML format, or a RELAX NG
 * schema in the compact syntax.
 */
public interface Parseable {
    /**
     * Parses this {@link Parseable} object into a RELAX NG pattern.
     *
     * @param sb
     *      The builder of the schema object model. This object
     *      dictates how the actual pattern is constructed.
     *
     * @return
     *      a parsed object. Always returns a non-null valid object.
     */
    <P extends ParsedPattern> P parse(SchemaBuilder<?,P,?,?,?,?> sb) throws BuildException, IllegalSchemaException;

    /**
     * Called from {@link Include} in response to
     * {@link Include#endInclude(Parseable, String, String, Location, Annotations)}
     * to parse the included grammar.
     *
     * @param g
     *      receives the events from the included grammar.
     */
    <P extends ParsedPattern> P parseInclude(String uri, SchemaBuilder<?,P,?,?,?,?> f, IncludedGrammar<P,?,?,?,?> g, String inheritedNs)
        throws BuildException, IllegalSchemaException;

    /**
     * Called from {@link SchemaBuilder} in response to
     * {@link SchemaBuilder#makeExternalRef(Parseable, String, String, Scope, Location, Annotations)}
     * to parse the referenced grammar.
     *
     * @param f
     *      receives the events from the referenced grammar.
     */
    <P extends ParsedPattern> P parseExternal(String uri, SchemaBuilder<?,P,?,?,?,?> f, Scope s, String inheritedNs)
        throws BuildException, IllegalSchemaException;
}
