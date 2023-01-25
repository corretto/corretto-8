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
 * @bug 8013827 8011950 8017212 8025128
 * @summary Check whether File.createTempFile can handle special parameters
 * @author Dan Xu
 */

import java.io.File;
import java.io.IOException;

public class SpecialTempFile {

    private static void test(String name, String[] prefix, String[] suffix,
                             boolean exceptionExpected) throws IOException
    {
        if (prefix == null || suffix == null
            || prefix.length != suffix.length)
        {
            return;
        }

        final String exceptionMsg = "Unable to create temporary file";
        String[] dirs = { null, "." };

        for (int i = 0; i < prefix.length; i++) {
            boolean exceptionThrown = false;
            File f = null;

            for (String dir: dirs) {
                System.out.println("In test " + name +
                                   ", creating temp file with prefix, " +
                                   prefix[i] + ", suffix, " + suffix[i] +
                                   ", in dir, " + dir);

                try {
                    if (dir == null || dir.isEmpty())
                        f = File.createTempFile(prefix[i], suffix[i]);
                    else
                        f = File.createTempFile(prefix[i], suffix[i], new File(dir));
                } catch (IOException e) {
                    if (exceptionExpected) {
                        if (e.getMessage().startsWith(exceptionMsg))
                            exceptionThrown = true;
                        else
                            System.out.println("Wrong error message:" +
                                               e.getMessage());
                    } else {
                        throw e;
                    }
                }

                if (exceptionExpected && (!exceptionThrown || f != null))
                    throw new RuntimeException("IOException is expected");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Common test
        final String name = "SpecialTempFile";
        File f = new File(System.getProperty("java.io.tmpdir"), name);
        if (!f.exists()) {
            f.createNewFile();
        }
        String[] nulPre = { name + "\u0000" };
        String[] nulSuf = { ".test" };
        test("NulName", nulPre, nulSuf, true);

        // Test JDK-8025128
        String[] goodPre = { "///..///", "/foo" };
        String[] goodSuf = { ".temp", ".tmp" };
        test("goodName", goodPre, goodSuf, false);

        // Test JDK-8011950
        String[] slashPre = { "temp", "///..///", "/foo" };
        String[] slashSuf = { "///..///..", "///..///..", "///..///.." };
        test("SlashedName", slashPre, slashSuf, true);

        // Windows tests
        if (!System.getProperty("os.name").startsWith("Windows"))
            return;

        // Test JDK-8013827
        String[] resvPre = { "LPT1.package.zip", "com7.4.package.zip" };
        String[] resvSuf = { ".temp", ".temp" };
        test("ReservedName", resvPre, resvSuf, true);
    }
}
