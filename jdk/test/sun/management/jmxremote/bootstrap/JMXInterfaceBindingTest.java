/*
 * Copyright (c) 2015, Red Hat Inc
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

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import jdk.testlibrary.ProcessTools;

/**
 * NOTE:
 *    This test requires at least a setup similar to the following in
 *    /etc/hosts file (or the windows equivalent). I.e. it expects it to
 *    be multi-homed and not both being the loop-back interface.
 *    For example:
 *    ----->8-------- /etc/hosts ----------->8---
 *    127.0.0.1   localhost
 *    192.168.0.1 localhost
 *    ----->8-------- /etc/hosts ----------->8---
 *
 * @test
 * @bug     6425769
 * @summary Test JMX agent host address binding. Same ports but different
 *          interfaces to bind to (using plain sockets and SSL sockets).
 *
 * @modules java.management/sun.management
 *          java.management/sun.management.jmxremote
 * @library /lib/testlibrary
 * @build jdk.testlibrary.* JMXAgentInterfaceBinding
 * @run main/timeout=5 JMXInterfaceBindingTest
 */
public class JMXInterfaceBindingTest {

    public static final int COMMUNICATION_ERROR_EXIT_VAL = 1;
    public static final int STOP_PROCESS_EXIT_VAL = 137;
    public static final int JMX_PORT = 9111;
    public static final int RMI_PORT = 9112;
    public static final String READY_MSG = "MainThread: Ready for connections";
    public static final String TEST_CLASS = JMXAgentInterfaceBinding.class.getSimpleName();
    public static final String KEYSTORE_LOC = System.getProperty("test.src", ".") +
                                              File.separator +
                                              "ssl" +
                                              File.separator +
                                              "keystore";
    public static final String TRUSTSTORE_LOC = System.getProperty("test.src", ".") +
                                                File.separator +
                                                "ssl" +
                                                File.separator +
                                                "truststore";
    public static final String TEST_CLASSPATH = System.getProperty("test.classes", ".");

    public void run(List<InetAddress> addrs) {
        System.out.println("DEBUG: Running tests with plain sockets.");
        runTests(addrs, false);
        System.out.println("DEBUG: Running tests with SSL sockets.");
        runTests(addrs, true);
    }

    private void runTests(List<InetAddress> addrs, boolean useSSL) {
        TestProcessThread[] jvms = new TestProcessThread[addrs.size()];
        for (int i = 0; i < addrs.size(); i++) {
            String addr = JMXAgentInterfaceBinding.wrapAddress(addrs.get(i).getHostAddress());
            System.out.println();
            String msg = String.format("DEBUG: Launching java tester for triplet (HOSTNAME,JMX_PORT,RMI_PORT) == (%s,%d,%d)",
                    addr,
                    JMX_PORT,
                    RMI_PORT);
            System.out.println(msg);
            jvms[i] = runJMXBindingTest(addr, useSSL);
            jvms[i].start();
            System.out.println("DEBUG: Started " + (i + 1) + " Process(es).");
        }
        int failedProcesses = 0;
        for (TestProcessThread pt: jvms) {
            try {
                pt.stopProcess();
                pt.join();
            } catch (InterruptedException e) {
                System.err.println("Failed to stop process: " + pt.getName());
                throw new RuntimeException("Test failed", e);
            }
            int exitValue = pt.getExitValue();
            // If there is a communication error (the case we care about)
            // we get a exit code of 1
            if (exitValue == COMMUNICATION_ERROR_EXIT_VAL) {
                // Failure case since the java processes should still be
                // running.
                System.err.println("Test FAILURE on " + pt.getName());
                failedProcesses++;
            } else if (exitValue == STOP_PROCESS_EXIT_VAL) {
                System.out.println("DEBUG: OK. Spawned java process terminated with expected exit code of " + STOP_PROCESS_EXIT_VAL);
            } else {
                System.err.println("Test FAILURE on " + pt.getName() + " reason: Unexpected exit code => " + exitValue);
                failedProcesses++;
            }
        }
        if (failedProcesses > 0) {
            throw new RuntimeException("Test FAILED. " + failedProcesses + " out of " + addrs.size() + " process(es) failed to start the JMX agent.");
        }
    }

