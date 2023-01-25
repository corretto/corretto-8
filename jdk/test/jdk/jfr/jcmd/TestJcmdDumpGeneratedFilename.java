/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jcmd;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.lang.management.ManagementFactory;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.test.lib.jfr.FileHelper;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @summary The test verifies JFR.dump command
 * @key jfr
 *
 * @library /lib /
 * @run main/othervm jdk.jfr.jcmd.TestJcmdDumpGeneratedFilename
 */
public class TestJcmdDumpGeneratedFilename {

    public static void main(String[] args) throws Exception {
        // Increase the id for a recording
        for (int i = 0; i < 300; i++) {
            new Recording();
        }
        try (Recording r = new Recording(Configuration.getConfiguration("default"))) {
            r.start();
            r.stop();
            testDumpFilename();
            testDumpFilename(r);
            testDumpDiectory();
            testDumpDiectory(r);
        }
    }

    private static void testDumpFilename() throws Exception {
        OutputAnalyzer output = JcmdHelper.jcmd("JFR.dump");
        verifyFile(readFilename(output), null);
    }

    private static void testDumpFilename(Recording r) throws Exception {
        OutputAnalyzer output = JcmdHelper.jcmd("JFR.dump", "name=" + r.getId());
        verifyFile(readFilename(output), r.getId());
    }

    private static void testDumpDiectory() throws Exception {
        Path directory = Paths.get(".").toAbsolutePath().normalize();
        OutputAnalyzer output = JcmdHelper.jcmd("JFR.dump", "filename=" + directory);
        String filename = readFilename(output);
        verifyFile(filename, null);
        verifyDirectory(filename, directory);
    }

    private static void testDumpDiectory(Recording r) throws Exception {
        Path directory = Paths.get(".").toAbsolutePath().normalize();
        OutputAnalyzer output = JcmdHelper.jcmd("JFR.dump", "name=" + r.getId(), "filename=" + directory);
        String filename = readFilename(output);
        verifyFile(filename, r.getId());
        verifyDirectory(filename, directory);
    }

    private static void verifyDirectory(String filename, Path directory) throws Exception {
        if (!filename.contains(directory.toAbsolutePath().normalize().toString())) {
            throw new Exception("Expected dump to be at " + directory);
        }
    }

    private static long getProcessId() {

        // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
        final String jvmName = ManagementFactory.getRuntimeMXBean().getName();

        final int index = jvmName.indexOf('@');

        if (index < 1) {
            // part before '@' empty (index = 0) / '@' not found (index = -1)
            return 42;
        }

        try {
            return Long.parseLong(jvmName.substring(0, index));
        } catch (NumberFormatException e) {
            // ignore
        }
        return 42;
    }

    private static void verifyFile(String filename, Long id) throws Exception {
        String idText = id == null ? "" : "-id-" + Long.toString(id);
        String expectedName = "hotspot-pid-" + getProcessId() + idText;
        if (!filename.contains(expectedName)) {
            throw new Exception("Expected filename to contain " + expectedName);
        }
        FileHelper.verifyRecording(new File(filename));
    }

    private static String readFilename(OutputAnalyzer output) throws Exception {
        Iterator<String> it = output.asLines().iterator();
        while (it.hasNext()) {
            String line = it.next();
            if (line.contains("written to")) {
                line = it.next(); // blank line
                return it.next();
            }
        }
        throw new Exception("Could not find filename of dumped recording.");
    }
}
