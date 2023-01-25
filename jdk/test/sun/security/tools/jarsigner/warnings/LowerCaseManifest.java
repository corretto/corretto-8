/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.JarUtils;

import java.nio.file.*;
import java.security.Security;
import java.util.Collections;


/**
 * @test
 * @bug 8273826
 * @summary Test for signed jar file with lowercase META-INF files
 * @library /lib/testlibrary ../
 * @build jdk.testlibrary.JarUtils
 * @run main LowerCaseManifest
 */
public class LowerCaseManifest extends Test {

    public static void main(String[] args) throws Throwable {
        new LowerCaseManifest().start();
    }

    private void start() throws Throwable {
        // create a jar file that contains one class file
        Utils.createFiles(FIRST_FILE);
        JarUtils.createJar(UNSIGNED_JARFILE, FIRST_FILE);

        // create key pair for jar signing
        createAlias(CA_KEY_ALIAS, "-ext", "bc:c");
        createAlias(KEY_ALIAS);

        issueCert(KEY_ALIAS);

        // sign jar
        OutputAnalyzer analyzer = jarsigner(
                "-keystore", KEYSTORE,
                "-verbose",
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-signedjar", SIGNED_JARFILE,
                UNSIGNED_JARFILE,
                KEY_ALIAS);

        checkSigning(analyzer);

        // verify signed jar
        analyzer = jarsigner(
                "-verify",
                "-verbose",
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                SIGNED_JARFILE,
                KEY_ALIAS);

        checkVerifying(analyzer, 0, JAR_VERIFIED);

        // verify signed jar in strict mode
        analyzer = jarsigner(
                "-verify",
                "-verbose",
                "-strict",
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                SIGNED_JARFILE,
                KEY_ALIAS);

        checkVerifying(analyzer, 0, JAR_VERIFIED);

        // convert the META-INF/ files to lower case
        FileSystem fs = FileSystems.newFileSystem(Paths.get(SIGNED_JARFILE), null);
        for (String s : new String[]{"ALIAS.SF",  "ALIAS.RSA", "MANIFEST.MF"}) {
            Path origPath = fs.getPath("META-INF/" + s);
            Path lowerCase = fs.getPath("META-INF/" + s.toLowerCase());
            Files.write(lowerCase, Files.readAllBytes(origPath));
            Files.delete(origPath);
        }
        fs.close();

        // verify signed jar in strict mode (with lower case META-INF names in place)
        analyzer = jarsigner(
                "-verify",
                "-verbose",
                "-strict",
                "-J-Djava.security.debug=jar",
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                SIGNED_JARFILE,
                KEY_ALIAS);

        checkVerifying(analyzer, 0,
                JAR_VERIFIED, "!not present in verifiedSigners");
        System.out.println("Test passed");
    }

}
