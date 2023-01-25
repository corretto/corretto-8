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

package com.sun.tools.internal.ws.util;

import com.sun.tools.internal.ws.spi.WSToolsObjectFactory;
import com.sun.tools.internal.ws.wscompile.WsgenTool;
import com.sun.tools.internal.ws.wscompile.WsimportTool;
import com.sun.xml.internal.ws.api.server.Container;

import java.io.OutputStream;

/**
 * Factory implementation class to instantiate concrete objects for JAX-WS tools.
 *
 * @author JAX-WS Development Team
 */
public class WSToolsObjectFactoryImpl extends WSToolsObjectFactory {

    @Override
    public boolean wsimport(OutputStream logStream, Container container, String[] args) {
        WsimportTool tool = new WsimportTool(logStream, container);
        return tool.run(args);
    }

    @Override
    public boolean wsgen(OutputStream logStream, Container container, String[] args) {
        WsgenTool tool = new WsgenTool(logStream, container);
        return tool.run(args);
    }
}
