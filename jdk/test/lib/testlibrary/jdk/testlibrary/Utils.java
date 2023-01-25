/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.testlibrary.Asserts.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedList;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.nio.file.Files;

/**
 * Common library for various test helper functions.
 */
public final class Utils {

    /**
     * Returns the sequence used by operating system to separate lines.
     */
    public static final String NEW_LINE = System.getProperty("line.separator");

    /**
     * Returns the value of 'test.vm.opts'system property.
     */
    public static final String VM_OPTIONS = System.getProperty("test.vm.opts", "").trim();

    /**
     * Returns the value of 'test.java.opts'system property.
     */
    public static final String JAVA_OPTIONS = System.getProperty("test.java.opts", "").trim();


    /**
    * Returns the value of 'test.timeout.factor' system property
    * converted to {@code double}.
    */
    public static final double TIMEOUT_FACTOR;
    static {
        String toFactor = System.getProperty("test.timeout.factor", "1.0");
        TIMEOUT_FACTOR = Double.parseDouble(toFactor);
    }

    /**
    * Returns the value of JTREG default test timeout in milliseconds
    * converted to {@code long}.
    */
    public static final long DEFAULT_TEST_TIMEOUT = TimeUnit.SECONDS.toMillis(120);

    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private Utils() {
        // Private constructor to prevent class instantiation
    }

    /**
     * Returns the list of VM options.
     *
     * @return List of VM options
     */
    public static List<String> getVmOptions() {
        return Arrays.asList(safeSplitString(VM_OPTIONS));
    }

    /**
     * Returns the list of VM options with -J prefix.
     *
     * @return The list of VM options with -J prefix
     */
    public static List<String> getForwardVmOptions() {
        String[] opts = safeSplitString(VM_OPTIONS);
        for (int i = 0; i < opts.length; i++) {
            opts[i] = "-J" + opts[i];
        }
        return Arrays.asList(opts);
    }

    /**
     * Returns the default JTReg arguments for a jvm running a test.
     * This is the combination of JTReg arguments test.vm.opts and test.java.opts.
     * @return An array of options, or an empty array if no opptions.
     */
    public static String[] getTestJavaOpts() {
        List<String> opts = new ArrayList<String>();
        Collections.addAll(opts, safeSplitString(VM_OPTIONS));
        Collections.addAll(opts, safeSplitString(JAVA_OPTIONS));
        return opts.toArray(new String[0]);
    }

    /**
     * Combines given arguments with default JTReg arguments for a jvm running a test.
     * This is the combination of JTReg arguments test.vm.opts and test.java.opts
     * @return The combination of JTReg test java options and user args.
     */
    public static String[] addTestJavaOpts(String... userArgs) {
        List<String> opts = new ArrayList<String>();
        Collections.addAll(opts, getTestJavaOpts());
        Collections.addAll(opts, userArgs);
        return opts.toArray(new String[0]);
    }

    /**
     * Removes any options specifying which GC to use, for example "-XX:+UseG1GC".
     * Removes any options matching: -XX:(+/-)Use*GC
     * Used when a test need to set its own GC version. Then any
     * GC specified by the framework must first be removed.
     * @return A copy of given opts with all GC options removed.
     */
    private static final Pattern useGcPattern = Pattern.compile("\\-XX\\:[\\+\\-]Use.+GC");
    public static List<String> removeGcOpts(List<String> opts) {
        List<String> optsWithoutGC = new ArrayList<String>();
        for (String opt : opts) {
            if (useGcPattern.matcher(opt).matches()) {
                System.out.println("removeGcOpts: removed " + opt);
            } else {
                optsWithoutGC.add(opt);
            }
        }
        return optsWithoutGC;
    }

    /**
     * Splits a string by white space.
     * Works like String.split(), but returns an empty array
     * if the string is null or empty.
     */
    private static String[] safeSplitString(String s) {
        if (s == null || s.trim().isEmpty()) {
            return new String[] {};
        }
        return s.trim().split("\\s+");
    }

