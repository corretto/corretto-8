/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6856415
 * @summary Miscellaneous tests, Exceptions
 * @compile -XDignore.symbol.file MiscTests.java
 * @run main MiscTests
 */


import java.io.File;
import java.io.FileNotFoundException;

public class MiscTests extends TestHelper {

    // 6856415: Checks to ensure that proper exceptions are thrown by java
    static void test6856415() {
        // No pkcs library on win-x64, so we bail out.
        if (is64Bit && isWindows) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("public static void main(String... args) {\n");
        sb.append("java.security.Provider p = new sun.security.pkcs11.SunPKCS11(args[0]);\n");
        sb.append("java.security.Security.insertProviderAt(p, 1);\n");
        sb.append("}");
        File testJar = new File("Foo.jar");
        testJar.delete();
        try {
            createJar(testJar, sb.toString());
        } catch (FileNotFoundException fnfe) {
            throw new RuntimeException(fnfe);
        }
        TestResult tr = doExec(javaCmd,
                "-Djava.security.manager", "-jar", testJar.getName(), "foo.bak");
        for (String s : tr.testOutput) {
            System.out.println(s);
    }
        if (!tr.contains("java.security.AccessControlException:" +
                " access denied (\"java.lang.RuntimePermission\"" +
                " \"accessClassInPackage.sun.security.pkcs11\")")) {
            System.out.println(tr.status);
        }
    }

    public static void main(String... args) {
        test6856415();
        if (testExitValue != 0) {
            throw new Error(testExitValue + " tests failed");
    }
}
}
