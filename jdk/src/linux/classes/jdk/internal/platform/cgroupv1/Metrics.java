/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
 *
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.platform.cgroupv1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.stream.Stream;

import jdk.internal.platform.cgroupv1.SubSystem.MemorySubSystem;

public class Metrics implements jdk.internal.platform.Metrics {
    private MemorySubSystem memory;
    private SubSystem cpu;
    private SubSystem cpuacct;
    private SubSystem cpuset;
    private SubSystem blkio;
    private boolean activeSubSystems;

    // Values returned larger than this number are unlimited.
    static long unlimited_minimum = 0x7FFFFFFFFF000000L;

    private static final Metrics INSTANCE = initContainerSubSystems();

    private static final String PROVIDER_NAME = "cgroupv1";

    private Metrics() {
        activeSubSystems = false;
    }

    public static Metrics getInstance() {
        return INSTANCE;
    }

    private static Metrics initContainerSubSystems() {
        if (!isUseContainerSupport()) {
            return null;
        }
        Metrics metrics = new Metrics();

        /**
         * Find the cgroup mount points for subsystems
         * by reading /proc/self/mountinfo
         *
         * Example for docker MemorySubSystem subsystem:
         * 219 214 0:29 /docker/7208cebd00fa5f2e342b1094f7bed87fa25661471a4637118e65f1c995be8a34 /sys/fs/cgroup/MemorySubSystem ro,nosuid,nodev,noexec,relatime - cgroup cgroup rw,MemorySubSystem
         *
         * Example for host:
         * 34 28 0:29 / /sys/fs/cgroup/MemorySubSystem rw,nosuid,nodev,noexec,relatime shared:16 - cgroup cgroup rw,MemorySubSystem
         */
        try (Stream<String> lines =
             readFilePrivileged(Paths.get("/proc/self/mountinfo"))) {

            lines.filter(line -> line.contains(" - cgroup "))
                 .map(line -> line.split(" "))
                 .forEach(entry -> createSubSystem(metrics, entry));

        } catch (IOException e) {
            return null;
        } catch (UncheckedIOException e) {
            return null;
        }

        /**
         * Read /proc/self/cgroup and map host mount point to
         * local one via /proc/self/mountinfo content above
         *
         * Docker example:
         * 5:memory:/docker/6558aed8fc662b194323ceab5b964f69cf36b3e8af877a14b80256e93aecb044
         *
         * Host example:
         * 5:memory:/user.slice
         *
         * Construct a path to the process specific memory and cpuset
         * cgroup directory.
         *
         * For a container running under Docker from memory example above
         * the paths would be:
         *
         * /sys/fs/cgroup/memory
         *
         * For a Host from memory example above the path would be:
         *
         * /sys/fs/cgroup/memory/user.slice
         *
         */
        try (Stream<String> lines =
             readFilePrivileged(Paths.get("/proc/self/cgroup"))) {

            // The limit value of 3 is because /proc/self/cgroup contains three
            // colon-separated tokens per line. The last token, cgroup path, might
            // contain a ':'.
            lines.map(line -> line.split(":", 3))
                 .filter(line -> (line.length >= 3))
                 .forEach(line -> setSubSystemPath(metrics, line));

        } catch (IOException e) {
            return null;
        } catch (UncheckedIOException e) {
            return null;
        }

        // Return Metrics object if we found any subsystems.
        if (metrics.activeSubSystems()) {
            return metrics;
        }

        return null;
    }

