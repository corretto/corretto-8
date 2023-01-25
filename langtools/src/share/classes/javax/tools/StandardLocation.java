/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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

package javax.tools;

import javax.tools.JavaFileManager.Location;

import java.util.concurrent.*;

/**
 * Standard locations of file objects.
 *
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */
public enum StandardLocation implements Location {

    /**
     * Location of new class files.
     */
    CLASS_OUTPUT,

    /**
     * Location of new source files.
     */
    SOURCE_OUTPUT,

    /**
     * Location to search for user class files.
     */
    CLASS_PATH,

    /**
     * Location to search for existing source files.
     */
    SOURCE_PATH,

    /**
     * Location to search for annotation processors.
     */
    ANNOTATION_PROCESSOR_PATH,

    /**
     * Location to search for platform classes.  Sometimes called
     * the boot class path.
     */
    PLATFORM_CLASS_PATH,

    /**
     * Location of new native header files.
     * @since 1.8
     */
    NATIVE_HEADER_OUTPUT;

    /**
     * Gets a location object with the given name.  The following
     * property must hold: {@code locationFor(x) ==
     * locationFor(y)} if and only if {@code x.equals(y)}.
     * The returned location will be an output location if and only if
     * name ends with {@code "_OUTPUT"}.
     *
     * @param name a name
     * @return a location
     */
    public static Location locationFor(final String name) {
        if (locations.isEmpty()) {
            // can't use valueOf which throws IllegalArgumentException
            for (Location location : values())
                locations.putIfAbsent(location.getName(), location);
        }
        locations.putIfAbsent(name.toString(/* null-check */), new Location() {
                public String getName() { return name; }
                public boolean isOutputLocation() { return name.endsWith("_OUTPUT"); }
            });
        return locations.get(name);
    }
    //where
        private static final ConcurrentMap<String,Location> locations
            = new ConcurrentHashMap<String,Location>();

    public String getName() { return name(); }

    public boolean isOutputLocation() {
        switch (this) {
            case CLASS_OUTPUT:
            case SOURCE_OUTPUT:
            case NATIVE_HEADER_OUTPUT:
                return true;
            default:
                return false;
        }
    }
}
