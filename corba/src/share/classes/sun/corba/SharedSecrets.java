/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.corba;

import com.sun.corba.se.impl.io.ValueUtility;
import sun.misc.JavaOISAccess;
import sun.misc.Unsafe;

import java.lang.reflect.Method;

/** A repository of "shared secrets", which are a mechanism for
    calling implementation-private methods in another package without
    using reflection. A package-private class implements a public
    interface and provides the ability to call package-private methods
    within that package; the object implementing that interface is
    provided through a third package to which access is restricted.
    This framework avoids the primary disadvantage of using reflection
    for this purpose, namely the loss of compile-time checking. */

// SharedSecrets cloned in corba repo to avoid build issues
public class SharedSecrets {
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static JavaCorbaAccess javaCorbaAccess;
    private static final Method getJavaOISAccessMethod;
    private static JavaOISAccess javaOISAccess;

    // Initialize getJavaOISAccessMethod using reflection.
    static {
        try {
            Class sharedSecret = Class.forName("sun.misc.SharedSecrets");
            getJavaOISAccessMethod =
                    sharedSecret.getMethod("getJavaOISAccess");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static JavaOISAccess getJavaOISAccess() {
        if (javaOISAccess == null) {
            try {
                javaOISAccess =
                        (JavaOISAccess) getJavaOISAccessMethod.invoke(null);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        return javaOISAccess;
    }

    public static JavaCorbaAccess getJavaCorbaAccess() {
        if (javaCorbaAccess == null) {
            // Ensure ValueUtility is initialized; we know that that class
            // provides the shared secret
            unsafe.ensureClassInitialized(ValueUtility.class);
        }
        return javaCorbaAccess;
    }

    public static void setJavaCorbaAccess(JavaCorbaAccess access) {
        javaCorbaAccess = access;
    }
}
