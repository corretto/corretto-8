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
import java.awt.image.BufferedImage;
import java.beans.PropertyVetoException;

import javax.swing.*;
import javax.swing.plaf.*;

import sun.swing.SwingUtilities2;

/**
 * From MacDockIconUI
 *
 * A JRSUI L&F implementation of JInternalFrame.JDesktopIcon
 * @author
 * @version
 */
public class AquaInternalFrameDockIconUI extends DesktopIconUI implements MouseListener, MouseMotionListener, ComponentListener {
    private static final String CACHED_FRAME_ICON_KEY = "apple.laf.internal.frameIcon";

    protected JInternalFrame.JDesktopIcon fDesktopIcon;
    protected JInternalFrame fFrame;
    protected ScaledImageLabel fIconPane;
    protected DockLabel fDockLabel;
    protected boolean fTrackingIcon = false;

    public static ComponentUI createUI(final JComponent c) {
        return new AquaInternalFrameDockIconUI();
    }

    public void installUI(final JComponent c) {
        fDesktopIcon = (JInternalFrame.JDesktopIcon)c;
        installComponents();
        installListeners();
    }

    public void uninstallUI(final JComponent c) {
        uninstallComponents();
        uninstallListeners();
        fDesktopIcon = null;
        fFrame = null;
    }

    protected void installComponents() {
        fFrame = fDesktopIcon.getInternalFrame();
        fIconPane = new ScaledImageLabel();
        fDesktopIcon.setLayout(new BorderLayout());
        fDesktopIcon.add(fIconPane, BorderLayout.CENTER);
    }

    protected void uninstallComponents() {
        fDesktopIcon.setLayout(null);
        fDesktopIcon.remove(fIconPane);
    }

    protected void installListeners() {
        fDesktopIcon.addMouseListener(this);
        fDesktopIcon.addMouseMotionListener(this);
        fFrame.addComponentListener(this);
    }

    protected void uninstallListeners() {
        fFrame.removeComponentListener(this);
        fDesktopIcon.removeMouseMotionListener(this);
        fDesktopIcon.removeMouseListener(this);
    }

    public Dimension getMinimumSize(final JComponent c) {
        return new Dimension(32, 32);
    }

    public Dimension getMaximumSize(final JComponent c) {
        return new Dimension(128, 128);
    }

    public Dimension getPreferredSize(final JComponent c) {
        return new Dimension(64, 64); //$ Dock preferred size
    }

    public Insets getInsets(final JComponent c) {
        return new Insets(0, 0, 0, 0);
    }

    void updateIcon() {
        fIconPane.updateIcon();
    }

    public void mousePressed(final MouseEvent e) {
        fTrackingIcon = fIconPane.mouseInIcon(e);
        if (fTrackingIcon) fIconPane.repaint();
    }

    public void mouseReleased(final MouseEvent e) {// only when it's actually in the image
        if (fFrame.isIconifiable() && fFrame.isIcon()) {
            if (fTrackingIcon) {
                fTrackingIcon = false;
                if (fIconPane.mouseInIcon(e)) {
                    if (fDockLabel != null) fDockLabel.hide();
                    try {
                        fFrame.setIcon(false);
                    } catch(final PropertyVetoException e2) {}
                } else {
                    fIconPane.repaint();
                }
            }
        }

        // if the mouse was completely outside fIconPane, hide the label
        if (fDockLabel != null && !fIconPane.getBounds().contains(e.getX(), e.getY())) fDockLabel.hide();
    }

    public void mouseEntered(final MouseEvent e) {
        if ((e.getModifiers() & InputEvent.BUTTON1_MASK) != 0) return;
        String title = fFrame.getTitle();
        if (title == null || title.equals("")) title = "Untitled";
        fDockLabel = new DockLabel(title);
        fDockLabel.show(fDesktopIcon);
    }

    public void mouseExited(final MouseEvent e) {
        if (fDockLabel != null && (e.getModifiers() & InputEvent.BUTTON1_MASK) == 0) fDockLabel.hide();
    }

    public void mouseClicked(final MouseEvent e) { }

    public void mouseDragged(final MouseEvent e) { }

    public void mouseMoved(final MouseEvent e) { }

    public void componentHidden(final ComponentEvent e) { }

    public void componentMoved(final ComponentEvent e) { }

    public void componentResized(final ComponentEvent e) {
        fFrame.putClientProperty(CACHED_FRAME_ICON_KEY, null);
    }

    public void componentShown(final ComponentEvent e) {
        fFrame.putClientProperty(CACHED_FRAME_ICON_KEY, null);
    }

    class ScaledImageLabel extends JLabel {
        ScaledImageLabel() {
            super(null, null, CENTER);
        }

