/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil;

import org.omg.CORBA.ORB;
import java.io.Serializable;
import java.util.Hashtable;
import com.sun.corba.se.impl.io.TypeMismatchException;
import java.net.MalformedURLException;
import com.sun.corba.se.impl.util.RepositoryId;

/**
 * Delegates to the current RepositoryId implementation in
 * com.sun.corba.se.impl.util.  This is necessary to
 * overcome the fact that many of RepositoryId's methods
 * are static.
 */
public final class RepIdDelegator
    implements RepositoryIdStrings,
               RepositoryIdUtility,
               RepositoryIdInterface
{
    // RepositoryIdFactory methods

    public String createForAnyType(Class type) {
        return RepositoryId.createForAnyType(type);
    }

    public String createForJavaType(Serializable ser)
        throws TypeMismatchException
    {
        return RepositoryId.createForJavaType(ser);
    }

    public String createForJavaType(Class clz)
        throws TypeMismatchException
    {
        return RepositoryId.createForJavaType(clz);
    }

    public String createSequenceRepID(java.lang.Object ser) {
        return RepositoryId.createSequenceRepID(ser);
    }

    public String createSequenceRepID(Class clazz) {
        return RepositoryId.createSequenceRepID(clazz);
    }

    public RepositoryIdInterface getFromString(String repIdString) {
        return new RepIdDelegator(RepositoryId.cache.getId(repIdString));
    }

    // RepositoryIdUtility methods

    public boolean isChunkedEncoding(int valueTag) {
        return RepositoryId.isChunkedEncoding(valueTag);
    }

    public boolean isCodeBasePresent(int valueTag) {
        return RepositoryId.isCodeBasePresent(valueTag);
    }

    public String getClassDescValueRepId() {
        return RepositoryId.kClassDescValueRepID;
    }

    public String getWStringValueRepId() {
        return RepositoryId.kWStringValueRepID;
    }

    public int getTypeInfo(int valueTag) {
        return RepositoryId.getTypeInfo(valueTag);
    }

    public int getStandardRMIChunkedNoRepStrId() {
        return RepositoryId.kPreComputed_StandardRMIChunked_NoRep;
    }

    public int getCodeBaseRMIChunkedNoRepStrId() {
        return RepositoryId.kPreComputed_CodeBaseRMIChunked_NoRep;
    }

    public int getStandardRMIChunkedId() {
        return RepositoryId.kPreComputed_StandardRMIChunked;
    }

    public int getCodeBaseRMIChunkedId() {
        return RepositoryId.kPreComputed_CodeBaseRMIChunked;
    }

    public int getStandardRMIUnchunkedId() {
        return RepositoryId.kPreComputed_StandardRMIUnchunked;
    }

    public int getCodeBaseRMIUnchunkedId() {
        return RepositoryId.kPreComputed_CodeBaseRMIUnchunked;
    }

    public int getStandardRMIUnchunkedNoRepStrId() {
        return RepositoryId.kPreComputed_StandardRMIUnchunked_NoRep;
    }

    public int getCodeBaseRMIUnchunkedNoRepStrId() {
        return RepositoryId.kPreComputed_CodeBaseRMIUnchunked_NoRep;
    }

    // RepositoryIdInterface methods

    public Class getClassFromType() throws ClassNotFoundException {
        return delegate.getClassFromType();
    }

    public Class getClassFromType(String codebaseURL)
        throws ClassNotFoundException, MalformedURLException
    {
        return delegate.getClassFromType(codebaseURL);
    }

    public Class getClassFromType(Class expectedType,
                                  String codebaseURL)
        throws ClassNotFoundException, MalformedURLException
    {
        return delegate.getClassFromType(expectedType, codebaseURL);
    }

    public String getClassName() {
        return delegate.getClassName();
    }

    // Constructor used for factory/utility cases
    public RepIdDelegator() {
        this(null);
    }

    // Constructor used by getIdFromString.  All non-static
    // RepositoryId methods will use the provided delegate.
    private RepIdDelegator(RepositoryId _delegate) {
        this.delegate = _delegate;
    }

    private final RepositoryId delegate;

    public String toString() {
        if (delegate != null)
            return delegate.toString();
        else
            return this.getClass().getName();
    }

    public boolean equals(Object obj) {
        if (delegate != null)
            return delegate.equals(obj);
        else
            return super.equals(obj);
    }

    public int hashCode() {
        if (delegate != null) {
            return delegate.hashCode();
        } else {
            return super.hashCode();
        }
    }
}
