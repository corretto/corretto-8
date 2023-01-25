/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.wsdl.document;

import javax.xml.namespace.QName;

/**
 * Interface defining WSDL-related constants.
 *
 * @author WS Development Team
 */
public interface WSDLConstants {

    // namespace URIs
    static final String NS_XMLNS = "http://www.w3.org/2000/xmlns/";
    static final String NS_WSDL = "http://schemas.xmlsoap.org/wsdl/";

    // QNames
    static final QName QNAME_BINDING = new QName(NS_WSDL, "binding");
    static final QName QNAME_DEFINITIONS = new QName(NS_WSDL, "definitions");
    static final QName QNAME_DOCUMENTATION = new QName(NS_WSDL, "documentation");
    static final QName QNAME_FAULT = new QName(NS_WSDL, "fault");
    static final QName QNAME_IMPORT = new QName(NS_WSDL, "import");
    static final QName QNAME_INPUT = new QName(NS_WSDL, "input");
    static final QName QNAME_MESSAGE = new QName(NS_WSDL, "message");
    static final QName QNAME_OPERATION = new QName(NS_WSDL, "operation");
    static final QName QNAME_OUTPUT = new QName(NS_WSDL, "output");
    static final QName QNAME_PART = new QName(NS_WSDL, "part");
    static final QName QNAME_PORT = new QName(NS_WSDL, "port");
    static final QName QNAME_PORT_TYPE = new QName(NS_WSDL, "portType");
    static final QName QNAME_SERVICE = new QName(NS_WSDL, "service");
    static final QName QNAME_TYPES = new QName(NS_WSDL, "types");

    static final QName QNAME_ATTR_ARRAY_TYPE = new QName(NS_WSDL, "arrayType");
}
