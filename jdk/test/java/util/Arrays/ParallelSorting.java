/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

/* Adapted from test/java/util/Arrays/Sorting.java
 *
 * Where that test checks Arrays.sort against manual quicksort routines,
 * this test checks parallelSort against either Arrays.sort or manual
 * quicksort routines.
 */

/*
 * @test
 * @bug 8003981
 * @run main ParallelSorting -shortrun
 * @summary Exercise Arrays.parallelSort (adapted from test Sorting)
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 */

import java.util.Arrays;
import java.util.Random;
import java.io.PrintStream;
import java.util.Comparator;

public class ParallelSorting {
    private static final PrintStream out = System.out;
    private static final PrintStream err = System.err;

    // Array lengths used in a long run (default)
    private static final int[] LONG_RUN_LENGTHS = {
        1000, 10000, 100000, 1000000 };

    // Array lengths used in a short run
    private static final int[] SHORT_RUN_LENGTHS = {
        5000, 9000, 10000, 12000 };

    // Random initial values used in a long run (default)
    private static final long[] LONG_RUN_RANDOMS = { 666, 0xC0FFEE, 999 };

    // Random initial values used in a short run
    private static final long[] SHORT_RUN_RANDOMS = { 666 };

    public static void main(String[] args) {
        boolean shortRun = args.length > 0 && args[0].equals("-shortrun");
        long start = System.currentTimeMillis();

        if (shortRun) {
            testAndCheck(SHORT_RUN_LENGTHS, SHORT_RUN_RANDOMS);
        } else {
            testAndCheck(LONG_RUN_LENGTHS, LONG_RUN_RANDOMS);
        }
        long end = System.currentTimeMillis();

        out.format("PASSED in %d sec.\n", Math.round((end - start) / 1E3));
    }

    private static void testAndCheck(int[] lengths, long[] randoms) {
        testEmptyAndNullIntArray();
        testEmptyAndNullLongArray();
        testEmptyAndNullShortArray();
        testEmptyAndNullCharArray();
        testEmptyAndNullByteArray();
        testEmptyAndNullFloatArray();
        testEmptyAndNullDoubleArray();

        for (int length : lengths) {
            testMergeSort(length);
            testAndCheckRange(length);
            testAndCheckSubArray(length);
        }
        for (long seed : randoms) {
            for (int length : lengths) {
                testAndCheckWithInsertionSort(length, new MyRandom(seed));
                testAndCheckWithCheckSum(length, new MyRandom(seed));
                testAndCheckWithScrambling(length, new MyRandom(seed));
                testAndCheckFloat(length, new MyRandom(seed));
                testAndCheckDouble(length, new MyRandom(seed));
                testStable(length, new MyRandom(seed));
            }
        }
    }

