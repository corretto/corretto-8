/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test Test6941923.java
 * @bug 6941923
 * @summary test flags for gc log rotation
 * @library /testlibrary
 * @run main/othervm/timeout=600 Test6941923
 *
 */
import com.oracle.java.testlibrary.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;

class GCLoggingGenerator {

    public static void main(String[] args) throws Exception {

        long sizeOfLog = Long.parseLong(args[0]);
        long lines = sizeOfLog / 80;
        // full.GC generates ad least 1-line which is not shorter then 80 chars
        // for some GC 2 shorter lines are generated
        for (long i = 0; i < lines; i++) {
            System.gc();
        }
    }
}

public class Test6941923 {

    static final File currentDirectory = new File(".");
    static final String logFileName = "test.log";
    static final int logFileSizeK = 16;
    static FilenameFilter logFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith(logFileName);
        }
    };

    public static void cleanLogs() {
        for (File log : currentDirectory.listFiles(logFilter)) {
            if (!log.delete()) {
                throw new Error("Unable to delete " + log.getAbsolutePath());
            }
        }
    }

    public static void runTest(int numberOfFiles) throws Exception {

        ArrayList<String> args = new ArrayList();
        String[] logOpts = new String[]{
            "-cp", System.getProperty("java.class.path"),
            "-Xloggc:" + logFileName,
            "-XX:-DisableExplicitGC", // to sure that System.gc() works
            "-XX:+PrintGC", "-XX:+PrintGCDetails", "-XX:+UseGCLogFileRotation",
            "-XX:NumberOfGCLogFiles=" + numberOfFiles,
            "-XX:GCLogFileSize=" + logFileSizeK + "K", "-Xmx128M"};
        // System.getProperty("test.java.opts") is '' if no options is set
        // need to skip such empty
        String[] externalVMopts = System.getProperty("test.java.opts").length() == 0
                ? new String[0]
                : System.getProperty("test.java.opts").split(" ");
        args.addAll(Arrays.asList(externalVMopts));
        args.addAll(Arrays.asList(logOpts));
        args.add(GCLoggingGenerator.class.getName());
        args.add(String.valueOf(numberOfFiles * logFileSizeK * 1024));
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args.toArray(new String[0]));
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File(GCLoggingGenerator.class.getName() + ".log"));
        Process process = pb.start();
        int result = process.waitFor();
        if (result != 0) {
            throw new Error("Unexpected exit code = " + result);
        }
        File[] logs = currentDirectory.listFiles(logFilter);
        int smallFilesNumber = 0;
        for (File log : logs) {
            if (log.length() < logFileSizeK * 1024) {
                smallFilesNumber++;
            }
        }
        if (logs.length != numberOfFiles) {
            throw new Error("There are only " + logs.length + " logs instead " + numberOfFiles);
        }
        if (smallFilesNumber > 1) {
            throw new Error("There should maximum one log with size < " + logFileSizeK + "K");
        }
    }

    public static void main(String[] args) throws Exception {
        cleanLogs();
        runTest(1);
        cleanLogs();
        runTest(3);
        cleanLogs();
    }
}
