/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4494033 7028815 7052425 8007338 8023608 8008164 8016549 8072461
 * @summary  Run tests on doclet stylesheet.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester TestStylesheet
 * @run main TestStylesheet
 */

public class TestStylesheet extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4494033-7028815-7052425-8007338-8072461";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "stylesheet.css",
            "/* Javadoc style sheet */"},
        {BUG_ID + FS + "stylesheet.css",
            "/*" + NL + "Overall document style" + NL + "*/"},
        {BUG_ID + FS + "stylesheet.css",
            "/*" + NL + "Heading styles" + NL + "*/"},
        {BUG_ID + FS + "stylesheet.css",
            "/*" + NL + "Navigation bar styles" + NL + "*/"},
        {BUG_ID + FS + "stylesheet.css",
            "body {" + NL + "    background-color:#ffffff;" + NL +
            "    color:#353833;" + NL +
            "    font-family:'DejaVu Sans', Arial, Helvetica, sans-serif;" + NL +
            "    font-size:14px;" + NL + "    margin:0;" + NL + "}"},
        {BUG_ID + FS + "stylesheet.css",
            "ul {" + NL + "    list-style-type:disc;" + NL + "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".overviewSummary caption, .memberSummary caption, .typeSummary caption," + NL +
            ".useSummary caption, .constantsSummary caption, .deprecatedSummary caption {" + NL +
            "    position:relative;" + NL +
            "    text-align:left;" + NL +
            "    background-repeat:no-repeat;" + NL +
            "    color:#253441;" + NL +
            "    font-weight:bold;" + NL +
            "    clear:none;" + NL +
            "    overflow:hidden;" + NL +
            "    padding:0px;" + NL +
            "    padding-top:10px;" + NL +
            "    padding-left:1px;" + NL +
            "    margin:0px;" + NL +
            "    white-space:pre;" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".overviewSummary caption span, .memberSummary caption span, .typeSummary caption span," + NL +
            ".useSummary caption span, .constantsSummary caption span, .deprecatedSummary caption span {" + NL +
            "    white-space:nowrap;" + NL +
            "    padding-top:5px;" + NL +
            "    padding-left:12px;" + NL +
            "    padding-right:12px;" + NL +
            "    padding-bottom:7px;" + NL +
            "    display:inline-block;" + NL +
            "    float:left;" + NL +
            "    background-color:#F8981D;" + NL +
            "    border: none;" + NL +
            "    height:16px;" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".memberSummary caption span.activeTableTab span {" + NL +
            "    white-space:nowrap;" + NL +
            "    padding-top:5px;" + NL +
            "    padding-left:12px;" + NL +
            "    padding-right:12px;" + NL +
            "    margin-right:3px;" + NL +
            "    display:inline-block;" + NL +
            "    float:left;" + NL +
            "    background-color:#F8981D;" + NL +
            "    height:16px;" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".memberSummary caption span.tableTab span {" + NL +
            "    white-space:nowrap;" + NL +
            "    padding-top:5px;" + NL +
            "    padding-left:12px;" + NL +
            "    padding-right:12px;" + NL +
            "    margin-right:3px;" + NL +
            "    display:inline-block;" + NL +
            "    float:left;" + NL +
            "    background-color:#4D7A97;" + NL +
            "    height:16px;" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".memberSummary caption span.tableTab, .memberSummary caption span.activeTableTab {" + NL +
            "    padding-top:0px;" + NL +
            "    padding-left:0px;" + NL +
            "    padding-right:0px;" + NL +
            "    background-image:none;" + NL +
            "    float:none;" + NL +
            "    display:inline;" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            "@import url('resources/fonts/dejavu.css');"},
        // Test the formatting styles for proper content display in use and constant values pages.
        {BUG_ID + FS + "stylesheet.css",
            ".overviewSummary td.colFirst, .overviewSummary th.colFirst," + NL +
            ".useSummary td.colFirst, .useSummary th.colFirst," + NL +
            ".overviewSummary td.colOne, .overviewSummary th.colOne," + NL +
            ".memberSummary td.colFirst, .memberSummary th.colFirst," + NL +
            ".memberSummary td.colOne, .memberSummary th.colOne," + NL +
            ".typeSummary td.colFirst{" + NL +
            "    width:25%;" + NL +
            "    vertical-align:top;" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".overviewSummary td, .memberSummary td, .typeSummary td," + NL +
            ".useSummary td, .constantsSummary td, .deprecatedSummary td {" + NL +
            "    text-align:left;" + NL +
            "    padding:0px 0px 12px 10px;" + NL +
            "}"},
        // Test whether a link to the stylesheet file is inserted properly
        // in the class documentation.
        {BUG_ID + FS + "pkg" + FS + "A.html",
            "<link rel=\"stylesheet\" type=\"text/css\" " +
            "href=\"../stylesheet.css\" title=\"Style\">"}
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "stylesheet.css",
            "* {" + NL + "    margin:0;" + NL + "    padding:0;" + NL + "}"}
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestStylesheet tester = new TestStylesheet();
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
