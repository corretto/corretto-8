/*
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @bug 8196969
 * @requires vm.hasSAandCanAttach
 * @library /test/lib
 * @run main/othervm ClhsdbJstackXcompStress
 */
public class ClhsdbJstackXcompStress {

    private static final int MAX_ITERATIONS = 20;
    private static final boolean DEBUG = false;

    private static boolean isMatchCompiledFrame(List<String> output) {
        List<String> filtered = output.stream().filter( s -> s.contains("Compiled frame"))
                                               .collect(Collectors.toList());
        System.out.println("DEBUG: " + filtered);
        return !filtered.isEmpty() &&
               filtered.stream().anyMatch( s -> s.contains("LingeredAppWithRecComputation") );
    }

    private static void runJstackInLoop(LingeredApp app) throws Exception {
        boolean anyMatchedCompiledFrame = false;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            JDKToolLauncher launcher = JDKToolLauncher
                    .createUsingTestJDK("jhsdb");
            launcher.addToolArg("jstack");
            launcher.addToolArg("--pid");
            launcher.addToolArg(Long.toString(app.getPid()));

            ProcessBuilder pb = new ProcessBuilder();
            pb.command(launcher.getCommand());
            Process jhsdb = pb.start();
            OutputAnalyzer out = new OutputAnalyzer(jhsdb);

            jhsdb.waitFor();

            if (DEBUG) {
                System.out.println(out.getStdout());
                System.err.println(out.getStderr());
            }

            out.stderrShouldBeEmpty(); // NPE's are reported on the err stream
            out.stdoutShouldNotContain("Error occurred during stack walking:");
            out.stdoutShouldContain(LingeredAppWithRecComputation.THREAD_NAME);
            List<String> stdoutList = Arrays.asList(out.getStdout().split("\\R"));
            anyMatchedCompiledFrame = anyMatchedCompiledFrame || isMatchCompiledFrame(stdoutList);
        }
        if (!anyMatchedCompiledFrame) {
             throw new RuntimeException("Expected jstack output to contain 'Compiled frame'");
        }
        System.out.println("DEBUG: jhsdb jstack did not throw NPE, as expected.");
    }

    public static void main(String... args) throws Exception {
        LingeredApp app = null;
        try {
            List<String> vmArgs = List.of("-Xcomp",
                                          "-XX:CompileCommand=dontinline,LingeredAppWithRecComputation.factorial",
                                          "-XX:CompileCommand=compileonly,LingeredAppWithRecComputation.testLoop",
                                          "-XX:CompileCommand=compileonly,LingeredAppWithRecComputation.factorial");
            app = new LingeredAppWithRecComputation();
            LingeredApp.startApp(vmArgs, app);
            System.out.println("Started LingeredAppWithRecComputation with pid " + app.getPid());
            runJstackInLoop(app);
            System.out.println("Test Completed");
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        } finally {
            LingeredApp.stopApp(app);
        }
    }
}
