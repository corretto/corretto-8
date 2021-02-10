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

package sha.predicate;

import com.oracle.java.testlibrary.Platform;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import com.oracle.java.testlibrary.cli.predicate.CPUSpecificPredicate;
import com.oracle.java.testlibrary.cli.predicate.OrPredicate;
import sun.hotspot.WhiteBox;

import java.util.function.BooleanSupplier;

/**
 * Helper class aimed to provide predicates on availability of SHA-related
 * CPU instructions and intrinsics.
 */
public class IntrinsicPredicates {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final long TIERED_MAX_LEVEL = 4L;
    /**
     * Boolean supplier that check if any method could be compiled by C2.
     * Method potentially could be compiled by C2 if Server VM is used and
     * either tiered compilation is disabled or TIERED_MAX_LEVEL tier is
     * reachable.
     *
     * Please don't place this definition after SHA*_INTRINSICS_AVAILABLE
     * definitions. Otherwise its value will be {@code null} at the time when
     * all dependent fields will be initialized.
     */
    private static final BooleanSupplier COMPILABLE_BY_C2 = () -> {
        boolean isTiered = IntrinsicPredicates.WHITE_BOX.getBooleanVMFlag(
                "TieredCompilation");
        long tieredMaxLevel = IntrinsicPredicates.WHITE_BOX.getIntxVMFlag(
                "TieredStopAtLevel");
        boolean maxLevelIsReachable = (tieredMaxLevel
                == IntrinsicPredicates.TIERED_MAX_LEVEL);
        return Platform.isServer() && (!isTiered || maxLevelIsReachable);
    };

    public static final BooleanSupplier SHA1_INSTRUCTION_AVAILABLE
            = new OrPredicate(
                    new CPUSpecificPredicate("sparc.*", new String[] { "sha1" },
                            null),
                    new CPUSpecificPredicate("aarch64", new String[] { "sha1" },
                            null));

    public static final BooleanSupplier SHA256_INSTRUCTION_AVAILABLE
            = new OrPredicate(new CPUSpecificPredicate("aarch64", new String[] { "sha256" },
                                                       null),
              new OrPredicate(new CPUSpecificPredicate("sparc.*",   new String[] { "sha256" },
                                                       null),
              new OrPredicate(new CPUSpecificPredicate("ppc64.*",   new String[] { "sha"    },
                                                       null),
                              new CPUSpecificPredicate("ppc64le.*", new String[] { "sha"    },
                                                       null))));

    public static final BooleanSupplier SHA512_INSTRUCTION_AVAILABLE
            = new OrPredicate(
                    new CPUSpecificPredicate("aarch64", new String[] { "sha512" },
                                             null),
                    new OrPredicate(new CPUSpecificPredicate("sparc.*",   new String[] { "sha512" },
                                                             null),
                    new OrPredicate(new CPUSpecificPredicate("ppc64.*",   new String[] { "sha"    },
                                                       null),
                    new CPUSpecificPredicate("ppc64le.*", new String[] { "sha"    },
                                             null))));

    public static final BooleanSupplier ANY_SHA_INSTRUCTION_AVAILABLE
            = new OrPredicate(IntrinsicPredicates.SHA1_INSTRUCTION_AVAILABLE,
                    new OrPredicate(
                            IntrinsicPredicates.SHA256_INSTRUCTION_AVAILABLE,
                            IntrinsicPredicates.SHA512_INSTRUCTION_AVAILABLE));

    public static final BooleanSupplier SHA1_INTRINSICS_AVAILABLE
            = new AndPredicate(new AndPredicate(
                    IntrinsicPredicates.SHA1_INSTRUCTION_AVAILABLE,
                    IntrinsicPredicates.COMPILABLE_BY_C2),
                IntrinsicPredicates.booleanOptionValue("UseSHA1Intrinsics"));

    public static final BooleanSupplier SHA256_INTRINSICS_AVAILABLE
            = new AndPredicate(new AndPredicate(
                    IntrinsicPredicates.SHA256_INSTRUCTION_AVAILABLE,
                    IntrinsicPredicates.COMPILABLE_BY_C2),
                IntrinsicPredicates.booleanOptionValue("UseSHA256Intrinsics"));

    public static final BooleanSupplier SHA512_INTRINSICS_AVAILABLE
            = new AndPredicate(new AndPredicate(
                    IntrinsicPredicates.SHA512_INSTRUCTION_AVAILABLE,
                    IntrinsicPredicates.COMPILABLE_BY_C2),
                IntrinsicPredicates.booleanOptionValue("UseSHA512Intrinsics"));

    private static BooleanSupplier booleanOptionValue(String option) {
        return () -> IntrinsicPredicates.WHITE_BOX.getBooleanVMFlag(option);
    }

    private IntrinsicPredicates() {
    }
}
