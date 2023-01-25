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
 */

 /*
 * @test
 * @bug 8194298
 * @summary Add support for per Socket configuration of TCP keepalive
 * @run main TcpKeepAliveTest
 */
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketOption;
import java.util.Set;
import jdk.net.ExtendedSocketOptions;
import jdk.net.Sockets;

public class TcpKeepAliveTest {

    private static final String LOCAL_HOST = "127.0.0.1";
    private static final int DEFAULT_KEEP_ALIVE_PROBES = 7;
    private static final int DEFAULT_KEEP_ALIVE_TIME = 1973;
    private static final int DEFAULT_KEEP_ALIVE_INTVL = 53;

    public static void main(String args[]) throws IOException {

        try (ServerSocket ss = new ServerSocket(0);
                Socket s = new Socket(LOCAL_HOST, ss.getLocalPort());
                DatagramSocket ds = new DatagramSocket(0);
                MulticastSocket mc = new MulticastSocket(0)) {
            Set<SocketOption<?>> supportedOpts = Sockets.supportedOptions(ss.getClass());
            Set<SocketOption<?>> supportedOptsClient = Sockets.supportedOptions(s.getClass());
            Set<SocketOption<?>> supportedOptsDG = Sockets.supportedOptions(ds.getClass());
            Set<SocketOption<?>> supportedOptsMC = Sockets.supportedOptions(mc.getClass());
            if (supportedOpts.contains(ExtendedSocketOptions.TCP_KEEPIDLE)) {
                Sockets.setOption(ss, ExtendedSocketOptions.TCP_KEEPIDLE, DEFAULT_KEEP_ALIVE_TIME);
                if (Sockets.getOption(ss, ExtendedSocketOptions.TCP_KEEPIDLE) != DEFAULT_KEEP_ALIVE_TIME) {
                    throw new RuntimeException("Test failed, TCP_KEEPIDLE should have been " + DEFAULT_KEEP_ALIVE_TIME);
                }
            }
            if (supportedOpts.contains(ExtendedSocketOptions.TCP_KEEPCOUNT)) {
                Sockets.setOption(ss, ExtendedSocketOptions.TCP_KEEPCOUNT, DEFAULT_KEEP_ALIVE_PROBES);
                if (Sockets.getOption(ss, ExtendedSocketOptions.TCP_KEEPCOUNT) != DEFAULT_KEEP_ALIVE_PROBES) {
                    throw new RuntimeException("Test failed, TCP_KEEPCOUNT should have been " + DEFAULT_KEEP_ALIVE_PROBES);
                }
            }
            if (supportedOpts.contains(ExtendedSocketOptions.TCP_KEEPINTERVAL)) {
                Sockets.setOption(ss, ExtendedSocketOptions.TCP_KEEPINTERVAL, DEFAULT_KEEP_ALIVE_INTVL);
                if (Sockets.getOption(ss, ExtendedSocketOptions.TCP_KEEPINTERVAL) != DEFAULT_KEEP_ALIVE_INTVL) {
                    throw new RuntimeException("Test failed, TCP_KEEPINTERVAL should have been " + DEFAULT_KEEP_ALIVE_INTVL);
                }
            }
            if (supportedOptsClient.contains(ExtendedSocketOptions.TCP_KEEPIDLE)) {
                Sockets.setOption(s, ExtendedSocketOptions.TCP_KEEPIDLE, DEFAULT_KEEP_ALIVE_TIME);
                if (Sockets.getOption(s, ExtendedSocketOptions.TCP_KEEPIDLE) != DEFAULT_KEEP_ALIVE_TIME) {
                    throw new RuntimeException("Test failed, TCP_KEEPIDLE should have been " + DEFAULT_KEEP_ALIVE_TIME);
                }
            }
            if (supportedOptsClient.contains(ExtendedSocketOptions.TCP_KEEPCOUNT)) {
                Sockets.setOption(s, ExtendedSocketOptions.TCP_KEEPCOUNT, DEFAULT_KEEP_ALIVE_PROBES);
                if (Sockets.getOption(s, ExtendedSocketOptions.TCP_KEEPCOUNT) != DEFAULT_KEEP_ALIVE_PROBES) {
                    throw new RuntimeException("Test failed, TCP_KEEPCOUNT should have been " + DEFAULT_KEEP_ALIVE_PROBES);
                }
            }
            if (supportedOptsClient.contains(ExtendedSocketOptions.TCP_KEEPINTERVAL)) {
                Sockets.setOption(s, ExtendedSocketOptions.TCP_KEEPINTERVAL, DEFAULT_KEEP_ALIVE_INTVL);
                if (Sockets.getOption(s, ExtendedSocketOptions.TCP_KEEPINTERVAL) != DEFAULT_KEEP_ALIVE_INTVL) {
                    throw new RuntimeException("Test failed, TCP_KEEPINTERVAL should have been " + DEFAULT_KEEP_ALIVE_INTVL);
                }
            }
            if (supportedOptsDG.contains(ExtendedSocketOptions.TCP_KEEPCOUNT)) {
                throw new RuntimeException("Test failed, TCP_KEEPCOUNT is applicable"
                        + " for TCP Sockets only.");
            }
            if (supportedOptsDG.contains(ExtendedSocketOptions.TCP_KEEPIDLE)) {
                throw new RuntimeException("Test failed, TCP_KEEPIDLE is applicable"
                        + " for TCP Sockets only.");
            }
            if (supportedOptsDG.contains(ExtendedSocketOptions.TCP_KEEPINTERVAL)) {
                throw new RuntimeException("Test failed, TCP_KEEPINTERVAL is applicable"
                        + " for TCP Sockets only.");
            }
            if (supportedOptsMC.contains(ExtendedSocketOptions.TCP_KEEPCOUNT)) {
                throw new RuntimeException("Test failed, TCP_KEEPCOUNT is applicable"
                        + " for TCP Sockets only");
            }
            if (supportedOptsMC.contains(ExtendedSocketOptions.TCP_KEEPIDLE)) {
                throw new RuntimeException("Test failed, TCP_KEEPIDLE is applicable"
                        + " for TCP Sockets only");
            }
            if (supportedOptsMC.contains(ExtendedSocketOptions.TCP_KEEPINTERVAL)) {
                throw new RuntimeException("Test failed, TCP_KEEPINTERVAL is applicable"
                        + " for TCP Sockets only");
            }
        }
    }
}
