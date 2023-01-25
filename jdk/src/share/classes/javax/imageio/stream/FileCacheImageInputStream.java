/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.stream;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import com.sun.imageio.stream.StreamCloser;
import com.sun.imageio.stream.StreamFinalizer;
import sun.java2d.Disposer;
import sun.java2d.DisposerRecord;

/**
 * An implementation of <code>ImageInputStream</code> that gets its
 * input from a regular <code>InputStream</code>.  A file is used to
 * cache previously read data.
 *
 */
public class FileCacheImageInputStream extends ImageInputStreamImpl {

    private InputStream stream;

    private File cacheFile;

    private RandomAccessFile cache;

    private static final int BUFFER_LENGTH = 1024;

    private byte[] buf = new byte[BUFFER_LENGTH];

    private long length = 0L;

    private boolean foundEOF = false;

    /** The referent to be registered with the Disposer. */
    private final Object disposerReferent;

    /** The DisposerRecord that closes the underlying cache. */
    private final DisposerRecord disposerRecord;

    /** The CloseAction that closes the stream in
     *  the StreamCloser's shutdown hook                     */
    private final StreamCloser.CloseAction closeAction;

    /**
     * Constructs a <code>FileCacheImageInputStream</code> that will read
     * from a given <code>InputStream</code>.
     *
     * <p> A temporary file is used as a cache.  If
     * <code>cacheDir</code>is non-<code>null</code> and is a
     * directory, the file will be created there.  If it is
     * <code>null</code>, the system-dependent default temporary-file
     * directory will be used (see the documentation for
     * <code>File.createTempFile</code> for details).
     *
     * @param stream an <code>InputStream</code> to read from.
     * @param cacheDir a <code>File</code> indicating where the
     * cache file should be created, or <code>null</code> to use the
     * system directory.
     *
     * @exception IllegalArgumentException if <code>stream</code> is
     * <code>null</code>.
     * @exception IllegalArgumentException if <code>cacheDir</code> is
     * non-<code>null</code> but is not a directory.
     * @exception IOException if a cache file cannot be created.
     */
    public FileCacheImageInputStream(InputStream stream, File cacheDir)
        throws IOException {
        if (stream == null) {
            throw new IllegalArgumentException("stream == null!");
        }
        if ((cacheDir != null) && !(cacheDir.isDirectory())) {
            throw new IllegalArgumentException("Not a directory!");
        }
        this.stream = stream;
        if (cacheDir == null)
            this.cacheFile = Files.createTempFile("imageio", ".tmp").toFile();
        else
            this.cacheFile = Files.createTempFile(cacheDir.toPath(), "imageio", ".tmp")
                                  .toFile();
        this.cache = new RandomAccessFile(cacheFile, "rw");

        this.closeAction = StreamCloser.createCloseAction(this);
        StreamCloser.addToQueue(closeAction);

        disposerRecord = new StreamDisposerRecord(cacheFile, cache);
        if (getClass() == FileCacheImageInputStream.class) {
            disposerReferent = new Object();
            Disposer.addRecord(disposerReferent, disposerRecord);
        } else {
            disposerReferent = new StreamFinalizer(this);
        }
    }

    /**
     * Ensures that at least <code>pos</code> bytes are cached,
     * or the end of the source is reached.  The return value
     * is equal to the smaller of <code>pos</code> and the
     * length of the source file.
     */
    private long readUntil(long pos) throws IOException {
        // We've already got enough data cached
        if (pos < length) {
            return pos;
        }
        // pos >= length but length isn't getting any bigger, so return it
        if (foundEOF) {
            return length;
        }

        long len = pos - length;
        cache.seek(length);
        while (len > 0) {
            // Copy a buffer's worth of data from the source to the cache
            // BUFFER_LENGTH will always fit into an int so this is safe
            int nbytes =
                stream.read(buf, 0, (int)Math.min(len, (long)BUFFER_LENGTH));
            if (nbytes == -1) {
                foundEOF = true;
                return length;
            }

            cache.write(buf, 0, nbytes);
            len -= nbytes;
            length += nbytes;
        }

        return pos;
    }

    public int read() throws IOException {
        checkClosed();
        bitOffset = 0;
        long next = streamPos + 1;
        long pos = readUntil(next);
        if (pos >= next) {
            cache.seek(streamPos++);
            return cache.read();
        } else {
            return -1;
        }
    }

    public int read(byte[] b, int off, int len) throws IOException {
        checkClosed();

        if (b == null) {
            throw new NullPointerException("b == null!");
        }
        // Fix 4430357 - if off + len < 0, overflow occurred
        if (off < 0 || len < 0 || off + len > b.length || off + len < 0) {
            throw new IndexOutOfBoundsException
                ("off < 0 || len < 0 || off+len > b.length || off+len < 0!");
        }

        bitOffset = 0;

        if (len == 0) {
            return 0;
        }

        long pos = readUntil(streamPos + len);

        // len will always fit into an int so this is safe
        len = (int)Math.min((long)len, pos - streamPos);
        if (len > 0) {
            cache.seek(streamPos);
            cache.readFully(b, off, len);
            streamPos += len;
            return len;
        } else {
            return -1;
        }
    }

    /**
     * Returns <code>true</code> since this
     * <code>ImageInputStream</code> caches data in order to allow
     * seeking backwards.
     *
     * @return <code>true</code>.
     *
     * @see #isCachedMemory
     * @see #isCachedFile
     */
    public boolean isCached() {
        return true;
    }

    /**
     * Returns <code>true</code> since this
     * <code>ImageInputStream</code> maintains a file cache.
     *
     * @return <code>true</code>.
     *
     * @see #isCached
     * @see #isCachedMemory
     */
    public boolean isCachedFile() {
        return true;
    }

    /**
     * Returns <code>false</code> since this
     * <code>ImageInputStream</code> does not maintain a main memory
     * cache.
     *
     * @return <code>false</code>.
     *
     * @see #isCached
     * @see #isCachedFile
     */
    public boolean isCachedMemory() {
        return false;
    }

    /**
     * Closes this <code>FileCacheImageInputStream</code>, closing
     * and removing the cache file.  The source <code>InputStream</code>
     * is not closed.
     *
     * @exception IOException if an error occurs.
     */
    public void close() throws IOException {
        super.close();
        disposerRecord.dispose(); // this will close/delete the cache file
        stream = null;
        cache = null;
        cacheFile = null;
        StreamCloser.removeFromQueue(closeAction);
    }

    /**
     * {@inheritDoc}
     */
    protected void finalize() throws Throwable {
        // Empty finalizer: for performance reasons we instead use the
        // Disposer mechanism for ensuring that the underlying
        // RandomAccessFile is closed/deleted prior to garbage collection
    }

    private static class StreamDisposerRecord implements DisposerRecord {
        private File cacheFile;
        private RandomAccessFile cache;

        public StreamDisposerRecord(File cacheFile, RandomAccessFile cache) {
            this.cacheFile = cacheFile;
            this.cache = cache;
        }

        public synchronized void dispose() {
            if (cache != null) {
                try {
                    cache.close();
                } catch (IOException e) {
                } finally {
                    cache = null;
                }
            }
            if (cacheFile != null) {
                cacheFile.delete();
                cacheFile = null;
            }
            // Note: Explicit removal of the stream from the StreamCloser
            // queue is not mandatory in this case, as it will be removed
            // automatically by GC shortly after this method is called.
        }
    }
}
