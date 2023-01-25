/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

import java.math.BigInteger;

/**
 * Utility class containing conversions between strings, arrays, and numeric
 * values.
 */

public class Convert {

    // Convert from a byte array to a hexadecimal representation as a string.
    public static String byteArrayToHexString(byte[] arr) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < arr.length; ++i) {
            byte curVal = arr[i];
            result.append(Character.forDigit(curVal >> 4 & 0xF, 16));
            result.append(Character.forDigit(curVal & 0xF, 16));
        }
        return result.toString();
    }

    // Expand a single byte to a byte array
    public static byte[] byteToByteArray(byte v, int length) {
        byte[] result = new byte[length];
        result[0] = v;
        return result;
    }

    // Convert a hexadecimal string to a byte array
    public static byte[] hexStringToByteArray(String str) {
        byte[] result = new byte[str.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Character.digit(str.charAt(2 * i), 16);
            result[i] <<= 4;
            result[i] += Character.digit(str.charAt(2 * i + 1), 16);
        }
        return result;
    }

    /*
     * Convert a hexadecimal string to the corresponding little-ending number
     * as a BigInteger. The clearHighBit argument determines whether the most
     * significant bit of the highest byte should be set to 0 in the result.
     */
    public static
    BigInteger hexStringToBigInteger(boolean clearHighBit, String str) {
        BigInteger result = BigInteger.ZERO;
        for (int i = 0; i < str.length() / 2; i++) {
            int curVal = Character.digit(str.charAt(2 * i), 16);
            curVal <<= 4;
            curVal += Character.digit(str.charAt(2 * i + 1), 16);
            if (clearHighBit && i == str.length() / 2 - 1) {
                curVal &= 0x7F;
            }
            result = result.add(BigInteger.valueOf(curVal).shiftLeft(8 * i));
        }
        return result;
    }
}