        void updateIcon() {
            final Object priorIcon = fFrame.getClientProperty(CACHED_FRAME_ICON_KEY);
            if (priorIcon instanceof ImageIcon) {
                setIcon((ImageIcon)priorIcon);
                return;
            }

            int width = fFrame.getWidth();
            int height = fFrame.getHeight();

            // Protect us from unsized frames, like in JCK test DefaultDesktopManager2008
            if (width <= 0 || height <= 0) {
                width = 128;
                height = 128;
            }

            final Image fImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            final Graphics g = fImage.getGraphics();
            fFrame.paint(g);
            g.dispose();

            final float scale = (float)fDesktopIcon.getWidth() / (float)Math.max(width, height) * 0.89f;
            // Sending in -1 for width xor height causes it to maintain aspect ratio
            final ImageIcon icon = new ImageIcon(fImage.getScaledInstance((int)(width * scale), -1, Image.SCALE_SMOOTH));
            fFrame.putClientProperty(CACHED_FRAME_ICON_KEY, icon);
            setIcon(icon);
        }

        public void paint(final Graphics g) {
            if (getIcon() == null) updateIcon();

            g.translate(0, 2);

            if (!fTrackingIcon) {
                super.paint(g);
                return;
            }

            final ImageIcon prev = (ImageIcon)getIcon();
            final ImageIcon pressedIcon = new ImageIcon(AquaUtils.generateSelectedDarkImage(prev.getImage()));
            setIcon(pressedIcon);
            super.paint(g);
            setIcon(prev);
        }

        boolean mouseInIcon(final MouseEvent e) {
            return getBounds().contains(e.getX(), e.getY());
        }

        public Dimension getPreferredSize() {
            return new Dimension(64, 64); //$ Dock preferred size
        }
    }

    class DockLabel extends JLabel {
        final static int NUB_HEIGHT = 7;
        final static int ROUND_ADDITIONAL_HEIGHT = 8;
        final static int ROUND_ADDITIONAL_WIDTH = 12;

        DockLabel(final String text) {
            super(text);
            setBorder(null);
            setOpaque(false);
            setFont(AquaFonts.getDockIconFont());

            final FontMetrics metrics = getFontMetrics(getFont());
            setSize(SwingUtilities.computeStringWidth(metrics, getText()) + ROUND_ADDITIONAL_WIDTH * 2, metrics.getAscent() + NUB_HEIGHT + ROUND_ADDITIONAL_HEIGHT);
        }

        public void paint(final Graphics g) {
            final int width = getWidth();
            final int height = getHeight();

            final Font font = getFont();
            final FontMetrics metrics = getFontMetrics(font);
            g.setFont(font);

            final String text = getText().trim();
            final int ascent = metrics.getAscent();

            final Rectangle2D stringBounds = metrics.getStringBounds(text, g);
            final int halfway = width / 2;

            final int x = (halfway - (int)stringBounds.getWidth() / 2);

            final Graphics2D g2d = g instanceof Graphics2D ? (Graphics2D)g : null;
            if (g2d != null) {
                g.setColor(UIManager.getColor("DesktopIcon.labelBackground"));
                final Object origAA = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                final int roundHeight = height - ROUND_ADDITIONAL_HEIGHT + 1;
                g.fillRoundRect(0, 0, width, roundHeight, roundHeight, roundHeight);

                final int[] xpts = { halfway, halfway + NUB_HEIGHT, halfway - NUB_HEIGHT };
                final int[] ypts = { height, height - NUB_HEIGHT, height - NUB_HEIGHT };
                g.fillPolygon(xpts, ypts, 3);

                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, origAA);
            }

            g.setColor(Color.black);
            SwingUtilities2.drawString(this, g, text, x, 2 + ascent);
            g.setColor(Color.white);
            SwingUtilities2.drawString(this, g, text, x, 1 + ascent);
        }

        public void show(final Component invoker) {
            final int desiredLocationX = (invoker.getWidth() - getWidth()) / 2;
            final int desiredLocationY = -(getHeight() + 6);

            Container parent = invoker.getParent();

            for (Container p = parent; p != null; p = p.getParent()) {
                if (p instanceof JRootPane) {
                    if (p.getParent() instanceof JInternalFrame) continue;
                    parent = ((JRootPane)p).getLayeredPane();
                    for (p = parent.getParent(); p != null && (!(p instanceof java.awt.Window)); p = p.getParent());
                    break;
                }
            }

            final Point p = SwingUtilities.convertPoint(invoker, desiredLocationX, desiredLocationY, parent);
            setLocation(p.x, p.y);
            if (parent instanceof JLayeredPane) {
                ((JLayeredPane)parent).add(this, JLayeredPane.POPUP_LAYER, 0);
            }
        }

        public void hide() {
            final Container parent = getParent();
            final Rectangle r = this.getBounds();
            if (parent == null) return;
            parent.remove(this);
            parent.repaint(r.x, r.y, r.width, r.height);
        }
    }
}
