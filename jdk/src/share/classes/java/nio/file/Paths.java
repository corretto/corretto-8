/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.file;

import java.nio.file.spi.FileSystemProvider;
import java.net.URI;

/**
 * This class consists exclusively of static methods that return a {@link Path}
 * by converting a path string or {@link URI}.
 *
 * @since 1.7
 */

public final class Paths {
    private Paths() { }

    /**
     * Converts a path string, or a sequence of strings that when joined form
     * a path string, to a {@code Path}. If {@code more} does not specify any
     * elements then the value of the {@code first} parameter is the path string
     * to convert. If {@code more} specifies one or more elements then each
     * non-empty string, including {@code first}, is considered to be a sequence
     * of name elements (see {@link Path}) and is joined to form a path string.
     * The details as to how the Strings are joined is provider specific but
     * typically they will be joined using the {@link FileSystem#getSeparator
     * name-separator} as the separator. For example, if the name separator is
     * "{@code /}" and {@code getPath("/foo","bar","gus")} is invoked, then the
     * path string {@code "/foo/bar/gus"} is converted to a {@code Path}.
     * A {@code Path} representing an empty path is returned if {@code first}
     * is the empty string and {@code more} does not contain any non-empty
     * strings.
     *
     * <p> The {@code Path} is obtained by invoking the {@link FileSystem#getPath
     * getPath} method of the {@link FileSystems#getDefault default} {@link
     * FileSystem}.
     *
     * <p> Note that while this method is very convenient, using it will imply
     * an assumed reference to the default {@code FileSystem} and limit the
     * utility of the calling code. Hence it should not be used in library code
     * intended for flexible reuse. A more flexible alternative is to use an
     * existing {@code Path} instance as an anchor, such as:
     * <pre>
     *     Path dir = ...
     *     Path path = dir.resolve("file");
     * </pre>
     *
     * @param   first
     *          the path string or initial part of the path string
     * @param   more
     *          additional strings to be joined to form the path string
     *
     * @return  the resulting {@code Path}
     *
     * @throws  InvalidPathException
     *          if the path string cannot be converted to a {@code Path}
     *
     * @see FileSystem#getPath
     */
    public static Path get(String first, String... more) {
        return FileSystems.getDefault().getPath(first, more);
    }

    /**
     * Converts the given URI to a {@link Path} object.
     *
     * <p> This method iterates over the {@link FileSystemProvider#installedProviders()
     * installed} providers to locate the provider that is identified by the
     * URI {@link URI#getScheme scheme} of the given URI. URI schemes are
     * compared without regard to case. If the provider is found then its {@link
     * FileSystemProvider#getPath getPath} method is invoked to convert the
     * URI.
     *
     * <p> In the case of the default provider, identified by the URI scheme
     * "file", the given URI has a non-empty path component, and undefined query
     * and fragment components. Whether the authority component may be present
     * is platform specific. The returned {@code Path} is associated with the
     * {@link FileSystems#getDefault default} file system.
     *
     * <p> The default provider provides a similar <em>round-trip</em> guarantee
     * to the {@link java.io.File} class. For a given {@code Path} <i>p</i> it
     * is guaranteed that
     * <blockquote><tt>
     * Paths.get(</tt><i>p</i><tt>.{@link Path#toUri() toUri}()).equals(</tt>
     * <i>p</i><tt>.{@link Path#toAbsolutePath() toAbsolutePath}())</tt>
     * </blockquote>
     * so long as the original {@code Path}, the {@code URI}, and the new {@code
     * Path} are all created in (possibly different invocations of) the same
     * Java virtual machine. Whether other providers make any guarantees is
     * provider specific and therefore unspecified.
     *
     * @param   uri
     *          the URI to convert
     *
     * @return  the resulting {@code Path}
     *
     * @throws  IllegalArgumentException
     *          if preconditions on the {@code uri} parameter do not hold. The
     *          format of the URI is provider specific.
     * @throws  FileSystemNotFoundException
     *          The file system, identified by the URI, does not exist and
     *          cannot be created automatically, or the provider identified by
     *          the URI's scheme component is not installed
     * @throws  SecurityException
     *          if a security manager is installed and it denies an unspecified
     *          permission to access the file system
     */
    public static Path get(URI uri) {
        String scheme =  uri.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Missing scheme");

        // check for default provider to avoid loading of installed providers
        if (scheme.equalsIgnoreCase("file"))
            return FileSystems.getDefault().provider().getPath(uri);

        // try to find provider
        for (FileSystemProvider provider: FileSystemProvider.installedProviders()) {
            if (provider.getScheme().equalsIgnoreCase(scheme)) {
                return provider.getPath(uri);
            }
        }

        throw new FileSystemNotFoundException("Provider \"" + scheme + "\" not installed");
    }
}