    private static void testEmptyAndNullIntArray() {
        ourDescription = "Check empty and null array";
        Arrays.parallelSort(new int[]{});
        Arrays.parallelSort(new int[]{}, 0, 0);

        try {
            Arrays.parallelSort((int[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.parallelSort((int[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.parallelSort(int[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.parallelSort(int[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullLongArray() {
        ourDescription = "Check empty and null array";
        Arrays.parallelSort(new long[]{});
        Arrays.parallelSort(new long[]{}, 0, 0);

        try {
            Arrays.parallelSort((long[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.parallelSort((long[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.parallelSort(long[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.parallelSort(long[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullShortArray() {
        ourDescription = "Check empty and null array";
        Arrays.parallelSort(new short[]{});
        Arrays.parallelSort(new short[]{}, 0, 0);

        try {
            Arrays.parallelSort((short[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.parallelSort((short[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.parallelSort(short[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.parallelSort(short[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullCharArray() {
        ourDescription = "Check empty and null array";
        Arrays.parallelSort(new char[]{});
        Arrays.parallelSort(new char[]{}, 0, 0);

        try {
            Arrays.parallelSort((char[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.parallelSort((char[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.parallelSort(char[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.parallelSort(char[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullByteArray() {
        ourDescription = "Check empty and null array";
        Arrays.parallelSort(new byte[]{});
        Arrays.parallelSort(new byte[]{}, 0, 0);

        try {
            Arrays.parallelSort((byte[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.parallelSort((byte[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.parallelSort(byte[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.parallelSort(byte[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullFloatArray() {
        ourDescription = "Check empty and null array";
        Arrays.parallelSort(new float[]{});
        Arrays.parallelSort(new float[]{}, 0, 0);

        try {
            Arrays.parallelSort((float[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.parallelSort((float[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.parallelSort(float[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.parallelSort(float[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullDoubleArray() {
        ourDescription = "Check empty and null array";
        Arrays.parallelSort(new double[]{});
        Arrays.parallelSort(new double[]{}, 0, 0);

        try {
            Arrays.parallelSort((double[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.parallelSort((double[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.parallelSort(double[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.parallelSort(double[]) shouldn't catch null array");
    }

    private static void testAndCheckSubArray(int length) {
        ourDescription = "Check sorting of subarray";
        int[] golden = new int[length];
        boolean newLine = false;

        for (int m = 1; m < length / 2; m *= 2) {
            newLine = true;
            int fromIndex = m;
            int toIndex = length - m;

            prepareSubArray(golden, fromIndex, toIndex, m);
            int[] test = golden.clone();

            for (TypeConverter converter : TypeConverter.values()) {
                out.println("Test 'subarray': " + converter +
                   " length = " + length + ", m = " + m);
                Object convertedGolden = converter.convert(golden);
                Object convertedTest = converter.convert(test);
                sortSubArray(convertedTest, fromIndex, toIndex);
                checkSubArray(convertedTest, fromIndex, toIndex, m);
            }
        }
        if (newLine) {
            out.println();
        }
    }

    private static void testAndCheckRange(int length) {
        ourDescription = "Check range check";
        int[] golden = new int[length];

        for (int m = 1; m < 2 * length; m *= 2) {
            for (int i = 1; i <= length; i++) {
                golden[i - 1] = i % m + m % i;
            }
            for (TypeConverter converter : TypeConverter.values()) {
                out.println("Test 'range': " + converter +
                   ", length = " + length + ", m = " + m);
                Object convertedGolden = converter.convert(golden);
                checkRange(convertedGolden, m);
            }
        }
        out.println();
    }

    private static void testStable(int length, MyRandom random) {
        ourDescription = "Check if sorting is stable";
        Pair[] a = build(length, random);

        out.println("Test 'stable': " + "random = " + random.getSeed() +
            ", length = " + length);
        Arrays.parallelSort(a);
        checkSorted(a);
        checkStable(a);
        out.println();

        a = build(length, random);

        out.println("Test 'stable' comparator: " + "random = " + random.getSeed() +
            ", length = " + length);
        Arrays.parallelSort(a, pairCmp);
        checkSorted(a);
        checkStable(a);
        out.println();

    }

    private static void checkSorted(Pair[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i].getKey() > a[i + 1].getKey()) {
                failedSort(i, "" + a[i].getKey(), "" + a[i + 1].getKey());
            }
        }
    }

    private static void checkStable(Pair[] a) {
        for (int i = 0; i < a.length / 4; ) {
            int key1 = a[i].getKey();
            int value1 = a[i++].getValue();
            int key2 = a[i].getKey();
            int value2 = a[i++].getValue();
            int key3 = a[i].getKey();
            int value3 = a[i++].getValue();
            int key4 = a[i].getKey();
            int value4 = a[i++].getValue();

            if (!(key1 == key2 && key2 == key3 && key3 == key4)) {
                failed("On position " + i + " keys are different " +
                    key1 + ", " + key2 + ", " + key3 + ", " + key4);
            }
            if (!(value1 < value2 && value2 < value3 && value3 < value4)) {
                failed("Sorting is not stable at position " + i +
                    ". Second values have been changed: " +  value1 + ", " +
                    value2 + ", " + value3 + ", " + value4);
            }
        }
    }

    private static Pair[] build(int length, Random random) {
        Pair[] a = new Pair[length * 4];

        for (int i = 0; i < a.length; ) {
            int key = random.nextInt();
            a[i++] = new Pair(key, 1);
            a[i++] = new Pair(key, 2);
            a[i++] = new Pair(key, 3);
            a[i++] = new Pair(key, 4);
        }
        return a;
    }

    private static Comparator<Pair> pairCmp = new Comparator<Pair>() {
        public int compare(Pair p1, Pair p2) {
            return p1.compareTo(p2);
        }
    };

    private static final class Pair implements Comparable<Pair> {
        Pair(int key, int value) {
            myKey = key;
            myValue = value;
        }

        int getKey() {
            return myKey;
        }

        int getValue() {
            return myValue;
        }

        public int compareTo(Pair pair) {
            if (myKey < pair.myKey) {
                return -1;
            }
            if (myKey > pair.myKey) {
                return 1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return "(" + myKey + ", " + myValue + ")";
        }

        private int myKey;
        private int myValue;
    }


    private static void testAndCheckWithInsertionSort(int length, MyRandom random) {
        if (length > 1000) {
            return;
        }
        ourDescription = "Check sorting with insertion sort";
        int[] golden = new int[length];

        for (int m = 1; m < 2 * length; m *= 2) {
            for (UnsortedBuilder builder : UnsortedBuilder.values()) {
                builder.build(golden, m, random);
                int[] test = golden.clone();

                for (TypeConverter converter : TypeConverter.values()) {
                    out.println("Test 'insertion sort': " + converter +
                        " " + builder + "random = " + random.getSeed() +
                        ", length = " + length + ", m = " + m);
                    Object convertedGolden = converter.convert(golden);
                    Object convertedTest1 = converter.convert(test);
                    Object convertedTest2 = converter.convert(test);
                    sort(convertedTest1);
                    sortByInsertionSort(convertedTest2);
                    compare(convertedTest1, convertedTest2);
                }
            }
        }
        out.println();
    }

    private static void testMergeSort(int length) {
        if (length < 1000) {
            return;
        }
        ourDescription = "Check merge sorting";
        int[] golden = new int[length];
        int period = 67; // java.util.DualPivotQuicksort.MAX_RUN_COUNT

        for (int m = period - 2; m <= period + 2; m++) {
            for (MergeBuilder builder : MergeBuilder.values()) {
                builder.build(golden, m);
                int[] test = golden.clone();

                for (TypeConverter converter : TypeConverter.values()) {
                    out.println("Test 'merge sort': " + converter + " " +
                        builder + "length = " + length + ", m = " + m);
                    Object convertedGolden = converter.convert(golden);
                    sort(convertedGolden);
                    checkSorted(convertedGolden);
                }
            }
        }
        out.println();
    }

    private static void testAndCheckWithCheckSum(int length, MyRandom random) {
        ourDescription = "Check sorting with check sum";
        int[] golden = new int[length];

        for (int m = 1; m < 2 * length; m *= 2) {
            for (UnsortedBuilder builder : UnsortedBuilder.values()) {
                builder.build(golden, m, random);
                int[] test = golden.clone();

                for (TypeConverter converter : TypeConverter.values()) {
                    out.println("Test 'check sum': " + converter +
                        " " + builder + "random = " + random.getSeed() +
                        ", length = " + length + ", m = " + m);
                    Object convertedGolden = converter.convert(golden);
                    Object convertedTest = converter.convert(test);
                    sort(convertedTest);
                    checkWithCheckSum(convertedTest, convertedGolden);
                }
            }
        }
        out.println();
    }

    private static void testAndCheckWithScrambling(int length, MyRandom random) {
        ourDescription = "Check sorting with scrambling";
        int[] golden = new int[length];

        for (int m = 1; m <= 7; m++) {
            if (m > length) {
                break;
            }
            for (SortedBuilder builder : SortedBuilder.values()) {
                builder.build(golden, m);
                int[] test = golden.clone();
                scramble(test, random);

                for (TypeConverter converter : TypeConverter.values()) {
                    out.println("Test 'scrambling': " + converter +
                       " " + builder + "random = " + random.getSeed() +
                       ", length = " + length + ", m = " + m);
                    Object convertedGolden = converter.convert(golden);
                    Object convertedTest = converter.convert(test);
                    sort(convertedTest);
                    compare(convertedTest, convertedGolden);
                }
            }
        }
        out.println();
    }

    private static void testAndCheckFloat(int length, MyRandom random) {
        ourDescription = "Check float sorting";
        float[] golden = new float[length];
        final int MAX = 10;
        boolean newLine = false;

        for (int a = 0; a <= MAX; a++) {
            for (int g = 0; g <= MAX; g++) {
                for (int z = 0; z <= MAX; z++) {
                    for (int n = 0; n <= MAX; n++) {
                        for (int p = 0; p <= MAX; p++) {
                            if (a + g + z + n + p > length) {
                                continue;
                            }
                            if (a + g + z + n + p < length) {
                                continue;
                            }
                            for (FloatBuilder builder : FloatBuilder.values()) {
                                out.println("Test 'float': random = " + random.getSeed() +
                                   ", length = " + length + ", a = " + a + ", g = " +
                                   g + ", z = " + z + ", n = " + n + ", p = " + p);
                                builder.build(golden, a, g, z, n, p, random);
                                float[] test = golden.clone();
                                scramble(test, random);
                                sort(test);
                                compare(test, golden, a, n, g);
                            }
                            newLine = true;
                        }
                    }
                }
            }
        }
        if (newLine) {
            out.println();
        }
    }

    private static void testAndCheckDouble(int length, MyRandom random) {
        ourDescription = "Check double sorting";
        double[] golden = new double[length];
        final int MAX = 10;
        boolean newLine = false;

        for (int a = 0; a <= MAX; a++) {
            for (int g = 0; g <= MAX; g++) {
                for (int z = 0; z <= MAX; z++) {
                    for (int n = 0; n <= MAX; n++) {
                        for (int p = 0; p <= MAX; p++) {
                            if (a + g + z + n + p > length) {
                                continue;
                            }
                            if (a + g + z + n + p < length) {
                                continue;
                            }
                            for (DoubleBuilder builder : DoubleBuilder.values()) {
                                out.println("Test 'double': random = " + random.getSeed() +
                                   ", length = " + length + ", a = " + a + ", g = " +
                                   g + ", z = " + z + ", n = " + n + ", p = " + p);
                                builder.build(golden, a, g, z, n, p, random);
                                double[] test = golden.clone();
                                scramble(test, random);
                                sort(test);
                                compare(test, golden, a, n, g);
                            }
                            newLine = true;
                        }
                    }
                }
            }
        }
        if (newLine) {
            out.println();
        }
    }

    private static void prepareSubArray(int[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            a[i] = 0xDEDA;
        }
        int middle = (fromIndex + toIndex) >>> 1;
        int k = 0;

        for (int i = fromIndex; i < middle; i++) {
            a[i] = k++;
        }
        for (int i = middle; i < toIndex; i++) {
            a[i] = k--;
        }
        for (int i = toIndex; i < a.length; i++) {
            a[i] = 0xBABA;
        }
    }

    private static void scramble(int[] a, Random random) {
        for (int i = 0; i < a.length * 7; i++) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private static void scramble(float[] a, Random random) {
        for (int i = 0; i < a.length * 7; i++) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private static void scramble(double[] a, Random random) {
        for (int i = 0; i < a.length * 7; i++) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static void swap(float[] a, int i, int j) {
        float t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static void swap(double[] a, int i, int j) {
        double t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static enum TypeConverter {
        INT {
            Object convert(int[] a) {
                return a.clone();
            }
        },
        LONG {
            Object convert(int[] a) {
                long[] b = new long[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (long) a[i];
                }
                return b;
            }
        },
        BYTE {
            Object convert(int[] a) {
                byte[] b = new byte[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (byte) a[i];
                }
                return b;
            }
        },
        SHORT {
            Object convert(int[] a) {
                short[] b = new short[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (short) a[i];
                }
                return b;
            }
        },
        CHAR {
            Object convert(int[] a) {
                char[] b = new char[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (char) a[i];
                }
                return b;
            }
        },
        FLOAT {
            Object convert(int[] a) {
                float[] b = new float[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (float) a[i];
                }
                return b;
            }
        },
        DOUBLE {
            Object convert(int[] a) {
                double[] b = new double[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (double) a[i];
                }
                return b;
            }
        },
        INTEGER {
            Object convert(int[] a) {
                Integer[] b = new Integer[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = new Integer(a[i]);
                }
                return b;
            }
        };

        abstract Object convert(int[] a);

        @Override public String toString() {
            String name = name();

            for (int i = name.length(); i < 9; i++) {
                name += " ";
            }
            return name;
        }
    }

    private static enum FloatBuilder {
        SIMPLE {
            void build(float[] x, int a, int g, int z, int n, int p, Random random) {
                int fromIndex = 0;
                float negativeValue = -random.nextFloat();
                float positiveValue =  random.nextFloat();

                writeValue(x, negativeValue, fromIndex, n);
                fromIndex += n;

                writeValue(x, -0.0f, fromIndex, g);
                fromIndex += g;

                writeValue(x, 0.0f, fromIndex, z);
                fromIndex += z;

                writeValue(x, positiveValue, fromIndex, p);
                fromIndex += p;

                writeValue(x, Float.NaN, fromIndex, a);
            }
        };

        abstract void build(float[] x, int a, int g, int z, int n, int p, Random random);
    }

    private static enum DoubleBuilder {
        SIMPLE {
            void build(double[] x, int a, int g, int z, int n, int p, Random random) {
                int fromIndex = 0;
                double negativeValue = -random.nextFloat();
                double positiveValue =  random.nextFloat();

                writeValue(x, negativeValue, fromIndex, n);
                fromIndex += n;

                writeValue(x, -0.0d, fromIndex, g);
                fromIndex += g;

                writeValue(x, 0.0d, fromIndex, z);
                fromIndex += z;

                writeValue(x, positiveValue, fromIndex, p);
                fromIndex += p;

                writeValue(x, Double.NaN, fromIndex, a);
            }
        };

        abstract void build(double[] x, int a, int g, int z, int n, int p, Random random);
    }

    private static void writeValue(float[] a, float value, int fromIndex, int count) {
        for (int i = fromIndex; i < fromIndex + count; i++) {
            a[i] = value;
        }
    }

    private static void compare(float[] a, float[] b, int numNaN, int numNeg, int numNegZero) {
        for (int i = a.length - numNaN; i < a.length; i++) {
            if (a[i] == a[i]) {
                failed("On position " + i + " must be NaN instead of " + a[i]);
            }
        }
        final int NEGATIVE_ZERO = Float.floatToIntBits(-0.0f);

        for (int i = numNeg; i < numNeg + numNegZero; i++) {
            if (NEGATIVE_ZERO != Float.floatToIntBits(a[i])) {
                failed("On position " + i + " must be -0.0 instead of " + a[i]);
            }
        }
        for (int i = 0; i < a.length - numNaN; i++) {
            if (a[i] != b[i]) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void writeValue(double[] a, double value, int fromIndex, int count) {
        for (int i = fromIndex; i < fromIndex + count; i++) {
            a[i] = value;
        }
    }

    private static void compare(double[] a, double[] b, int numNaN, int numNeg, int numNegZero) {
        for (int i = a.length - numNaN; i < a.length; i++) {
            if (a[i] == a[i]) {
                failed("On position " + i + " must be NaN instead of " + a[i]);
            }
        }
        final long NEGATIVE_ZERO = Double.doubleToLongBits(-0.0d);

        for (int i = numNeg; i < numNeg + numNegZero; i++) {
            if (NEGATIVE_ZERO != Double.doubleToLongBits(a[i])) {
                failed("On position " + i + " must be -0.0 instead of " + a[i]);
            }
        }
        for (int i = 0; i < a.length - numNaN; i++) {
            if (a[i] != b[i]) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static enum SortedBuilder {
        REPEATED {
            void build(int[] a, int m) {
                int period = a.length / m;
                int i = 0;
                int k = 0;

                while (true) {
                    for (int t = 1; t <= period; t++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = k;
                    }
                    if (i >= a.length) {
                        return;
                    }
                    k++;
                }
            }
        },
        ORGAN_PIPES {
            void build(int[] a, int m) {
                int i = 0;
                int k = m;

                while (true) {
                    for (int t = 1; t <= m; t++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = k;
                    }
                }
            }
        };

        abstract void build(int[] a, int m);

        @Override public String toString() {
            String name = name();

            for (int i = name.length(); i < 12; i++) {
                name += " ";
            }
            return name;
        }
    }

    private static enum MergeBuilder {
        ASCENDING {
            void build(int[] a, int m) {
                int period = a.length / m;
                int v = 1, i = 0;

                for (int k = 0; k < m; k++) {
                    v = 1;
                    for (int p = 0; p < period; p++) {
                        a[i++] = v++;
                    }
                }
                for (int j = i; j < a.length - 1; j++) {
                    a[j] = v++;
                }
                a[a.length - 1] = 0;
            }
        },
        DESCENDING {
            void build(int[] a, int m) {
                int period = a.length / m;
                int v = -1, i = 0;

                for (int k = 0; k < m; k++) {
                    v = -1;
                    for (int p = 0; p < period; p++) {
                        a[i++] = v--;
                    }
                }
                for (int j = i; j < a.length - 1; j++) {
                    a[j] = v--;
                }
                a[a.length - 1] = 0;
            }
        };

        abstract void build(int[] a, int m);

        @Override public String toString() {
            String name = name();

            for (int i = name.length(); i < 12; i++) {
                name += " ";
            }
            return name;
        }
    }

    private static enum UnsortedBuilder {
        RANDOM {
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = random.nextInt();
                }
            }
        },
        ASCENDING {
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = m + i;
                }
            }
        },
        DESCENDING {
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = a.length - m - i;
                }
            }
        },
        ALL_EQUAL {
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = m;
                }
            }
        },
        SAW {
            void build(int[] a, int m, Random random) {
                int incCount = 1;
                int decCount = a.length;
                int i = 0;
                int period = m--;

                while (true) {
                    for (int k = 1; k <= period; k++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = incCount++;
                    }
                    period += m;

                    for (int k = 1; k <= period; k++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = decCount--;
                    }
                    period += m;
                }
            }
        },
        REPEATED {
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = i % m;
                }
            }
        },
        DUPLICATED {
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = random.nextInt(m);
                }
            }
        },
        ORGAN_PIPES {
            void build(int[] a, int m, Random random) {
                int middle = a.length / (m + 1);

                for (int i = 0; i < middle; i++) {
                    a[i] = i;
                }
                for (int i = middle; i < a.length; i++) {
                    a[i] = a.length - i - 1;
                }
            }
        },
        STAGGER {
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = (i * m + i) % a.length;
                }
            }
        },
        PLATEAU {
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = Math.min(i, m);
                }
            }
        },
        SHUFFLE {
            void build(int[] a, int m, Random random) {
                int x = 0, y = 0;
                for (int i = 0; i < a.length; i++) {
                    a[i] = random.nextBoolean() ? (x += 2) : (y += 2);
                }
            }
        };

        abstract void build(int[] a, int m, Random random);

        @Override public String toString() {
            String name = name();

            for (int i = name.length(); i < 12; i++) {
                name += " ";
            }
            return name;
        }
    }

    private static void checkWithCheckSum(Object test, Object golden) {
        checkSorted(test);
        checkCheckSum(test, golden);
    }

    private static void failed(String message) {
        err.format("\n*** TEST FAILED - %s.\n\n%s.\n\n", ourDescription, message);
        throw new RuntimeException("Test failed - see log file for details");
    }

    private static void failedSort(int index, String value1, String value2) {
        failed("Array is not sorted at " + index + "-th position: " +
            value1 + " and " + value2);
    }

    private static void failedCompare(int index, String value1, String value2) {
        failed("On position " + index + " must be " + value2 + " instead of " + value1);
    }

    private static void compare(Object test, Object golden) {
        if (test instanceof int[]) {
            compare((int[]) test, (int[]) golden);
        } else if (test instanceof long[]) {
            compare((long[]) test, (long[]) golden);
        } else if (test instanceof short[]) {
            compare((short[]) test, (short[]) golden);
        } else if (test instanceof byte[]) {
            compare((byte[]) test, (byte[]) golden);
        } else if (test instanceof char[]) {
            compare((char[]) test, (char[]) golden);
        } else if (test instanceof float[]) {
            compare((float[]) test, (float[]) golden);
        } else if (test instanceof double[]) {
            compare((double[]) test, (double[]) golden);
        } else if (test instanceof Integer[]) {
            compare((Integer[]) test, (Integer[]) golden);
        } else {
            failed("Unknow type of array: " + test + " of class " +
                test.getClass().getName());
        }
    }

    private static void compare(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(long[] a, long[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(short[] a, short[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(char[] a, char[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(float[] a, float[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(Integer[] a, Integer[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].compareTo(b[i]) != 0) {
                failedCompare(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void checkSorted(Object object) {
        if (object instanceof int[]) {
            checkSorted((int[]) object);
        } else if (object instanceof long[]) {
            checkSorted((long[]) object);
        } else if (object instanceof short[]) {
            checkSorted((short[]) object);
        } else if (object instanceof byte[]) {
            checkSorted((byte[]) object);
        } else if (object instanceof char[]) {
            checkSorted((char[]) object);
        } else if (object instanceof float[]) {
            checkSorted((float[]) object);
        } else if (object instanceof double[]) {
            checkSorted((double[]) object);
        } else if (object instanceof Integer[]) {
            checkSorted((Integer[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void checkSorted(int[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(long[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(short[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(byte[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(char[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(float[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(double[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(Integer[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i].intValue() > a[i + 1].intValue()) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkCheckSum(Object test, Object golden) {
        if (checkSumXor(test) != checkSumXor(golden)) {
            failed("Original and sorted arrays are not identical [xor]");
        }
        if (checkSumPlus(test) != checkSumPlus(golden)) {
            failed("Original and sorted arrays are not identical [plus]");
        }
    }

    private static int checkSumXor(Object object) {
        if (object instanceof int[]) {
            return checkSumXor((int[]) object);
        } else if (object instanceof long[]) {
            return checkSumXor((long[]) object);
        } else if (object instanceof short[]) {
            return checkSumXor((short[]) object);
        } else if (object instanceof byte[]) {
            return checkSumXor((byte[]) object);
        } else if (object instanceof char[]) {
            return checkSumXor((char[]) object);
        } else if (object instanceof float[]) {
            return checkSumXor((float[]) object);
        } else if (object instanceof double[]) {
            return checkSumXor((double[]) object);
        } else if (object instanceof Integer[]) {
            return checkSumXor((Integer[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
            return -1;
        }
    }

    private static int checkSumXor(Integer[] a) {
        int checkSum = 0;

        for (Integer e : a) {
            checkSum ^= e.intValue();
        }
        return checkSum;
    }

    private static int checkSumXor(int[] a) {
        int checkSum = 0;

        for (int e : a) {
            checkSum ^= e;
        }
        return checkSum;
    }

    private static int checkSumXor(long[] a) {
        long checkSum = 0;

        for (long e : a) {
            checkSum ^= e;
        }
        return (int) checkSum;
    }

    private static int checkSumXor(short[] a) {
        short checkSum = 0;

        for (short e : a) {
            checkSum ^= e;
        }
        return (int) checkSum;
    }

    private static int checkSumXor(byte[] a) {
        byte checkSum = 0;

        for (byte e : a) {
            checkSum ^= e;
        }
        return (int) checkSum;
    }

    private static int checkSumXor(char[] a) {
        char checkSum = 0;

        for (char e : a) {
            checkSum ^= e;
        }
        return (int) checkSum;
    }

    private static int checkSumXor(float[] a) {
        int checkSum = 0;

        for (float e : a) {
            checkSum ^= (int) e;
        }
        return checkSum;
    }

    private static int checkSumXor(double[] a) {
        int checkSum = 0;

        for (double e : a) {
            checkSum ^= (int) e;
        }
        return checkSum;
    }

    private static int checkSumPlus(Object object) {
        if (object instanceof int[]) {
            return checkSumPlus((int[]) object);
        } else if (object instanceof long[]) {
            return checkSumPlus((long[]) object);
        } else if (object instanceof short[]) {
            return checkSumPlus((short[]) object);
        } else if (object instanceof byte[]) {
            return checkSumPlus((byte[]) object);
        } else if (object instanceof char[]) {
            return checkSumPlus((char[]) object);
        } else if (object instanceof float[]) {
            return checkSumPlus((float[]) object);
        } else if (object instanceof double[]) {
            return checkSumPlus((double[]) object);
        } else if (object instanceof Integer[]) {
            return checkSumPlus((Integer[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
            return -1;
        }
    }

    private static int checkSumPlus(int[] a) {
        int checkSum = 0;

        for (int e : a) {
            checkSum += e;
        }
        return checkSum;
    }

    private static int checkSumPlus(long[] a) {
        long checkSum = 0;

        for (long e : a) {
            checkSum += e;
        }
        return (int) checkSum;
    }

    private static int checkSumPlus(short[] a) {
        short checkSum = 0;

        for (short e : a) {
            checkSum += e;
        }
        return (int) checkSum;
    }

    private static int checkSumPlus(byte[] a) {
        byte checkSum = 0;

        for (byte e : a) {
            checkSum += e;
        }
        return (int) checkSum;
    }

    private static int checkSumPlus(char[] a) {
        char checkSum = 0;

        for (char e : a) {
            checkSum += e;
        }
        return (int) checkSum;
    }

    private static int checkSumPlus(float[] a) {
        int checkSum = 0;

        for (float e : a) {
            checkSum += (int) e;
        }
        return checkSum;
    }

    private static int checkSumPlus(double[] a) {
        int checkSum = 0;

        for (double e : a) {
            checkSum += (int) e;
        }
        return checkSum;
    }

    private static int checkSumPlus(Integer[] a) {
        int checkSum = 0;

        for (Integer e : a) {
            checkSum += e.intValue();
        }
        return checkSum;
    }

    private static void sortByInsertionSort(Object object) {
        if (object instanceof int[]) {
            sortByInsertionSort((int[]) object);
        } else if (object instanceof long[]) {
            sortByInsertionSort((long[]) object);
        } else if (object instanceof short[]) {
            sortByInsertionSort((short[]) object);
        } else if (object instanceof byte[]) {
            sortByInsertionSort((byte[]) object);
        } else if (object instanceof char[]) {
            sortByInsertionSort((char[]) object);
        } else if (object instanceof float[]) {
            sortByInsertionSort((float[]) object);
        } else if (object instanceof double[]) {
            sortByInsertionSort((double[]) object);
        } else if (object instanceof Integer[]) {
            sortByInsertionSort((Integer[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void sortByInsertionSort(int[] a) {
        for (int j, i = 1; i < a.length; i++) {
            int ai = a[i];
            for (j = i - 1; j >= 0 && ai < a[j]; j--) {
                a[j + 1] = a[j];
            }
            a[j + 1] = ai;
        }
    }

    private static void sortByInsertionSort(long[] a) {
        for (int j, i = 1; i < a.length; i++) {
            long ai = a[i];
            for (j = i - 1; j >= 0 && ai < a[j]; j--) {
                a[j + 1] = a[j];
            }
            a[j + 1] = ai;
        }
    }

    private static void sortByInsertionSort(short[] a) {
        for (int j, i = 1; i < a.length; i++) {
            short ai = a[i];
            for (j = i - 1; j >= 0 && ai < a[j]; j--) {
                a[j + 1] = a[j];
            }
            a[j + 1] = ai;
        }
    }

    private static void sortByInsertionSort(byte[] a) {
        for (int j, i = 1; i < a.length; i++) {
            byte ai = a[i];
            for (j = i - 1; j >= 0 && ai < a[j]; j--) {
                a[j + 1] = a[j];
            }
            a[j + 1] = ai;
        }
    }

    private static void sortByInsertionSort(char[] a) {
        for (int j, i = 1; i < a.length; i++) {
            char ai = a[i];
            for (j = i - 1; j >= 0 && ai < a[j]; j--) {
                a[j + 1] = a[j];
            }
            a[j + 1] = ai;
        }
    }

    private static void sortByInsertionSort(float[] a) {
        for (int j, i = 1; i < a.length; i++) {
            float ai = a[i];
            for (j = i - 1; j >= 0 && ai < a[j]; j--) {
                a[j + 1] = a[j];
            }
            a[j + 1] = ai;
        }
    }

    private static void sortByInsertionSort(double[] a) {
        for (int j, i = 1; i < a.length; i++) {
            double ai = a[i];
            for (j = i - 1; j >= 0 && ai < a[j]; j--) {
                a[j + 1] = a[j];
            }
            a[j + 1] = ai;
        }
    }

    private static void sortByInsertionSort(Integer[] a) {
        for (int j, i = 1; i < a.length; i++) {
            Integer ai = a[i];
            for (j = i - 1; j >= 0 && ai < a[j]; j--) {
                a[j + 1] = a[j];
            }
            a[j + 1] = ai;
        }
    }

    private static void sort(Object object) {
        if (object instanceof int[]) {
            Arrays.parallelSort((int[]) object);
        } else if (object instanceof long[]) {
            Arrays.parallelSort((long[]) object);
        } else if (object instanceof short[]) {
            Arrays.parallelSort((short[]) object);
        } else if (object instanceof byte[]) {
            Arrays.parallelSort((byte[]) object);
        } else if (object instanceof char[]) {
            Arrays.parallelSort((char[]) object);
        } else if (object instanceof float[]) {
            Arrays.parallelSort((float[]) object);
        } else if (object instanceof double[]) {
            Arrays.parallelSort((double[]) object);
        } else if (object instanceof Integer[]) {
            Arrays.parallelSort((Integer[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void sortSubArray(Object object, int fromIndex, int toIndex) {
        if (object instanceof int[]) {
            Arrays.parallelSort((int[]) object, fromIndex, toIndex);
        } else if (object instanceof long[]) {
            Arrays.parallelSort((long[]) object, fromIndex, toIndex);
        } else if (object instanceof short[]) {
            Arrays.parallelSort((short[]) object, fromIndex, toIndex);
        } else if (object instanceof byte[]) {
            Arrays.parallelSort((byte[]) object, fromIndex, toIndex);
        } else if (object instanceof char[]) {
            Arrays.parallelSort((char[]) object, fromIndex, toIndex);
        } else if (object instanceof float[]) {
            Arrays.parallelSort((float[]) object, fromIndex, toIndex);
        } else if (object instanceof double[]) {
            Arrays.parallelSort((double[]) object, fromIndex, toIndex);
        } else if (object instanceof Integer[]) {
            Arrays.parallelSort((Integer[]) object, fromIndex, toIndex);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void checkSubArray(Object object, int fromIndex, int toIndex, int m) {
        if (object instanceof int[]) {
            checkSubArray((int[]) object, fromIndex, toIndex, m);
        } else if (object instanceof long[]) {
            checkSubArray((long[]) object, fromIndex, toIndex, m);
        } else if (object instanceof short[]) {
            checkSubArray((short[]) object, fromIndex, toIndex, m);
        } else if (object instanceof byte[]) {
            checkSubArray((byte[]) object, fromIndex, toIndex, m);
        } else if (object instanceof char[]) {
            checkSubArray((char[]) object, fromIndex, toIndex, m);
        } else if (object instanceof float[]) {
            checkSubArray((float[]) object, fromIndex, toIndex, m);
        } else if (object instanceof double[]) {
            checkSubArray((double[]) object, fromIndex, toIndex, m);
        } else if (object instanceof Integer[]) {
            checkSubArray((Integer[]) object, fromIndex, toIndex, m);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void checkSubArray(Integer[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i].intValue() != 0xDEDA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i].intValue() > a[i + 1].intValue()) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i].intValue() != 0xBABA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }
    }

    private static void checkSubArray(int[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != 0xDEDA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != 0xBABA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }
    }

    private static void checkSubArray(byte[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (byte) 0xDEDA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (byte) 0xBABA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }
    }

    private static void checkSubArray(long[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (long) 0xDEDA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (long) 0xBABA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }
    }

    private static void checkSubArray(char[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (char) 0xDEDA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (char) 0xBABA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }
    }

    private static void checkSubArray(short[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (short) 0xDEDA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (short) 0xBABA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }
    }

    private static void checkSubArray(float[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (float) 0xDEDA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (float) 0xBABA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }
    }

    private static void checkSubArray(double[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (double) 0xDEDA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failedSort(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (double) 0xBABA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }
    }

    private static void checkRange(Object object, int m) {
        if (object instanceof int[]) {
            checkRange((int[]) object, m);
        } else if (object instanceof long[]) {
            checkRange((long[]) object, m);
        } else if (object instanceof short[]) {
            checkRange((short[]) object, m);
        } else if (object instanceof byte[]) {
            checkRange((byte[]) object, m);
        } else if (object instanceof char[]) {
            checkRange((char[]) object, m);
        } else if (object instanceof float[]) {
            checkRange((float[]) object, m);
        } else if (object instanceof double[]) {
            checkRange((double[]) object, m);
        } else if (object instanceof Integer[]) {
            checkRange((Integer[]) object, m);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void checkRange(Integer[] a, int m) {
        try {
            Arrays.parallelSort(a, m + 1, m);

            failed("ParallelSort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.parallelSort(a, -m, a.length);

                failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.parallelSort(a, 0, a.length + m);

                    failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(int[] a, int m) {
        try {
            Arrays.parallelSort(a, m + 1, m);

            failed("ParallelSort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.parallelSort(a, -m, a.length);

                failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.parallelSort(a, 0, a.length + m);

                    failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(long[] a, int m) {
        try {
            Arrays.parallelSort(a, m + 1, m);

            failed("ParallelSort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.parallelSort(a, -m, a.length);

                failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.parallelSort(a, 0, a.length + m);

                    failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(byte[] a, int m) {
        try {
            Arrays.parallelSort(a, m + 1, m);

            failed("ParallelSort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.parallelSort(a, -m, a.length);

                failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.parallelSort(a, 0, a.length + m);

                    failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(short[] a, int m) {
        try {
            Arrays.parallelSort(a, m + 1, m);

            failed("ParallelSort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.parallelSort(a, -m, a.length);

                failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.parallelSort(a, 0, a.length + m);

                    failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(char[] a, int m) {
        try {
            Arrays.parallelSort(a, m + 1, m);

            failed("ParallelSort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.parallelSort(a, -m, a.length);

                failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.parallelSort(a, 0, a.length + m);

                    failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(float[] a, int m) {
        try {
            Arrays.parallelSort(a, m + 1, m);

            failed("ParallelSort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.parallelSort(a, -m, a.length);

                failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.parallelSort(a, 0, a.length + m);

                    failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(double[] a, int m) {
        try {
            Arrays.parallelSort(a, m + 1, m);

            failed("ParallelSort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.parallelSort(a, -m, a.length);

                failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.parallelSort(a, 0, a.length + m);

                    failed("ParallelSort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void outArray(Object[] a) {
        for (int i = 0; i < a.length; i++) {
            out.print(a[i] + " ");
        }
        out.println();
    }

    private static void outArray(int[] a) {
        for (int i = 0; i < a.length; i++) {
            out.print(a[i] + " ");
        }
        out.println();
    }

    private static void outArray(float[] a) {
        for (int i = 0; i < a.length; i++) {
            out.print(a[i] + " ");
        }
        out.println();
    }

    private static void outArray(double[] a) {
        for (int i = 0; i < a.length; i++) {
            out.print(a[i] + " ");
        }
        out.println();
    }

    private static class MyRandom extends Random {
        MyRandom(long seed) {
            super(seed);
            mySeed = seed;
        }

        long getSeed() {
            return mySeed;
        }

        private long mySeed;
    }

    private static String ourDescription;
}
