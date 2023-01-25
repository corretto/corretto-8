/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.impl;

import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeRef;

/**
 * @author Kohsuke Kawaguchi
 */
class TypeRefImpl<TypeT,ClassDeclT> implements TypeRef<TypeT,ClassDeclT> {
    private final QName elementName;
    private final TypeT type;
    protected final ElementPropertyInfoImpl<TypeT,ClassDeclT,?,?> owner;
    private NonElement<TypeT,ClassDeclT> ref;
    private final boolean isNillable;
    private String defaultValue;

    public TypeRefImpl(ElementPropertyInfoImpl<TypeT, ClassDeclT, ?, ?> owner, QName elementName, TypeT type, boolean isNillable, String defaultValue) {
        this.owner = owner;
        this.elementName = elementName;
        this.type = type;
        this.isNillable = isNillable;
        this.defaultValue = defaultValue;
        assert owner!=null;
        assert elementName!=null;
        assert type!=null;
    }

    public NonElement<TypeT,ClassDeclT> getTarget() {
        if(ref==null)
            calcRef();
        return ref;
    }

    public QName getTagName() {
        return elementName;
    }

    public boolean isNillable() {
        return isNillable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    protected void link() {
        // if we have'nt computed the ref yet, do so now.
        calcRef();
    }

    private void calcRef() {
        // we can't do this eagerly because of a cyclic dependency
        ref = owner.parent.builder.getTypeInfo(type,owner);
        assert ref!=null;
    }

    public PropertyInfo<TypeT,ClassDeclT> getSource() {
        return owner;
    }
}
