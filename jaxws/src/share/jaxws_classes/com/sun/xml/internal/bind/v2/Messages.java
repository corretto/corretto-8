/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Formats error messages.
 */
public enum Messages {
    ILLEGAL_ENTRY,          // 1 arg
    ERROR_LOADING_CLASS,    // 2 args
    INVALID_PROPERTY_VALUE, // 2 args
    UNSUPPORTED_PROPERTY,   // 1 arg
    BROKEN_CONTEXTPATH,     // 1 arg
    NO_DEFAULT_CONSTRUCTOR_IN_INNER_CLASS, // 1 arg
    INVALID_TYPE_IN_MAP, // 0args
    INVALID_JAXP_IMPLEMENTATION, // 1 arg
    JAXP_SUPPORTED_PROPERTY, // 1 arg
    JAXP_UNSUPPORTED_PROPERTY, // 1 arg
    JAXP_XML_SECURITY_DISABLED, // no arg
    JAXP_EXTERNAL_ACCESS_CONFIGURED, // no arg
    ;

    private static final ResourceBundle rb = ResourceBundle.getBundle(Messages.class.getName());

    public String toString() {
        return format();
    }

    public String format( Object... args ) {
        return MessageFormat.format( rb.getString(name()), args );
    }
}
