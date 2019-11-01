/*
 * Copyright (c) 2019, Amazon.com and/or its affiliates. All rights reserved.
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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MemoryType;
import java.lang.String;
import java.lang.System;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

// Run with a heap size tailored to HeapMemoryMonitor. allocateObjects().
// -Xmx50m works fine with the defaults.

class HeapMemoryMonitor {

    private long usedKb = 0;
    private long totalKb = 0;

    private long afterGCUsedKb = 0;
    private long afterGCTotalKb = 0;

    private final List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
    private final List<MemoryPoolMXBean> heapMemoryPoolMXBeans = new ArrayList<MemoryPoolMXBean>();
    private final List<MemoryPoolMXBean> afterGCHeapMemoryPoolMXBeans = new ArrayList<MemoryPoolMXBean>();

    private final DecimalFormat df = new DecimalFormat("###");

    public static void main(String[] args) throws Exception {
        HeapMemoryMonitor h = new HeapMemoryMonitor();
        h.run();
    }

    public HeapMemoryMonitor() {
        System.out.println("Monitor heap memory utilization.");

	String memoryPoolName;

        for (MemoryPoolMXBean memoryPool : memoryPoolMXBeans) {
	    //	    System.out.println(memoryPool.getName() + ' ' + memoryPool.getType());
	    if (memoryPool.getType() != MemoryType.HEAP) {
                // Ignore non-HEAP memory pools
                continue;
            }
            heapMemoryPoolMXBeans.add(memoryPool);

            memoryPoolName = memoryPool.getName();
            if (memoryPoolName.contains("Eden")) {
                // Ignore Eden, since it's just an allocation buffer
                continue;
            }
            afterGCHeapMemoryPoolMXBeans.add(memoryPool);
        }

	df.setRoundingMode(RoundingMode.CEILING);
    }

    private void run() {
        dump("                                 Start");
        allocateObjects();
        System.gc(); // Promote everything to old gen
        dump("After allocation and old gen promotion");
        allocateObjects();
        dump("    After more allocation (split gens)");
        System.gc();
        dump("               After old gen promotion");
    }

    private void dump(String header) {
        System.out.println(header + ": HeapMemory        " + getHeapMemory() + " = " +
                           df.format(getHeapMemoryUse()) + '%');
        System.out.println(header + ": HeapMemoryAfterGC " + getHeapMemoryAfterGC() + " = " +
                           df.format(getHeapMemoryAfterGCUse()) + '%');
        System.out.flush();
    }

    public String getHeapMemory() {
        getMemoryUsage();
        return usedKb + "/" + totalKb + " Kb";
    }

    public double getHeapMemoryUse() {
        getMemoryUsage();
        if (totalKb == 0) return 0;
        return ((double)usedKb * 100) / (double)totalKb;
    }

    public String getHeapMemoryAfterGC() {
        getAfterGCMemoryUsage();
        return afterGCUsedKb + "/" + afterGCTotalKb + " Kb";
    }

    public double getHeapMemoryAfterGCUse() {
        getAfterGCMemoryUsage();
        if (afterGCTotalKb == 0) return 0;
        return ((double)afterGCUsedKb * 100) / (double)afterGCTotalKb;
    }

    private void getMemoryUsage() {
        // Report on the sum of HEAP spaces
        long used, max;
	MemoryUsage usage;
        for (MemoryPoolMXBean memoryPool : heapMemoryPoolMXBeans) {
	    //            System.out.println("getMemoryUsage: " + memoryPool.getName());
            usage = memoryPool.getUsage();
            used = usage.getUsed();
            usedKb += used / 1024;
            max = usage.getMax();
            // max can be undefined (-1).
            // See http://docs.oracle.com/javase/7/docs/api/java/lang/management/MemoryUsage.html
            totalKb += max == -1 ? 0 : max / 1024;
	    //	    System.out.println("Used " + usedKb + " Max " + totalKb);
        }
    }

    private void getAfterGCMemoryUsage() {
        // Report on the sum of non-Eden HEAP spaces after the last gc
        long used, max;
	MemoryUsage usage;
        for (MemoryPoolMXBean memoryPool : afterGCHeapMemoryPoolMXBeans) {
	    //            System.out.println("getAfterGCMemoryUsage: " + memoryPool.getName());
            usage = memoryPool.getCollectionUsage();
            used = usage.getUsed();
            afterGCUsedKb += used / 1024;
            max = usage.getMax();
            afterGCTotalKb += max == -1 ? 0 : max / 1024;
	    //	    System.out.println("Used " + afterGCUsedKb + " Max " + afterGCTotalKb);
	}
    }

    private final List<byte[]> liveObjects = new ArrayList<byte[]>();

    private static final int ALLOCATION_SIZE = 20000;
    private static final int ALLOCATION_COUNT = 15;

    private void allocateObjects() {
        // Allocates data and promotes it to the old gen
        for (int i = 0; i < ALLOCATION_COUNT; ++i) {
            liveObjects.add(new byte[ALLOCATION_SIZE * 50]);
        }
    }
}
