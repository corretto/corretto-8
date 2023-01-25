/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.apple.laf;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.beans.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.text.*;

public class AquaCaret extends DefaultCaret implements UIResource, PropertyChangeListener {
    final boolean isMultiLineEditor;
    final JTextComponent c;

    boolean mFocused = false;

    public AquaCaret(final Window inParentWindow, final JTextComponent inComponent) {
        super();
        c = inComponent;
        isMultiLineEditor = (c instanceof JTextArea || c instanceof JEditorPane);
        inComponent.addPropertyChangeListener(this);
    }

    protected Highlighter.HighlightPainter getSelectionPainter() {
        return AquaHighlighter.getInstance();
    }

    /**
     * Only show the flashing caret if the selection range is zero
     */
    public void setVisible(boolean e) {
        if (e) e = getDot() == getMark();
        super.setVisible(e);
    }

    protected void fireStateChanged() {
        // If we have focus the caret should only flash if the range length is zero
        if (mFocused) setVisible(getComponent().isEditable());

        super.fireStateChanged();
    }

    public void propertyChange(final PropertyChangeEvent evt) {
        final String propertyName = evt.getPropertyName();

        if (AquaFocusHandler.FRAME_ACTIVE_PROPERTY.equals(propertyName)) {
            final JTextComponent comp = ((JTextComponent)evt.getSource());

            if (evt.getNewValue() == Boolean.TRUE) {
                setVisible(comp.hasFocus());
            } else {
                setVisible(false);
            }

            if (getDot() != getMark()) comp.getUI().damageRange(comp, getDot(), getMark());
        }
    }

    // --- FocusListener methods --------------------------

    private boolean shouldSelectAllOnFocus = true;
    public void focusGained(final FocusEvent e) {
        final JTextComponent component = getComponent();
        if (!component.isEnabled() || !component.isEditable()) {
            super.focusGained(e);
            return;
        }

        mFocused = true;
        if (!shouldSelectAllOnFocus) {
            shouldSelectAllOnFocus = true;
            super.focusGained(e);
            return;
        }

        if (isMultiLineEditor) {
            super.focusGained(e);
            return;
        }

        final int end = component.getDocument().getLength();
        final int dot = getDot();
        final int mark = getMark();
        if (dot == mark) {
            if (dot == 0) {
                component.setCaretPosition(end);
                component.moveCaretPosition(0);
            } else if (dot == end) {
                component.setCaretPosition(0);
                component.moveCaretPosition(end);
            }
        }

        super.focusGained(e);
    }

    public void focusLost(final FocusEvent e) {
        mFocused = false;
        shouldSelectAllOnFocus = true;
        if (isMultiLineEditor) {
            setVisible(false);
            c.repaint();
        } else {
            super.focusLost(e);
        }
    }

    // This fixes the problem where when on the mac you have to ctrl left click to
    // get popup triggers the caret has code that only looks at button number.
    // see radar # 3125390
    public void mousePressed(final MouseEvent e) {
        if (!e.isPopupTrigger()) {
            super.mousePressed(e);
            shouldSelectAllOnFocus = false;
        }
    }

    /**
     * Damages the area surrounding the caret to cause
     * it to be repainted in a new location.  If paint()
     * is reimplemented, this method should also be
     * reimplemented.  This method should update the
     * caret bounds (x, y, width, and height).
     *
     * @param r  the current location of the caret
     * @see #paint
     */
    protected synchronized void damage(final Rectangle r) {
        if (r == null || fPainting) return;

        x = r.x - 4;
        y = r.y;
        width = 10;
        height = r.height;

        // Don't damage the border area.  We can't paint a partial border, so get the
        // intersection of the caret rectangle and the component less the border, if any.
        final Rectangle caretRect = new Rectangle(x, y, width, height);
        final Border border = getComponent().getBorder();
        if (border != null) {
            final Rectangle alloc = getComponent().getBounds();
            alloc.x = alloc.y = 0;
            final Insets borderInsets = border.getBorderInsets(getComponent());
            alloc.x += borderInsets.left;
            alloc.y += borderInsets.top;
            alloc.width -= borderInsets.left + borderInsets.right;
            alloc.height -= borderInsets.top + borderInsets.bottom;
            Rectangle2D.intersect(caretRect, alloc, caretRect);
        }
        x = caretRect.x;
        y = caretRect.y;
        width = Math.max(caretRect.width, 1);
        height = Math.max(caretRect.height, 1);
        repaint();
    }

    boolean fPainting = false;

    // See <rdar://problem/3833837> 1.4.2_05-141.3: JTextField performance with Aqua L&F
    // We are getting into a circular condition with the BasicCaret paint code since it doesn't know about the fact that our
    // damage routine above elminates the border. Sadly we can't easily change either one, so we will
    // add a painting flag and not damage during a repaint.
    public void paint(final Graphics g) {
        if (isVisible()) {
            fPainting = true;
            super.paint(g);
            fPainting = false;
        }
    }
}
