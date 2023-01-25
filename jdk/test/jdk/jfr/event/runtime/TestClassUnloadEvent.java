/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test
 * @summary The test verifies that a class unload event is created when class is unloaded
 * @key jfr
 *
 * @library /lib /
 * @build jdk.jfr.event.runtime.TestClasses
 * @run main/othervm -XX:+PrintGCDetails -XX:+PrintGC  -verbose:class -Xmx16m jdk.jfr.event.runtime.TestClassUnloadEvent
 */

/**
 * System.gc() will trigger class unloading if -XX:+ExplicitGCInvokesConcurrent is NOT set.
 * If this flag is set G1 will never unload classes on System.gc() and
 * CMS will not guarantee that all semantically dead classes will be unloaded.
 * As far as the "jfr" key guarantees no VM flags are set from the outside
 * it should be enough with System.gc().
 */
public final class TestClassUnloadEvent {
    private final static String TEST_CLASS_NAME = "jdk.jfr.event.runtime.TestClasses";
    private final static String EVENT_PATH = EventNames.ClassUnload;

    // Declare unloadableClassLoader as "public static"
    // to prevent the compiler to optimize away all unread writes
    public static TestClassLoader unloadableClassLoader;

    public static void main(String[] args) throws Throwable {
        Recording recording = new Recording();
        recording.enable(EVENT_PATH).withThreshold(Duration.ofMillis(0));
        unloadableClassLoader = new TestClassLoader();
        recording.start();
        unloadableClassLoader.loadClass(TEST_CLASS_NAME);
        unloadableClassLoader = null;
        System.gc();
        recording.stop();

        List<RecordedEvent> events = Events.fromRecording(recording);
        Events.hasEvents(events);
        boolean isAnyFound = false;
        for (RecordedEvent event : events) {
            System.out.println("Event:" + event);
            RecordedClass unloadedClass = event.getValue("unloadedClass");
            if (TEST_CLASS_NAME.equals(unloadedClass.getName())) {
                RecordedClassLoader definingClassLoader = unloadedClass.getClassLoader();
                Asserts.assertEquals(TestClassLoader.class.getName(), definingClassLoader.getType().getName(),
                    "Expected " + TestClassLoader.class.getName() + ", got " + definingClassLoader.getType().getName());
                //Asserts.assertEquals(TestClassLoader.CLASS_LOADER_NAME, definingClassLoader.getName(),
                  //  "Expected " + TestClassLoader.CLASS_LOADER_NAME + ", got " + definingClassLoader.getName());
                Asserts.assertFalse(isAnyFound, "Found more than 1 event");
                isAnyFound = true;
            }
        }
        Asserts.assertTrue(isAnyFound, "No events found");
    }
}
