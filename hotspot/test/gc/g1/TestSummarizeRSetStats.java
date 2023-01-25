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
 * @test TestSummarizeRSetStats.java
 * @bug 8013895
 * @library /testlibrary
 * @build TestSummarizeRSetStatsTools TestSummarizeRSetStats
 * @summary Verify output of -XX:+G1SummarizeRSetStats
 * @run main TestSummarizeRSetStats
 *
 * Test the output of G1SummarizeRSetStats in conjunction with G1SummarizeRSetStatsPeriod.
 */

public class TestSummarizeRSetStats {

    public static void main(String[] args) throws Exception {
        String result;

        if (!TestSummarizeRSetStatsTools.testingG1GC()) {
            return;
        }

        // no remembered set summary output
        result = TestSummarizeRSetStatsTools.runTest(null, 0);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 0, 0);

        // no remembered set summary output
        result = TestSummarizeRSetStatsTools.runTest(null, 2);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 0, 0);

        // no remembered set summary output
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:G1SummarizeRSetStatsPeriod=1" }, 3);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 0, 0);

        // single remembered set summary output at the end
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:+G1SummarizeRSetStats" }, 0);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 1, 0);

        // single remembered set summary output at the end
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:+G1SummarizeRSetStats" }, 2);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 1, 0);

        // single remembered set summary output
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=1" }, 0);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 1, 0);

        // two times remembered set summary output
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=1" }, 1);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 1, 2);

        // four times remembered set summary output
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=1" }, 3);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 1, 6);

        // three times remembered set summary output
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=2" }, 3);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 1, 4);

        // single remembered set summary output
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=100" }, 3);
        TestSummarizeRSetStatsTools.expectRSetSummaries(result, 1, 2);
    }
}

