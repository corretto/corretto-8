/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @library /testlibrary
 * @run main/othervm -Xbatch -XX:+IgnoreUnrecognizedVMOptions -XX:+EliminateAutoBox
 *                   -XX:CompileOnly=::valueOf,::byteValue,::shortValue,::testUnsignedByte,::testUnsignedShort
 *                   UnsignedLoads
 */
import static com.oracle.java.testlibrary.Asserts.assertEQ;

public class UnsignedLoads {
    public static int testUnsignedByte() {
        byte[] bytes = new byte[] {-1};
        int res = 0;
        for (int i = 0; i < 100000; i++) {
            for (Byte b : bytes) {
                res = b & 0xff;
            }
        }
        return res;
    }

    public static int testUnsignedShort() {
        int res = 0;
        short[] shorts = new short[] {-1};
        for (int i = 0; i < 100000; i++) {
            for (Short s : shorts) {
                res = s & 0xffff;
            }
        }
        return res;
    }

    public static void main(String[] args) {
        assertEQ(testUnsignedByte(),    255);
        assertEQ(testUnsignedShort(), 65535);
        System.out.println("TEST PASSED");
    }
}
