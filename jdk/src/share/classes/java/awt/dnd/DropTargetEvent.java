/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.dnd;

import java.util.EventObject;
import java.awt.dnd.DropTargetContext;

/**
 * The <code>DropTargetEvent</code> is the base
 * class for both the <code>DropTargetDragEvent</code>
 * and the <code>DropTargetDropEvent</code>.
 * It encapsulates the current state of the Drag and
 * Drop operations, in particular the current
 * <code>DropTargetContext</code>.
 *
 * @since 1.2
 *
 */

public class DropTargetEvent extends java.util.EventObject {

    private static final long serialVersionUID = 2821229066521922993L;

    /**
     * Construct a <code>DropTargetEvent</code> object with
     * the specified <code>DropTargetContext</code>.
     * <P>
     * @param dtc The <code>DropTargetContext</code>
     * @throws NullPointerException if {@code dtc} equals {@code null}.
     * @see #getSource()
     * @see #getDropTargetContext()
     */

    public DropTargetEvent(DropTargetContext dtc) {
        super(dtc.getDropTarget());

        context  = dtc;
    }

    /**
     * This method returns the <code>DropTargetContext</code>
     * associated with this <code>DropTargetEvent</code>.
     * <P>
     * @return the <code>DropTargetContext</code>
     */

    public DropTargetContext getDropTargetContext() {
        return context;
    }

    /**
     * The <code>DropTargetContext</code> associated with this
     * <code>DropTargetEvent</code>.
     *
     * @serial
     */
    protected DropTargetContext   context;
}
