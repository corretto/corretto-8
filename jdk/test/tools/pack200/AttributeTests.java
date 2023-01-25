/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/*
 * @test
 * @bug 6746111 8005252 8008262
 * @summary tests various classfile format and attribute handling by pack200
 * @compile -XDignore.symbol.file Utils.java AttributeTests.java
 * @run main AttributeTests
 * @author ksrini
 */
public class AttributeTests {

    public static void main(String... args) throws Exception {
        test6746111();
        testMethodParameters();
        Utils.cleanup();
    }

    /*
     * this tests ensure that MethodParameters produces by javac is packed
     * correctly. Usually this is not the case as new attributes are available
     * in the sdk jars, since MethodParameters happens to be an optional
     * attribute, thus this test.
     */
    static void testMethodParameters() throws Exception {
        List<String> scratch = new ArrayList<>();
        final String fname = "MP";
        String javaFileName = fname + Utils.JAVA_FILE_EXT;
        String javaClassName = fname + Utils.CLASS_FILE_EXT;
        scratch.add("class " + fname + " {");
        scratch.add("void foo2(int j, final int k){}");
        scratch.add("}");
        File cwd = new File(".");
        File javaFile = new File(cwd, javaFileName);
        Utils.createFile(javaFile, scratch);

        Utils.compiler(javaFile.getName(), "-parameters");

        // jar the file up
        File testjarFile = new File(cwd, "test" + Utils.JAR_FILE_EXT);
        Utils.jar("cvf", testjarFile.getName(), javaClassName);

        Utils.testWithRepack(testjarFile, "--unknown-attribute=error");
    }
    /*
     * this test checks to see if we get the expected strings for output
     */
    static void test6746111() throws Exception {
        String pack200Cmd = Utils.getPack200Cmd();
        File badAttrJar = new File(".", "badattr.jar");
        Utils.copyFile(new File(Utils.TEST_SRC_DIR, "badattr.jar"), badAttrJar);
        File testJar = new File(".", "test.jar");
        List<String> cmds = new ArrayList<String>();
        cmds.add(pack200Cmd);
        cmds.add("--repack");
        cmds.add("-v");
        cmds.add(testJar.getAbsolutePath());
        cmds.add(badAttrJar.getAbsolutePath());
        List<String> output = Utils.runExec(cmds);
        /*
         * compare the repacked jar bit-wise, as all the files
         * should be transmitted "as-is".
         */
        Utils.doCompareBitWise(badAttrJar.getAbsoluteFile(), testJar.getAbsoluteFile());
        String[] expectedStrings = {
            "WARNING: Passing class file uncompressed due to unrecognized" +
                    " attribute: Foo.class",
            "INFO: com.sun.java.util.jar.pack.Attribute$FormatException: " +
                    "class attribute \"XourceFile\":  is unknown attribute " +
                    "in class Foo",
            "INFO: com.sun.java.util.jar.pack.ClassReader$ClassFormatException: " +
                    "AnnotationDefault: attribute length cannot be zero, in Test.message()",
            "WARNING: Passing class file uncompressed due to unknown class format: Test.class"
        };
        List<String> notfoundList = new ArrayList<String>();
        notfoundList.addAll(Arrays.asList(expectedStrings));
        // make sure the expected messages are emitted
        for (String x : output) {
            findString(x, notfoundList, expectedStrings);
        }
        if (!notfoundList.isEmpty()) {
            System.out.println("Not found:");
            for (String x : notfoundList) {
                System.out.println(x);
            }
            throw new Exception("Test fails: " + notfoundList.size() +
                    " expected strings not found");
        }
        testJar.delete();
        badAttrJar.delete();
    }

    private static void findString(String outputStr, List<String> notfoundList,
            String[] expectedStrings) {
        for (String y : expectedStrings) {
            if (outputStr.contains(y)) {
                notfoundList.remove(y);
                return;
            }
        }
    }
}
