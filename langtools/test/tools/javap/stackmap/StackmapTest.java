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
 * @test
 * @bug 6271292
 * @summary Verify that javap prints StackMapTable attribute contents
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main StackmapTest
 */

import java.util.Arrays;
import java.util.List;

//original test: test/tools/javap/stackmap/T6271292.sh
public class StackmapTest {

    private static final String TestSrc =
        "public class Test extends SuperClass {\n" +
        "    public static void main(String[] args) {\n" +
        "        new SuperClass((args[0].equals(\"0\")) ? 0 : 1)\n" +
        "            .test();\n" +
        "    }\n" +
        "    Test(boolean b) {\n" +
        "        super(b ? 1 : 2);\n" +
        "    }\n" +
        "}\n" +
        "class SuperClass {\n" +
        "    double d;\n" +
        "    SuperClass(double dd) { d = dd; }\n" +
        "    double test() {\n" +
        "        if (d == 0)\n" +
        "            return d;\n" +
        "        else\n" +
        "            return d > 0 ? d++ : d--;\n" +
        "    }\n" +
        "}\n";

    private static final String goldenOut =
        "frame_type = 255 /* full_frame */\n" +
        "frame_type = 255 /* full_frame */\n" +
        "frame_type = 73 /* same_locals_1_stack_item */\n" +
        "frame_type = 255 /* full_frame */\n" +
        "offset_delta = 19\n" +
        "offset_delta = 0\n" +
        "offset_delta = 2\n" +
        "stack = [ uninitialized 0, uninitialized 0 ]\n" +
        "stack = [ uninitialized 0, uninitialized 0, double ]\n" +
        "stack = [ this ]\n" +
        "stack = [ this, double ]\n" +
        "locals = [ class \"[Ljava/lang/String;\" ]\n" +
        "locals = [ class \"[Ljava/lang/String;\" ]\n" +
        "locals = [ this, int ]\n";

    public static void main(String[] args) throws Exception {
        //        @compile T6271292.java
        ToolBox.JavaToolArgs javacParams =
                new ToolBox.JavaToolArgs().setSources(TestSrc);
        ToolBox.javac(javacParams);

//        "${TESTJAVA}${FS}bin${FS}javap" ${TESTTOOLVMOPTS} -classpath "${TESTCLASSES}" -verbose T6271292 > "${JAVAPFILE}"
        ToolBox.JavaToolArgs javapParams =
                new ToolBox.JavaToolArgs()
                .setAllArgs("-v", "Test.class");
        String out = ToolBox.javap(javapParams);
        List<String> grepResult = ToolBox.grep("frame_type", out,
                ToolBox.lineSeparator);
        grepResult.addAll(ToolBox.grep("offset_delta", out, ToolBox.lineSeparator));
        grepResult.addAll(ToolBox.grep("stack = ", out, ToolBox.lineSeparator));
        grepResult.addAll(ToolBox.grep("locals = ", out, ToolBox.lineSeparator));
        List<String> goldenList = Arrays.asList(goldenOut.split("\n"));

//        diff -w "${OUTFILE}" "${TESTSRC}${FS}T6271292.out"
        ToolBox.compareLines(goldenList, grepResult, true);
    }

}
