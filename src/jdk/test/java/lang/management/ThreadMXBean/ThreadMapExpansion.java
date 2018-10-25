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
 *
 */

/**
 * @test
 * @summary This test is to verify that SimpleOpenHashtable can
 *          rehash and expand when the entry number reaches 
 *          threadhold. By default, the initial size of 
 *          SimpleOpenHashtable is 256. 
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import java.util.ArrayList;

public class ThreadMapExpansion {

    static final int DEFAULT_THREAD_MAP_SIZE = 256;
    static final int NUMBER_OF_THREAD_TO_GENERATE = 300;

    private static boolean testFailed = false;

    static class JavaThread extends Thread{
        public void run() {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.out.println("Unexpected exception is thrown.");
                e.printStackTrace(System.out);
                testFailed = true;
            }
        }
    }

    public static void main (String[] Agrs) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        for (int i = 0; i < NUMBER_OF_THREAD_TO_GENERATE; i++) {
            Thread javaThread = new JavaThread();
            javaThread.setDaemon(true);
            javaThread.start();
        }

        int threadCount = threadBean.getThreadCount();
        if (threadCount < DEFAULT_THREAD_MAP_SIZE) {
            System.out.println("TEST FAILED: " +
                                "Minimal number of live thread count is " +
                                NUMBER_OF_THREAD_TO_GENERATE +
                                "ThreadMXBean.getThreadCount() returned "
                                + threadCount);
            testFailed = true;
        }

        if (testFailed) {
            throw new RuntimeException("TEST FAILED.");
        }

        System.out.println("TEST PASSED.");
    }
}
