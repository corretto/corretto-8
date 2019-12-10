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

import com.oracle.java.testlibrary.Platform;
import com.oracle.java.testlibrary.cli.CommandLineOptionTest;
import sha.predicate.IntrinsicPredicates;

import java.util.function.BooleanSupplier;

/**
 * Base class for all CLI tests on SHA-related options.
 *
 * Instead of using huge complex tests for each option, each test is constructed
 * from several test cases shared among different tests.
 */
public class SHAOptionsBase extends CommandLineOptionTest {
    protected static final String USE_SHA_OPTION = "UseSHA";
    protected static final String USE_SHA1_INTRINSICS_OPTION
            = "UseSHA1Intrinsics";
    protected static final String USE_SHA256_INTRINSICS_OPTION
            = "UseSHA256Intrinsics";
    protected static final String USE_SHA512_INTRINSICS_OPTION
            = "UseSHA512Intrinsics";

    // Note that strings below will be passed to
    // CommandLineOptionTest.verifySameJVMStartup and thus are regular
    // expressions, not just a plain strings.
    protected static final String SHA_INSTRUCTIONS_ARE_NOT_AVAILABLE
            = "SHA instructions are not available on this CPU";
    protected static final String SHA1_INSTRUCTION_IS_NOT_AVAILABLE
            = "SHA1 instruction is not available on this CPU\\.";
    protected static final String SHA256_INSTRUCTION_IS_NOT_AVAILABLE
            = "SHA256 instruction \\(for SHA-224 and SHA-256\\) "
            + "is not available on this CPU\\.";
    protected static final String SHA512_INSTRUCTION_IS_NOT_AVAILABLE
            = "SHA512 instruction \\(for SHA-384 and SHA-512\\) "
            + "is not available on this CPU\\.";
    protected static final String SHA_INTRINSICS_ARE_NOT_AVAILABLE
            = "SHA intrinsics are not available on this CPU";

    private final TestCase[] testCases;

    /**
     * Returns warning message that should occur in VM output if an option with
     * the name {@code optionName} was turned on and CPU does not support
     * required instructions.
     *
     * @param optionName The name of the option for which warning message should
     *                   be returned.
     * @return A warning message that will be printed out to VM output if CPU
     *         instructions required by the option are not supported.
     */
    protected static String getWarningForUnsupportedCPU(String optionName) {
        if (Platform.isSparc()) {
            switch (optionName) {
                case SHAOptionsBase.USE_SHA_OPTION:
                    return SHAOptionsBase.SHA_INSTRUCTIONS_ARE_NOT_AVAILABLE;
                case SHAOptionsBase.USE_SHA1_INTRINSICS_OPTION:
                    return SHAOptionsBase.SHA1_INSTRUCTION_IS_NOT_AVAILABLE;
                case SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION:
                    return SHAOptionsBase.SHA256_INSTRUCTION_IS_NOT_AVAILABLE;
                case SHAOptionsBase.USE_SHA512_INTRINSICS_OPTION:
                    return SHAOptionsBase.SHA512_INSTRUCTION_IS_NOT_AVAILABLE;
                default:
                    throw new Error("Unexpected option " + optionName);
            }
        } else if (Platform.isX64() || Platform.isX86()) {
            switch (optionName) {
                case SHAOptionsBase.USE_SHA_OPTION:
                    return SHAOptionsBase.SHA_INSTRUCTIONS_ARE_NOT_AVAILABLE;
                case SHAOptionsBase.USE_SHA1_INTRINSICS_OPTION:
                case SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION:
                case SHAOptionsBase.USE_SHA512_INTRINSICS_OPTION:
                    return SHAOptionsBase.SHA_INTRINSICS_ARE_NOT_AVAILABLE;
                default:
                    throw new Error("Unexpected option " + optionName);
            }
        } else if (Platform.isAArch64()) {
            switch (optionName) {
                case SHAOptionsBase.USE_SHA_OPTION:
                    return SHAOptionsBase.SHA_INSTRUCTIONS_ARE_NOT_AVAILABLE;
                case SHAOptionsBase.USE_SHA1_INTRINSICS_OPTION:
                    return SHAOptionsBase.SHA1_INSTRUCTION_IS_NOT_AVAILABLE;
                case SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION:
                    return SHAOptionsBase.SHA256_INSTRUCTION_IS_NOT_AVAILABLE;
                case SHAOptionsBase.USE_SHA512_INTRINSICS_OPTION:
                    return SHAOptionsBase.SHA512_INSTRUCTION_IS_NOT_AVAILABLE;
                default:
                    throw new Error("Unexpected option " + optionName);
            }
        } else {
            throw new Error("Support for CPUs other then X86 or SPARC is not "
                    + "implemented.");
        }
    }

    /**
     * Returns the predicate indicating whether or not CPU instructions required
     * by the option with name {@code optionName} are available.
     *
     * @param optionName The name of the option for which a predicate should be
     *                   returned.
     * @return The predicate on availability of CPU instructions required by the
     *         option.
     */
    protected static BooleanSupplier getPredicateForOption(String optionName) {
        switch (optionName) {
            case SHAOptionsBase.USE_SHA_OPTION:
                return IntrinsicPredicates.ANY_SHA_INSTRUCTION_AVAILABLE;
            case SHAOptionsBase.USE_SHA1_INTRINSICS_OPTION:
                return IntrinsicPredicates.SHA1_INSTRUCTION_AVAILABLE;
            case SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION:
                return IntrinsicPredicates.SHA256_INSTRUCTION_AVAILABLE;
            case SHAOptionsBase.USE_SHA512_INTRINSICS_OPTION:
                return IntrinsicPredicates.SHA512_INSTRUCTION_AVAILABLE;
            default:
                throw new Error("Unexpected option " + optionName);
        }
    }

    public SHAOptionsBase(TestCase... testCases) {
        super(Boolean.TRUE::booleanValue);
        this.testCases = testCases;
    }

    @Override
    protected void runTestCases() throws Throwable {
        for (TestCase testCase : testCases) {
            testCase.test();
        }
    }

    public static abstract class TestCase {
        protected final String optionName;
        private final BooleanSupplier predicate;

        protected TestCase(String optionName, BooleanSupplier predicate) {
            this.optionName = optionName;
            this.predicate = predicate;
        }

        protected final void test() throws Throwable {
            String testCaseName = this.getClass().getName();
            if (!predicate.getAsBoolean()) {
                System.out.println("Skipping " + testCaseName
                        + " due to predicate failure.");
                return;
            } else {
                System.out.println("Running " + testCaseName);
            }

            verifyWarnings();
            verifyOptionValues();
        }

        protected void verifyWarnings() throws Throwable {
        }

        protected void verifyOptionValues() throws Throwable {
        }
    }
}
