/*
 * Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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
  test
  @bug       6396785
  @summary   MenuItem activated with space should swallow this space.
  @author    anton.tarasov@...: area=awt.focus
  @run       applet MenuItemActivatedTest.html
*/

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.applet.Applet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.InvocationTargetException;
import test.java.awt.regtesthelpers.Util;

public class MenuItemActivatedTest extends Applet {
    Robot robot;
    JFrame frame = new JFrame("Test Frame");
    JDialog dialog = new JDialog((Window)null, "Test Dialog", Dialog.ModalityType.DOCUMENT_MODAL);
    JTextField text = new JTextField();
    JMenuBar bar = new JMenuBar();
    JMenu menu = new JMenu("Menu");
    JMenuItem item = new JMenuItem("item");
    AtomicBoolean gotEvent = new AtomicBoolean(false);

    public static void main(String[] args) {
        MenuItemActivatedTest app = new MenuItemActivatedTest();
        app.init();
        app.start();
    }

    public void init() {
        robot = Util.createRobot();

        // Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        this.setLayout (new BorderLayout ());
        Sysout.createDialogWithInstructions(new String[]
            {"This is an automatic test. Simply wait until it is done."
            });
    }

    public void start() {
        menu.setMnemonic('f');
        menu.add(item);
        bar.add(menu);
        frame.setJMenuBar(bar);
        frame.pack();

        item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    dialog.add(text);
                    dialog.pack();
                dialog.setVisible(true);
                }
            });

        text.addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) {
                    if (e.getKeyChar() == ' ') {
                        Sysout.println(e.toString());
                        synchronized (gotEvent) {
                            gotEvent.set(true);
                            gotEvent.notifyAll();
                        }
                    }
                }
            });

        frame.setVisible(true);
        Util.waitForIdle(robot);

        robot.keyPress(KeyEvent.VK_ALT);
        robot.delay(20);
        robot.keyPress(KeyEvent.VK_F);
        robot.delay(20);
        robot.keyRelease(KeyEvent.VK_F);
        robot.delay(20);
        robot.keyRelease(KeyEvent.VK_ALT);
        Util.waitForIdle(robot);

        item.setSelected(true);
        Util.waitForIdle(robot);

        robot.keyPress(KeyEvent.VK_SPACE);
        robot.delay(20);
        robot.keyRelease(KeyEvent.VK_SPACE);

        if (Util.waitForCondition(gotEvent, 2000)) {
            throw new TestFailedException("a space went into the dialog's text field!");
        }

        Sysout.println("Test passed.");
    }
}

class TestFailedException extends RuntimeException {
    TestFailedException(String msg) {
        super("Test failed: " + msg);
    }
}

/****************************************************
 Standard Test Machinery
 DO NOT modify anything below -- it's a standard
  chunk of code whose purpose is to make user
  interaction uniform, and thereby make it simpler
  to read and understand someone else's test.
 ****************************************************/

/**
 This is part of the standard test machinery.
 It creates a dialog (with the instructions), and is the interface
  for sending text messages to the user.
 To print the instructions, send an array of strings to Sysout.createDialog
  WithInstructions method.  Put one line of instructions per array entry.
 To display a message for the tester to see, simply call Sysout.println
  with the string to be displayed.
 This mimics System.out.println but works within the test harness as well
  as standalone.
 */

class Sysout
{
    static TestDialog dialog;

    public static void createDialogWithInstructions( String[] instructions )
    {
        dialog = new TestDialog( new Frame(), "Instructions" );
        dialog.printInstructions( instructions );
//        dialog.setVisible(true);
        println( "Any messages for the tester will display here." );
    }

    public static void createDialog( )
    {
        dialog = new TestDialog( new Frame(), "Instructions" );
        String[] defInstr = { "Instructions will appear here. ", "" } ;
        dialog.printInstructions( defInstr );
//        dialog.setVisible(true);
        println( "Any messages for the tester will display here." );
    }


    public static void printInstructions( String[] instructions )
    {
        dialog.printInstructions( instructions );
    }


    public static void println( String messageIn )
    {
        dialog.displayMessage( messageIn );
    }

}// Sysout  class

/**
  This is part of the standard test machinery.  It provides a place for the
   test instructions to be displayed, and a place for interactive messages
   to the user to be displayed.
  To have the test instructions displayed, see Sysout.
  To have a message to the user be displayed, see Sysout.
  Do not call anything in this dialog directly.
  */
class TestDialog extends Dialog
{

    TextArea instructionsText;
    TextArea messageText;
    int maxStringLength = 80;

    //DO NOT call this directly, go through Sysout
    public TestDialog( Frame frame, String name )
    {
        super( frame, name );
        int scrollBoth = TextArea.SCROLLBARS_BOTH;
        instructionsText = new TextArea( "", 15, maxStringLength, scrollBoth );
        add( "North", instructionsText );

        messageText = new TextArea( "", 5, maxStringLength, scrollBoth );
        add("Center", messageText);

        pack();

//        setVisible(true);
    }// TestDialog()

    //DO NOT call this directly, go through Sysout
    public void printInstructions( String[] instructions )
    {
        //Clear out any current instructions
        instructionsText.setText( "" );

        //Go down array of instruction strings

        String printStr, remainingStr;
        for( int i=0; i < instructions.length; i++ )
        {
            //chop up each into pieces maxSringLength long
            remainingStr = instructions[ i ];
            while( remainingStr.length() > 0 )
            {
                //if longer than max then chop off first max chars to print
                if( remainingStr.length() >= maxStringLength )
                {
                    //Try to chop on a word boundary
                    int posOfSpace = remainingStr.
                        lastIndexOf( ' ', maxStringLength - 1 );

                    if( posOfSpace <= 0 ) posOfSpace = maxStringLength - 1;

                    printStr = remainingStr.substring( 0, posOfSpace + 1 );
                    remainingStr = remainingStr.substring( posOfSpace + 1 );
                }
                //else just print
                else
                {
                    printStr = remainingStr;
                    remainingStr = "";
                }

                instructionsText.append( printStr + "\n" );

            }// while

        }// for

    }//printInstructions()

    //DO NOT call this directly, go through Sysout
    public void displayMessage( String messageIn )
    {
        messageText.append( messageIn + "\n" );
        System.out.println(messageIn);
    }

}// TestDialog  class
