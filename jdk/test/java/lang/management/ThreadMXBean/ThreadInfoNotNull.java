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
 * @summary This test ensures a thread's ThreadInfo object is not 
 *          null, especially the primitive thread (tid=1).
 */

import java.lang.management.*;

public class ThreadInfoNotNull {

    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private static final long[] threads = threadMXBean.getAllThreadIds();
    private static final ThreadInfo[] tInfoArray = threadMXBean.getThreadInfo(threads);

    private static void threadInfoNotNull() {
        for (ThreadInfo ti : tInfoArray) {
            if (ti == null) {
                throw new RuntimeException("TEST FAILED: " +
                                            " Null ThreadInfo");
            }
        }
    }

    private static void primitiveThreadExist() {
        boolean containsPrimitiveThread = false;
        for (long tid : threads) {
            containsPrimitiveThread = (tid == 1) ? true : false;
        }

        if (!containsPrimitiveThread) {
            throw new RuntimeException("TEST FAILED: " +
                                        " No primitive thread found");
        }
    }

    public static void main(String[] args) 
        throws Exception{
        primitiveThreadExist();
        threadInfoNotNull();

        System.out.println("TEST PASSED.");
    }
}
