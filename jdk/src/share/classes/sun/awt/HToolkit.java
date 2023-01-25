/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt;

import sun.awt.datatransfer.DataTransferer;

import java.awt.*;
import java.awt.dnd.*;
import java.awt.dnd.peer.DragSourceContextPeer;
import java.awt.im.InputMethodHighlight;
import java.awt.im.spi.InputMethodDescriptor;
import java.awt.image.*;
import java.awt.datatransfer.Clipboard;
import java.awt.peer.*;
import java.util.Map;
import java.util.Properties;

/*
 * HToolkit is a platform independent Toolkit used
 * with the HeadlessToolkit.  It is primarily used
 * in embedded JRE's that do not have sun/awt/X11 classes.
 */
public class HToolkit extends SunToolkit
    implements ComponentFactory {

    private static final KeyboardFocusManagerPeer kfmPeer = new KeyboardFocusManagerPeer() {
        public void setCurrentFocusedWindow(Window win) {}
        public Window getCurrentFocusedWindow() { return null; }
        public void setCurrentFocusOwner(Component comp) {}
        public Component getCurrentFocusOwner() { return null; }
        public void clearGlobalFocusOwner(Window activeWindow) {}
    };

    public HToolkit() {
    }

    /*
     * Component peer objects - unsupported.
     */

    public WindowPeer createWindow(Window target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public FramePeer createLightweightFrame(LightweightFrame target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public FramePeer createFrame(Frame target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public DialogPeer createDialog(Dialog target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public ButtonPeer createButton(Button target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public TextFieldPeer createTextField(TextField target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public ChoicePeer createChoice(Choice target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public LabelPeer createLabel(Label target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public ListPeer createList(List target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public CheckboxPeer createCheckbox(Checkbox target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public ScrollbarPeer createScrollbar(Scrollbar target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public ScrollPanePeer createScrollPane(ScrollPane target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public TextAreaPeer createTextArea(TextArea target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public FileDialogPeer createFileDialog(FileDialog target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public MenuBarPeer createMenuBar(MenuBar target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public MenuPeer createMenu(Menu target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public PopupMenuPeer createPopupMenu(PopupMenu target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public MenuItemPeer createMenuItem(MenuItem target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public CheckboxMenuItemPeer createCheckboxMenuItem(CheckboxMenuItem target)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public DragSourceContextPeer createDragSourceContextPeer(
        DragGestureEvent dge)
        throws InvalidDnDOperationException {
        throw new InvalidDnDOperationException("Headless environment");
    }

    public RobotPeer createRobot(Robot target, GraphicsDevice screen)
        throws AWTException, HeadlessException {
        throw new HeadlessException();
    }

    public KeyboardFocusManagerPeer getKeyboardFocusManagerPeer() {
        // See 6833019.
        return kfmPeer;
    }

    public TrayIconPeer createTrayIcon(TrayIcon target)
      throws HeadlessException {
        throw new HeadlessException();
    }

    public SystemTrayPeer createSystemTray(SystemTray target)
      throws HeadlessException {
        throw new HeadlessException();
    }

    public boolean isTraySupported() {
        return false;
    }

    @Override
    public DataTransferer getDataTransferer() {
        return null;
    }

    public GlobalCursorManager getGlobalCursorManager()
        throws HeadlessException {
        throw new HeadlessException();
    }

    /*
     * Headless toolkit - unsupported.
     */
    protected void loadSystemColors(int[] systemColors)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public ColorModel getColorModel()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public int getScreenResolution()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public Map mapInputMethodHighlight(InputMethodHighlight highlight)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public int getMenuShortcutKeyMask()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public boolean getLockingKeyState(int keyCode)
        throws UnsupportedOperationException {
        throw new HeadlessException();
    }

    public void setLockingKeyState(int keyCode, boolean on)
        throws UnsupportedOperationException {
        throw new HeadlessException();
    }

    public Cursor createCustomCursor(Image cursor, Point hotSpot, String name)
        throws IndexOutOfBoundsException, HeadlessException {
        throw new HeadlessException();
    }

    public Dimension getBestCursorSize(int preferredWidth, int preferredHeight)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public int getMaximumCursorColors()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public <T extends DragGestureRecognizer> T
        createDragGestureRecognizer(Class<T> abstractRecognizerClass,
                                    DragSource ds, Component c,
                                    int srcActions, DragGestureListener dgl)
    {
        return null;
    }

    public int getScreenHeight()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public int getScreenWidth()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public Dimension getScreenSize()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public Insets getScreenInsets(GraphicsConfiguration gc)
        throws HeadlessException {
        throw new HeadlessException();
    }

    public void setDynamicLayout(boolean dynamic)
        throws HeadlessException {
        throw new HeadlessException();
    }

    protected boolean isDynamicLayoutSet()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public boolean isDynamicLayoutActive()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public Clipboard getSystemClipboard()
        throws HeadlessException {
        throw new HeadlessException();
    }

    /*
     * Printing
     */
    public PrintJob getPrintJob(Frame frame, String jobtitle,
        JobAttributes jobAttributes,
        PageAttributes pageAttributes) {
        if (frame != null) {
            // Should never happen
            throw new HeadlessException();
        }
        throw new IllegalArgumentException(
                "PrintJob not supported in a headless environment");
    }

    public PrintJob getPrintJob(Frame frame, String doctitle, Properties props)
    {
        if (frame != null) {
            // Should never happen
            throw new HeadlessException();
        }
        throw new IllegalArgumentException(
                "PrintJob not supported in a headless environment");
    }

    /*
     * Headless toolkit - supported.
     */

    public void sync() {
        // Do nothing
    }

    protected boolean syncNativeQueue(final long timeout) {
        return false;
    }

    public void beep() {
        // Send alert character
        System.out.write(0x07);
    }


    /*
     * Fonts
     */
    public FontPeer getFontPeer(String name, int style) {
        return (FontPeer)null;
    }

    /*
     * Modality
     */
    public boolean isModalityTypeSupported(Dialog.ModalityType modalityType) {
        return false;
    }

    public boolean isModalExclusionTypeSupported(Dialog.ModalExclusionType exclusionType) {
        return false;
    }

    public boolean isDesktopSupported() {
        return false;
    }

    public DesktopPeer createDesktopPeer(Desktop target)
    throws HeadlessException{
        throw new HeadlessException();
    }

    public boolean isWindowOpacityControlSupported() {
        return false;
    }

    public boolean isWindowShapingSupported() {
        return false;
    }

    public boolean isWindowTranslucencySupported() {
        return false;
    }

    public  void grab(Window w) { }

    public void ungrab(Window w) { }

    protected boolean syncNativeQueue() { return false; }

    public InputMethodDescriptor getInputMethodAdapterDescriptor()
        throws AWTException
    {
        return (InputMethodDescriptor)null;
    }
}
