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
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.jfr.event.runtime;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import sun.misc.Unsafe;

/**
 * @test
 * @summary Test Shutdown event
 * @key jfr
 * @library /lib /
 * @run main/othervm jdk.jfr.event.runtime.TestShutdownEvent
 */
public class TestShutdownEvent {
    private static ShutdownEventSubTest subTests[] = {
             new TestLastNonDaemon(),
             new TestSystemExit(),
             new TestVMCrash(),
             new TestUnhandledException(),
             new TestRuntimeHalt(),
             new TestSig("TERM"),
             new TestSig("HUP"),
             new TestSig("INT")
    };

    public static void main(String[] args) throws Throwable {
        for (int i = 0; i < subTests.length; ++i) {
            if (subTests[i].isApplicable()) {
                runSubtest(i);
            } else {
                System.out.println("Skipping non-applicable test: " + i);
            }
        }
    }

    private static void runSubtest(int subTestIndex) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true,
                                "-XX:+LogJFR",
                                "-XX:-CreateMinidumpOnCrash",
                                "-XX:StartFlightRecording=filename=./dumped.jfr,dumponexit=true,settings=default",
                                "jdk.jfr.event.runtime.TestShutdownEvent$TestMain",
                                String.valueOf(subTestIndex));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        System.out.println(output.getOutput());
        int exitCode = output.getExitValue();
        System.out.println("Exit code: " + exitCode);

        String recordingName = output.firstMatch("emergency jfr file: (.*.jfr)", 1);
        if (recordingName == null) {
            recordingName = "./dumped.jfr";
        }

        List<RecordedEvent> events = RecordingFile.readAllEvents(Paths.get(recordingName));
        List<RecordedEvent> filteredEvents = events.stream()
            .filter(e -> e.getEventType().getName().equals(EventNames.Shutdown))
            .sorted(Comparator.comparing(RecordedEvent::getStartTime))
            .collect(Collectors.toList());

        Asserts.assertEquals(filteredEvents.size(), 1);
        RecordedEvent event = filteredEvents.get(0);
        subTests[subTestIndex].verifyEvents(event, exitCode);
    }

    @SuppressWarnings("unused")
    private static class TestMain {
        public static void main(String[] args) throws Exception {
            ShutdownEventSubTest subTest = subTests[Integer.parseInt(args[0])];
            System.out.println("Running subtest " + args[0] + " (" + subTest.getClass().getName() + ")");
            subTest.runTest();
        }
    }

    private interface ShutdownEventSubTest {
        default boolean isApplicable() {
            return true;
        }
        void runTest();
        void verifyEvents(RecordedEvent event, int exitCode);
    }

    public static Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe)f.get(null);
        } catch (Exception e) {
            Asserts.fail("Could not access Unsafe");
        }
        return null;
    }

    // Basic stack trace validation, checking that the runTest method is part of the stack
    static void validateStackTrace(RecordedStackTrace stackTrace) {
        List<RecordedFrame> frames = stackTrace.getFrames();
        Asserts.assertFalse(frames.isEmpty());
        Asserts.assertTrue(frames.stream()
                           .anyMatch(t -> t.getMethod().getName().equals("runTest")));
    }


    // =========================================================================
    private static class TestLastNonDaemon implements ShutdownEventSubTest {
        @Override
        public void runTest() {
            // Do nothing - this is the default exit reason
        }

        @Override
        public void verifyEvents(RecordedEvent event, int exitCode) {
            Events.assertField(event, "reason").equal("No remaining non-daemon Java threads");
        }
    }

    private static class TestSystemExit implements ShutdownEventSubTest {
        @Override
        public void runTest() {
            System.out.println("Running System.exit");
            System.exit(42);
        }

        @Override
        public void verifyEvents(RecordedEvent event, int exitCode) {
            Events.assertField(event, "reason").equal("Shutdown requested from Java");
            validateStackTrace(event.getStackTrace());
        }
    }

    private static class TestVMCrash implements ShutdownEventSubTest {

        @Override
        public void runTest() {
            System.out.println("Attempting to crash");
            getUnsafe().putInt(0L, 0);
        }

        @Override
        public void verifyEvents(RecordedEvent event, int exitCode) {
            Events.assertField(event, "reason").equal("VM Error");
            validateStackTrace(event.getStackTrace());
        }
    }

    private static class TestUnhandledException implements ShutdownEventSubTest {
        @Override
        public void runTest() {
            throw new RuntimeException("Unhandled");
        }

        @Override
        public void verifyEvents(RecordedEvent event, int exitCode) {
            Events.assertField(event, "reason").equal("No remaining non-daemon Java threads");
        }
    }

    private static class TestRuntimeHalt implements ShutdownEventSubTest {
        @Override
        public void runTest() {
            System.out.println("Running Runtime.getRuntime.halt");
            Runtime.getRuntime().halt(17);
        }

        @Override
        public void verifyEvents(RecordedEvent event, int exitCode) {
            Events.assertField(event, "reason").equal("Shutdown requested from Java");
            validateStackTrace(event.getStackTrace());
        }
    }

    private static class TestSig implements ShutdownEventSubTest {

        private final String signalName;

        @Override
        public boolean isApplicable() {
            if (Platform.isWindows()) {
                return false;
            }
            if (signalName.equals("HUP") && Platform.isSolaris()) {
                return false;
            }
            return true;
        }

        public TestSig(String signalName) {
            this.signalName = signalName;
        }

        @Override
        public void runTest() {
            try {
                long pid = ProcessTools.getProcessId();
                System.out.println("Sending SIG" + signalName + " to process " + pid);
                Runtime.getRuntime().exec("kill -" + signalName + " " + pid).waitFor();
                Thread.sleep(60_1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Process survived the SIG" + signalName + " signal!");
        }

        @Override
        public void verifyEvents(RecordedEvent event, int exitCode) {
            if (exitCode == 0) {
                System.out.println("Process exited normally with exit code 0, skipping the verification");
                return;
            }
            Events.assertField(event, "reason").equal("Shutdown requested from Java");
            Events.assertEventThread(event);
            Asserts.assertEquals(event.getThread().getJavaName(), "SIG" + signalName + " handler");
        }
    }
}
