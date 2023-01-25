/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4313882 7183800
 * @summary Test DatagramChannel's send and receive methods
 * @author Mike McCloskey
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;


public class Connect {

    static PrintStream log = System.err;

    public static void main(String[] args) throws Exception {
        test();
    }

    static void test() throws Exception {
        Reactor r = new Reactor();
        Actor a = new Actor(r.port());
        invoke(a, r);
    }

    static void invoke(Sprintable reader, Sprintable writer) throws Exception {

        Thread writerThread = new Thread(writer);
        writerThread.start();

        Thread readerThread = new Thread(reader);
        readerThread.start();

        writerThread.join();
        readerThread.join();

        reader.throwException();
        writer.throwException();
    }

    public interface Sprintable extends Runnable {
        public void throwException() throws Exception;
    }

    public static class Actor implements Sprintable {
        final int port;
        Exception e = null;

        Actor(int port) {
            this.port = port;
        }

        public void throwException() throws Exception {
            if (e != null)
                throw e;
        }

        public void run() {
            try {
                DatagramChannel dc = DatagramChannel.open();

                // Send a message
                ByteBuffer bb = ByteBuffer.allocateDirect(256);
                bb.put("hello".getBytes());
                bb.flip();
                InetAddress address = InetAddress.getLocalHost();
                if (address.isLoopbackAddress()) {
                    address = InetAddress.getLoopbackAddress();
                }
                InetSocketAddress isa = new InetSocketAddress(address, port);
                dc.connect(isa);
                dc.write(bb);

                // Try to send to some other address
                address = InetAddress.getLocalHost();
                InetSocketAddress bogus = new InetSocketAddress(address, 3333);
                try {
                    dc.send(bb, bogus);
                    throw new RuntimeException("Allowed bogus send while connected");
                } catch (IllegalArgumentException iae) {
                    // Correct behavior
                }

                // Read a reply
                bb.flip();
                dc.read(bb);
                bb.flip();
                CharBuffer cb = Charset.forName("US-ASCII").
                newDecoder().decode(bb);
                log.println("From Reactor: "+isa+ " said " +cb);

                // Clean up
                dc.disconnect();
                dc.close();
            } catch (Exception ex) {
                e = ex;
            }
        }
    }

    public static class Reactor implements Sprintable {
        final DatagramChannel dc;
        Exception e = null;

        Reactor() throws IOException {
            dc = DatagramChannel.open().bind(new InetSocketAddress(0));
        }

        int port() {
            return dc.socket().getLocalPort();
        }

        public void throwException() throws Exception {
            if (e != null)
                throw e;
        }

        public void run() {
            try {
                // Listen for a message
                ByteBuffer bb = ByteBuffer.allocateDirect(100);
                SocketAddress sa = dc.receive(bb);
                bb.flip();
                CharBuffer cb = Charset.forName("US-ASCII").
                newDecoder().decode(bb);
                log.println("From Actor: "+sa+ " said " +cb);

                // Reply to sender
                dc.connect(sa);
                bb.flip();
                dc.write(bb);

                // Clean up
                dc.disconnect();
                dc.close();
            } catch (Exception ex) {
                e = ex;
            }
        }
    }
}
