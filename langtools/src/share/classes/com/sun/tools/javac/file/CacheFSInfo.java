/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.file;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.tools.javac.util.Context;

/**
 * Caching implementation of FSInfo.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class CacheFSInfo extends FSInfo {

    /**
     * Register a Context.Factory to create a CacheFSInfo.
     */
    public static void preRegister(Context context) {
        context.put(FSInfo.class, new Context.Factory<FSInfo>() {
            public FSInfo make(Context c) {
                FSInfo instance = new CacheFSInfo();
                c.put(FSInfo.class, instance);
                return instance;
            }
        });
    }

    public void clearCache() {
        cache.clear();
    }

    @Override
    public File getCanonicalFile(File file) {
        Entry e = getEntry(file);
        return e.canonicalFile;
    }

    @Override
    public boolean exists(File file) {
        Entry e = getEntry(file);
        return e.exists;
    }

    @Override
    public boolean isDirectory(File file) {
        Entry e = getEntry(file);
        return e.isDirectory;
    }

    @Override
    public boolean isFile(File file) {
        Entry e = getEntry(file);
        return e.isFile;
    }

    @Override
    public List<File> getJarClassPath(File file) throws IOException {
        // don't bother to lock the cache, because it is thread-safe, and
        // because the worst that can happen would be to create two identical
        // jar class paths together and have one overwrite the other.
        Entry e = getEntry(file);
        if (e.jarClassPath == null)
            e.jarClassPath = super.getJarClassPath(file);
        return e.jarClassPath;
    }

    private Entry getEntry(File file) {
        // don't bother to lock the cache, because it is thread-safe, and
        // because the worst that can happen would be to create two identical
        // entries together and have one overwrite the other.
        Entry e = cache.get(file);
        if (e == null) {
            e = new Entry();
            e.canonicalFile = super.getCanonicalFile(file);
            e.exists = super.exists(file);
            e.isDirectory = super.isDirectory(file);
            e.isFile = super.isFile(file);
            cache.put(file, e);
        }
        return e;
    }

    // could also be a Map<File,SoftReference<Entry>> ?
    private Map<File,Entry> cache = new ConcurrentHashMap<File,Entry>();

    private static class Entry {
        File canonicalFile;
        boolean exists;
        boolean isFile;
        boolean isDirectory;
        List<File> jarClassPath;
    }
}
