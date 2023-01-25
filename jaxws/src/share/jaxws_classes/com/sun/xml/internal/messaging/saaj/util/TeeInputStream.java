/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Created on Feb 28, 2003
 *
 * To change this generated comment go to
 * Window>Preferences>Java>Code Generation>Code Template
 */
package com.sun.xml.internal.messaging.saaj.util;

import java.io.*;

/**
 * @author pgoodwin
 */
public class TeeInputStream extends InputStream {
    protected InputStream source;
    protected OutputStream copySink;

    public TeeInputStream(InputStream source, OutputStream sink) {
        super();
        this.copySink = sink;
        this.source = source;
    }

    public int read() throws IOException {
        int result = source.read();
        copySink.write(result);
        return result;
    }

    public int available() throws IOException {
        return source.available();
    }

    public void close() throws IOException {
        source.close();
    }

    public synchronized void mark(int readlimit) {
        source.mark(readlimit);
    }

    public boolean markSupported() {
        return source.markSupported();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int result = source.read(b, off, len);
        copySink.write(b, off, len);
        return result;
    }

    public int read(byte[] b) throws IOException {
        int result = source.read(b);
        copySink.write(b);
        return result;
    }

    public synchronized void reset() throws IOException {
        source.reset();
    }

    public long skip(long n) throws IOException {
        return source.skip(n);
    }

}
