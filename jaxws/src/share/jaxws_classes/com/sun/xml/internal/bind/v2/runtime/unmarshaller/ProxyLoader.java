/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import org.xml.sax.SAXException;

/**
 * {@link Loader} that delegates the processing to another {@link Loader}
 * at {@link #startElement(UnmarshallingContext.State, TagName)}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ProxyLoader extends Loader {
    public ProxyLoader() {
        super(false);
    }

    @Override
    public final void startElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
        Loader loader = selectLoader(state,ea);
        state.setLoader(loader);
        loader.startElement(state,ea);
    }

    /**
     * Picks the loader to delegate to.
     *
     * @return never null.
     */
    protected abstract Loader selectLoader(UnmarshallingContext.State state, TagName ea) throws SAXException;

    @Override
    public final void leaveElement(UnmarshallingContext.State state, TagName ea) {
        // this loader is used just to forward to another loader,
        // so we should never get this event.
        throw new IllegalStateException();
    }
}
