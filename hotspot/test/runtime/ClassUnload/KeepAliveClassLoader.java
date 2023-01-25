/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test KeepAliveClassLoader
 * @summary This test case uses a java.lang.ClassLoader instance to keep a class alive.
 * @library /testlibrary /testlibrary/whitebox /runtime/testlibrary
 * @library classes
 * @build KeepAliveClassLoader test.Empty
 * @build ClassUnloadCommon
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xmn8m -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI KeepAliveClassLoader
 */

import sun.hotspot.WhiteBox;

/**
 * Test that verifies that classes are not unloaded when specific types of references are kept to them.
 */
public class KeepAliveClassLoader {
  private static final String className = "test.Empty";
  private static final WhiteBox wb = WhiteBox.getWhiteBox();
  public static Object escape = null;


  public static void main(String... args) throws Exception {
    ClassLoader cl = ClassUnloadCommon.newClassLoader();
    Class<?> c = cl.loadClass(className);
    Object o = c.newInstance();
    o = null; c = null;
    escape = cl;

    {
        boolean isAlive = wb.isClassAlive(className);
        System.out.println("testClassLoader (1) alive: " + isAlive);

        ClassUnloadCommon.failIf(!isAlive, "should be alive");
    }
    ClassUnloadCommon.triggerUnloading();

    {
        boolean isAlive = wb.isClassAlive(className);
        System.out.println("testClassLoader (2) alive: " + isAlive);

        ClassUnloadCommon.failIf(!isAlive, "should be alive");
    }
    cl = null;
    escape = null;
    ClassUnloadCommon.triggerUnloading();

    {
        boolean isAlive = wb.isClassAlive(className);
        System.out.println("testClassLoader (3) alive: " + isAlive);
        ClassUnloadCommon.failIf(isAlive, "should be unloaded");
    }

  }
}
