/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Common library for various test jar file utility functions.
 */
public final class JarUtils {

    /**
     * Create jar file with specified files. If a specified file does not exist,
     * a new jar entry will be created with the file name itself as the content.
     */
    public static void createJar(String dest, String... files)
            throws IOException {
        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(dest), new Manifest())) {
            for (String file : files) {
                System.out.println(String.format("Adding %s to %s",
                        file, dest));

                // add an archive entry, and write a file
                jos.putNextEntry(new JarEntry(file));
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.transferTo(jos);
                } catch (FileNotFoundException e) {
                    jos.write(file.getBytes());
                }
            }
        }
        System.out.println();
    }

    /**
     * Add or remove specified files to existing jar file. If a specified file
     * to be updated or added does not exist, the jar entry will be created
     * with the file name itself as the content.
     *
     * @param src the original jar file name
     * @param dest the new jar file name
     * @param files the files to update. The list is broken into 2 groups
     *              by a "-" string. The files before in the 1st group will
     *              be either updated or added. The files in the 2nd group
     *              will be removed. If no "-" exists, all files belong to
     *              the 1st group.
     */
    public static void updateJar(String src, String dest, String... files)
            throws IOException {
        Map<String,Object> changes = new HashMap<>();
        boolean update = true;
        for (String file : files) {
            if (file.equals("-")) {
                update = false;
            } else if (update) {
                try {
                    Path p = Paths.get(file);
                    if (Files.exists(p)) {
                        changes.put(file, p);
                    } else {
                        changes.put(file, file);
                    }
                } catch (InvalidPathException e) {
                    // Fallback if file not a valid Path.
                    changes.put(file, file);
                }
            } else {
                changes.put(file, Boolean.FALSE);
            }
        }
        updateJar(src, dest, changes);
    }

    /**
     * Update content of a jar file.
     *
     * @param src the original jar file name
     * @param dest the new jar file name
     * @param changes a map of changes, key is jar entry name, value is content.
     *                Value can be Path, byte[] or String. If key exists in
     *                src but value is Boolean FALSE. The entry is removed.
     *                Existing entries in src not a key is unmodified.
     * @throws IOException
     */
    public static void updateJar(String src, String dest,
                                 Map<String,Object> changes)
            throws IOException {

        // What if input changes is immutable?
        changes = new HashMap<>(changes);

        System.out.printf("Creating %s from %s...\n", dest, src);
        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(dest))) {

            try (JarFile srcJarFile = new JarFile(src)) {
                Enumeration<JarEntry> entries = srcJarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (changes.containsKey(name)) {
                        System.out.println(String.format("- Update %s", name));
                        updateEntry(jos, name, changes.get(name));
                        changes.remove(name);
                    } else {
                        System.out.println(String.format("- Copy %s", name));
                        jos.putNextEntry(entry);
                        srcJarFile.getInputStream(entry).transferTo(jos);
                    }
                }
            }
            for (Map.Entry<String, Object> e : changes.entrySet()) {
                System.out.println(String.format("- Add %s", e.getKey()));
                updateEntry(jos, e.getKey(), e.getValue());
            }
        }
        System.out.println();
    }

    private static void updateEntry(JarOutputStream jos, String name, Object content)
           throws IOException {
        if (content instanceof Boolean) {
            if (((Boolean) content).booleanValue()) {
                throw new RuntimeException("Boolean value must be FALSE");
            }
        } else {
            jos.putNextEntry(new JarEntry(name));
            if (content instanceof Path) {
                Files.newInputStream((Path) content).transferTo(jos);
            } else if (content instanceof byte[]) {
                jos.write((byte[]) content);
            } else if (content instanceof String) {
                jos.write(((String) content).getBytes());
            } else {
                throw new RuntimeException("Unknown type " + content.getClass());
            }
        }
    }
}
