/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

/* @test TestMemoryMXBeansAndPoolsPresence
 * @key gc
 * @bug 8191564
 * @summary Tests that GarbageCollectorMXBeans and GC MemoryPools are created.
 * @library /testlibrary
 * @requires vm.gc == null
 * @run main/othervm -XX:+UseParallelGC TestMemoryMXBeansAndPoolsPresence Parallel
 * @run main/othervm -XX:+UseSerialGC TestMemoryMXBeansAndPoolsPresence Serial
 * @run main/othervm -XX:+UseConcMarkSweepGC TestMemoryMXBeansAndPoolsPresence CMS
 * @run main/othervm -XX:+UseG1GC TestMemoryMXBeansAndPoolsPresence G1
 */

import java.util.List;
import java.util.ArrayList;
import java.lang.management.*;
import java.util.stream.*;

import com.oracle.java.testlibrary.Asserts;

class GCBeanDescription {
    public String name;
    public String[] poolNames;

    public GCBeanDescription(String name, String[] poolNames) {
        this.name = name;
        this.poolNames = poolNames;
    }
}

public class TestMemoryMXBeansAndPoolsPresence {
    public static void test(GCBeanDescription... expectedBeans) {
        List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        Asserts.assertEQ(expectedBeans.length, gcBeans.size());

        for (GCBeanDescription desc : expectedBeans) {
            List<GarbageCollectorMXBean> beans = gcBeans.stream()
                                                        .filter(b -> b.getName().equals(desc.name))
                                                        .collect(Collectors.toList());
            Asserts.assertEQ(beans.size(), 1);

            GarbageCollectorMXBean bean = beans.get(0);
            Asserts.assertEQ(desc.name, bean.getName());

            String[] pools = bean.getMemoryPoolNames();
            Asserts.assertEQ(desc.poolNames.length, pools.length);
            for (int i = 0; i < desc.poolNames.length; i++) {
                Asserts.assertEQ(desc.poolNames[i], pools[i]);
            }
        }
    }

    public static void main(String[] args) {
        switch (args[0]) {
            case "G1":
                test(new GCBeanDescription("G1 Young Generation", new String[] {"G1 Eden Space", "G1 Survivor Space", "G1 Old Gen"}),
                     new GCBeanDescription("G1 Old Generation",   new String[] {"G1 Eden Space", "G1 Survivor Space", "G1 Old Gen"}));
                break;
            case "CMS":
                test(new GCBeanDescription("ParNew",              new String[] {"Par Eden Space", "Par Survivor Space"}),
                     new GCBeanDescription("ConcurrentMarkSweep", new String[] {"Par Eden Space", "Par Survivor Space", "CMS Old Gen"}));
                break;
            case "Parallel":
                test(new GCBeanDescription("PS Scavenge",         new String[] {"PS Eden Space", "PS Survivor Space"}),
                     new GCBeanDescription("PS MarkSweep",        new String[] {"PS Eden Space", "PS Survivor Space", "PS Old Gen"}));
                break;
            case "Serial":
                test(new GCBeanDescription("Copy",              new String[] {"Eden Space", "Survivor Space"}),
                     new GCBeanDescription("MarkSweepCompact",  new String[] {"Eden Space", "Survivor Space", "Tenured Gen"}));
                break;
            default:
                Asserts.assertTrue(false);
                break;

        }
    }
}
