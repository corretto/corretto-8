/*
 * Copyright (c) 2002, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc.resources;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for sun.misc.
 *
 * @author Michael Colburn
 */

public final class Messages extends java.util.ListResourceBundle {

    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     * <p>
     * @return the contents of this <code>ResourceBundle</code>.
     */
    protected Object[][] getContents() {
        return new Object[][]{
            {"optpkg.versionerror", "ERROR: Invalid version format used in {0} JAR file. Check the documentation for the supported version format."},
            {"optpkg.attributeerror", "ERROR: The required {0} JAR manifest attribute is not set in {1} JAR file."},
            {"optpkg.attributeserror", "ERROR: Some required JAR manifest attributes are not set in {0} JAR file."}
        };
    }

}
