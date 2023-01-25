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
 * @bug 8001071
 * @summary Add simple range check into VM implemenation of Unsafe access methods
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.*;
import sun.misc.Unsafe;

public class RangeCheck {

    public static void main(String args[]) throws Exception {
        if (!Platform.isDebugBuild()) {
            System.out.println("Testing assert which requires a debug build. Passing silently.");
            return;
        }

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                true,
                "-Xmx32m",
                "-XX:-TransmitErrorReport",
                DummyClassWithMainRangeCheck.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldMatch("assert\\(byte_offset < p_size\\) failed: Unsafe access: offset \\d+ > object's size \\d+");
    }

    public static class DummyClassWithMainRangeCheck {
        public static void main(String args[]) throws Exception {
            Unsafe unsafe = Utils.getUnsafe();
            unsafe.getObject(new DummyClassWithMainRangeCheck(), Short.MAX_VALUE);
        }
    }
}
