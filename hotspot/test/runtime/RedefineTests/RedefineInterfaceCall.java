/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8174962
 * @summary Redefine class with interface method call
 * @library /testlibrary
 * @build RedefineClassHelper
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar -XX:TraceRedefineClasses=3174407 RedefineInterfaceCall
 */

import static com.oracle.java.testlibrary.Asserts.assertEquals;

interface I1 { default int m() { return 0; } }
interface I2 extends I1 {}

public class RedefineInterfaceCall {

    public static class C implements I2 {
        public int test(I2 i) {
            return i.m(); // invokeinterface cpCacheEntry
        }
    }

    static String newI1 =
      "interface I1 { default int m() { return 1; } }";

    static String newC =
        "public class RedefineInterfaceCall$C implements I2 { " +
        "  public int test(I2 i) { " +
        "    return i.m(); " +
        "  } " +
        "} ";

    static int test(I2 i) {
        return i.m(); // invokeinterface cpCacheEntry
    }

    public static void main(String[] args) throws Exception {
        C c = new C();

        assertEquals(test(c),   0);
        assertEquals(c.test(c), 0);

        RedefineClassHelper.redefineClass(C.class, newC);

        assertEquals(c.test(c), 0);

        RedefineClassHelper.redefineClass(I1.class, newI1);

        assertEquals(test(c),   1);
        assertEquals(c.test(c), 1);

        RedefineClassHelper.redefineClass(C.class, newC);

        assertEquals(c.test(c), 1);
    }
}
