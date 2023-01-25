/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.tests.java.util.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.LambdaTestHelpers;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;
import java.util.stream.StreamOpFlagTestHelper;
import java.util.stream.StreamTestDataProvider;
import java.util.stream.TestData;

import org.testng.annotations.Test;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.groupingByConcurrent;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.LambdaTestHelpers.assertContents;
import static java.util.stream.LambdaTestHelpers.assertContentsUnordered;
import static java.util.stream.LambdaTestHelpers.mDoubler;

/**
 * TabulatorsTest
 *
 * @author Brian Goetz
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TabulatorsTest extends OpTestCase {

    private static abstract class TabulationAssertion<T, U> {
        abstract void assertValue(U value,
                                  Supplier<Stream<T>> source,
                                  boolean ordered) throws ReflectiveOperationException;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class GroupedMapAssertion<T, K, V, M extends Map<K, ? extends V>> extends TabulationAssertion<T, M> {
        private final Class<? extends Map> clazz;
        private final Function<T, K> classifier;
        private final TabulationAssertion<T,V> downstream;

        protected GroupedMapAssertion(Function<T, K> classifier,
                                      Class<? extends Map> clazz,
                                      TabulationAssertion<T, V> downstream) {
            this.clazz = clazz;
            this.classifier = classifier;
            this.downstream = downstream;
        }

        void assertValue(M map,
                         Supplier<Stream<T>> source,
                         boolean ordered) throws ReflectiveOperationException {
            if (!clazz.isAssignableFrom(map.getClass()))
                fail(String.format("Class mismatch in GroupedMapAssertion: %s, %s", clazz, map.getClass()));
            assertContentsUnordered(map.keySet(), source.get().map(classifier).collect(toSet()));
            for (Map.Entry<K, ? extends V> entry : map.entrySet()) {
                K key = entry.getKey();
                downstream.assertValue(entry.getValue(),
                                       () -> source.get().filter(e -> classifier.apply(e).equals(key)),
                                       ordered);
            }
        }
    }

    static class ToMapAssertion<T, K, V, M extends Map<K,V>> extends TabulationAssertion<T, M> {
        private final Class<? extends Map> clazz;
        private final Function<T, K> keyFn;
        private final Function<T, V> valueFn;
        private final BinaryOperator<V> mergeFn;

        ToMapAssertion(Function<T, K> keyFn,
                       Function<T, V> valueFn,
                       BinaryOperator<V> mergeFn,
                       Class<? extends Map> clazz) {
            this.clazz = clazz;
            this.keyFn = keyFn;
            this.valueFn = valueFn;
            this.mergeFn = mergeFn;
        }

        @Override
        void assertValue(M map, Supplier<Stream<T>> source, boolean ordered) throws ReflectiveOperationException {
            Set<K> uniqueKeys = source.get().map(keyFn).collect(toSet());
            assertTrue(clazz.isAssignableFrom(map.getClass()));
            assertEquals(uniqueKeys, map.keySet());
            source.get().forEach(t -> {
                K key = keyFn.apply(t);
                V v = source.get()
                            .filter(e -> key.equals(keyFn.apply(e)))
                            .map(valueFn)
                            .reduce(mergeFn)
                            .get();
                assertEquals(map.get(key), v);
            });
        }
    }

    static class PartitionAssertion<T, D> extends TabulationAssertion<T, Map<Boolean,D>> {
        private final Predicate<T> predicate;
        private final TabulationAssertion<T,D> downstream;

        protected PartitionAssertion(Predicate<T> predicate,
                                     TabulationAssertion<T, D> downstream) {
            this.predicate = predicate;
            this.downstream = downstream;
        }

        void assertValue(Map<Boolean, D> map,
                         Supplier<Stream<T>> source,
                         boolean ordered) throws ReflectiveOperationException {
            if (!Map.class.isAssignableFrom(map.getClass()))
                fail(String.format("Class mismatch in PartitionAssertion: %s", map.getClass()));
            assertEquals(2, map.size());
            downstream.assertValue(map.get(true), () -> source.get().filter(predicate), ordered);
            downstream.assertValue(map.get(false), () -> source.get().filter(predicate.negate()), ordered);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class ListAssertion<T> extends TabulationAssertion<T, List<T>> {
        @Override
        void assertValue(List<T> value, Supplier<Stream<T>> source, boolean ordered)
                throws ReflectiveOperationException {
            if (!List.class.isAssignableFrom(value.getClass()))
                fail(String.format("Class mismatch in ListAssertion: %s", value.getClass()));
            Stream<T> stream = source.get();
            List<T> result = new ArrayList<>();
            for (Iterator<T> it = stream.iterator(); it.hasNext(); ) // avoid capturing result::add
                result.add(it.next());
            if (StreamOpFlagTestHelper.isStreamOrdered(stream) && ordered)
                assertContents(value, result);
            else
                assertContentsUnordered(value, result);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class CollectionAssertion<T> extends TabulationAssertion<T, Collection<T>> {
        private final Class<? extends Collection> clazz;
        private final boolean targetOrdered;

        protected CollectionAssertion(Class<? extends Collection> clazz, boolean targetOrdered) {
            this.clazz = clazz;
            this.targetOrdered = targetOrdered;
        }

        @Override
        void assertValue(Collection<T> value, Supplier<Stream<T>> source, boolean ordered)
                throws ReflectiveOperationException {
            if (!clazz.isAssignableFrom(value.getClass()))
                fail(String.format("Class mismatch in CollectionAssertion: %s, %s", clazz, value.getClass()));
            Stream<T> stream = source.get();
            Collection<T> result = clazz.newInstance();
            for (Iterator<T> it = stream.iterator(); it.hasNext(); ) // avoid capturing result::add
                result.add(it.next());
            if (StreamOpFlagTestHelper.isStreamOrdered(stream) && targetOrdered && ordered)
                assertContents(value, result);
            else
                assertContentsUnordered(value, result);
        }
    }

    static class ReduceAssertion<T, U> extends TabulationAssertion<T, U> {
        private final U identity;
        private final Function<T, U> mapper;
        private final BinaryOperator<U> reducer;

        ReduceAssertion(U identity, Function<T, U> mapper, BinaryOperator<U> reducer) {
            this.identity = identity;
            this.mapper = mapper;
            this.reducer = reducer;
        }

        @Override
        void assertValue(U value, Supplier<Stream<T>> source, boolean ordered)
                throws ReflectiveOperationException {
            Optional<U> reduced = source.get().map(mapper).reduce(reducer);
            if (value == null)
                assertTrue(!reduced.isPresent());
            else if (!reduced.isPresent()) {
                assertEquals(value, identity);
            }
            else {
                assertEquals(value, reduced.get());
            }
        }
    }

    private <T> ResultAsserter<T> mapTabulationAsserter(boolean ordered) {
        return (act, exp, ord, par) -> {
            if (par && (!ordered || !ord)) {
                TabulatorsTest.nestedMapEqualityAssertion(act, exp);
            }
            else {
                LambdaTestHelpers.assertContentsEqual(act, exp);
            }
        };
    }

    private<T, M extends Map>
    void exerciseMapTabulation(TestData<T, Stream<T>> data,
                               Collector<T, ?, ? extends M> collector,
                               TabulationAssertion<T, M> assertion)
            throws ReflectiveOperationException {
        boolean ordered = !collector.characteristics().contains(Collector.Characteristics.UNORDERED);

        M m = withData(data)
                .terminal(s -> s.collect(collector))
                .resultAsserter(mapTabulationAsserter(ordered))
                .exercise();
        assertion.assertValue(m, () -> data.stream(), ordered);

        m = withData(data)
                .terminal(s -> s.unordered().collect(collector))
                .resultAsserter(mapTabulationAsserter(ordered))
                .exercise();
        assertion.assertValue(m, () -> data.stream(), false);
    }

    private static void nestedMapEqualityAssertion(Object o1, Object o2) {
        if (o1 instanceof Map) {
            Map m1 = (Map) o1;
            Map m2 = (Map) o2;
            assertContentsUnordered(m1.keySet(), m2.keySet());
            for (Object k : m1.keySet())
                nestedMapEqualityAssertion(m1.get(k), m2.get(k));
        }
        else if (o1 instanceof Collection) {
            assertContentsUnordered(((Collection) o1), ((Collection) o2));
        }
        else
            assertEquals(o1, o2);
    }

    private<T, R> void assertCollect(TestData.OfRef<T> data,
                                     Collector<T, ?, R> collector,
                                     Function<Stream<T>, R> streamReduction) {
        R check = streamReduction.apply(data.stream());
        withData(data).terminal(s -> s.collect(collector)).expectedResult(check).exercise();
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testReduce(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        assertCollect(data, Collectors.reducing(0, Integer::sum),
                      s -> s.reduce(0, Integer::sum));
        assertCollect(data, Collectors.reducing(Integer.MAX_VALUE, Integer::min),
                      s -> s.min(Integer::compare).orElse(Integer.MAX_VALUE));
        assertCollect(data, Collectors.reducing(Integer.MIN_VALUE, Integer::max),
                      s -> s.max(Integer::compare).orElse(Integer.MIN_VALUE));

        assertCollect(data, Collectors.reducing(Integer::sum),
                      s -> s.reduce(Integer::sum));
        assertCollect(data, Collectors.minBy(Comparator.naturalOrder()),
                      s -> s.min(Integer::compare));
        assertCollect(data, Collectors.maxBy(Comparator.naturalOrder()),
                      s -> s.max(Integer::compare));

        assertCollect(data, Collectors.reducing(0, x -> x*2, Integer::sum),
                      s -> s.map(x -> x*2).reduce(0, Integer::sum));

        assertCollect(data, Collectors.summingLong(x -> x * 2L),
                      s -> s.map(x -> x*2L).reduce(0L, Long::sum));
        assertCollect(data, Collectors.summingInt(x -> x * 2),
                      s -> s.map(x -> x*2).reduce(0, Integer::sum));
        assertCollect(data, Collectors.summingDouble(x -> x * 2.0d),
                      s -> s.map(x -> x * 2.0d).reduce(0.0d, Double::sum));

        assertCollect(data, Collectors.averagingInt(x -> x * 2),
                      s -> s.mapToInt(x -> x * 2).average().orElse(0));
        assertCollect(data, Collectors.averagingLong(x -> x * 2),
                      s -> s.mapToLong(x -> x * 2).average().orElse(0));
        assertCollect(data, Collectors.averagingDouble(x -> x * 2),
                      s -> s.mapToDouble(x -> x * 2).average().orElse(0));

        // Test explicit Collector.of
        Collector<Integer, long[], Double> avg2xint = Collector.of(() -> new long[2],
                                                                   (a, b) -> {
                                                                       a[0] += b * 2;
                                                                       a[1]++;
                                                                   },
                                                                   (a, b) -> {
                                                                       a[0] += b[0];
                                                                       a[1] += b[1];
                                                                       return a;
                                                                   },
                                                                   a -> a[1] == 0 ? 0.0d : (double) a[0] / a[1]);
        assertCollect(data, avg2xint,
                      s -> s.mapToInt(x -> x * 2).average().orElse(0));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testJoin(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        withData(data)
                .terminal(s -> s.map(Object::toString).collect(Collectors.joining()))
                .expectedResult(join(data, ""))
                .exercise();

        Collector<String, StringBuilder, String> likeJoining = Collector.of(StringBuilder::new, StringBuilder::append, (sb1, sb2) -> sb1.append(sb2.toString()), StringBuilder::toString);
        withData(data)
                .terminal(s -> s.map(Object::toString).collect(likeJoining))
                .expectedResult(join(data, ""))
                .exercise();

        withData(data)
                .terminal(s -> s.map(Object::toString).collect(Collectors.joining(",")))
                .expectedResult(join(data, ","))
                .exercise();

        withData(data)
                .terminal(s -> s.map(Object::toString).collect(Collectors.joining(",", "[", "]")))
                .expectedResult("[" + join(data, ",") + "]")
                .exercise();

        withData(data)
                .terminal(s -> s.map(Object::toString)
                                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                                .toString())
                .expectedResult(join(data, ""))
                .exercise();

        withData(data)
                .terminal(s -> s.map(Object::toString)
                                .collect(() -> new StringJoiner(","),
                                         (sj, cs) -> sj.add(cs),
                                         (j1, j2) -> j1.merge(j2))
                                .toString())
                .expectedResult(join(data, ","))
                .exercise();

        withData(data)
                .terminal(s -> s.map(Object::toString)
                                .collect(() -> new StringJoiner(",", "[", "]"),
                                         (sj, cs) -> sj.add(cs),
                                         (j1, j2) -> j1.merge(j2))
                                .toString())
                .expectedResult("[" + join(data, ",") + "]")
                .exercise();
    }

    private<T> String join(TestData.OfRef<T> data, String delim) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (T i : data) {
            if (!first)
                sb.append(delim);
            sb.append(i.toString());
            first = false;
        }
        return sb.toString();
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testSimpleToMap(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Function<Integer, Integer> keyFn = i -> i * 2;
        Function<Integer, Integer> valueFn = i -> i * 4;

        List<Integer> dataAsList = Arrays.asList(data.stream().toArray(Integer[]::new));
        Set<Integer> dataAsSet = new HashSet<>(dataAsList);

        BinaryOperator<Integer> sum = Integer::sum;
        for (BinaryOperator<Integer> op : Arrays.asList((u, v) -> u,
                                                        (u, v) -> v,
                                                        sum)) {
            try {
                exerciseMapTabulation(data, toMap(keyFn, valueFn),
                                      new ToMapAssertion<>(keyFn, valueFn, op, HashMap.class));
                if (dataAsList.size() != dataAsSet.size())
                    fail("Expected ISE on input with duplicates");
            }
            catch (IllegalStateException e) {
                if (dataAsList.size() == dataAsSet.size())
                    fail("Expected no ISE on input without duplicates");
            }

            exerciseMapTabulation(data, toMap(keyFn, valueFn, op),
                                  new ToMapAssertion<>(keyFn, valueFn, op, HashMap.class));

            exerciseMapTabulation(data, toMap(keyFn, valueFn, op, TreeMap::new),
                                  new ToMapAssertion<>(keyFn, valueFn, op, TreeMap.class));
        }

        // For concurrent maps, only use commutative merge functions
        try {
            exerciseMapTabulation(data, toConcurrentMap(keyFn, valueFn),
                                  new ToMapAssertion<>(keyFn, valueFn, sum, ConcurrentHashMap.class));
            if (dataAsList.size() != dataAsSet.size())
                fail("Expected ISE on input with duplicates");
        }
        catch (IllegalStateException e) {
            if (dataAsList.size() == dataAsSet.size())
                fail("Expected no ISE on input without duplicates");
        }

        exerciseMapTabulation(data, toConcurrentMap(keyFn, valueFn, sum),
                              new ToMapAssertion<>(keyFn, valueFn, sum, ConcurrentHashMap.class));

        exerciseMapTabulation(data, toConcurrentMap(keyFn, valueFn, sum, ConcurrentSkipListMap::new),
                              new ToMapAssertion<>(keyFn, valueFn, sum, ConcurrentSkipListMap.class));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testSimpleGroupBy(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Function<Integer, Integer> classifier = i -> i % 3;

        // Single-level groupBy
        exerciseMapTabulation(data, groupingBy(classifier),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new ListAssertion<>()));
        exerciseMapTabulation(data, groupingByConcurrent(classifier),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new ListAssertion<>()));

        // With explicit constructors
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, toCollection(HashSet::new)),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new CollectionAssertion<Integer>(HashSet.class, false)));
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new,
                                                   toCollection(HashSet::new)),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new CollectionAssertion<Integer>(HashSet.class, false)));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testTwoLevelGroupBy(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Function<Integer, Integer> classifier = i -> i % 6;
        Function<Integer, Integer> classifier2 = i -> i % 23;

        // Two-level groupBy
        exerciseMapTabulation(data,
                              groupingBy(classifier, groupingBy(classifier2)),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new GroupedMapAssertion<>(classifier2, HashMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as upstream
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, groupingBy(classifier2)),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new GroupedMapAssertion<>(classifier2, HashMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as downstream
        exerciseMapTabulation(data,
                              groupingBy(classifier, groupingByConcurrent(classifier2)),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new GroupedMapAssertion<>(classifier2, ConcurrentHashMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as upstream and downstream
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, groupingByConcurrent(classifier2)),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new GroupedMapAssertion<>(classifier2, ConcurrentHashMap.class,
                                                                                  new ListAssertion<>())));

        // With explicit constructors
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, groupingBy(classifier2, TreeMap::new, toCollection(HashSet::new))),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new GroupedMapAssertion<>(classifier2, TreeMap.class,
                                                                                  new CollectionAssertion<Integer>(HashSet.class, false))));
        // with concurrent as upstream
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new, groupingBy(classifier2, TreeMap::new, toList())),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new GroupedMapAssertion<>(classifier2, TreeMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as downstream
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, groupingByConcurrent(classifier2, ConcurrentSkipListMap::new, toList())),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new GroupedMapAssertion<>(classifier2, ConcurrentSkipListMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as upstream and downstream
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new, groupingByConcurrent(classifier2, ConcurrentSkipListMap::new, toList())),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new GroupedMapAssertion<>(classifier2, ConcurrentSkipListMap.class,
                                                                                  new ListAssertion<>())));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testGroupedReduce(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Function<Integer, Integer> classifier = i -> i % 3;

        // Single-level simple reduce
        exerciseMapTabulation(data,
                              groupingBy(classifier, reducing(0, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));
        // with concurrent
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, reducing(0, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));

        // With explicit constructors
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, reducing(0, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));
        // with concurrent
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new, reducing(0, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));

        // Single-level map-reduce
        exerciseMapTabulation(data,
                              groupingBy(classifier, reducing(0, mDoubler, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new ReduceAssertion<>(0, mDoubler, Integer::sum)));
        // with concurrent
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, reducing(0, mDoubler, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new ReduceAssertion<>(0, mDoubler, Integer::sum)));

        // With explicit constructors
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, reducing(0, mDoubler, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new ReduceAssertion<>(0, mDoubler, Integer::sum)));
        // with concurrent
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new, reducing(0, mDoubler, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new ReduceAssertion<>(0, mDoubler, Integer::sum)));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testSimplePartition(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Predicate<Integer> classifier = i -> i % 3 == 0;

        // Single-level partition to downstream List
        exerciseMapTabulation(data,
                              partitioningBy(classifier),
                              new PartitionAssertion<>(classifier, new ListAssertion<>()));
        exerciseMapTabulation(data,
                              partitioningBy(classifier, toList()),
                              new PartitionAssertion<>(classifier, new ListAssertion<>()));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testTwoLevelPartition(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Predicate<Integer> classifier = i -> i % 3 == 0;
        Predicate<Integer> classifier2 = i -> i % 7 == 0;

        // Two level partition
        exerciseMapTabulation(data,
                              partitioningBy(classifier, partitioningBy(classifier2)),
                              new PartitionAssertion<>(classifier,
                                                       new PartitionAssertion(classifier2, new ListAssertion<>())));

        // Two level partition with reduce
        exerciseMapTabulation(data,
                              partitioningBy(classifier, reducing(0, Integer::sum)),
                              new PartitionAssertion<>(classifier,
                                                       new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testComposeFinisher(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        List<Integer> asList = exerciseTerminalOps(data, s -> s.collect(toList()));
        List<Integer> asImmutableList = exerciseTerminalOps(data, s -> s.collect(collectingAndThen(toList(), Collections::unmodifiableList)));
        assertEquals(asList, asImmutableList);
        try {
            asImmutableList.add(0);
            fail("Expecting immutable result");
        }
        catch (UnsupportedOperationException ignored) { }
    }

}
