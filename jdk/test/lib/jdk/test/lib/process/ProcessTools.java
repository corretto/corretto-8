/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.lang.management.ManagementFactory;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Utils;

public final class ProcessTools {
    private static final class LineForwarder extends StreamPumper.LinePump {
        private final PrintStream ps;
        private final String prefix;
        LineForwarder(String prefix, PrintStream os) {
            this.ps = os;
            this.prefix = prefix;
        }
        @Override
        protected void processLine(String line) {
            ps.println("[" + prefix + "] " + line);
        }
    }

    private ProcessTools() {
    }

    /**
     * Pumps stdout and stderr from running the process into a String.
     *
     * @param processBuilder ProcessBuilder to run.
     * @return Output from process.
     * @throws IOException If an I/O error occurs.
     */
    public static OutputBuffer getOutput(ProcessBuilder processBuilder) throws IOException {
        return getOutput(processBuilder.start());
    }

    /**
     * Pumps stdout and stderr the running process into a String.
     *
     * @param process Process to pump.
     * @return Output from process.
     * @throws IOException If an I/O error occurs.
     */
    public static OutputBuffer getOutput(Process process) throws IOException {
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        StreamPumper outPumper = new StreamPumper(process.getInputStream(), stdoutBuffer);
        StreamPumper errPumper = new StreamPumper(process.getErrorStream(), stderrBuffer);
        Thread outPumperThread = new Thread(outPumper);
        Thread errPumperThread = new Thread(errPumper);

        outPumperThread.setDaemon(true);
        errPumperThread.setDaemon(true);

        outPumperThread.start();
        errPumperThread.start();

        try {
            process.waitFor();
            outPumperThread.join();
            errPumperThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        return new OutputBuffer(stdoutBuffer.toString(), stderrBuffer.toString());
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * @param name The process name
     * @param processBuilder The process builder
     * @return Returns the initialized process
     * @throws IOException
     */
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder)
    throws IOException {
        return startProcess(name, processBuilder, (Consumer<String>)null);
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>It is possible to monitor the in-streams via the provided {@code consumer}
     * @param name The process name
     * @param consumer {@linkplain Consumer} instance to process the in-streams
     * @param processBuilder The process builder
     * @return Returns the initialized process
     * @throws IOException
     */
    @SuppressWarnings("overloads")
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder,
                                       Consumer<String> consumer)
    throws IOException {
        try {
            return startProcess(name, processBuilder, consumer, null, -1, TimeUnit.NANOSECONDS);
        } catch (InterruptedException | TimeoutException e) {
            // will never happen
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * It is possible to wait for the process to get to a warmed-up state
     * via {@linkplain Predicate} condition on the STDOUT
     * </p>
     * @param name The process name
     * @param processBuilder The process builder
     * @param linePredicate The {@linkplain Predicate} to use on the STDOUT
     *                      Used to determine the moment the target app is
     *                      properly warmed-up.
     *                      It can be null - in that case the warmup is skipped.
     * @param timeout The timeout for the warmup waiting; -1 = no wait; 0 = wait forever
     * @param unit The timeout {@linkplain TimeUnit}
     * @return Returns the initialized {@linkplain Process}
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     */
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder,
                                       final Predicate<String> linePredicate,
                                       long timeout,
                                       TimeUnit unit)
    throws IOException, InterruptedException, TimeoutException {
        return startProcess(name, processBuilder, null, linePredicate, timeout, unit);
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * It is possible to wait for the process to get to a warmed-up state
     * via {@linkplain Predicate} condition on the STDOUT and monitor the
     * in-streams via the provided {@linkplain Consumer}
     * </p>
     * @param name The process name
     * @param processBuilder The process builder
     * @param lineConsumer  The {@linkplain Consumer} the lines will be forwarded to
     * @param linePredicate The {@linkplain Predicate} to use on the STDOUT
     *                      Used to determine the moment the target app is
     *                      properly warmed-up.
     *                      It can be null - in that case the warmup is skipped.
     * @param timeout The timeout for the warmup waiting; -1 = no wait; 0 = wait forever
     * @param unit The timeout {@linkplain TimeUnit}
     * @return Returns the initialized {@linkplain Process}
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     */
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder,
                                       final Consumer<String> lineConsumer,
                                       final Predicate<String> linePredicate,
                                       long timeout,
                                       TimeUnit unit)
    throws IOException, InterruptedException, TimeoutException {
        System.out.println("["+name+"]:" + processBuilder.command().stream().collect(Collectors.joining(" ")));
        Process p = processBuilder.start();
        StreamPumper stdout = new StreamPumper(p.getInputStream());
        StreamPumper stderr = new StreamPumper(p.getErrorStream());

        stdout.addPump(new LineForwarder(name, System.out));
        stderr.addPump(new LineForwarder(name, System.err));
        if (lineConsumer != null) {
            StreamPumper.LinePump pump = new StreamPumper.LinePump() {
                @Override
                protected void processLine(String line) {
                    lineConsumer.accept(line);
                }
            };
            stdout.addPump(pump);
            stderr.addPump(pump);
        }


        CountDownLatch latch = new CountDownLatch(1);
        if (linePredicate != null) {
            StreamPumper.LinePump pump = new StreamPumper.LinePump() {
                @Override
                protected void processLine(String line) {
                    if (latch.getCount() > 0 && linePredicate.test(line)) {
                        latch.countDown();
                    }
                }
            };
            stdout.addPump(pump);
            stderr.addPump(pump);
        } else {
            latch.countDown();
        }
        final Future<Void> stdoutTask = stdout.process();
        final Future<Void> stderrTask = stderr.process();

        try {
            if (timeout > -1) {
                if (timeout == 0) {
                    latch.await();
                } else {
                    if (!latch.await(Utils.adjustTimeout(timeout), unit)) {
                        throw new TimeoutException();
                    }
                }
            }
        } catch (TimeoutException | InterruptedException e) {
            System.err.println("Failed to start a process (thread dump follows)");
            for(Map.Entry<Thread, StackTraceElement[]> s : Thread.getAllStackTraces().entrySet()) {
                printStack(s.getKey(), s.getValue());
            }

            if (p.isAlive()) {
                p.destroyForcibly();
            }

            stdoutTask.cancel(true);
            stderrTask.cancel(true);
            throw e;
        }

        return new ProcessImpl(p, stdoutTask, stderrTask);
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * It is possible to wait for the process to get to a warmed-up state
     * via {@linkplain Predicate} condition on the STDOUT. The warm-up will
     * wait indefinitely.
     * </p>
     * @param name The process name
     * @param processBuilder The process builder
     * @param linePredicate The {@linkplain Predicate} to use on the STDOUT
     *                      Used to determine the moment the target app is
     *                      properly warmed-up.
     *                      It can be null - in that case the warmup is skipped.
     * @return Returns the initialized {@linkplain Process}
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     */
    @SuppressWarnings("overloads")
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder,
                                       final Predicate<String> linePredicate)
    throws IOException, InterruptedException, TimeoutException {
        return startProcess(name, processBuilder, linePredicate, 0, TimeUnit.SECONDS);
    }

    /**
     * Get the process id of the current running Java process
     *
     * @return Process id
     */
    public static long getProcessId() throws Exception {
        final String jvmName = ManagementFactory.getRuntimeMXBean().getName();

        final int index = jvmName.indexOf('@');

        if (index < 1) {
            // part before '@' empty (index = 0) / '@' not found (index = -1)
            return 42;
        }

        try {
            return Long.parseLong(jvmName.substring(0, index));
        } catch (NumberFormatException e) {
            // ignore
        }
        return 42;
    }



    /**
     * Create ProcessBuilder using the java launcher from the jdk to be tested and
     * with any platform specific arguments prepended
     */
    public static ProcessBuilder createJavaProcessBuilder(String... command) {
        return createJavaProcessBuilder(false, command);
    }

    /**
     * Create ProcessBuilder using the java launcher from the jdk to be tested,
     * and with any platform specific arguments prepended.
     *
     * @param addTestVmAndJavaOptions If true, adds test.vm.opts and test.java.opts
     *        to the java arguments.
     * @param command Arguments to pass to the java command.
     * @return The ProcessBuilder instance representing the java command.
     */
    public static ProcessBuilder createJavaProcessBuilder(boolean addTestVmAndJavaOptions, String... command) {
        String javapath = JDKToolFinder.getJDKTool("java");

        ArrayList<String> args = new ArrayList<>();
        args.add(javapath);

        args.add("-cp");
        args.add(System.getProperty("java.class.path"));

        if (addTestVmAndJavaOptions) {
            Collections.addAll(args, Utils.getTestJavaOpts());
        }

        Collections.addAll(args, command);

        // Reporting
        StringBuilder cmdLine = new StringBuilder();
        for (String cmd : args)
            cmdLine.append(cmd).append(' ');
        System.out.println("Command line: [" + cmdLine.toString() + "]");

        return new ProcessBuilder(args.toArray(new String[args.size()]));
    }

    private static void printStack(Thread t, StackTraceElement[] stack) {
        System.out.println("\t" +  t +
                           " stack: (length = " + stack.length + ")");
        if (t != null) {
            for (StackTraceElement stack1 : stack) {
                System.out.println("\t" + stack1);
            }
            System.out.println();
        }
    }

    /**
     * Executes a test jvm process, waits for it to finish and returns the process output.
     * The default jvm options from jtreg, test.vm.opts and test.java.opts, are added.
     * The java from the test.jdk is used to execute the command.
     *
     * The command line will be like:
     * {test.jdk}/bin/java {test.vm.opts} {test.java.opts} cmds
     *
     * The jvm process will have exited before this method returns.
     *
     * @param cmds User specified arguments.
     * @return The output from the process.
     */
    public static OutputAnalyzer executeTestJvm(String... cmds) throws Exception {
        ProcessBuilder pb = createJavaProcessBuilder(Utils.addTestJavaOpts(cmds));
        return executeProcess(pb);
    }

    /**
     * @see #executeTestJvm(String...)
     * @param cmds User specified arguments.
     * @return The output from the process.
     */
    public static OutputAnalyzer executeTestJava(String... cmds) throws Exception {
        return executeTestJvm(cmds);
    }

    /**
     * Executes a process, waits for it to finish and returns the process output.
     * The process will have exited before this method returns.
     * @param pb The ProcessBuilder to execute.
     * @return The {@linkplain OutputAnalyzer} instance wrapping the process.
     */
    public static OutputAnalyzer executeProcess(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = null;
        Process p = null;
        boolean failed = false;
        try {
            p = pb.start();
            output = new OutputAnalyzer(p);
            p.waitFor();

            return output;
        } catch (Throwable t) {
            if (p != null) {
                p.destroyForcibly().waitFor();
            }

            failed = true;
            System.out.println("executeProcess() failed: " + t);
            throw t;
        } finally {
            if (failed) {
                System.err.println(getProcessLog(pb, output));
            }
        }
    }

    /**
     * Executes a process, waits for it to finish and returns the process output.
     *
     * The process will have exited before this method returns.
     *
     * @param cmds The command line to execute.
     * @return The output from the process.
     */
    public static OutputAnalyzer executeProcess(String... cmds) throws Throwable {
        return executeProcess(new ProcessBuilder(cmds));
    }

    /**
     * Used to log command line, stdout, stderr and exit code from an executed process.
     * @param pb The executed process.
     * @param output The output from the process.
     */
    public static String getProcessLog(ProcessBuilder pb, OutputAnalyzer output) {
        String stderr = output == null ? "null" : output.getStderr();
        String stdout = output == null ? "null" : output.getStdout();
        String exitValue = output == null ? "null": Integer.toString(output.getExitValue());
        StringBuilder logMsg = new StringBuilder();
        final String nl = System.getProperty("line.separator");
        logMsg.append("--- ProcessLog ---" + nl);
        logMsg.append("cmd: " + getCommandLine(pb) + nl);
        logMsg.append("exitvalue: " + exitValue + nl);
        logMsg.append("stderr: " + stderr + nl);
        logMsg.append("stdout: " + stdout + nl);

        return logMsg.toString();
    }

    /**
     * @return The full command line for the ProcessBuilder.
     */
    public static String getCommandLine(ProcessBuilder pb) {
        if (pb == null) {
            return "null";
        }
        StringBuilder cmd = new StringBuilder();
        for (String s : pb.command()) {
            cmd.append(s).append(" ");
        }
        return cmd.toString().trim();
    }

    /**
     * Executes a process, waits for it to finish, prints the process output
     * to stdout, and returns the process output.
     *
     * The process will have exited before this method returns.
     *
     * @param cmds The command line to execute.
     * @return The {@linkplain OutputAnalyzer} instance wrapping the process.
     */
    public static OutputAnalyzer executeCommand(String... cmds)
            throws Throwable {
        String cmdLine = Arrays.stream(cmds).collect(Collectors.joining(" "));
        System.out.println("Command line: [" + cmdLine + "]");
        OutputAnalyzer analyzer = ProcessTools.executeProcess(cmds);
        System.out.println(analyzer.getOutput());
        return analyzer;
    }

    /**
     * Executes a process, waits for it to finish, prints the process output
     * to stdout and returns the process output.
     *
     * The process will have exited before this method returns.
     *
     * @param pb The ProcessBuilder to execute.
     * @return The {@linkplain OutputAnalyzer} instance wrapping the process.
     */
    public static OutputAnalyzer executeCommand(ProcessBuilder pb)
            throws Throwable {
        String cmdLine = pb.command().stream().collect(Collectors.joining(" "));
        System.out.println("Command line: [" + cmdLine + "]");
        OutputAnalyzer analyzer = ProcessTools.executeProcess(pb);
        System.out.println(analyzer.getOutput());
        return analyzer;
    }

    private static class ProcessImpl extends Process {

        private final Process p;
        private final Future<Void> stdoutTask;
        private final Future<Void> stderrTask;

        public ProcessImpl(Process p, Future<Void> stdoutTask, Future<Void> stderrTask) {
            this.p = p;
            this.stdoutTask = stdoutTask;
            this.stderrTask = stderrTask;
        }

        @Override
        public OutputStream getOutputStream() {
            return p.getOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return p.getInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return p.getErrorStream();
        }

        @Override
        public int waitFor() throws InterruptedException {
            int rslt = p.waitFor();
            waitForStreams();
            return rslt;
        }

        @Override
        public int exitValue() {
            return p.exitValue();
        }

        @Override
        public void destroy() {
            p.destroy();
        }

        public long pid() {
            try {
               return  ProcessTools.getProcessId();
            } catch (Exception e) {
               //shit happens,  ignore
            }
            return 42;
        }

        @Override
        public boolean isAlive() {
            return p.isAlive();
        }

        @Override
        public Process destroyForcibly() {
            return p.destroyForcibly();
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            boolean rslt = p.waitFor(timeout, unit);
            if (rslt) {
                waitForStreams();
            }
            return rslt;
        }

        private void waitForStreams() throws InterruptedException {
            try {
                stdoutTask.get();
            } catch (ExecutionException e) {
            }
            try {
                stderrTask.get();
            } catch (ExecutionException e) {
            }
        }
    }
}
