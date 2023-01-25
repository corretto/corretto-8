/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.api;

/**
 * Represents the spec version constant.
 *
 * @author Kohsuke Kawaguchi
 */
public enum SpecVersion {
    V2_0, V2_1, V2_2;

    /**
     * Returns true if this version is equal or later than the given one.
     */
    public boolean isLaterThan(SpecVersion t) {
        return this.ordinal()>=t.ordinal();
    }

    /**
     * Parses "2.0", "2.1", and "2.2" into the {@link SpecVersion} object.
     *
     * @return null for parsing failure.
     */
    public static SpecVersion parse(String token) {
        if(token.equals("2.0"))
            return V2_0;
        if(token.equals("2.1"))
            return V2_1;
        if(token.equals("2.2"))
            return V2_2;
        return null;
    }

    public static final SpecVersion LATEST = V2_2;
}
