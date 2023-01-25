/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @bug 4486658
 * @compile -source 1.5 LockOncePerThreadLoops.java
 * @run main/timeout=15000 LockOncePerThreadLoops
 * @summary Checks for missed signals by locking and unlocking each of an array of locks once per thread
 */

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;

public final class LockOncePerThreadLoops {
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static final LoopHelpers.SimpleRandom rng = new LoopHelpers.SimpleRandom();
    static boolean print = false;
    static int nlocks = 50000;
    static int nthreads = 100;
    static int replications = 5;

    public static void main(String[] args) throws Exception {
        if (args.length > 0)
            replications = Integer.parseInt(args[0]);

        if (args.length > 1)
            nlocks = Integer.parseInt(args[1]);

        print = true;

        for (int i = 0; i < replications; ++i) {
            System.out.print("Iteration: " + i);
            new ReentrantLockLoop().test();
            Thread.sleep(100);
        }
        pool.shutdown();
        if (! pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS))
            throw new Error();
    }

    static final class ReentrantLockLoop implements Runnable {
        private int v = rng.next();
        private volatile int result = 17;
        final ReentrantLock[]locks = new ReentrantLock[nlocks];

        private final ReentrantLock lock = new ReentrantLock();
        private final LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        private final CyclicBarrier barrier;
        ReentrantLockLoop() {
            barrier = new CyclicBarrier(nthreads+1, timer);
            for (int i = 0; i < nlocks; ++i)
                locks[i] = new ReentrantLock();
        }

        final void test() throws Exception {
            for (int i = 0; i < nthreads; ++i)
                pool.execute(this);
            barrier.await();
            barrier.await();
            if (print) {
                long time = timer.getTime();
                double secs = (double)(time) / 1000000000.0;
                System.out.println("\t " + secs + "s run time");
            }

            int r = result;
            if (r == 0) // avoid overoptimization
                System.out.println("useless result: " + r);
        }

        public final void run() {
            try {
                barrier.await();
                int sum = v;
                int x = 0;
                for (int i = 0; i < locks.length; ++i) {
                    locks[i].lock();
                    try {
                            v = x += ~(v - i);
                    }
                    finally {
                        locks[i].unlock();
                    }
                    // Once in a while, do something more expensive
                    if ((~i & 255) == 0) {
                        sum += LoopHelpers.compute1(LoopHelpers.compute2(x));
                    }
                    else
                        sum += sum ^ x;
                }
                barrier.await();
                result += sum;
            }
            catch (Exception ie) {
                return;
            }
        }
    }

}
