/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4796166
 * @summary Linger interval delays usage of released file descriptor
 */

import java.net.*;
import java.io.*;

public class LingerTest {

    static class Sender implements Runnable {
        Socket s;

        public Sender(Socket s) {
            this.s = s;
        }

        public void run() {
            System.out.println ("Sender starts");
            try {
                s.getOutputStream().write(new byte[128*1024]);
            }
            catch (IOException ioe) {
            }
            System.out.println ("Sender ends");
        }
    }

    static class Closer implements Runnable {
        Socket s;

        public Closer(Socket s) {
            this.s = s;
        }

        public void run() {
            System.out.println ("Closer starts");
            try {
                s.close();
            }
            catch (IOException ioe) {
            }
            System.out.println ("Closer ends");
        }
    }

    static class Other implements Runnable {
        int port;
        long delay;
        boolean connected = false;

        public Other(int port, long delay) {
            this.port = port;
            this.delay = delay;
        }

        public void run() {
            System.out.println ("Other starts: sleep " + delay);
            try {
                Thread.sleep(delay);
                System.out.println ("Other opening socket");
                Socket s = new Socket("localhost", port);
                synchronized (this) {
                    connected = true;
                }
                s.close();
            }
            catch (Exception ioe) {
                ioe.printStackTrace();
            }
            System.out.println ("Other ends");
        }

        public synchronized boolean connected() {
            return connected;
        }
    }

    public static void main(String args[]) throws Exception {
        ServerSocket ss = new ServerSocket(0);

        Socket s1 = new Socket("localhost", ss.getLocalPort());
        Socket s2 = ss.accept();

        // setup conditions for untransmitted data and lengthy
            // linger interval
            s1.setSendBufferSize(128*1024);
        s1.setSoLinger(true, 30);
        s2.setReceiveBufferSize(1*1024);

        // start sender
            Thread thr = new Thread(new Sender(s1));
        thr.start();

        // other thread that will connect after 5 seconds.
            Other other = new Other(ss.getLocalPort(), 5000);
        thr = new Thread(other);
        thr.start();

        // give sender time to queue the data
            System.out.println ("Main sleep 1000");
            Thread.sleep(1000);
            System.out.println ("Main continue");

        // close the socket asynchronously
            (new Thread(new Closer(s1))).start();

            System.out.println ("Main sleep 15000");
        // give other time to run
            Thread.sleep(15000);
            System.out.println ("Main closing serversocket");

        ss.close();
        // check that other is done
            if (!other.connected()) {
            throw new RuntimeException("Other thread is blocked");
        }
        System.out.println ("Main ends");
    }
}
