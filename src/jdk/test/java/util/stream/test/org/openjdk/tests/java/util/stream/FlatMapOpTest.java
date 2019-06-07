/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @summary flat-map operations
 * @bug 8044047 8076458 8075939
 */

package org.openjdk.tests.java.util.stream;

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.LambdaTestHelpers.*;
import static java.util.stream.ThowableHelper.checkNPE;

@Test
public class FlatMapOpTest extends OpTestCase {

    @Test
    public void testNullMapper() {
        checkNPE(() -> Stream.of(1).flatMap(null));
        checkNPE(() -> IntStream.of(1).flatMap(null));
        checkNPE(() -> LongStream.of(1).flatMap(null));
        checkNPE(() -> DoubleStream.of(1).flatMap(null));
    }

    static final Function<Integer, Stream<Integer>> integerRangeMapper
            = e -> IntStream.range(0, e).boxed();

    @Test
    public void testFlatMap() {
        String[] stringsArray = {"hello", "there", "", "yada"};
        Stream<String> strings = Arrays.asList(stringsArray).stream();
        assertConcat(strings.flatMap(flattenChars).iterator(), "hellothereyada");

        assertCountSum(countTo(10).stream().flatMap(mfId), 10, 55);
        assertCountSum(countTo(10).stream().flatMap(mfNull), 0, 0);
        assertCountSum(countTo(3).stream().flatMap(mfLt), 6, 4);

        exerciseOps(TestData.Factory.ofArray("stringsArray", stringsArray), s -> s.flatMap(flattenChars));
        exerciseOps(TestData.Factory.ofArray("LONG_STRING", new String[] {LONG_STRING}), s -> s.flatMap(flattenChars));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testOps(String name, TestData.OfRef<Integer> data) {
        Collection<Integer> result = exerciseOps(data, s -> s.flatMap(mfId));
        assertEquals(data.size(), result.size());

        result = exerciseOps(data, s -> s.flatMap(mfNull));
        assertEquals(0, result.size());

        result = exerciseOps(data, s-> s.flatMap(e -> Stream.empty()));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "StreamTestData<Integer>.small", dataProviderClass = StreamTestDataProvider.class)
    public void testOpsX(String name, TestData.OfRef<Integer> data) {
        exerciseOps(data, s -> s.flatMap(mfLt));
        exerciseOps(data, s -> s.flatMap(integerRangeMapper));
        exerciseOps(data, s -> s.flatMap((Integer e) -> IntStream.range(0, e).boxed().limit(10)));
    }

    @Test
    public void testOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        Stream.of(0).flatMap(i -> IntStream.range(0, 100).boxed()).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    //

    @Test(dataProvider = "IntStreamTestData", dataProviderClass = IntStreamTestDataProvider.class)
    public void testIntOps(String name, TestData.OfInt data) {
        Collection<Integer> result = exerciseOps(data, s -> s.flatMap(IntStream::of));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.boxed().flatMapToInt(IntStream::of));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.flatMap(i -> IntStream.empty()));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "IntStreamTestData.small", dataProviderClass = IntStreamTestDataProvider.class)
    public void testIntOpsX(String name, TestData.OfInt data) {
        exerciseOps(data, s -> s.flatMap(e -> IntStream.range(0, e)));
        exerciseOps(data, s -> s.flatMap(e -> IntStream.range(0, e).limit(10)));

        exerciseOps(data, s -> s.boxed().flatMapToInt(e -> IntStream.range(0, e)));
        exerciseOps(data, s -> s.boxed().flatMapToInt(e -> IntStream.range(0, e).limit(10)));
    }

    @Test
    public void testIntOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        IntStream.of(0).flatMap(i -> IntStream.range(0, 100)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);

        count.set(0);
        Stream.of(0).flatMapToInt(i -> IntStream.range(0, 100)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    //

    @Test(dataProvider = "LongStreamTestData", dataProviderClass = LongStreamTestDataProvider.class)
    public void testLongOps(String name, TestData.OfLong data) {
        Collection<Long> result = exerciseOps(data, s -> s.flatMap(LongStream::of));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.boxed().flatMapToLong(LongStream::of));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> LongStream.empty());
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "LongStreamTestData.small", dataProviderClass = LongStreamTestDataProvider.class)
    public void testLongOpsX(String name, TestData.OfLong data) {
        exerciseOps(data, s -> s.flatMap(e -> LongStream.range(0, e)));
        exerciseOps(data, s -> s.flatMap(e -> LongStream.range(0, e).limit(10)));
    }

    @Test
    public void testLongOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        LongStream.of(0).flatMap(i -> LongStream.range(0, 100)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);

        count.set(0);
        Stream.of(0).flatMapToLong(i -> LongStream.range(0, 100)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    //

    @Test(dataProvider = "DoubleStreamTestData", dataProviderClass = DoubleStreamTestDataProvider.class)
    public void testDoubleOps(String name, TestData.OfDouble data) {
        Collection<Double> result = exerciseOps(data, s -> s.flatMap(DoubleStream::of));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.boxed().flatMapToDouble(DoubleStream::of));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> DoubleStream.empty());
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "DoubleStreamTestData.small", dataProviderClass = DoubleStreamTestDataProvider.class)
    public void testDoubleOpsX(String name, TestData.OfDouble data) {
        exerciseOps(data, s -> s.flatMap(e -> IntStream.range(0, (int) e).asDoubleStream()));
        exerciseOps(data, s -> s.flatMap(e -> IntStream.range(0, (int) e).limit(10).asDoubleStream()));
    }

    @Test
    public void testDoubleOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        DoubleStream.of(0).flatMap(i -> IntStream.range(0, 100).asDoubleStream()).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);

        count.set(0);
        Stream.of(0).flatMapToDouble(i -> IntStream.range(0, 100).asDoubleStream()).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }
}
