/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4458085 7095949
 * @summary  Redirects Limited to 5
 */

/*
 * Simulate a server that redirects ( to a different URL) 9 times
 * and see if the client correctly follows the trail
 */

import java.io.*;
import java.net.*;

class RedirLimitServer extends Thread {
    static final int TIMEOUT = 10 * 1000;
    static final int NUM_REDIRECTS = 9;

    static final String reply1 = "HTTP/1.1 307 Temporary Redirect\r\n" +
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Location: http://localhost:";
    static final String reply2 = ".html\r\n" +
        "Connection: close\r\n" +
        "Content-Type: text/html; charset=iso-8859-1\r\n\r\n" +
        "<html>Hello</html>";
    static final String reply3 = "HTTP/1.1 200 Ok\r\n" +
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Connection: close\r\n" +
        "Content-Type: text/html; charset=iso-8859-1\r\n\r\n" +
        "World";

    final ServerSocket ss;
    final int port;

    RedirLimitServer(ServerSocket ss) throws IOException {
        this.ss = ss;
        port = this.ss.getLocalPort();
        this.ss.setSoTimeout(TIMEOUT);
    }

    static final byte[] requestEnd = new byte[] {'\r', '\n', '\r', '\n' };

    // Read until the end of a HTTP request
    void readOneRequest(InputStream is) throws IOException {
        int requestEndCount = 0, r;
        while ((r = is.read()) != -1) {
            if (r == requestEnd[requestEndCount]) {
                requestEndCount++;
                if (requestEndCount == 4) {
                    break;
                }
            } else {
                requestEndCount = 0;
            }
        }
    }

    public void run() {
        try {
            for (int i=0; i<NUM_REDIRECTS; i++) {
                try (Socket s = ss.accept()) {
                    s.setSoTimeout(TIMEOUT);
                    readOneRequest(s.getInputStream());
                    String reply = reply1 + port + "/redirect" + i + reply2;
                    s.getOutputStream().write(reply.getBytes());
                }
            }
            try (Socket s = ss.accept()) {
                s.setSoTimeout(TIMEOUT);
                readOneRequest(s.getInputStream());
                s.getOutputStream().write(reply3.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { ss.close(); } catch (IOException unused) {}
        }
    }
};

public class RedirectLimit {
    public static void main(String[] args) throws Exception {
        ServerSocket ss = new ServerSocket (0);
        int port = ss.getLocalPort();
        RedirLimitServer server = new RedirLimitServer(ss);
        server.start();

        URL url = new URL("http://localhost:" + port);
        URLConnection conURL =  url.openConnection();

        conURL.setDoInput(true);
        conURL.setAllowUserInteraction(false);
        conURL.setUseCaches(false);

        try (InputStream in = conURL.getInputStream()) {
            if ((in.read() != (int)'W') || (in.read()!=(int)'o')) {
                throw new RuntimeException("Unexpected string read");
            }
        }
    }
}
