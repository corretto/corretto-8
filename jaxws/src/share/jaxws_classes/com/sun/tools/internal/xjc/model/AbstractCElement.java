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

package com.sun.tools.internal.xjc.model;

import javax.xml.bind.annotation.XmlTransient;

import com.sun.xml.internal.xsom.XSComponent;

import org.xml.sax.Locator;

/**
 * Partial default implementation of {@link CElement}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractCElement extends AbstractCTypeInfoImpl implements CElement {

    /**
     * The location in the source file where this class was declared.
     */
    @XmlTransient
    private final Locator locator;

    private boolean isAbstract;

    protected AbstractCElement(Model model, XSComponent source, Locator locator, CCustomizations customizations) {
        super(model, source, customizations);
        this.locator = locator;
    }

    public Locator getLocator() {
        return locator;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract() {
        isAbstract = true;
    }
}
