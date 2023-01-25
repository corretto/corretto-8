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
 * @test TestGCId
 * @bug 8043607
 * @summary Ensure that the GCId is logged
 * @key gc
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.OutputAnalyzer;

public class TestGCId {
  public static void main(String[] args) throws Exception {
    testGCId("UseParallelGC", "PrintGC");
    testGCId("UseParallelGC", "PrintGCDetails");

    testGCId("UseG1GC", "PrintGC");
    testGCId("UseG1GC", "PrintGCDetails");

    testGCId("UseConcMarkSweepGC", "PrintGC");
    testGCId("UseConcMarkSweepGC", "PrintGCDetails");

    testGCId("UseSerialGC", "PrintGC");
    testGCId("UseSerialGC", "PrintGCDetails");
  }

  private static void verifyContainsGCIDs(OutputAnalyzer output) {
    output.shouldMatch("^#0: \\[");
    output.shouldMatch("^#1: \\[");
    output.shouldHaveExitValue(0);
  }

  private static void verifyContainsNoGCIDs(OutputAnalyzer output) {
    output.shouldNotMatch("^#[0-9]+: \\[");
    output.shouldHaveExitValue(0);
  }

  private static void testGCId(String gcFlag, String logFlag) throws Exception {
    // GCID logging enabled
    ProcessBuilder pb_enabled =
      ProcessTools.createJavaProcessBuilder("-XX:+" + gcFlag, "-XX:+" + logFlag, "-Xmx10M", "-XX:+PrintGCID", GCTest.class.getName());
    verifyContainsGCIDs(new OutputAnalyzer(pb_enabled.start()));

    // GCID logging disabled
    ProcessBuilder pb_disabled =
      ProcessTools.createJavaProcessBuilder("-XX:+" + gcFlag, "-XX:+" + logFlag, "-Xmx10M", "-XX:-PrintGCID", GCTest.class.getName());
    verifyContainsNoGCIDs(new OutputAnalyzer(pb_disabled.start()));

    // GCID logging default
    ProcessBuilder pb_default =
      ProcessTools.createJavaProcessBuilder("-XX:+" + gcFlag, "-XX:+" + logFlag, "-Xmx10M", GCTest.class.getName());
    verifyContainsNoGCIDs(new OutputAnalyzer(pb_default.start()));
  }

  static class GCTest {
    private static byte[] garbage;
    public static void main(String [] args) {
      System.out.println("Creating garbage");
      // create 128MB of garbage. This should result in at least one GC
      for (int i = 0; i < 1024; i++) {
        garbage = new byte[128 * 1024];
      }
      // do a system gc to get one more gc
      System.gc();
      System.out.println("Done");
    }
  }
}
