/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * The "debuggee" used by the unit tests for the ProcessAttachingConnector.
 * This debuggee binds to a random TCP port and waits for a client to connect.
 */
import java.net.Socket;
import java.net.ServerSocket;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.File;
import java.io.FileOutputStream;

public class ProcessAttachDebuggee {
    public static void main(String args[]) throws Exception {
        // bind to a random port
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();

        // Write the port number to the given file
        File partial = new File(args[0] + ".partial");
        File portFile = new File(args[0]);
        try (FileOutputStream fos = new FileOutputStream(partial)) {
            fos.write( Integer.toString(port).getBytes("UTF-8") );
        }
        Files.move(partial.toPath(), portFile.toPath(), StandardCopyOption.ATOMIC_MOVE);

        System.out.println("Debuggee bound to port: " + port);
        System.out.flush();

        // wait for test harness to connect
        Socket s = ss.accept();
        s.close();
        ss.close();

        System.out.println("Debuggee shutdown.");
    }
}
