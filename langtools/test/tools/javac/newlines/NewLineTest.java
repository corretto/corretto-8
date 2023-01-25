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
 * @bug 4110560 4785453
 * @summary portability : javac.properties
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main NewLineTest
 */

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

//original test: test/tools/javac/newlines/Newlines.sh
public class NewLineTest {

    public static void main(String args[]) throws Exception {
//        "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -J-Dline.separator='@' > ${TMP1} 2>&1
        File javacErrOutput = new File("output.txt");
        ToolBox.AnyToolArgs cmdArgs =
                new ToolBox.AnyToolArgs(ToolBox.Expect.FAIL)
                .appendArgs(ToolBox.javacBinary)
                .appendArgs(ToolBox.testToolVMOpts)
                .appendArgs("-J-Dline.separator='@'")
                .setErrOutput(javacErrOutput);
        ToolBox.executeCommand(cmdArgs);

//        result=`cat ${TMP1} | wc -l`
//        if [ "$result" -eq 0 ] passed
        List<String> lines = Files.readAllLines(javacErrOutput.toPath(),
                Charset.defaultCharset());
        if (lines.size() != 1) {
            throw new AssertionError("The compiler output should have one line only");
        }
    }

}
