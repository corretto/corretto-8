/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8211936
 * @summary Tests of BigDecimal.intValue() and BigDecimal.longValue()
 */
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.AbstractMap.SimpleImmutableEntry;

public class IntegralValueTests {
    public static void main(String... args) {
        int failures =
            integralValuesTest(INT_VALUES, true) +
            integralValuesTest(LONG_VALUES, false);
        if (failures != 0) {
            throw new RuntimeException
                ("Incurred " + failures + " failures for {int,long}Value().");
        }
    }

    private static final Map<BigDecimal, Number> INT_VALUES = Stream.of(
         // 2**31 - 1
         new SimpleImmutableEntry<>(new BigDecimal("2147483647"), Integer.MAX_VALUE),
         new SimpleImmutableEntry<>(new BigDecimal("2147483647.0"), Integer.MAX_VALUE),
         new SimpleImmutableEntry<>(new BigDecimal("2147483647.00"), Integer.MAX_VALUE),

         new SimpleImmutableEntry<>(new BigDecimal("-2147483647"), -Integer.MAX_VALUE),
         new SimpleImmutableEntry<>(new BigDecimal("-2147483647.0"), -Integer.MAX_VALUE),

         // -2**31
         new SimpleImmutableEntry<>(new BigDecimal("-2147483648"), Integer.MIN_VALUE),
         new SimpleImmutableEntry<>(new BigDecimal("-2147483648.1"), Integer.MIN_VALUE),
         new SimpleImmutableEntry<>(new BigDecimal("-2147483648.01"), Integer.MIN_VALUE),

         // -2**31 + 1 truncation to 2**31 - 1
         new SimpleImmutableEntry<>(new BigDecimal("-2147483649"), Integer.MAX_VALUE),

         // 2**64 - 1 truncation to 1
         new SimpleImmutableEntry<>(new BigDecimal("4294967295"), -1),

         // 2**64 truncation to 0
         new SimpleImmutableEntry<>(new BigDecimal("4294967296"), 0),

         // Fast path truncation to 0
         new SimpleImmutableEntry<>(new BigDecimal("1e32"), 0),

         // Slow path truncation to -2**31
         new SimpleImmutableEntry<>(new BigDecimal("1e31"), Integer.MIN_VALUE),

         // Slow path
         new SimpleImmutableEntry<>(new BigDecimal("1e0"), 1),

         // Fast path round to 0
         new SimpleImmutableEntry<>(new BigDecimal("9e-1"), 0),

         // Some random values
         new SimpleImmutableEntry<>(new BigDecimal("900e-1"), 90), // Increasing negative exponents
         new SimpleImmutableEntry<>(new BigDecimal("900e-2"), 9),
         new SimpleImmutableEntry<>(new BigDecimal("900e-3"), 0),

         // Fast path round to 0
         new SimpleImmutableEntry<>(new BigDecimal("123456789e-9"), 0),

         // Slow path round to 1
         new SimpleImmutableEntry<>(new BigDecimal("123456789e-8"), 1),

         // Increasing positive exponents
         new SimpleImmutableEntry<>(new BigDecimal("10000001e1"), 100000010),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e10"), -1315576832),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e100"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e1000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e10000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e100000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e1000000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e10000000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e100000000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e1000000000"), 0),

         // Increasing negative exponents
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-1"), 1000000),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-10"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-100"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-1000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-10000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-100000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-1000000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-10000000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-100000000"), 0),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-1000000000"), 0),

         // Currency calculation to 4 places
         new SimpleImmutableEntry<>(new BigDecimal("12345.0001"), 12345),
         new SimpleImmutableEntry<>(new BigDecimal("12345.9999"), 12345),
         new SimpleImmutableEntry<>(new BigDecimal("-12345.0001"), -12345),
         new SimpleImmutableEntry<>(new BigDecimal("-12345.9999"), -12345)
    ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<BigDecimal, Number> LONG_VALUES = Stream.of(
         // 2**63 - 1
         new SimpleImmutableEntry<>(new BigDecimal("9223372036854775807"), Long.MAX_VALUE),
         new SimpleImmutableEntry<>(new BigDecimal("9223372036854775807.0"), Long.MAX_VALUE),
         new SimpleImmutableEntry<>(new BigDecimal("9223372036854775807.00"), Long.MAX_VALUE),

         // 2**63 truncation to -2**63
         new SimpleImmutableEntry<>(new BigDecimal("-9223372036854775808"), Long.MIN_VALUE),
         new SimpleImmutableEntry<>(new BigDecimal("-9223372036854775808.1"), Long.MIN_VALUE),
         new SimpleImmutableEntry<>(new BigDecimal("-9223372036854775808.01"), Long.MIN_VALUE),

         // -2**63 + 1 truncation to 2**63 - 1
         new SimpleImmutableEntry<>(new BigDecimal("-9223372036854775809"), 9223372036854775807L),

         // 2**64 - 1 truncation to -1
         new SimpleImmutableEntry<>(new BigDecimal("18446744073709551615"), -1L),

         // 2**64 truncation to 0
         new SimpleImmutableEntry<>(new BigDecimal("18446744073709551616"), 0L),

         // Slow path truncation to -2**63
         new SimpleImmutableEntry<>(new BigDecimal("1e63"),  -9223372036854775808L),
         new SimpleImmutableEntry<>(new BigDecimal("-1e63"), -9223372036854775808L),
         // Fast path with larger magnitude scale
         new SimpleImmutableEntry<>(new BigDecimal("1e64"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("-1e64"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("1e65"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("-1e65"), 0L),

         // Slow path
         new SimpleImmutableEntry<>(new BigDecimal("1e0"), 1L),

         // Fast path round to 0
         new SimpleImmutableEntry<>(new BigDecimal("9e-1"), 0L),

         // Some random values
         new SimpleImmutableEntry<>(new BigDecimal("900e-1"), 90L), // Increasing negative exponents
         new SimpleImmutableEntry<>(new BigDecimal("900e-2"), 9L),
         new SimpleImmutableEntry<>(new BigDecimal("900e-3"), 0L),

         // Fast path round to 0
         new SimpleImmutableEntry<>(new BigDecimal("123456789e-9"), 0L),

         // Slow path round to 1
         new SimpleImmutableEntry<>(new BigDecimal("123456789e-8"), 1L),

         // Increasing positive exponents
         new SimpleImmutableEntry<>(new BigDecimal("10000001e1"), 100000010L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e10"), 100000010000000000L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e100"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e1000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e10000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e100000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e1000000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e10000000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e100000000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e1000000000"), 0L),

         // Increasing negative exponents
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-1"), 1000000L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-10"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-100"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-1000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-10000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-100000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-1000000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-10000000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-100000000"), 0L),
         new SimpleImmutableEntry<>(new BigDecimal("10000001e-1000000000"), 0L),

         // Currency calculation to 4 places
         new SimpleImmutableEntry<>(new BigDecimal("12345.0001"), 12345L),
         new SimpleImmutableEntry<>(new BigDecimal("12345.9999"), 12345L),
         new SimpleImmutableEntry<>(new BigDecimal("-12345.0001"), -12345L),
         new SimpleImmutableEntry<>(new BigDecimal("-12345.9999"), -12345L)
    ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static int integralValuesTest(Map<BigDecimal, Number> v, boolean isInt) {
        System.err.format("Testing %s%n", isInt ? "Integer" : "Long");
        int failures = 0;
        for (Map.Entry<BigDecimal, Number> testCase : v.entrySet()) {
            BigDecimal bd = testCase.getKey();
            Number expected = testCase.getValue();
            try {
                if (isInt) {
                    int intValue = bd.intValue();
                    if (intValue != (int)expected) {
                        failures += reportError(bd, expected, intValue, isInt);
                    }
                } else {
                    long longValue = bd.longValue();
                    if (longValue != (long)expected) {
                        failures += reportError(bd, expected, longValue, isInt);
                    }
                }
            } catch (Exception e) {
                failures++;
                System.err.format("Unexpected exception %s for %s%n",
                                  e, bd.toString());
            }
        }
        return failures;
    }

    private static int reportError(BigDecimal bd, Number expected, long longValue, boolean isInt) {
        System.err.format("For %s, scale=%d, expected %d, actual %d, simple %d%n",
                          bd.toString(), bd.scale(),
                          (isInt ? (Integer) expected : (Long) expected ),
                          longValue,
                          (isInt ? simpleIntValue(bd): simpleLongValue(bd) ));
        return 1;
    }

    private static long simpleLongValue(BigDecimal bd) {
        return bd.toBigInteger().longValue();
    }

    private static int simpleIntValue(BigDecimal bd) {
        return bd.toBigInteger().intValue();
    }
}
