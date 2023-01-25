/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8059100
 * @summary Test that you can decrease NMT tracking level but not increase it.
 * @key nmt
 * @library /testlibrary /testlibrary/whitebox
 * @build ChangeTrackingLevel
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail ChangeTrackingLevel
 */

import com.oracle.java.testlibrary.*;
import sun.hotspot.WhiteBox;

public class ChangeTrackingLevel {

    public static WhiteBox wb = WhiteBox.getWhiteBox();
    public static void main(String args[]) throws Exception {
        boolean testChangeLevel = wb.NMTChangeTrackingLevel();
        if (testChangeLevel) {
            System.out.println("NMT level change test passed.");
        } else {
            // it also fails if the VM asserts.
            throw new RuntimeException("NMT level change test failed");
        }
    }
};
