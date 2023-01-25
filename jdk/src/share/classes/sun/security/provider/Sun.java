/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.util.*;
import java.security.*;

import sun.security.action.PutAllAction;

/**
 * The SUN Security Provider.
 *
 */
public final class Sun extends Provider {

    private static final long serialVersionUID = 6440182097568097204L;

    private static final String INFO = "SUN " +
    "(DSA key/parameter generation; DSA signing; SHA-1, MD5 digests; " +
    "SecureRandom; X.509 certificates; JKS & DKS keystores; " +
    "PKIX CertPathValidator; " +
    "PKIX CertPathBuilder; LDAP, Collection CertStores, JavaPolicy Policy; " +
    "JavaLoginConfig Configuration)";

    public Sun() {
        /* We are the SUN provider */
        super("SUN", 1.8d, INFO);

        // if there is no security manager installed, put directly into
        // the provider. Otherwise, create a temporary map and use a
        // doPrivileged() call at the end to transfer the contents
        if (System.getSecurityManager() == null) {
            SunEntries.putEntries(this);
        } else {
            // use LinkedHashMap to preserve the order of the PRNGs
            Map<Object, Object> map = new LinkedHashMap<>();
            SunEntries.putEntries(map);
            AccessController.doPrivileged(new PutAllAction(this, map));
        }
    }

}