    private TestProcessThread runJMXBindingTest(String address, boolean useSSL) {
        List<String> args = new ArrayList<>();
        args.add("-classpath");
        args.add(TEST_CLASSPATH);
        args.add("-Dcom.sun.management.jmxremote.host=" + address);
        args.add("-Dcom.sun.management.jmxremote.port=" + JMX_PORT);
        args.add("-Dcom.sun.management.jmxremote.rmi.port=" + RMI_PORT);
        args.add("-Dcom.sun.management.jmxremote.authenticate=false");
        args.add("-Dcom.sun.management.jmxremote.ssl=" + Boolean.toString(useSSL));
        if (useSSL) {
            args.add("-Dcom.sun.management.jmxremote.registry.ssl=true");
            args.add("-Djavax.net.ssl.keyStore=" + KEYSTORE_LOC);
            args.add("-Djavax.net.ssl.trustStore=" + TRUSTSTORE_LOC);
            args.add("-Djavax.net.ssl.keyStorePassword=password");
            args.add("-Djavax.net.ssl.trustStorePassword=trustword");
        }
        args.add(TEST_CLASS);
        args.add(address);
        args.add(Integer.toString(JMX_PORT));
        args.add(Integer.toString(RMI_PORT));
        args.add(Boolean.toString(useSSL));
        try {
            ProcessBuilder builder = ProcessTools.createJavaProcessBuilder(args.toArray(new String[] {}));
            System.out.println(ProcessTools.getCommandLine(builder));
            TestProcessThread jvm = new TestProcessThread("JMX-Tester-" + address, JMXInterfaceBindingTest::isJMXAgentResponseAvailable, builder);
            return jvm;
        } catch (Exception e) {
            throw new RuntimeException("Test failed", e);
        }

    }

    private static boolean isJMXAgentResponseAvailable(String line) {
        if (line.equals(READY_MSG)) {
            System.out.println("DEBUG: Found expected READY_MSG.");
            return true;
        } else if (line.startsWith("Error:")) {
            // Allow for a JVM process that exits with
            // "Error: JMX connector server communication error: ..."
            // to continue as well since we handle that case elsewhere.
            // This has the effect that the test does not timeout and
            // fails with an exception in the test.
            System.err.println("PROBLEM: JMX agent of target JVM did not start as it should.");
            return true;
        } else {
            return false;
        }
    }

    public static void main(String[] args) {
        List<InetAddress> addrs = getAddressesForLocalHost();
        if (addrs.size() < 2) {
            System.out.println("Ignoring manual test since no more than one IPs are configured for 'localhost'");
            return;
        }
        JMXInterfaceBindingTest test = new JMXInterfaceBindingTest();
        test.run(addrs);
        System.out.println("All tests PASSED.");
    }

    private static List<InetAddress> getAddressesForLocalHost() {
        try {
            List<InetAddress> filtered = new ArrayList<>();
            for (NetworkInterface iface: Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr: Collections.list(iface.getInetAddresses())) {
                    if (isNonloopbackLocalhost(addr)) {
                        filtered.add(addr);
                    }
                }
            }
            return filtered;
        } catch (SocketException e) {
            throw new RuntimeException("Test failed", e);
        }
    }

    // we need 'real' localhost addresses only (eg. not loopback ones)
    // so we can bind the remote JMX connector to them
    private static boolean isNonloopbackLocalhost(InetAddress i) {
        if (!i.isLoopbackAddress()) {
            return i.getHostName().toLowerCase().equals("localhost");
        }
        return false;
    }

    private static class TestProcessThread extends Thread {

        private final Predicate<String> predicate;
        private final ProcessBuilder pb;
        private final CountDownLatch latch;
        private Process process;

        public TestProcessThread(String threadName, Predicate<String> predicate, ProcessBuilder pb) {
            super(threadName);
            this.predicate = predicate;
            this.pb = pb;
            this.latch = new CountDownLatch(1);
        }

        @Override
        public void run() {
            try {
                process = ProcessTools.startProcess(getName(), pb, predicate, 10, TimeUnit.SECONDS);
                latch.countDown();
                process.waitFor();
            } catch (Exception e) {
                throw new RuntimeException("Test failed", e);
            }
        }

        public void stopProcess() {
            try {
                latch.await();
            } catch (InterruptedException e1) {
                throw new RuntimeException("Test failed", e1);
            }
            if (process != null) {
                process.destroyForcibly();
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Test failed", e);
                }
            }
        }

        public int getExitValue() {
            return process.exitValue();
        }
    }
}
