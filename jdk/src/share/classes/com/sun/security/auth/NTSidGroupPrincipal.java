/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.auth;

/**
 * <p> This class extends <code>NTSid</code>
 * and represents one of the groups to which a Windows NT user belongs.
 *
 * <p> Principals such as this <code>NTSidGroupPrincipal</code>
 * may be associated with a particular <code>Subject</code>
 * to augment that <code>Subject</code> with an additional
 * identity.  Refer to the <code>Subject</code> class for more information
 * on how to achieve this.  Authorization decisions can then be based upon
 * the Principals associated with a <code>Subject</code>.
 *
 * @see java.security.Principal
 * @see javax.security.auth.Subject
 * @see com.sun.security.auth.NTSid
 */
@jdk.Exported
public class NTSidGroupPrincipal extends NTSid {

    private static final long serialVersionUID = -1373347438636198229L;

    /**
     * Create an <code>NTSidGroupPrincipal</code> with a Windows NT group name.
     *
     * <p>
     *
     * @param name the Windows NT group SID for this user. <p>
     *
     * @exception NullPointerException if the <code>name</code>
     *                  is <code>null</code>.
     */
    public NTSidGroupPrincipal(String name) {
        super(name);
    }

    /**
     * Return a string representation of this <code>NTSidGroupPrincipal</code>.
     *
     * <p>
     *
     * @return a string representation of this <code>NTSidGroupPrincipal</code>.
     */
    public String toString() {
        java.text.MessageFormat form = new java.text.MessageFormat
                (sun.security.util.ResourcesMgr.getString
                        ("NTSidGroupPrincipal.name",
                        "sun.security.util.AuthResources"));
        Object[] source = {getName()};
        return form.format(source);
    }

    /**
     * Compares the specified Object with this <code>NTSidGroupPrincipal</code>
     * for equality.  Returns true if the given object is also a
     * <code>NTSidGroupPrincipal</code> and the two NTSidGroupPrincipals
     * have the same SID.
     *
     * <p>
     *
     * @param o Object to be compared for equality with this
     *          <code>NTSidGroupPrincipal</code>.
     *
     * @return true if the specified Object is equal equal to this
     *          <code>NTSidGroupPrincipal</code>.
     */
    public boolean equals(Object o) {
            if (o == null)
                return false;

        if (this == o)
            return true;

        if (!(o instanceof NTSidGroupPrincipal))
            return false;

        return super.equals(o);
    }

}
