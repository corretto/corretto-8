/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8055008
 * @summary Redefine EMCP and non-EMCP methods that are running in an infinite loop
 * @library /testlibrary
 * @build RedefineClassHelper
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar RedefineRunningMethods
 */
public class RedefineRunningMethods {

    public static String newB =
                "class RedefineRunningMethods$B {" +
                "   static int count1 = 0;" +
                "   static int count2 = 0;" +
                "   public static volatile boolean stop = false;" +
                "  static void localSleep() { " +
                "    try{ " +
                "      Thread.currentThread().sleep(10);" +
                "    } catch(InterruptedException ie) { " +
                "    } " +
                " } " +
                "   public static void infinite() { " +
                "       System.out.println(\"infinite called\");" +
                "   }" +
                "   public static void infinite_emcp() { " +
                "       while (!stop) { count2++; localSleep(); }" +
                "   }" +
                "}";

    public static String evenNewerB =
                "class RedefineRunningMethods$B {" +
                "   static int count1 = 0;" +
                "   static int count2 = 0;" +
                "   public static volatile boolean stop = false;" +
                "  static void localSleep() { " +
                "    try{ " +
                "      Thread.currentThread().sleep(1);" +
                "    } catch(InterruptedException ie) { " +
                "    } " +
                " } " +
                "   public static void infinite() { }" +
                "   public static void infinite_emcp() { " +
                "       System.out.println(\"infinite_emcp now obsolete called\");" +
                "   }" +
                "}";

    static class B {
        static int count1 = 0;
        static int count2 = 0;
        public static volatile boolean stop = false;
        static void localSleep() {
          try{
            Thread.currentThread().sleep(10);//sleep for 10 ms
          } catch(InterruptedException ie) {
          }
        }

        public static void infinite() {
            while (!stop) { count1++; localSleep(); }
        }
        public static void infinite_emcp() {
            while (!stop) { count2++; localSleep(); }
        }
    }


    public static void main(String[] args) throws Exception {

        new Thread() {
            public void run() {
                B.infinite();
            }
        }.start();

        new Thread() {
            public void run() {
                B.infinite_emcp();
            }
        }.start();

        RedefineClassHelper.redefineClass(B.class, newB);

        System.gc();

        B.infinite();

        // Start a thread with the second version of infinite_emcp running
        new Thread() {
            public void run() {
                B.infinite_emcp();
            }
        }.start();

        for (int i = 0; i < 20 ; i++) {
            String s = new String("some garbage");
            System.gc();
        }

        RedefineClassHelper.redefineClass(B.class, evenNewerB);
        System.gc();

        for (int i = 0; i < 20 ; i++) {
            B.infinite();
            String s = new String("some garbage");
            System.gc();
        }

        B.infinite_emcp();

        // purge should clean everything up.
        B.stop = true;

        for (int i = 0; i < 20 ; i++) {
            B.infinite();
            String s = new String("some garbage");
            System.gc();
        }
    }
}
