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
 * @bug 8003424
 * @summary Testing UseCompressedClassPointers with CDS
 * @library /testlibrary
 * @run main CDSCompressedKPtrs
 */

import com.oracle.java.testlibrary.*;

public class CDSCompressedKPtrs {
  public static void main(String[] args) throws Exception {
    ProcessBuilder pb;
    if (Platform.is64bit()) {
      pb = ProcessTools.createJavaProcessBuilder(
        "-XX:+UseCompressedClassPointers", "-XX:+UseCompressedOops",
        "-XX:+UnlockDiagnosticVMOptions", "-XX:SharedArchiveFile=./sample.jsa", "-Xshare:dump");
      OutputAnalyzer output = new OutputAnalyzer(pb.start());
      try {
        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder(
          "-XX:+UseCompressedClassPointers", "-XX:+UseCompressedOops",
          "-XX:+UnlockDiagnosticVMOptions", "-XX:SharedArchiveFile=./sample.jsa", "-Xshare:on", "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("sharing");
        output.shouldHaveExitValue(0);

      } catch (RuntimeException e) {
        // Report 'passed' if CDS was turned off.
        output.shouldContain("Unable to use shared archive");
        output.shouldHaveExitValue(1);
      }
    }
  }
}
