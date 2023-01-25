/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4671694
 * @summary Test to make sure link to superclass is generated for
 * each class in serialized form page.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestSuperClassInSerialForm
 * @run main TestSuperClassInSerialForm
 */

public class TestSuperClassInSerialForm extends JavadocTester {

    private static final String BUG_ID = "4671694";

    private static final String[][] TEST = {
        {BUG_ID + FS + "serialized-form.html",
         "<a href=\"pkg/SubClass.html\" title=\"class in pkg\">pkg.SubClass</a> extends <a href=\"pkg/SuperClass.html\" title=\"class in pkg\">SuperClass</a>"}
    };

    private static final String[][] NEGATED_TEST = NO_TEST;
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestSuperClassInSerialForm tester = new TestSuperClassInSerialForm();
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
