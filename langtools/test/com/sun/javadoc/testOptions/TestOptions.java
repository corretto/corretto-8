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
 * @bug      4749567
 * @summary  Test the output for -header and -footer options.
 * @author   Bhavesh Patel
 * @library  ../lib/
 * @build    JavadocTester TestOptions
 * @run main TestOptions
 */

public class TestOptions extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4749567";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-header", "Test header", "-footer", "Test footer",
        "-sourcepath", SRC_DIR, "pkg"
    };

    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "package-summary.html",
            "<div class=\"aboutLanguage\">Test header</div>"},
        {BUG_ID + FS + "pkg" + FS + "package-summary.html",
            "<div class=\"aboutLanguage\">Test footer</div>"}
    };

    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestOptions tester = new TestOptions();
        run(tester, ARGS, TEST, NEGATED_TEST);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}

