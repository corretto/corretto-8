/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.awt;

import java.security.BasicPermission;

/**
 * This class is for AWT permissions.
 * An <code>AWTPermission</code> contains a target name but
 * no actions list; you either have the named permission
 * or you don't.
 *
 * <P>
 * The target name is the name of the AWT permission (see below). The naming
 * convention follows the hierarchical property naming convention.
 * Also, an asterisk could be used to represent all AWT permissions.
 *
 * <P>
 * The following table lists all the possible <code>AWTPermission</code>
 * target names, and for each provides a description of what the
 * permission allows and a discussion of the risks of granting code
 * the permission.
 *
 * <table border=1 cellpadding=5 summary="AWTPermission target names, descriptions, and associated risks.">
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 *
 * <tr>
 *   <td>accessClipboard</td>
 *   <td>Posting and retrieval of information to and from the AWT clipboard</td>
 *   <td>This would allow malfeasant code to share
 * potentially sensitive or confidential information.</td>
 * </tr>
 *
 * <tr>
 *   <td>accessEventQueue</td>
 *   <td>Access to the AWT event queue</td>
 *   <td>After retrieving the AWT event queue,
 * malicious code may peek at and even remove existing events
 * from its event queue, as well as post bogus events which may purposefully
 * cause the application or applet to misbehave in an insecure manner.</td>
 * </tr>
 *
 * <tr>
 *   <td>accessSystemTray</td>
 *   <td>Access to the AWT SystemTray instance</td>
 *   <td>This would allow malicious code to add tray icons to the system tray.
 * First, such an icon may look like the icon of some known application
 * (such as a firewall or anti-virus) and order a user to do something unsafe
 * (with help of balloon messages). Second, the system tray may be glutted with
 * tray icons so that no one could add a tray icon anymore.</td>
 * </tr>
 *
 * <tr>
 *   <td>createRobot</td>
 *   <td>Create java.awt.Robot objects</td>
 *   <td>The java.awt.Robot object allows code to generate native-level
 * mouse and keyboard events as well as read the screen. It could allow
 * malicious code to control the system, run other programs, read the
 * display, and deny mouse and keyboard access to the user.</td>
 * </tr>
 *
 * <tr>
 *   <td>fullScreenExclusive</td>
 *   <td>Enter full-screen exclusive mode</td>
 *   <td>Entering full-screen exclusive mode allows direct access to
 * low-level graphics card memory.  This could be used to spoof the
 * system, since the program is in direct control of rendering. Depending on
 * the implementation, the security warning may not be shown for the windows
 * used to enter the full-screen exclusive mode (assuming that the {@code
 * fullScreenExclusive} permission has been granted to this application). Note
 * that this behavior does not mean that the {@code
 * showWindowWithoutWarningBanner} permission will be automatically granted to
 * the application which has the {@code fullScreenExclusive} permission:
 * non-full-screen windows will continue to be shown with the security
 * warning.</td>
 * </tr>
 *
 * <tr>
 *   <td>listenToAllAWTEvents</td>
 *   <td>Listen to all AWT events, system-wide</td>
 *   <td>After adding an AWT event listener,
 * malicious code may scan all AWT events dispatched in the system,
 * allowing it to read all user input (such as passwords).  Each
 * AWT event listener is called from within the context of that
 * event queue's EventDispatchThread, so if the accessEventQueue
 * permission is also enabled, malicious code could modify the
 * contents of AWT event queues system-wide, causing the application
 * or applet to misbehave in an insecure manner.</td>
 * </tr>
 *
 * <tr>
 *   <td>readDisplayPixels</td>
 *   <td>Readback of pixels from the display screen</td>
 *   <td>Interfaces such as the java.awt.Composite interface or the
 * java.awt.Robot class allow arbitrary code to examine pixels on the
 * display enable malicious code to snoop on the activities of the user.</td>
 * </tr>
 *
 * <tr>
 *   <td>replaceKeyboardFocusManager</td>
 *   <td>Sets the <code>KeyboardFocusManager</code> for
 *       a particular thread.
 *   <td>When <code>SecurityManager</code> is installed, the invoking
 *       thread must be granted this permission in order to replace
 *       the current <code>KeyboardFocusManager</code>.  If permission
 *       is not granted, a <code>SecurityException</code> will be thrown.
 * </tr>
 *
 * <tr>
 *   <td>setAppletStub</td>
 *   <td>Setting the stub which implements Applet container services</td>
 *   <td>Malicious code could set an applet's stub and result in unexpected
 * behavior or denial of service to an applet.</td>
 * </tr>
 *
 * <tr>
 *   <td>setWindowAlwaysOnTop</td>
 *   <td>Setting always-on-top property of the window: {@link Window#setAlwaysOnTop}</td>
 *   <td>The malicious window might make itself look and behave like a real full desktop, so that
 * information entered by the unsuspecting user is captured and subsequently misused </td>
 * </tr>
 *
 * <tr>
 *   <td>showWindowWithoutWarningBanner</td>
 *   <td>Display of a window without also displaying a banner warning
 * that the window was created by an applet</td>
 *   <td>Without this warning,
 * an applet may pop up windows without the user knowing that they
 * belong to an applet.  Since users may make security-sensitive
 * decisions based on whether or not the window belongs to an applet
 * (entering a username and password into a dialog box, for example),
 * disabling this warning banner may allow applets to trick the user
 * into entering such information.</td>
 * </tr>
 *
 * <tr>
 *   <td>toolkitModality</td>
 *   <td>Creating {@link Dialog.ModalityType#TOOLKIT_MODAL TOOLKIT_MODAL} dialogs
 *       and setting the {@link Dialog.ModalExclusionType#TOOLKIT_EXCLUDE
 *       TOOLKIT_EXCLUDE} window property.</td>
 *   <td>When a toolkit-modal dialog is shown from an applet, it blocks all other
 * applets in the browser. When launching applications from Java Web Start,
 * its windows (such as the security dialog) may also be blocked by toolkit-modal
 * dialogs, shown from these applications.</td>
 * </tr>
 *
 * <tr>
 *   <td>watchMousePointer</td>
 *   <td>Getting the information about the mouse pointer position at any
 * time</td>
 *   <td>Constantly watching the mouse pointer,
 * an applet can make guesses about what the user is doing, i.e. moving
 * the mouse to the lower left corner of the screen most likely means that
 * the user is about to launch an application. If a virtual keypad is used
 * so that keyboard is emulated using the mouse, an applet may guess what
 * is being typed.</td>
 * </tr>
 * </table>
 *
 * @see java.security.BasicPermission
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 * @see java.lang.SecurityManager
 *
 *
 * @author Marianne Mueller
 * @author Roland Schemers
 */

public final class AWTPermission extends BasicPermission {

    /** use serialVersionUID from the Java 2 platform for interoperability */
    private static final long serialVersionUID = 8890392402588814465L;

    /**
     * Creates a new <code>AWTPermission</code> with the specified name.
     * The name is the symbolic name of the <code>AWTPermission</code>,
     * such as "topLevelWindow", "systemClipboard", etc. An asterisk
     * may be used to indicate all AWT permissions.
     *
     * @param name the name of the AWTPermission
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> is empty.
     */

    public AWTPermission(String name)
    {
        super(name);
    }

    /**
     * Creates a new <code>AWTPermission</code> object with the specified name.
     * The name is the symbolic name of the <code>AWTPermission</code>, and the
     * actions string is currently unused and should be <code>null</code>.
     *
     * @param name the name of the <code>AWTPermission</code>
     * @param actions should be <code>null</code>
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> is empty.
     */

    public AWTPermission(String name, String actions)
    {
        super(name, actions);
    }
}
