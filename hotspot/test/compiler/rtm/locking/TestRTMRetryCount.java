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
 *
 */

/**
 * @test
 * @bug 8031320
 * @summary Verify that RTMRetryCount affects actual amount of retries.
 * @library /testlibrary /testlibrary/whitebox /compiler/testlibrary
 * @build TestRTMRetryCount
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI TestRTMRetryCount
 */

import java.util.List;

import com.oracle.java.testlibrary.*;
import com.oracle.java.testlibrary.cli.CommandLineOptionTest;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import rtm.*;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

/**
 * Test verifies that RTMRetryCount option actually affects amount of
 * retries on lock busy.
 */
public class TestRTMRetryCount extends CommandLineOptionTest {
    /**
     * Time in ms, during which busy lock will be locked.
     */
    private static final int LOCKING_TIME = 5000;
    private static final boolean INFLATE_MONITOR = true;

    private TestRTMRetryCount() {
        super(new AndPredicate(new SupportedCPU(), new SupportedVM()));
    }

    @Override
    protected void runTestCases() throws Throwable {
        verifyRTMRetryCount(0);
        verifyRTMRetryCount(1);
        verifyRTMRetryCount(5);
        verifyRTMRetryCount(10);
    }

    private void verifyRTMRetryCount(int retryCount) throws Throwable {
        CompilableTest busyLock = new BusyLock();
        long expectedAborts = retryCount + 1L;

        OutputAnalyzer outputAnalyzer = RTMTestBase.executeRTMTest(
                busyLock,
                "-XX:-UseRTMXendForLockBusy",
                "-XX:RTMTotalCountIncrRate=1",
                CommandLineOptionTest.prepareNumericFlag("RTMRetryCount",
                        retryCount),
                "-XX:RTMTotalCountIncrRate=1",
                "-XX:+PrintPreciseRTMLockingStatistics",
                BusyLock.class.getName(),
                Boolean.toString(TestRTMRetryCount.INFLATE_MONITOR),
                Integer.toString(TestRTMRetryCount.LOCKING_TIME)
        );

        outputAnalyzer.shouldHaveExitValue(0);

        List<RTMLockingStatistics> statistics = RTMLockingStatistics.fromString(
                busyLock.getMethodWithLockName(), outputAnalyzer.getStdout());

        Asserts.assertEQ(statistics.size(), 1, "VM output should contain "
                + "exactly one rtm locking statistics entry for method "
                + busyLock.getMethodWithLockName());

        Asserts.assertEQ(statistics.get(0).getTotalAborts(), expectedAborts,
                String.format("It is expected to get %d aborts",
                        expectedAborts));
    }

    public static void main(String args[]) throws Throwable {
        new TestRTMRetryCount().test();
    }
}
