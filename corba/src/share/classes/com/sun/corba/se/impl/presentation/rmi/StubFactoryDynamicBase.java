/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.presentation.rmi ;

import java.io.SerializablePermission;
import java.lang.reflect.InvocationHandler ;
import java.lang.reflect.Proxy ;

import com.sun.corba.se.spi.presentation.rmi.PresentationManager ;
import com.sun.corba.se.spi.presentation.rmi.DynamicStub ;

import com.sun.corba.se.spi.orbutil.proxy.InvocationHandlerFactory ;
import com.sun.corba.se.spi.orbutil.proxy.LinkedInvocationHandler ;

public abstract class StubFactoryDynamicBase extends StubFactoryBase
{
    protected final ClassLoader loader ;

    private static Void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SerializablePermission(
                    "enableSubclassImplementation"));
        }
        return null;
    }

    private StubFactoryDynamicBase(Void unused,
            PresentationManager.ClassData classData, ClassLoader loader) {
        super(classData);
        // this.loader must not be null, or the newProxyInstance call
        // will fail.
        if (loader == null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null)
                cl = ClassLoader.getSystemClassLoader();
            this.loader = cl ;
        } else {
            this.loader = loader ;
        }
    }

    public StubFactoryDynamicBase( PresentationManager.ClassData classData,
        ClassLoader loader )
    {
        this (checkPermission(), classData, loader);
    }

    public abstract org.omg.CORBA.Object makeStub() ;
}
