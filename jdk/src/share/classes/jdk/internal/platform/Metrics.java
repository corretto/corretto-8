/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.platform;

import java.lang.reflect.Method;

/**
 * Operating System Metrics class
 *
 * @implNote Some of the APIs within this class return metrics for an
 * "Isolation Group" or "Container".  When the term "Isolation Group"
 * is used in the API description, this refers to either:
 *
 *<ol>
 *<li> All processes, including the current process within a container.
 *
 *<li> All processes, including the current process running together
 *    isolated from other non-isolated processes.
 *
 *<li> All processes running on a host when that there is no isolation
 *     in effect.
 *</ol>
 *
 * @author bobv
 */

public interface Metrics {

    /**
     * Returns an instance of the Metrics class.
     *
     * @return Metrics object or null if not supported on this platform.
     */
    public static Metrics systemMetrics() {
        try {
            // We currently only support cgroupv1
            Class<?> c = Class.forName("jdk.internal.platform.cgroupv1.Metrics");
            @SuppressWarnings("unchecked")
            Method m = c.getMethod("getInstance");
            return (Metrics) m.invoke(null);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Returns the interface responsible for providing the
     * platform metrics.
     *
     * @implNote
     * Metrics are currently only supported Linux.
     * The provider for Linux is cgroupsv1.
     *
     * @return The name of the provider.
     *
     */
    public String getProvider();


    /*****************************************************************
     * CPU Accounting Subsystem
     ****************************************************************/

    /**
     * Returns the aggregate time, in nanoseconds, consumed by all
     * tasks in the Isolation Group.
     *
     * @return Time in nanoseconds or 0L if metric is not available.
     *
     */
    public long getCpuUsage();

    /**
     * Returns the aggregate time, in nanoseconds, consumed by all tasks in
     * the Isolation Group, separated by CPU. If the current process
     * is running within a container, the reported time will only be
     * valid for processes running within the same container.  The values
     * are returned in an array, one entry for each physical processor
     * on the system.  Time values for processors unavailable to this
     * Group are undefined.
     *
     * @return long array of time values.  The size of the array is equal
     *         to the total number of physical processors in the system. If
     *         this metric is not available, a zero length array will be
     *         returned.
     *
     */
    public long[] getPerCpuUsage();

    /**
     * Returns the aggregate user time, in nanoseconds, consumed by all
     * tasks in the Isolation Group.
     *
     * @return User time in nanoseconds or 0L if metric is not available.
     *
     */
    public long getCpuUserUsage();

    /**
     * Returns the aggregate system time, in nanoseconds, consumed by
     * all tasks in the Isolation Group.
     *
     * @return System time in nanoseconds or 0L if metric is not available.
     *
     */
    public long getCpuSystemUsage();

    /*****************************************************************
     * CPU Scheduling Metrics
     ****************************************************************/

    /**
     * Returns the length of the scheduling period, in
     * microseconds, for processes within the Isolation Group.
     *
     * @return time in microseconds or 0L if metric is not available.
     *
     */
    public long getCpuPeriod();

    /**
     * Returns the total available run-time allowed, in microseconds,
     * during each scheduling period for all tasks in the Isolation
     * Group.
     *
     * @return time in microseconds or -1 if the quota is unlimited.
     *
     */
    public long getCpuQuota();


    /**
     * Returns the relative weighting of processes with the Isolation
     * Group used for prioritizing the scheduling of processes across
     * all Isolation Groups running on a host.
     *
     * @implNote
     * Popular container orchestration systems have standardized shares
     * to be multiples of 1024, where 1024 is interpreted as 1 CPU share
     * of execution.  Users can distribute CPU resources to multiple
     * Isolation Groups by specifying the CPU share weighting needed by
     * each process.  To request 2 CPUS worth of execution time, CPU shares
     * would be set to 2048.
     *
     * @return shares value or -1 if no share set.
     *
     */
    public long getCpuShares();

    /**
     * Returns the number of time-slice periods that have elapsed if
     * a CPU quota has been setup for the Isolation Group; otherwise
     * returns 0.
     *
     * @return count of elapsed periods or 0 if the quota is unlimited.
     *
     */
    public long getCpuNumPeriods();

    /**
     * Returns the number of time-slice periods that the group has
     * been throttled or limited due to the group exceeding its quota
     * if a CPU quota has been setup for the Isolation Group.
     *
     * @return count of throttled periods or 0 if the quota is unlimited.
     *
     */
    public long getCpuNumThrottled();

    /**
     * Returns the total time duration, in nanoseconds, that the
     * group has been throttled or limited due to the group exceeding
     * its quota if a CPU quota has been setup for the Isolation Group.
     *
     * @return Throttled time in nanoseconds or 0 if the quota is unlimited.
     *
     */
    public long getCpuThrottledTime();


    /**
     * Returns the number of effective processors that this Isolation
     * group has available to it.  This effective processor count is
     * computed based on the number of dedicated CPUs, CPU shares and
     * CPU quotas in effect for this isolation group.
     *
     * This method returns the same value as
     * {@link java.lang.Runtime#availableProcessors()}.
     *
     * @return The number of effective CPUs.
     *
     */
    public long getEffectiveCpuCount();

    /*****************************************************************
     * CPU Sets
     ****************************************************************/

    /**
     * Returns the CPUS that are available for execution of processes
     * in the current Isolation Group. The size of the array is equal
     * to the total number of CPUs and the elements in the array are the
     * physical CPU numbers that are available.  Some of the CPUs returned
     * may be offline.  To get the current online CPUs, use
     * {@link getEffectiveCpuSetCpus()}.
     *
     * @return An array of available CPUs or a zero length array
     *         if the metric is not available.
     *
     */
    public int[] getCpuSetCpus();

    /**
     * Returns the CPUS that are available and online for execution of
     * processes within the current Isolation Group. The size of the
     * array is equal to the total number of CPUs and the elements in
     * the array are the physical CPU numbers.
     *
     * @return An array of available and online CPUs or a zero length
     *         array if the metric is not available.
     *
     */
    public int[] getEffectiveCpuSetCpus();

    /**
     * Returns the memory nodes that are available for use by processes
     * in the current Isolation Group. The size of the array is equal
     * to the total number of nodes and the elements in the array are the
     * physical node numbers that are available.  Some of the nodes returned
     * may be offline.  To get the current online memory nodes, use
     * {@link getEffectiveCpuSetMems()}.
     *
     * @return An array of available memory nodes or a zero length array
     *         if the metric is not available.
     *
     */
    public int[] getCpuSetMems();

    /**
     * Returns the memory nodes that are available and online for use by
     * processes within the current Isolation Group. The size of the
     * array is equal to the total number of nodes and the elements in
     * the array are the physical node numbers.
     *
     * @return An array of available and online nodes or a zero length
     *         array if the metric is not available.
     *
     */
    public int[] getEffectiveCpuSetMems();

    /**
     * Returns the (attempts per second * 1000), if enabled, that the
     * operating system tries to satisfy a memory request for any
     * process in the current Isolation Group when no free memory is
     * readily available.  Use {@link #isCpuSetMemoryPressureEnabled()} to
     * to determine if this support is enabled.
     *
     * @return Memory pressure or 0 if not enabled or metric is not
     *         available.
     *
     */
    public double getCpuSetMemoryPressure();

    /**
     * Returns the state of the memory pressure detection support.
     *
     * @return true if the support is available and enabled, otherwise false.
     *
     */
    public boolean isCpuSetMemoryPressureEnabled();

    /*****************************************************************
     * Memory Subsystem
     ****************************************************************/

    /**
     * Returns the number of times that user memory requests in the
     * Isolation Group have exceeded the memory limit.
     *
     * @return The number of exceeded requests or 0 if none or metric
     *         is not available.
     *
     */
    public long getMemoryFailCount();

    /**
     * Returns the maximum amount of physical memory, in bytes, that
     * can be allocated in the Isolation Group.
     *
     * @return The maximum amount of memory in bytes or -1 if either
     *         there is no limit set or this metric is not available.
     *
     */
    public long getMemoryLimit();

    /**
     * Returns the largest amount of physical memory, in bytes, that
     * have been allocated in the Isolation Group.
     *
     * @return The largest amount of memory in bytes or or 0 if this
     *         metric is not available.
     *
     */
    public long getMemoryMaxUsage();

    /**
     * Returns the amount of physical memory, in bytes, that is currently
     * allocated in the current Isolation Group.
     *
     * @return The amount of memory in bytes allocated or 0 if this
     *         metric is not available.
     *
     */
    public long getMemoryUsage();

    /**
     * Returns the number of times that kernel memory requests in the
     * Isolation Group have exceeded the kernel memory limit.
     *
     * @return The number of exceeded requests or 0 if none or metric
     *         is not available.
     *
     */
    public long getKernelMemoryFailCount();

    /**
     * Returns the maximum amount of kernel physical memory, in bytes, that
     * can be allocated in the Isolation Group.
     *
     * @return The maximum amount of memory in bytes or -1 if either
     *         there is no limit set or this metric is not available.
     *
     */
    public long getKernelMemoryLimit();

    /**
     * Returns the largest amount of kernel physical memory, in bytes, that
     * have been allocated in the Isolation Group.
     *
     * @return The largest amount of memory in bytes or or 0 if this
     *         metric is not available.
     *
     */
    public long getKernelMemoryMaxUsage();

    /**
     * Returns the amount of kernel physical memory, in bytes, that
     * is currently allocated in the current Isolation Group.
     *
     * @return The amount of memory in bytes allocated or 0 if this
     *         metric is not available.
     *
     */
    public long getKernelMemoryUsage();

    /**
     * Returns the number of times that networking memory requests in the
     * Isolation Group have exceeded the kernel memory limit.
     *
     * @return The number of exceeded requests or 0 if none or metric
     *         is not available.
     *
     */
    public long getTcpMemoryFailCount();

    /**
     * Returns the maximum amount of networking physical memory, in bytes,
     * that can be allocated in the Isolation Group.
     *
     * @return The maximum amount of memory in bytes or -1 if either
     *         there is no limit set or this metric is not available.
     *
     */
    public long getTcpMemoryLimit();

    /**
     * Returns the largest amount of networking physical memory, in bytes,
     * that have been allocated in the Isolation Group.
     *
     * @return The largest amount of memory in bytes or or 0 if this
     *         metric is not available.
     *
     */
    public long getTcpMemoryMaxUsage();

    /**
     * Returns the amount of networking physical memory, in bytes, that
     * is currently allocated in the current Isolation Group.
     *
     * @return The amount of memory in bytes allocated or 0 if this
     *         metric is not available.
     *
     */
    public long getTcpMemoryUsage();

    /**
     * Returns the number of times that user memory requests in the
     * Isolation Group have exceeded the memory + swap limit.
     *
     * @return The number of exceeded requests or 0 if none or metric
     *         is not available.
     *
     */
    public long getMemoryAndSwapFailCount();

    /**
     * Returns the maximum amount of physical memory and swap space,
     * in bytes, that can be allocated in the Isolation Group.
     *
     * @return The maximum amount of memory in bytes or -1 if either
     *         there is no limit set or this metric is not available.
     *
     */
    public long getMemoryAndSwapLimit();

    /**
     * Returns the largest amount of physical memory and swap space,
     * in bytes, that have been allocated in the Isolation Group.
     *
     * @return The largest amount of memory in bytes or or 0 if this
     *         metric is not available.
     *
     */
    public long getMemoryAndSwapMaxUsage();

    /**
     * Returns the amount of physical memory and swap space, in bytes,
     * that is currently allocated in the current Isolation Group.
     *
     * @return The amount of memory in bytes allocated or 0 if this
     *         metric is not available.
     *
     */
    public long getMemoryAndSwapUsage();

    /**
     * Returns the state of the Operating System Out of Memory termination
     * policy.
     *
     * @return Returns true if operating system will terminate processes
     *         in the Isolation Group that exceed the amount of available
     *         memory, otherwise false.  Flase will be returned if this
     *         capability is not available on the current operating system.
     *
     */
    public boolean isMemoryOOMKillEnabled();

    /**
     * Returns the hint to the operating system that allows groups
     * to specify the minimum amount of physical memory that they need to
     * achieve reasonable performance in low memory systems.  This allows
     * host systems to provide greater sharing of memory.
     *
     * @return The minimum amount of physical memory, in bytes, that the
     *         operating system will try to maintain under low memory
     *         conditions.  If this metric is not available, 0 will be
     *         returned.
     *
     */
    public long getMemorySoftLimit();

    /*****************************************************************
     * BlKIO Subsystem
     ****************************************************************/

    /**
     * Returns the number of block I/O requests to the disk that have been
     * issued by the Isolation Group.
     *
     * @return The count of requests or 0 if this metric is not available.
     *
     */
    public long getBlkIOServiceCount();

    /**
     * Returns the number of block I/O bytes that have been transferred
     * to/from the disk by the Isolation Group.
     *
     * @return The number of bytes transferred or 0 if this metric is not available.
     *
     */
    public long getBlkIOServiced();
}
