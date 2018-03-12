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

/*
 * @test Test8195115.java
 * @bug 8195115
 * @requires vm.gc=="G1" | vm.gc=="null"
 * @summary G1 Old Gen's CollectionUsage.used is zero after mixed GC which is incorrect
 * @run main/othervm -Xmx64m -Xms64m -verbose:gc -XX:+UseG1GC Test8195115
 */
import java.util.*;
import java.lang.management.*;

// 8195115 says that for the "G1 Old Gen" MemoryPool, CollectionUsage.used
// is zero for G1 after a mixed collection.

public class Test8195115 {

    private String poolName = "G1 Old Gen";
    private String collectorName = "G1 Young Generation";

    public static void main(String [] args) {
        Test8195115 t = new Test8195115();
        t.run();
    }

    public Test8195115() {
        System.out.println("Monitor G1 Old Gen pool with G1 Young Generation collector.");
    }

    public void run() {
	// Find memory pool and collector
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        MemoryPoolMXBean pool = null;
        boolean foundPool = false;
        for (int i = 0; i < pools.size(); i++) {
            pool = pools.get(i);
            String name = pool.getName();
            if (name.contains(poolName)) {
                System.out.println("Found pool: " + name);
                foundPool = true;
                break;
            }
        }
        if (!foundPool) {
            throw new RuntimeException(poolName + " not found");
        }

        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        GarbageCollectorMXBean collector = null;
        boolean foundCollector = false;
        for (int i = 0; i < collectors.size(); i++) {
            collector = collectors.get(i);
            String name = collector.getName();
            if (name.contains(collectorName)) {
                System.out.println("Found collector: " + name);
                foundCollector = true;
                break;
            }
        }
        if (!foundCollector) {
            throw new RuntimeException(collectorName + " not found, test with -XX:+UseG1GC");
        }

        // Use some memory, enough that young, but not mixed, collections
        // have happened.
        allocationWork(20*1024*1024, 0);
        System.out.println("Done allocationWork for pure young collections");

        // Verify no non-zero result was stored
        long usage = pool.getCollectionUsage().getUsed();
        System.out.println(poolName + ": usage after GC = " + usage);
        if (usage > 0) {
            throw new RuntimeException("Premature mixed collections(s)");
        }

        // Verify that collections were done
        long collectionCount = collector.getCollectionCount();
        System.out.println(collectorName + ": collection count = "
                           + collectionCount);
        long collectionTime = collector.getCollectionTime();
        System.out.println(collectorName + ": collection time  = "
                           + collectionTime);
        if (collectionCount <= 0) {
            throw new RuntimeException("Collection count <= 0");
        }
        if (collectionTime <= 0) {
            throw new RuntimeException("Collector has not run");
        }

        // Must run with options to ensure no stop the world full GC,
        // but at least one GC cycle that includes a mixed collection.
        allocationWork(20*1024*1024, 10*1024*1024);
        System.out.println("Done allocationWork for mixed collections");

        usage = pool.getCollectionUsage().getUsed();
        System.out.println(poolName + ": usage after GC = " + usage);
        if (usage <= 0) {
            throw new RuntimeException(poolName + " found with zero usage");
        }

        long newCollectionCount = collector.getCollectionCount();
        System.out.println(collectorName + ": collection count = "
                           + newCollectionCount);
        long newCollectionTime = collector.getCollectionTime();
        System.out.println(collectorName + ": collection time  = "
                           + newCollectionTime);
        if (newCollectionCount <= collectionCount) {
            throw new RuntimeException("No new collection");
        }
        if (newCollectionTime <= collectionTime) {
            throw new RuntimeException("Collector has not run some more");
        }

        System.out.println("Test passed.");
    }

    public void allocationWork(long persistentTarget, long target) {
        long persistentSizeAllocated = 0;
        long sizeAllocated = 0;
        char[] template = new char[10240];
        Random rand = new Random();
        PrimitiveIterator.OfInt indexiter = rand.ints().iterator();
        PrimitiveIterator.OfInt valueiter = rand.ints().iterator();

        Set<String> persistentStrings = new HashSet<>(1000);
	while (persistentSizeAllocated < persistentTarget) {
            for (int i = 0; i < 100; i++) {
                template[Math.abs(indexiter.next() % 10240)] = (char)valueiter.nextInt();
                persistentStrings.add(new String(template));
            }
	    persistentSizeAllocated += 100*10240*2;
        }
        while (sizeAllocated < target) {
            Set<String> strings = new HashSet<>(1000);
            for (int i = 0; i < 1000; i++) {
                template[Math.abs(indexiter.next() % 10240)] = (char)valueiter.nextInt();
                strings.add(new String(template));
            }
            sizeAllocated += 1000*10240*2;
        }
    }
}
