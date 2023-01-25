/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidAlgorithmParameterException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.X509CertSelector;
import java.security.cert.X509CRLSelector;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;

import sun.security.util.Cache;

/**
 * Helper used by URICertStore and others when delegating to another CertStore
 * to fetch certs and CRLs.
 */

public abstract class CertStoreHelper {

    private static final int NUM_TYPES = 2;
    private final static Map<String,String> classMap = new HashMap<>(NUM_TYPES);
    static {
        classMap.put(
            "LDAP",
            "sun.security.provider.certpath.ldap.LDAPCertStoreHelper");
        classMap.put(
            "SSLServer",
            "sun.security.provider.certpath.ssl.SSLServerCertStoreHelper");
    };
    private static Cache<String, CertStoreHelper> cache
        = Cache.newSoftMemoryCache(NUM_TYPES);

    public static CertStoreHelper getInstance(final String type)
        throws NoSuchAlgorithmException
    {
        CertStoreHelper helper = cache.get(type);
        if (helper != null) {
            return helper;
        }
        final String cl = classMap.get(type);
        if (cl == null) {
            throw new NoSuchAlgorithmException(type + " not available");
        }
        try {
            helper = AccessController.doPrivileged(
                new PrivilegedExceptionAction<CertStoreHelper>() {
                    public CertStoreHelper run() throws ClassNotFoundException {
                        try {
                            Class<?> c = Class.forName(cl, true, null);
                            CertStoreHelper csh
                                = (CertStoreHelper)c.newInstance();
                            cache.put(type, csh);
                            return csh;
                        } catch (InstantiationException |
                                 IllegalAccessException e) {
                            throw new AssertionError(e);
                        }
                    }
            });
            return helper;
        } catch (PrivilegedActionException e) {
            throw new NoSuchAlgorithmException(type + " not available",
                                               e.getException());
        }
    }

    static boolean isCausedByNetworkIssue(String type, CertStoreException cse) {
        switch (type) {
            case "LDAP":
            case "SSLServer":
                try {
                    CertStoreHelper csh = CertStoreHelper.getInstance(type);
                    return csh.isCausedByNetworkIssue(cse);
                } catch (NoSuchAlgorithmException nsae) {
                    return false;
                }
            case "URI":
                Throwable t = cse.getCause();
                return (t != null && t instanceof IOException);
            default:
                // we don't know about any other remote CertStore types
                return false;
        }
    }

    /**
     * Returns a CertStore using the given URI as parameters.
     */
    public abstract CertStore getCertStore(URI uri)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException;

    /**
     * Wraps an existing X509CertSelector when needing to avoid DN matching
     * issues.
     */
    public abstract X509CertSelector wrap(X509CertSelector selector,
                          X500Principal certSubject,
                          String dn)
        throws IOException;

    /**
     * Wraps an existing X509CRLSelector when needing to avoid DN matching
     * issues.
     */
    public abstract X509CRLSelector wrap(X509CRLSelector selector,
                         Collection<X500Principal> certIssuers,
                         String dn)
        throws IOException;

    /**
     * Returns true if the cause of the CertStoreException is a network
     * related issue.
     */
    public abstract boolean isCausedByNetworkIssue(CertStoreException e);
}