    static Stream<String> readFilePrivileged(Path path) throws IOException {
        try {
            PrivilegedExceptionAction<Stream<String>> pea = () -> Files.lines(path);
            return AccessController.doPrivileged(pea);
        } catch (PrivilegedActionException e) {
            unwrapIOExceptionAndRethrow(e);
            throw new InternalError(e.getCause());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    static void unwrapIOExceptionAndRethrow(PrivilegedActionException pae) throws IOException {
        Throwable x = pae.getCause();
        if (x instanceof IOException)
            throw (IOException) x;
        if (x instanceof RuntimeException)
            throw (RuntimeException) x;
        if (x instanceof Error)
            throw (Error) x;
    }
    /**
     * createSubSystem objects and initialize mount points
     */
    private static void createSubSystem(Metrics metric, String[] mountentry) {
        if (mountentry.length < 5) return;

        Path p = Paths.get(mountentry[4]);
        String[] subsystemNames = p.getFileName().toString().split(",");

        for (String subsystemName: subsystemNames) {
            switch (subsystemName) {
                case "memory":
                    metric.setMemorySubSystem(new MemorySubSystem(mountentry[3], mountentry[4]));
                    break;
                case "cpuset":
                    metric.setCpuSetSubSystem(new SubSystem(mountentry[3], mountentry[4]));
                    break;
                case "cpuacct":
                    metric.setCpuAcctSubSystem(new SubSystem(mountentry[3], mountentry[4]));
                    break;
                case "cpu":
                    metric.setCpuSubSystem(new SubSystem(mountentry[3], mountentry[4]));
                    break;
                case "blkio":
                    metric.setBlkIOSubSystem(new SubSystem(mountentry[3], mountentry[4]));
                    break;
                default:
                    // Ignore subsystems that we don't support
                    break;
            }
        }
    }

    /**
     * setSubSystemPath based on the contents of /proc/self/cgroup
     */
    private static void setSubSystemPath(Metrics metric, String[] entry) {
        String controller = entry[1];
        String base = entry[2];
        if (controller != null && base != null) {
            for (String cName: controller.split(",")) {
                switch (cName) {
                    case "memory":
                        setPath(metric, metric.MemorySubSystem(), base);
                        break;
                    case "cpuset":
                        setPath(metric, metric.CpuSetSubSystem(), base);
                        break;
                    case "cpuacct":
                        setPath(metric, metric.CpuAcctSubSystem(), base);
                        break;
                    case "cpu":
                        setPath(metric, metric.CpuSubSystem(), base);
                        break;
                    case "blkio":
                        setPath(metric, metric.BlkIOSubSystem(), base);
                        break;
                    // Ignore subsystems that we don't support
                    default:
                        break;
                }
            }
        }
    }

    private static void setPath(Metrics metric, SubSystem subsystem, String base) {
        if (subsystem != null) {
            subsystem.setPath(base);
            if (subsystem instanceof MemorySubSystem) {
                MemorySubSystem memorySubSystem = (MemorySubSystem)subsystem;
                boolean isHierarchial = getHierarchical(memorySubSystem);
                memorySubSystem.setHierarchical(isHierarchial);
                boolean isSwapEnabled = getSwapEnabled(memorySubSystem);
                memorySubSystem.setSwapEnabled(isSwapEnabled);
            }
            metric.setActiveSubSystems();
        }
    }


    private static boolean getHierarchical(MemorySubSystem subsystem) {
        long hierarchical = SubSystem.getLongValue(subsystem, "memory.use_hierarchy");
        return hierarchical > 0;
    }

    private static boolean getSwapEnabled(MemorySubSystem subsystem) {
        long retval = SubSystem.getLongValue(subsystem, "memory.memsw.limit_in_bytes");
        return retval > 0;
    }

    private void setActiveSubSystems() {
        activeSubSystems = true;
    }

    private boolean activeSubSystems() {
        return activeSubSystems;
    }

    private void setMemorySubSystem(MemorySubSystem memory) {
        this.memory = memory;
    }

    private void setCpuSubSystem(SubSystem cpu) {
        this.cpu = cpu;
    }

    private void setCpuAcctSubSystem(SubSystem cpuacct) {
        this.cpuacct = cpuacct;
    }

    private void setCpuSetSubSystem(SubSystem cpuset) {
        this.cpuset = cpuset;
    }

    private void setBlkIOSubSystem(SubSystem blkio) {
        this.blkio = blkio;
    }

    private SubSystem MemorySubSystem() {
        return memory;
    }

    private SubSystem CpuSubSystem() {
        return cpu;
    }

    private SubSystem CpuAcctSubSystem() {
        return cpuacct;
    }

    private SubSystem CpuSetSubSystem() {
        return cpuset;
    }

    private SubSystem BlkIOSubSystem() {
        return blkio;
    }

    public String getProvider() {
        return PROVIDER_NAME;
    }

    /*****************************************************************
     * CPU Accounting Subsystem
     ****************************************************************/


    public long getCpuUsage() {
        return SubSystem.getLongValue(cpuacct, "cpuacct.usage");
    }

    public long[] getPerCpuUsage() {
        String usagelist = SubSystem.getStringValue(cpuacct, "cpuacct.usage_percpu");
        if (usagelist == null) {
            return new long[0];
        }

        String list[] = usagelist.split(" ");
        long percpu[] = new long[list.length];
        for (int i = 0; i < list.length; i++) {
            percpu[i] = Long.parseLong(list[i]);
        }
        return percpu;
    }

    public long getCpuUserUsage() {
        return SubSystem.getLongEntry(cpuacct, "cpuacct.stat", "user");
    }

    public long getCpuSystemUsage() {
        return SubSystem.getLongEntry(cpuacct, "cpuacct.stat", "system");
    }


    /*****************************************************************
     * CPU Subsystem
     ****************************************************************/


    public long getCpuPeriod() {
        return SubSystem.getLongValue(cpuacct, "cpu.cfs_period_us");
    }

    public long getCpuQuota() {
        return SubSystem.getLongValue(cpuacct, "cpu.cfs_quota_us");
    }

    public long getCpuShares() {
        long retval = SubSystem.getLongValue(cpuacct, "cpu.shares");
        if (retval == 0 || retval == 1024)
            return -1;
        else
            return retval;
    }

    public long getCpuNumPeriods() {
        return SubSystem.getLongEntry(cpuacct, "cpu.stat", "nr_periods");
    }

    public long getCpuNumThrottled() {
        return SubSystem.getLongEntry(cpuacct, "cpu.stat", "nr_throttled");
    }

    public long getCpuThrottledTime() {
        return SubSystem.getLongEntry(cpuacct, "cpu.stat", "throttled_time");
    }

    public long getEffectiveCpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }


    /*****************************************************************
     * CPUSet Subsystem
     ****************************************************************/

    public int[] getCpuSetCpus() {
        return SubSystem.StringRangeToIntArray(SubSystem.getStringValue(cpuset, "cpuset.cpus"));
    }

    public int[] getEffectiveCpuSetCpus() {
        return SubSystem.StringRangeToIntArray(SubSystem.getStringValue(cpuset, "cpuset.effective_cpus"));
    }

    public int[] getCpuSetMems() {
        return SubSystem.StringRangeToIntArray(SubSystem.getStringValue(cpuset, "cpuset.mems"));
    }

    public int[] getEffectiveCpuSetMems() {
        return SubSystem.StringRangeToIntArray(SubSystem.getStringValue(cpuset, "cpuset.effective_mems"));
    }

    public double getCpuSetMemoryPressure() {
        return SubSystem.getDoubleValue(cpuset, "cpuset.memory_pressure");
    }

    public boolean isCpuSetMemoryPressureEnabled() {
        long val = SubSystem.getLongValue(cpuset, "cpuset.memory_pressure_enabled");
        return (val == 1);
    }


    /*****************************************************************
     * Memory Subsystem
     ****************************************************************/


    public long getMemoryFailCount() {
        return SubSystem.getLongValue(memory, "memory.failcnt");
    }

    public long getMemoryLimit() {
        long retval = SubSystem.getLongValue(memory, "memory.limit_in_bytes");
        if (retval > unlimited_minimum) {
            if (memory.isHierarchical()) {
                // memory.limit_in_bytes returned unlimited, attempt
                // hierarchical memory limit
                String match = "hierarchical_memory_limit";
                retval = SubSystem.getLongValueMatchingLine(memory,
                                                            "memory.stat",
                                                            match,
                                                            Metrics::convertHierachicalLimitLine);
            }
        }
        return retval > unlimited_minimum ? -1L : retval;
    }

    public static long convertHierachicalLimitLine(String line) {
        String[] tokens = line.split("\\s");
        if (tokens.length == 2) {
            String strVal = tokens[1];
            return SubSystem.convertStringToLong(strVal);
        }
        return unlimited_minimum + 1; // unlimited
    }

    public long getMemoryMaxUsage() {
        return SubSystem.getLongValue(memory, "memory.max_usage_in_bytes");
    }

    public long getMemoryUsage() {
        return SubSystem.getLongValue(memory, "memory.usage_in_bytes");
    }

    public long getKernelMemoryFailCount() {
        return SubSystem.getLongValue(memory, "memory.kmem.failcnt");
    }

    public long getKernelMemoryLimit() {
        long retval = SubSystem.getLongValue(memory, "memory.kmem.limit_in_bytes");
        return retval > unlimited_minimum ? -1L : retval;
    }

    public long getKernelMemoryMaxUsage() {
        return SubSystem.getLongValue(memory, "memory.kmem.max_usage_in_bytes");
    }

    public long getKernelMemoryUsage() {
        return SubSystem.getLongValue(memory, "memory.kmem.usage_in_bytes");
    }

    public long getTcpMemoryFailCount() {
        return SubSystem.getLongValue(memory, "memory.kmem.tcp.failcnt");
    }

    public long getTcpMemoryLimit() {
        long retval =  SubSystem.getLongValue(memory, "memory.kmem.tcp.limit_in_bytes");
        return retval > unlimited_minimum ? -1L : retval;
    }

    public long getTcpMemoryMaxUsage() {
        return SubSystem.getLongValue(memory, "memory.kmem.tcp.max_usage_in_bytes");
    }

    public long getTcpMemoryUsage() {
        return SubSystem.getLongValue(memory, "memory.kmem.tcp.usage_in_bytes");
    }

    public long getMemoryAndSwapFailCount() {
        if (memory != null && !memory.isSwapEnabled()) {
            return getMemoryFailCount();
        }
        return SubSystem.getLongValue(memory, "memory.memsw.failcnt");
    }

    public long getMemoryAndSwapLimit() {
        if (memory != null && !memory.isSwapEnabled()) {
            return getMemoryLimit();
        }
        long retval = SubSystem.getLongValue(memory, "memory.memsw.limit_in_bytes");
        if (retval > unlimited_minimum) {
            if (memory.isHierarchical()) {
                // memory.memsw.limit_in_bytes returned unlimited, attempt
                // hierarchical memory limit
                String match = "hierarchical_memsw_limit";
                retval = SubSystem.getLongValueMatchingLine(memory,
                                                            "memory.stat",
                                                            match,
                                                            Metrics::convertHierachicalLimitLine);
            }
        }
        return retval > unlimited_minimum ? -1L : retval;
    }

    public long getMemoryAndSwapMaxUsage() {
        if (memory != null && !memory.isSwapEnabled()) {
            return getMemoryMaxUsage();
        }
        return SubSystem.getLongValue(memory, "memory.memsw.max_usage_in_bytes");
    }

    public long getMemoryAndSwapUsage() {
        if (memory != null && !memory.isSwapEnabled()) {
            return getMemoryUsage();
        }
        return SubSystem.getLongValue(memory, "memory.memsw.usage_in_bytes");
    }

    public boolean isMemoryOOMKillEnabled() {
        long val = SubSystem.getLongEntry(memory, "memory.oom_control", "oom_kill_disable");
        return (val == 0);
    }

    public long getMemorySoftLimit() {
        long retval = SubSystem.getLongValue(memory, "memory.soft_limit_in_bytes");
        return retval > unlimited_minimum ? -1L : retval;
    }


    /*****************************************************************
     * BlKIO Subsystem
     ****************************************************************/


    public long getBlkIOServiceCount() {
        return SubSystem.getLongEntry(blkio, "blkio.throttle.io_service_bytes", "Total");
    }

    public long getBlkIOServiced() {
        return SubSystem.getLongEntry(blkio, "blkio.throttle.io_serviced", "Total");
    }

    private static native boolean isUseContainerSupport();

}
