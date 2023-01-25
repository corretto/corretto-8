/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/* @test TestVerifySubSet.java
 * @key gc
 * @bug 8072725
 * @summary Test VerifySubSet option
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.OutputAnalyzer;
import com.oracle.java.testlibrary.ProcessTools;
import java.util.ArrayList;
import java.util.Collections;

class RunSystemGC {
    public static void main(String args[]) throws Exception {
        System.gc();
    }
}

public class TestVerifySubSet {
    private static String[] getTestJavaOpts() {
        String testVmOptsStr = System.getProperty("test.java.opts");
        if (!testVmOptsStr.isEmpty()) {
            return testVmOptsStr.split(" ");
        } else {
            return new String[] {};
        }
    }

    private static OutputAnalyzer runTest(String subset) throws Exception {
        ArrayList<String> vmOpts = new ArrayList();

        Collections.addAll(vmOpts, getTestJavaOpts());
        Collections.addAll(vmOpts, new String[] {"-XX:+UnlockDiagnosticVMOptions",
                                                 "-XX:+VerifyBeforeGC",
                                                 "-XX:+VerifyAfterGC",
                                                 "-XX:VerifySubSet="+subset,
                                                 RunSystemGC.class.getName()});
        ProcessBuilder pb =
            ProcessTools.createJavaProcessBuilder(vmOpts.toArray(new String[vmOpts.size()]));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        System.out.println("Output:\n" + output.getOutput());
        return output;
    }

    public static void main(String args[]) throws Exception {

        OutputAnalyzer output;

        output = runTest("heap, threads, codecache, metaspace");
        output.shouldContain("Heap");
        output.shouldContain("Threads");
        output.shouldContain("CodeCache");
        output.shouldContain("MetaspaceAux");
        output.shouldNotContain("SymbolTable");
        output.shouldNotContain("StringTable");
        output.shouldNotContain("SystemDictionary");
        output.shouldNotContain("CodeCache Oops");
        output.shouldHaveExitValue(0);

        output = runTest("hello, threads, codecache, metaspace");
        output.shouldContain("memory sub-system is unknown, please correct it");
        output.shouldNotContain("Threads");
        output.shouldNotContain("CodeCache");
        output.shouldNotContain("MetaspaceAux");
        output.shouldHaveExitValue(1);
    }
}
