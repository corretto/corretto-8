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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;

/**
 * This class consists exclusively of static utility methods for invoking the
 * java compiler.
 */

public final class CompilerUtils {
    private static final String TEST_JAVA_HOME = System.getProperty("test.jdk");
    private static final String FS = File.separator;
    private static final String JAVAC = TEST_JAVA_HOME + FS + "bin" + FS + "javac";

    private CompilerUtils() { }

    /**
     * Compile all the java sources in {@code <source>/**} to
     * {@code <destination>/**}. The destination directory will be created if
     * it doesn't exist.
     *
     * All warnings/errors emitted by the compiler are output to System.out/err.
     *
     * @return true if the compilation is successful
     *
     * @throws IOException
     *         if there is an I/O error scanning the source tree or
     *         creating the destination directory
     * @throws UnsupportedOperationException
     *         if there is no system java compiler
     */
    public static boolean compile(Path source, Path destination, String ... options)
        throws IOException
    {
        List<Path> sources
            = Files.find(source, Integer.MAX_VALUE,
                (file, attrs) -> (file.toString().endsWith(".java")))
                .collect(Collectors.toList());

        Files.createDirectories(destination);
        List<String> opts = Arrays.asList(options);
        List<String> compileargs = new ArrayList<String> ();
        compileargs.add(JAVAC);
        compileargs.add("-d");
        compileargs.add(destination.toString());
        for(String opt: opts) {
            compileargs.add(opt);
        }
        for(Path p: sources) {
            compileargs.add(p.toString());
        }

        OutputAnalyzer output = null;
        try {
            String[] sarr = compileargs.toArray(new String[0]);
            output = ProcessTools.executeCommand(sarr);
            output.shouldHaveExitValue(0);
        } catch (Throwable t) {
            throw new RuntimeException("Javac failed", t);
        }

        return true;
    }
}
