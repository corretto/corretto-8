/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.sjavac.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import com.sun.tools.sjavac.Log;

/**
 * The PortFile class mediates access to a short binary file containing the tcp/ip port (for the localhost)
 * and a cookie necessary for the server answering on that port. The file can be locked using file system
 * primitives to avoid race conditions when several javac clients are started at the same. Note that file
 * system locking is not always supported on a all operating systems and/or file systems.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
class PortFile {

    // Port file format:
    // byte ordering: high byte first = big endian
    // Magic nr, 4 byte int, first in file.
    private final static int magicNr = 0x1174;
    // Followed by a 4 byte int, with the port nr.
    // Followed by a 8 byte long, with cookie nr.

    private String filename;
    private File file;
    private File stopFile;
    private RandomAccessFile rwfile;
    private FileChannel channel;
    private FileLock lock;

    private boolean containsPortInfo;
    private int serverPort;
    private long serverCookie;
    private int myServerPort;
    private long myServerCookie;

    /**
     * Create a new portfile.
     * @param filename is the path to the file.
     */
    public PortFile(String fn) throws FileNotFoundException
    {
        filename = fn;
        file = new File(filename);
        stopFile = new File(filename+".stop");
        rwfile = new RandomAccessFile(file, "rw");
        // The rwfile should only be readable by the owner of the process
        // and no other! How do we do that on a RandomAccessFile?
        channel = rwfile.getChannel();
        containsPortInfo = false;
        lock = null;
    }

    /**
     * Lock the port file.
     */
    void lock() throws IOException {
        lock = channel.lock();
    }

    /**
     * Read the values from the port file in the file system.
     * Expects the port file to be locked.
     */
    public void getValues()  {
        containsPortInfo = false;
        if (lock == null) {
            // Not locked, remain ignorant about port file contents.
            return;
        }
        try {
            if (rwfile.length()>0) {
                rwfile.seek(0);
                int nr = rwfile.readInt();
                serverPort = rwfile.readInt();
                serverCookie = rwfile.readLong();

                if (nr == magicNr) {
                    containsPortInfo = true;
                } else {
                    containsPortInfo = false;
                }
            }
        } catch (Exception e) {
            containsPortInfo = false;
        }
    }

    /**
     * Did the locking and getValues succeed?
     */
    public boolean containsPortInfo() {
        return containsPortInfo;
    }

    /**
     * If so, then we can acquire the tcp/ip port on localhost.
     */
    public int getPort() {
        assert(containsPortInfo);
        return serverPort;
    }

    /**
     * If so, then we can acquire the server cookie.
     */
    public long getCookie() {
        assert(containsPortInfo);
        return serverCookie;
    }

    /**
     * Store the values into the locked port file.
     */
    public void setValues(int port, long cookie) throws IOException {
        assert(lock != null);
        rwfile.seek(0);
        // Write the magic nr that identifes a port file.
        rwfile.writeInt(magicNr);
        rwfile.writeInt(port);
        rwfile.writeLong(cookie);
        myServerPort = port;
        myServerCookie = cookie;
    }

    /**
     * Delete the port file.
     */
    public void delete() throws IOException {
        // Access to file must be closed before deleting.
        rwfile.close();
        // Now delete.
        file.delete();
    }

    /**
     * Is the port file still there?
     */
    public boolean exists() throws IOException {
        return file.exists();
    }

    /**
     * Is a stop file there?
     */
    public boolean markedForStop() throws IOException {
        if (stopFile.exists()) {
            try {
                stopFile.delete();
            } catch (Exception e)
            {}
            return true;
        }
        return false;
    }

    /**
     * Unlock the port file.
     */
    public void unlock() throws IOException {
        assert(lock != null);
        lock.release();
        lock = null;
    }

    /**
     * Wait for the port file to contain values that look valid.
     * Return true, if a-ok, false if the valid values did not materialize within 5 seconds.
     */
    public synchronized boolean waitForValidValues() throws IOException, FileNotFoundException {
        for (int tries = 0; tries < 50; tries++) {
            lock();
            getValues();
            unlock();
            if (containsPortInfo) {
                Log.debug("Found valid values in port file after waiting "+(tries*100)+"ms");
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e)
            {}
        }
        Log.debug("Gave up waiting for valid values in port file");
        return false;
    }

    /**
     * Check if the portfile still contains my values, assuming that I am the server.
     */
    public synchronized boolean stillMyValues() throws IOException, FileNotFoundException {
        for (;;) {
            try {
                lock();
                getValues();
                unlock();
                if (containsPortInfo) {
                    if (serverPort == myServerPort &&
                        serverCookie == myServerCookie) {
                        // Everything is ok.
                        return true;
                    }
                    // Someone has overwritten the port file.
                    // Probably another javac server, lets quit.
                    return false;
                }
                // Something else is wrong with the portfile. Lets quit.
                return false;
            } catch (FileLockInterruptionException e) {
                continue;
            }
            catch (ClosedChannelException e) {
                // The channel has been closed since sjavac is exiting.
                return false;
            }
        }
    }

    /**
     * Return the name of the port file.
     */
    public String getFilename() {
        return filename;
    }
}
