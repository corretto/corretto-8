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
 * @summary Verify PrintPreciseRTMLockingStatistics on CPUs with
 *          rtm support and on VM with rtm locking support,
 * @library /testlibrary /testlibrary/whitebox /compiler/testlibrary
 * @build TestPrintPreciseRTMLockingStatisticsOptionOnSupportedConfig
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   TestPrintPreciseRTMLockingStatisticsOptionOnSupportedConfig
 */

import com.oracle.java.testlibrary.cli.*;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

public class TestPrintPreciseRTMLockingStatisticsOptionOnSupportedConfig
        extends TestPrintPreciseRTMLockingStatisticsBase {
    private TestPrintPreciseRTMLockingStatisticsOptionOnSupportedConfig() {
        super(new AndPredicate(new SupportedVM(), new SupportedCPU()));
    }

    @Override
    protected void verifyOptionValues() throws Throwable {
        super.verifyOptionValues();
        // verify default value
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                TestPrintPreciseRTMLockingStatisticsBase.DEFAULT_VALUE,
                CommandLineOptionTest.UNLOCK_DIAGNOSTIC_VM_OPTIONS,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:+UseRTMLocking");

        CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                TestPrintPreciseRTMLockingStatisticsBase.DEFAULT_VALUE,
                CommandLineOptionTest.UNLOCK_DIAGNOSTIC_VM_OPTIONS,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:-UseRTMLocking", prepareOptionValue("true"));

        // verify that option could be turned on
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "true",
                CommandLineOptionTest.UNLOCK_DIAGNOSTIC_VM_OPTIONS,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:+UseRTMLocking", prepareOptionValue("true"));
    }

    public static void main(String args[]) throws Throwable {
        new TestPrintPreciseRTMLockingStatisticsOptionOnSupportedConfig()
                .test();
    }
}
