/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6370908
 * @summary Add support for HTTP_CONNECT proxy in Socket class
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import static java.lang.System.out;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import sun.net.www.MessageHeader;

public class HttpProxy {
    final String proxyHost;
    final int proxyPort;
    static final int SO_TIMEOUT = 15000;

    public static void main(String[] args) throws Exception {
        String host;
        int port;
        if (args.length == 0) {
            // Start internal proxy
            ConnectProxyTunnelServer proxy = new ConnectProxyTunnelServer();
            proxy.start();
            host = "localhost";
            port = proxy.getLocalPort();
            out.println("Running with internal proxy: " + host + ":" + port);
        } else if (args.length == 2) {
            host = args[0];
            port = Integer.valueOf(args[1]);
            out.println("Running against specified proxy server: " + host + ":" + port);
        } else {
            System.err.println("Usage: java HttpProxy [<proxy host> <proxy port>]");
            return;
        }

        HttpProxy p = new HttpProxy(host, port);
        p.test();
    }

    public HttpProxy(String proxyHost, int proxyPort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    void test() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress(proxyHost, proxyPort);
        Proxy httpProxy = new Proxy(Proxy.Type.HTTP, proxyAddress);

        try (ServerSocket ss = new ServerSocket(0);
             Socket sock = new Socket(httpProxy)) {
            sock.setSoTimeout(SO_TIMEOUT);
            sock.setTcpNoDelay(false);

            InetSocketAddress externalAddress =
                new InetSocketAddress(InetAddress.getLocalHost(), ss.getLocalPort());

            out.println("Trying to connect to server socket on " + externalAddress);
            sock.connect(externalAddress);
            try (Socket externalSock = ss.accept()) {
                // perform some simple checks
                check(sock.isBound(), "Socket is not bound");
                check(sock.isConnected(), "Socket is not connected");
                check(!sock.isClosed(), "Socket should not be closed");
                check(sock.getSoTimeout() == SO_TIMEOUT,
                        "Socket should have a previously set timeout");
                check(sock.getTcpNoDelay() ==  false, "NODELAY should be false");

                simpleDataExchange(sock, externalSock);
            }
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) out.println(message);
    }

    static Exception unexpected(Exception e) {
        out.println("Unexcepted Exception: " + e);
        e.printStackTrace();
        return e;
    }

    // performs a simple exchange of data between the two sockets
    // and throws an exception if there is any problem.
    void simpleDataExchange(Socket s1, Socket s2) throws Exception {
        try (final InputStream i1 = s1.getInputStream();
             final InputStream i2 = s2.getInputStream();
             final OutputStream o1 = s1.getOutputStream();
             final OutputStream o2 = s2.getOutputStream()) {
            startSimpleWriter("simpleWriter1", o1, 100);
            startSimpleWriter("simpleWriter2", o2, 200);
            simpleRead(i2, 100);
            simpleRead(i1, 200);
        }
    }

    void startSimpleWriter(String threadName, final OutputStream os, final int start) {
        (new Thread(new Runnable() {
            public void run() {
                try { simpleWrite(os, start); }
                catch (Exception e) {unexpected(e); }
            }}, threadName)).start();
    }

    void simpleWrite(OutputStream os, int start) throws Exception {
        byte b[] = new byte[2];
        for (int i=start; i<start+100; i++) {
            b[0] = (byte) (i / 256);
            b[1] = (byte) (i % 256);
            os.write(b);
        }
    }

    void simpleRead(InputStream is, int start) throws Exception {
        byte b[] = new byte [2];
        for (int i=start; i<start+100; i++) {
            int x = is.read(b);
            if (x == 1)
                x += is.read(b,1,1);
            if (x!=2)
                throw new Exception("read error");
            int r = bytes(b[0], b[1]);
            if (r != i)
                throw new Exception("read " + r + " expected " +i);
        }
    }

    int bytes(byte b1, byte b2) {
        int i1 = (int)b1 & 0xFF;
        int i2 = (int)b2 & 0xFF;
        return i1 * 256 + i2;
    }

    static class ConnectProxyTunnelServer extends Thread {

        private final ServerSocket ss;

        public ConnectProxyTunnelServer() throws IOException {
            ss = new ServerSocket(0);
        }

        @Override
        public void run() {
            try (Socket clientSocket = ss.accept()) {
                processRequest(clientSocket);
            } catch (Exception e) {
                out.println("Proxy Failed: " + e);
                e.printStackTrace();
            } finally {
                try { ss.close(); } catch (IOException x) { unexpected(x); }
            }
        }

        /**
         * Returns the port on which the proxy is accepting connections.
         */
        public int getLocalPort() {
            return ss.getLocalPort();
        }

        /*
         * Processes the CONNECT request
         */
        private void processRequest(Socket clientSocket) throws Exception {
            MessageHeader mheader = new MessageHeader(clientSocket.getInputStream());
            String statusLine = mheader.getValue(0);

            if (!statusLine.startsWith("CONNECT")) {
                out.println("proxy server: processes only "
                                  + "CONNECT method requests, recieved: "
                                  + statusLine);
                return;
            }

            // retrieve the host and port info from the status-line
            InetSocketAddress serverAddr = getConnectInfo(statusLine);

            //open socket to the server
            try (Socket serverSocket = new Socket(serverAddr.getAddress(),
                                                  serverAddr.getPort())) {
                Forwarder clientFW = new Forwarder(clientSocket.getInputStream(),
                                                   serverSocket.getOutputStream());
                Thread clientForwarderThread = new Thread(clientFW, "ClientForwarder");
                clientForwarderThread.start();
                send200(clientSocket);
                Forwarder serverFW = new Forwarder(serverSocket.getInputStream(),
                                                   clientSocket.getOutputStream());
                serverFW.run();
                clientForwarderThread.join();
            }
        }

        private void send200(Socket clientSocket) throws IOException {
            OutputStream out = clientSocket.getOutputStream();
            PrintWriter pout = new PrintWriter(out);

            pout.println("HTTP/1.1 200 OK");
            pout.println();
            pout.flush();
        }

        /*
         * This method retrieves the hostname and port of the tunnel destination
         * from the request line.
         * @param connectStr
         *        of the form: <i>CONNECT server-name:server-port HTTP/1.x</i>
         */
        static InetSocketAddress getConnectInfo(String connectStr)
            throws Exception
        {
            try {
                int starti = connectStr.indexOf(' ');
                int endi = connectStr.lastIndexOf(' ');
                String connectInfo = connectStr.substring(starti+1, endi).trim();
                // retrieve server name and port
                endi = connectInfo.indexOf(':');
                String name = connectInfo.substring(0, endi);
                int port = Integer.parseInt(connectInfo.substring(endi+1));
                return new InetSocketAddress(name, port);
            } catch (Exception e) {
                out.println("Proxy recieved a request: " + connectStr);
                throw unexpected(e);
            }
        }
    }

    /* Reads from the given InputStream and writes to the given OutputStream */
    static class Forwarder implements Runnable
    {
        private final InputStream in;
        private final OutputStream os;

        Forwarder(InputStream in, OutputStream os) {
            this.in = in;
            this.os = os;
        }

        @Override
        public void run() {
            try {
                byte[] ba = new byte[1024];
                int count;
                while ((count = in.read(ba)) != -1) {
                    os.write(ba, 0, count);
                }
            } catch (IOException e) {
                unexpected(e);
            }
        }
    }
}
