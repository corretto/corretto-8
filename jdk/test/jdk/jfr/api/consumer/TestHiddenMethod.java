/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.api.consumer;

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.time.Duration;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.test.lib.jfr.Events;


/**
 * @test
 * @key jfr
 *
 *
 * @library /lib /
 *

 *
 * @run main/othervm jdk.jfr.api.consumer.TestHiddenMethod
 */
public final class TestHiddenMethod {

    public static void main(String[] args) throws Throwable {
        Recording recording = new Recording();
        recording.enable(MyEvent.class).withThreshold(Duration.ofMillis(0));
        recording.start();

        // Commit event with hidden methods
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");
        engine.eval(
                "function emit() {"
                + "  print('About to emit event from Javascript');"
                + "  var TestEvent = Java.type(\"jdk.jfr.api.consumer.TestHiddenMethod$MyEvent\");"
                + "  var event = new TestEvent;"
                + "  event.begin();"
                + "  event.end();"
                + "  event.commit();"
                + "  print('Event emitted from Javascript!');"
                + "}"
                + "emit();");

        // Commit event with visible method
        MyEvent visible = new MyEvent();
        visible.begin();
        visible.end();
        visible.commit();
        recording.stop();

        List<RecordedEvent> events = Events.fromRecording(recording);
        assertEquals(2, events.size(), "Expected two events");
        RecordedEvent hiddenEvent = events.get(0);
        RecordedEvent visibleEvent = events.get(1);

        System.out.println("hiddenEvent:" + hiddenEvent);
        System.out.println("visibleEvent:" + visibleEvent);

        assertTrue(hasHiddenStackFrame(hiddenEvent), "No hidden frame in hidden event: " + hiddenEvent);
        assertFalse(hasHiddenStackFrame(visibleEvent), "Hidden frame in visible event: " + visibleEvent);
    }

    private static boolean hasHiddenStackFrame(RecordedEvent event) throws Throwable {
        RecordedStackTrace stacktrace = event.getStackTrace();
        List<RecordedFrame> frames = stacktrace.getFrames();
        assertFalse(frames.isEmpty(), "Stacktrace frames was empty");
        for (RecordedFrame frame : frames) {
            if (frame.getMethod().isHidden()) {
                return true;
            }
        }
        return false;
    }

    public static class MyEvent extends Event {
    }

}
