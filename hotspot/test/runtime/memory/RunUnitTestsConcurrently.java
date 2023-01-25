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
 * @summary Test launches unit tests inside vm concurrently
 * @library /testlibrary /testlibrary/whitebox
 * @build RunUnitTestsConcurrently
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI RunUnitTestsConcurrently 30 15000
 */

import com.oracle.java.testlibrary.*;
import sun.hotspot.WhiteBox;

public class RunUnitTestsConcurrently {

  private static WhiteBox wb;
  private static long timeout;
  private static long timeStamp;

  public static class Worker implements Runnable {
    @Override
    public void run() {
      while (System.currentTimeMillis() - timeStamp < timeout) {
        WhiteBox.getWhiteBox().runMemoryUnitTests();
      }
    }
  }

  public static void main(String[] args) throws InterruptedException {
    if (!Platform.isDebugBuild() || !Platform.is64bit()) {
      return;
    }
    wb = WhiteBox.getWhiteBox();
    System.out.println("Starting threads");

    int threads = Integer.valueOf(args[0]);
    timeout = Long.valueOf(args[1]);

    timeStamp = System.currentTimeMillis();

    Thread[] threadsArray = new Thread[threads];
    for (int i = 0; i < threads; i++) {
      threadsArray[i] = new Thread(new Worker());
      threadsArray[i].start();
    }
    for (int i = 0; i < threads; i++) {
      threadsArray[i].join();
    }

    System.out.println("Quitting test.");
  }
}
