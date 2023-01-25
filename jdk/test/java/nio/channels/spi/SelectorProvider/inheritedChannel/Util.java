/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * A collection of utility methods used by the SelectorProvider.inheritedChannel
 * unit tests.
 */
import java.net.*;
import java.io.*;
import java.nio.channels.*;
import java.lang.reflect.*;

// dependency on Sun implementation
import sun.nio.ch.*;

public class Util {

    private static Object get(String className, String fieldName, Object o) throws Exception {
        Class cl = Class.forName(className);
        Field fld = cl.getDeclaredField(fieldName);
        fld.setAccessible(true);
        return fld.get(o);
    }

    private static int fdVal(FileDescriptor fdObj) throws Exception {
        Object fdVal = get("java.io.FileDescriptor", "fd", fdObj);
        return ((Integer)fdVal).intValue();
    }

    /*
     * Return the file descriptor underlying a given SocketChannel
     */
    public static int getFD(SocketChannel sc) {
        try {
            Object fdObj = get("sun.nio.ch.SocketChannelImpl", "fd", sc);
            return fdVal((FileDescriptor)fdObj);
        } catch (Exception x) {
            x.printStackTrace();
            throw new InternalError(x.getMessage());
        }
    }

    /*
     * Return the file descriptor underlying a given ServerSocketChannel
     */
    public static int getFD(ServerSocketChannel ssc) {
        try {
            Object fdObj = get("sun.nio.ch.ServerSocketChannelImpl", "fd", ssc);
            return fdVal((FileDescriptor)fdObj);
        } catch (Exception x) {
            x.printStackTrace();
            throw new InternalError(x.getMessage());
        }
    }

    /*
     * Return the file descriptor underlying a given DatagramChannel
     */
    public static int getFD(DatagramChannel dc) {
        try {
            Object fdObj = get("sun.nio.ch.DatagramChannelImpl", "fd", dc);
            return fdVal((FileDescriptor)fdObj);
        } catch (Exception x) {
            x.printStackTrace();
            throw new InternalError(x.getMessage());
        }
    }

    /*
     * Return the "java" command and any initial arguments to start the runtime
     * in the current configuration.
     *
     * Typically it will return something like :-
     *      cmd[0] = "/usr/local/java/solaris-sparc/bin/java"
     * or
     *      cmd[0] = "/usr/local/java/solaris-sparc/bin/sparcv9/java"
     *      cmd[1] = "-d64"
     */
    public static String[] javaCommand() {
        String exe = System.getProperty("java.home") + File.separator + "bin" +
            File.separator;
        String arch = System.getProperty("os.arch");
        if (arch.equals("sparcv9")) {
            String cmd[] = new String[2];
            cmd[0] = exe + "sparcv9/java";
            cmd[1] = "-d64";
            return cmd;
        } else {
            String cmd[] = new String[1];
            cmd[0] = exe += "java";
            return cmd;
        }
    }
}