    /**
     * @return The full command line for the ProcessBuilder.
     */
    public static String getCommandLine(ProcessBuilder pb) {
        StringBuilder cmd = new StringBuilder();
        for (String s : pb.command()) {
            cmd.append(s).append(" ");
        }
        return cmd.toString();
    }

    /**
     * Returns the free port on the local host.
     * The function will spin until a valid port number is found.
     *
     * @return The port number
     * @throws InterruptedException if any thread has interrupted the current thread
     * @throws IOException if an I/O error occurs when opening the socket
     */
    public static int getFreePort() throws InterruptedException, IOException {
        int port = -1;

        while (port <= 0) {
            Thread.sleep(100);

            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(0);
                port = serverSocket.getLocalPort();
            } finally {
                serverSocket.close();
            }
        }

        return port;
    }

    /**
     * Returns the name of the local host.
     *
     * @return The host name
     * @throws UnknownHostException if IP address of a host could not be determined
     */
    public static String getHostname() throws UnknownHostException {
        InetAddress inetAddress = InetAddress.getLocalHost();
        String hostName = inetAddress.getHostName();

        assertTrue((hostName != null && !hostName.isEmpty()),
                "Cannot get hostname");

        return hostName;
    }

    /**
     * Uses "jcmd -l" to search for a jvm pid. This function will wait
     * forever (until jtreg timeout) for the pid to be found.
     * @param key Regular expression to search for
     * @return The found pid.
     */
    public static int waitForJvmPid(String key) throws Throwable {
        final long iterationSleepMillis = 250;
        System.out.println("waitForJvmPid: Waiting for key '" + key + "'");
        System.out.flush();
        while (true) {
            int pid = tryFindJvmPid(key);
            if (pid >= 0) {
                return pid;
            }
            Thread.sleep(iterationSleepMillis);
        }
    }

    /**
     * Searches for a jvm pid in the output from "jcmd -l".
     *
     * Example output from jcmd is:
     * 12498 sun.tools.jcmd.JCmd -l
     * 12254 /tmp/jdk8/tl/jdk/JTwork/classes/com/sun/tools/attach/Application.jar
     *
     * @param key A regular expression to search for.
     * @return The found pid, or -1 if Enot found.
     * @throws Exception If multiple matching jvms are found.
     */
    public static int tryFindJvmPid(String key) throws Throwable {
        OutputAnalyzer output = null;
        try {
            JDKToolLauncher jcmdLauncher = JDKToolLauncher.create("jcmd");
            jcmdLauncher.addToolArg("-l");
            output = ProcessTools.executeProcess(jcmdLauncher.getCommand());
            output.shouldHaveExitValue(0);

            // Search for a line starting with numbers (pid), follwed by the key.
            Pattern pattern = Pattern.compile("([0-9]+)\\s.*(" + key + ").*\\r?\\n");
            Matcher matcher = pattern.matcher(output.getStdout());

            int pid = -1;
            if (matcher.find()) {
                pid = Integer.parseInt(matcher.group(1));
                System.out.println("findJvmPid.pid: " + pid);
                if (matcher.find()) {
                    throw new Exception("Found multiple JVM pids for key: " + key);
                }
            }
            return pid;
        } catch (Throwable t) {
            System.out.println(String.format("Utils.findJvmPid(%s) failed: %s", key, t));
            throw t;
        }
    }

    /**
     * Returns file content as a list of strings
     *
     * @param file File to operate on
     * @return List of strings
     * @throws IOException
     */
    public static List<String> fileAsList(File file) throws IOException {
        assertTrue(file.exists() && file.isFile(),
                file.getAbsolutePath() + " does not exist or not a file");
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.getAbsolutePath()))) {
            while (reader.ready()) {
                output.add(reader.readLine().replace(NEW_LINE, ""));
            }
        }
        return output;
    }

    /**
     * Helper method to read all bytes from InputStream
     *
     * @param is InputStream to read from
     * @return array of bytes
     * @throws IOException
     */
    public static byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int capacity = buf.length;
        int nread = 0;
        int n;
        for (;;) {
            // read to EOF which may read more or less than initial buffer size
            while ((n = is.read(buf, nread, capacity - nread)) > 0)
                nread += n;

            // if the last call to read returned -1, then we're done
            if (n < 0)
                break;

            // need to allocate a larger buffer
            if (capacity <= MAX_BUFFER_SIZE - capacity) {
                capacity = capacity << 1;
            } else {
                if (capacity == MAX_BUFFER_SIZE)
                    throw new OutOfMemoryError("Required array size too large");
                capacity = MAX_BUFFER_SIZE;
            }
            buf = Arrays.copyOf(buf, capacity);
        }
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }

    /**
     * Adjusts the provided timeout value for the TIMEOUT_FACTOR
     * @param tOut the timeout value to be adjusted
     * @return The timeout value adjusted for the value of "test.timeout.factor"
     *         system property
     */
    public static long adjustTimeout(long tOut) {
        return Math.round(tOut * Utils.TIMEOUT_FACTOR);
    }

    /**
     * Interface same as java.lang.Runnable but with
     * method {@code run()} able to throw any Throwable.
     */
    public static interface ThrowingRunnable {
        void run() throws Throwable;
    }

    /**
     * Filters out an exception that may be thrown by the given
     * test according to the given filter.
     *
     * @param test - method that is invoked and checked for exception.
     * @param filter - function that checks if the thrown exception matches
     *                 criteria given in the filter's implementation.
     * @return - exception that matches the filter if it has been thrown or
     *           {@code null} otherwise.
     * @throws Throwable - if test has thrown an exception that does not
     *                     match the filter.
     */
    public static Throwable filterException(ThrowingRunnable test,
            Function<Throwable, Boolean> filter) throws Throwable {
        try {
            test.run();
        } catch (Throwable t) {
            if (filter.apply(t)) {
                return t;
            } else {
                throw t;
            }
        }
        return null;
    }

    private static final int BUFFER_SIZE = 1024;

    /**
     * Reads all bytes from the input stream and writes the bytes to the
     * given output stream in the order that they are read. On return, the
     * input stream will be at end of stream. This method does not close either
     * stream.
     * <p>
     * This method may block indefinitely reading from the input stream, or
     * writing to the output stream. The behavior for the case where the input
     * and/or output stream is <i>asynchronously closed</i>, or the thread
     * interrupted during the transfer, is highly input and output stream
     * specific, and therefore not specified.
     * <p>
     * If an I/O error occurs reading from the input stream or writing to the
     * output stream, then it may do so after some bytes have been read or
     * written. Consequently the input stream may not be at end of stream and
     * one, or both, streams may be in an inconsistent state. It is strongly
     * recommended that both streams be promptly closed if an I/O error occurs.
     *
     * @param  in the input stream, non-null
     * @param  out the output stream, non-null
     * @return the number of bytes transferred
     * @throws IOException if an I/O error occurs when reading or writing
     * @throws NullPointerException if {@code in} or {@code out} is {@code null}
     *
     */
    public static long transferTo(InputStream in, OutputStream out)
            throws IOException  {
        long transferred = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer, 0, BUFFER_SIZE)) >= 0) {
            out.write(buffer, 0, read);
            transferred += read;
        }
        return transferred;
    }

    // Parses the specified source file for "@{id} breakpoint" tags and returns
    // list of the line numbers containing the tag.
    // Example:
    //   System.out.println("BP is here");  // @1 breakpoint
    public static List<Integer> parseBreakpoints(String filePath, int id) {
        final String pattern = "@" + id + " breakpoint";
        int lineNum = 1;
        List<Integer> result = new LinkedList<>();
        try {
            for (String line: Files.readAllLines(Paths.get(filePath))) {
                if (line.contains(pattern)) {
                    result.add(lineNum);
                }
                lineNum++;
            }
        } catch (IOException ex) {
            throw new RuntimeException("failed to parse " + filePath, ex);
        }
        return result;
    }

    // gets full test source path for the given test filename
    public static String getTestSourcePath(String fileName) {
        return Paths.get(System.getProperty("test.src")).resolve(fileName).toString();
    }
}
