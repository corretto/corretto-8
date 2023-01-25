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
/**
 * @test @bug 5015163
 * @summary test String merge/join that is the inverse of String.split()
 * @run testng StringJoinTest
 * @author Jim Gish
 */
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Test(groups = {"unit","string","lang","libs"})
public class StringJoinTest {
    private final static String DASH = "-";
    private final static String BEGIN = "Hi there";
    private final static String JIM = "Jim";
    private final static String JOHN = "John";
    private final static String AND_JOE = "and Joe";
    private final static String BILL = "Bill";
    private final static String BOB = "Bob";
    private final static String AND_BO = "and Bo";
    private final static String ZEKE = "Zeke";
    private final static String ZACK = "Zack";
    private final static String AND_ZOE = "and Zoe";

    /**
     * Tests the join() methods on String
     */
    public void testJoinStringVarargs() {
        // check a non-null join of String array (var-args) elements
        String expectedResult = BEGIN + DASH + JIM + DASH + JOHN + DASH + AND_JOE;
        String result = String.join(DASH, BEGIN, JIM, JOHN, AND_JOE);

        assertEquals(result, expectedResult, "BEGIN.join(DASH, JIM, JOHN, AND_JOE)");
        // test with just one element
        assertEquals(String.join(DASH, BEGIN), BEGIN);
    }

    public void testJoinStringArray() {
        // check a non-null join of Object[] with String elements
        String[] theBs = {BILL, BOB, AND_BO};
        String result = String.join(DASH, theBs);
        String expectedResult = BILL + DASH + BOB + DASH + AND_BO;
        assertEquals(result, expectedResult, "String.join(DASH, theBs)");
    }

    public void testJoinEmptyStringArray() {
        // check a non-null join of Object[] with String elements
        String[] empties = {};
        String result = String.join(DASH, empties);
        assertEquals(result, "", "String.join(DASH, empties)");
    }

    @Test(expectedExceptions = {NullPointerException.class})
    public void testJoinNullStringArray() {
        // check a non-null join of Object[] with String elements
        String[] empties = null;
        String result = String.join(DASH, empties);
    }

    @Test(expectedExceptions = {NullPointerException.class})
    public void testJoinNullIterableStringList() {
        // check join of an Iterables
        List<CharSequence> theZsList = null;
        String.join(DASH, theZsList);
    }

    public void testJoinIterableStringList() {
        // check join of an Iterables
        List<CharSequence> theZsList = new ArrayList<>();
        theZsList.add(ZEKE);
        theZsList.add(ZACK);
        theZsList.add(AND_ZOE);
        assertEquals(String.join(DASH, theZsList), ZEKE + DASH + ZACK + DASH
                + AND_ZOE, "String.join(DASH, theZsList))");
    }

    public void testJoinNullStringList() {
        List<CharSequence> nullList = null;
        try {
            assertEquals( String.join( DASH, nullList ), "null" );
            fail("Null container should cause NPE");
        } catch (NullPointerException npe) {}
        assertEquals(String.join(DASH, null, null), "null" + DASH + "null");
    }

    @Test(expectedExceptions = {NullPointerException.class})
    public void testJoinNullDelimiter() {
        String.join(null, JIM, JOHN);
    }
}
