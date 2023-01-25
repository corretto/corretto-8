/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4204897 4256097 4785453 4863609
 * @summary Test that '.jar' files in -extdirs are found.
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main ExtDirTest
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

//original test: test/tools/javac/ExtDirs/ExtDirs.sh
public class ExtDirTest {

    private static final String ExtDirTestClass1Src =
        "package pkg1;\n" +
        "\n" +
        "public class ExtDirTestClass1 {}";

    private static final String ExtDirTestClass2Src =
        "package pkg2;\n" +
        "\n" +
        "public class ExtDirTestClass2 {}";

    private static final String ExtDirTest_1Src =
        "import pkg1.*;\n" +
        "\n" +
        "public class ExtDirTest_1 {\n" +
        "  ExtDirTestClass1 x;\n" +
        "}";

    private static final String ExtDirTest_2Src =
        "import pkg1.*;\n" +
        "import pkg2.*;\n" +
        "\n" +
        "public class ExtDirTest_2 {\n" +
        "  ExtDirTestClass1 x;\n" +
        "  ExtDirTestClass2 y;\n" +
        "}";

    private static final String ExtDirTest_3Src =
        "import pkg1.*;\n" +
        "import pkg2.*;\n" +
        "\n" +
        "public class ExtDirTest_3 {\n" +
        "  ExtDirTestClass1 x;\n" +
        "  ExtDirTestClass2 y;\n" +
        "}";

    private static final String jar1Manifest =
        "Manifest-Version: 1.0\n" +
        "\n" +
        "Name: pkg1/ExtDirTestClass1.class\n" +
        "Digest-Algorithms: SHA MD5 \n" +
        "SHA-Digest: 9HEcO9LJmND3cvOlq/AbUsbD9S0=\n" +
        "MD5-Digest: hffPBwfqcUcnEdNv4PXu1Q==\n" +
        "\n" +
        "Name: pkg1/ExtDirTestClass1.java\n" +
        "Digest-Algorithms: SHA MD5 \n" +
        "SHA-Digest: 2FQVe6w3n2Ma1ACYpe8a988EBU8=\n" +
        "MD5-Digest: /Ivr4zVI9MSM26NmqWtZpQ==\n";

    private static final String jar2Manifest =
        "Manifest-Version: 1.0\n" +
        "\n" +
        "Name: pkg2/ExtDirTestClass2.class\n" +
        "Digest-Algorithms: SHA MD5 \n" +
        "SHA-Digest: elbPaqWf8hjj1+ZkkdW3PGTsilo=\n" +
        "MD5-Digest: 57Nn0e2t1yEQfu/4kSw8yg==\n" +
        "\n" +
        "Name: pkg2/ExtDirTestClass2.java\n" +
        "Digest-Algorithms: SHA MD5 \n" +
        "SHA-Digest: ILJOhwHg5US+yuw1Sc1d+Avu628=\n" +
        "MD5-Digest: j8wnz8wneEcuJ/gjXBBQNA==\n";

    List<String> ouputDirParam = Arrays.asList("-d", ".");

    public static void main(String args[]) throws Exception {
        new ExtDirTest().run();
    }

    void run() throws Exception {
        createJars();
        compileWithExtDirs();
    }

    void createJars() throws Exception {
        sun.tools.jar.Main jarGenerator =
                new sun.tools.jar.Main(System.out, System.err, "jar");

        ToolBox.JavaToolArgs javacParams =
                new ToolBox.JavaToolArgs()
                .setOptions(ouputDirParam)
                .setSources(ExtDirTestClass1Src);
        ToolBox.javac(javacParams);

        ToolBox.writeFile(Paths.get("pkg1", "MANIFEST.MF"), jar1Manifest);
        jarGenerator.run(new String[] {"cfm", "pkg1.jar", "pkg1/MANIFEST.MF",
            "pkg1/ExtDirTestClass1.class"});

        javacParams.setSources(ExtDirTestClass2Src);
        ToolBox.javac(javacParams);

        ToolBox.writeFile(Paths.get("pkg2", "MANIFEST.MF"), jar2Manifest);
        jarGenerator.run(new String[] {"cfm", "pkg2.jar", "pkg2/MANIFEST.MF",
            "pkg2/ExtDirTestClass2.class"});

        ToolBox.copyFile(Paths.get("ext1", "pkg1.jar"), Paths.get("pkg1.jar"));
        ToolBox.copyFile(Paths.get("ext2", "pkg2.jar"), Paths.get("pkg2.jar"));
        ToolBox.copyFile(Paths.get("ext3", "pkg1.jar"), Paths.get("pkg1.jar"));
        ToolBox.copyFile(Paths.get("ext3", "pkg2.jar"), Paths.get("pkg2.jar"));

        Files.delete(Paths.get("pkg1.jar"));
        Files.delete(Paths.get("pkg2.jar"));

        Files.delete(Paths.get("pkg1", "ExtDirTestClass1.class"));
        Files.delete(Paths.get("pkg1", "MANIFEST.MF"));
        Files.delete(Paths.get("pkg1"));
        Files.delete(Paths.get("pkg2", "ExtDirTestClass2.class"));
        Files.delete(Paths.get("pkg2", "MANIFEST.MF"));
        Files.delete(Paths.get("pkg2"));
    }

    void compileWithExtDirs() throws Exception {

//javac -extdirs ext1 ExtDirTest_1.java
        ToolBox.JavaToolArgs params =
                new ToolBox.JavaToolArgs()
                .setOptions("-d", ".", "-extdirs", "ext1")
                .setSources(ExtDirTest_1Src);
        ToolBox.javac(params);

//javac -extdirs ext1:ext2 ExtDirTest_2.java
        params.setOptions("-d", ".", "-extdirs", "ext1" + File.pathSeparator + "ext2")
                .setSources(ExtDirTest_2Src);
        ToolBox.javac(params);

//javac -extdirs ext3 ExtDirTest_3.java
        params.setOptions("-d", ".", "-extdirs", "ext3")
                .setSources(ExtDirTest_3Src);
        ToolBox.javac(params);
    }

}
