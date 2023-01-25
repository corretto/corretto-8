/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
  @test
  @bug 6566434
  @library ../../regtesthelpers
  @build Util Sysout AbstractTest
  @summary Choice in unfocusable window responds to keyboard
  @author Andrei Dmitriev: area=awt-choice
  @run main UnfocusableToplevel
*/

/**
 * UnfocusableToplevel.java
 *
 * summary:
 */

import java.awt.*;
import java.awt.event.*;
import test.java.awt.regtesthelpers.AbstractTest;
import test.java.awt.regtesthelpers.Sysout;
import test.java.awt.regtesthelpers.Util;

public class UnfocusableToplevel {

    final static Robot robot = Util.createRobot();
    final static int REASONABLE_PATH_TIME = 5000;

    public static void main(String []s)
    {
        Frame f = new Frame();
        Window w = new Window(f);
        final Choice ch = new Choice();

        ch.add("item 1");
        ch.add("item 2");
        ch.add("item 3");
        ch.add("item 4");
        ch.add("item 5");
        w.add(ch);
        w.setLayout(new FlowLayout());
        w.setSize(200, 200);

        ch.addKeyListener(new KeyAdapter(){
                public void keyTyped(KeyEvent e){
                    traceEvent("keytyped", e);
                }
                public void keyPressed(KeyEvent e){
                    traceEvent("keypress", e);
                }
                public void keyReleased(KeyEvent e){
                    traceEvent("keyrelease", e);
                }
        });

        ch.addItemListener(new ItemListener(){
                public void itemStateChanged(ItemEvent ie){
                    traceEvent("stateChanged", ie);
                }
            });

        w.setVisible(true);

        Util.waitForIdle(robot);

        Util.clickOnComp(ch, robot);
        Util.waitForIdle(robot);

        // will not test if the dropdown become opened as there is no reliable
        // technique to accomplish that rather then checking color of dropdown
        // Will suppose that the dropdown appears

        testKeys();
        Util.waitForIdle(robot);
    }

    private static void testKeys(){
        typeKey(KeyEvent.VK_UP);
        typeKey(KeyEvent.VK_DOWN);
        typeKey(KeyEvent.VK_K);
        typeKey(KeyEvent.VK_PAGE_UP);
        typeKey(KeyEvent.VK_PAGE_DOWN);
    }

    private static void typeKey(int keyChar){
        try {
            robot.keyPress(keyChar);
            robot.delay(5);
        } finally {
            robot.keyRelease(keyChar);
        }
        robot.delay(100);
    }

    private static void traceEvent(String message, AWTEvent e){
        AbstractTest.fail(message + " " + e.toString());
    }
}
