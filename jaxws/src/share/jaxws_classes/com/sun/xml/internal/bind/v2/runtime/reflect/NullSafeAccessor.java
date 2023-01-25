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

package com.sun.xml.internal.bind.v2.runtime.reflect;

import com.sun.xml.internal.bind.api.AccessorException;

/**
 * {@link Accessor} wrapper that replaces a null with an empty collection.
 *
 * <p>
 * This is so that JAX-WS property accessor will work like an ordinary getter.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class NullSafeAccessor<B,V,P> extends Accessor<B,V> {
    private final Accessor<B,V> core;
    private final Lister<B,V,?,P> lister;

    public NullSafeAccessor(Accessor<B,V> core, Lister<B,V,?,P> lister) {
        super(core.getValueType());
        this.core = core;
        this.lister = lister;
    }

    public V get(B bean) throws AccessorException {
        V v = core.get(bean);
        if(v==null) {
            // creates a new object
            P pack = lister.startPacking(bean,core);
            lister.endPacking(pack,bean,core);
            v = core.get(bean);
        }
        return v;
    }

    public void set(B bean, V value) throws AccessorException {
        core.set(bean,value);
    }
}
