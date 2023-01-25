/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.io.IOException;

import jdk.jfr.events.FileReadEvent;
import jdk.jfr.events.FileWriteEvent;

/**
 * See {@link JITracer} for an explanation of this code.
 */
@JIInstrumentationTarget("java.io.RandomAccessFile")
final class RandomAccessFileInstrumentor {

    private RandomAccessFileInstrumentor() {
    }

    private String path;

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int read() throws IOException {
        FileReadEvent event = FileReadEvent.EVENT.get();
        if (!event.isEnabled()) {
            return read();
        }
        int result = 0;
        try {
            event.begin();
            result = read();
            if (result < 0) {
                event.endOfFile = true;
            } else {
                event.bytesRead = 1;
            }
        } finally {
            event.path = path;
            event.commit();
            event.reset();
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int read(byte b[]) throws IOException {
        FileReadEvent event = FileReadEvent.EVENT.get();
        if (!event.isEnabled()) {
            return read(b);
        }
        int bytesRead = 0;
        try {
            event.begin();
            bytesRead = read(b);
        } finally {
            if (bytesRead < 0) {
                event.endOfFile = true;
            } else {
                event.bytesRead = bytesRead;
            }
            event.path = path;
            event.commit();
            event.reset();
        }
        return bytesRead;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int read(byte b[], int off, int len) throws IOException {
        FileReadEvent event = FileReadEvent.EVENT.get();
        if (!event.isEnabled()) {
            return read(b, off, len);
        }
        int bytesRead = 0;
        try {
            event.begin();
            bytesRead = read(b, off, len);
        } finally {
            if (bytesRead < 0) {
                event.endOfFile = true;
            } else {
                event.bytesRead = bytesRead;
            }
            event.path = path;
            event.commit();
            event.reset();
        }
        return bytesRead;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public void write(int b) throws IOException {
        FileWriteEvent event = FileWriteEvent.EVENT.get();
        if (!event.isEnabled()) {
            write(b);
            return;
        }
        try {
            event.begin();
            write(b);
            event.bytesWritten = 1;
        } finally {
            event.path = path;
            event.commit();
            event.reset();
        }
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public void write(byte b[]) throws IOException {
        FileWriteEvent event = FileWriteEvent.EVENT.get();
        if (!event.isEnabled()) {
            write(b);
            return;
        }
        try {
            event.begin();
            write(b);
            event.bytesWritten = b.length;
        } finally {
            event.path = path;
            event.commit();
            event.reset();
        }
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public void write(byte b[], int off, int len) throws IOException {
        FileWriteEvent event = FileWriteEvent.EVENT.get();
        if (!event.isEnabled()) {
            write(b, off, len);
            return;
        }
        try {
            event.begin();
            write(b, off, len);
            event.bytesWritten = len;
        } finally {
            event.path = path;
            event.commit();
            event.reset();
        }
    }

}
