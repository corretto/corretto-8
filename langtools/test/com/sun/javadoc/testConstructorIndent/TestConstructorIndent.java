/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4904037 8026567
 * @summary  The constructor comments should be surrounded by
 *           <dl></dl>.  Check for this in the output.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestConstructorIndent
 * @run main TestConstructorIndent
 */

public class TestConstructorIndent extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4904037";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, SRC_DIR + FS + "C.java"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "C.html", "<div class=\"block\">" +
                 "This is just a simple constructor.</div>" + NL +
                 "<dl>" + NL + "<dt><span class=\"paramLabel\">Parameters:</span></dt>" + NL +
                 "<dd><code>i</code> - a param.</dd>" + NL +"</dl>"
        }
    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestConstructorIndent tester = new TestConstructorIndent();
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
