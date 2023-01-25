/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary tests RegExp framework
 * @author Mike McCloskey
 * @bug 4481568 4482696 4495089 4504687 4527731 4599621 4631553 4619345
 * 4630911 4672616 4711773 4727935 4750573 4792284 4803197 4757029 4808962
 * 4872664 4803179 4892980 4900747 4945394 4938995 4979006 4994840 4997476
 * 5013885 5003322 4988891 5098443 5110268 6173522 4829857 5027748 6376940
 * 6358731 6178785 6284152 6231989 6497148 6486934 6233084 6504326 6635133
 * 6350801 6676425 6878475 6919132 6931676 6948903 6990617 7014645 7039066
 * 7067045 7014640 7189363 8007395 8013252 8013254 8012646 8023647 6559590
 * 8027645 6854417 8169056
 * @key randomness
 */

import java.util.regex.*;
import java.util.Random;
import java.io.*;
import java.util.*;
import java.nio.CharBuffer;
import java.util.function.Predicate;

/**
 * This is a test class created to check the operation of
 * the Pattern and Matcher classes.
 */
public class RegExTest {

    private static Random generator = new Random();
    private static boolean failure = false;
    private static int failCount = 0;
    private static String firstFailure = null;

    /**
     * Main to interpret arguments and run several tests.
     *
     */
    public static void main(String[] args) throws Exception {
        // Most of the tests are in a file
        processFile("TestCases.txt");
        //processFile("PerlCases.txt");
        processFile("BMPTestCases.txt");
        processFile("SupplementaryTestCases.txt");

        // These test many randomly generated char patterns
        bm();
        slice();

        // These are hard to put into the file
        escapes();
        blankInput();

        // Substitition tests on randomly generated sequences
        globalSubstitute();
        stringbufferSubstitute();
        substitutionBasher();

        // Canonical Equivalence
        ceTest();

        // Anchors
        anchorTest();

        // boolean match calls
        matchesTest();
        lookingAtTest();

        // Pattern API
        patternMatchesTest();

        // Misc
        lookbehindTest();
        nullArgumentTest();
        backRefTest();
        groupCaptureTest();
        caretTest();
        charClassTest();
        emptyPatternTest();
        findIntTest();
        group0Test();
        longPatternTest();
        octalTest();
        ampersandTest();
        negationTest();
        splitTest();
        appendTest();
        caseFoldingTest();
        commentsTest();
        unixLinesTest();
        replaceFirstTest();
        gTest();
        zTest();
        serializeTest();
        reluctantRepetitionTest();
        multilineDollarTest();
        dollarAtEndTest();
        caretBetweenTerminatorsTest();
        // This RFE rejected in Tiger numOccurrencesTest();
        javaCharClassTest();
        nonCaptureRepetitionTest();
        notCapturedGroupCurlyMatchTest();
        escapedSegmentTest();
        literalPatternTest();
        literalReplacementTest();
        regionTest();
        toStringTest();
        negatedCharClassTest();
        findFromTest();
        boundsTest();
        unicodeWordBoundsTest();
        caretAtEndTest();
        wordSearchTest();
        hitEndTest();
        toMatchResultTest();
        surrogatesInClassTest();
        removeQEQuotingTest();
        namedGroupCaptureTest();
        nonBmpClassComplementTest();
        unicodePropertiesTest();
        unicodeHexNotationTest();
        unicodeClassesTest();
        horizontalAndVerticalWSTest();
        linebreakTest();
        branchTest();
        groupCurlyNotFoundSuppTest();
        groupCurlyBackoffTest();
        patternAsPredicate();

        if (failure) {
            throw new
                RuntimeException("RegExTest failed, 1st failure: " +
                                 firstFailure);
        } else {
            System.err.println("OKAY: All tests passed.");
        }
    }

    // Utility functions

    private static String getRandomAlphaString(int length) {
        StringBuffer buf = new StringBuffer(length);
        for (int i=0; i<length; i++) {
            char randChar = (char)(97 + generator.nextInt(26));
            buf.append(randChar);
        }
        return buf.toString();
    }

    private static void check(Matcher m, String expected) {
        m.find();
        if (!m.group().equals(expected))
            failCount++;
    }

    private static void check(Matcher m, String result, boolean expected) {
        m.find();
        if (m.group().equals(result) != expected)
            failCount++;
    }

    private static void check(Pattern p, String s, boolean expected) {
        if (p.matcher(s).find() != expected)
            failCount++;
    }

    private static void check(String p, String s, boolean expected) {
        Matcher matcher = Pattern.compile(p).matcher(s);
        if (matcher.find() != expected)
            failCount++;
    }

    private static void check(String p, char c, boolean expected) {
        String propertyPattern = expected ? "\\p" + p : "\\P" + p;
        Pattern pattern = Pattern.compile(propertyPattern);
        char[] ca = new char[1]; ca[0] = c;
        Matcher matcher = pattern.matcher(new String(ca));
        if (!matcher.find())
            failCount++;
    }

    private static void check(String p, int codePoint, boolean expected) {
        String propertyPattern = expected ? "\\p" + p : "\\P" + p;
        Pattern pattern = Pattern.compile(propertyPattern);
        char[] ca = Character.toChars(codePoint);
        Matcher matcher = pattern.matcher(new String(ca));
        if (!matcher.find())
            failCount++;
    }

    private static void check(String p, int flag, String input, String s,
                              boolean expected)
    {
        Pattern pattern = Pattern.compile(p, flag);
        Matcher matcher = pattern.matcher(input);
        if (expected)
            check(matcher, s, expected);
        else
            check(pattern, input, false);
    }

    private static void report(String testName) {
        int spacesToAdd = 30 - testName.length();
        StringBuffer paddedNameBuffer = new StringBuffer(testName);
        for (int i=0; i<spacesToAdd; i++)
            paddedNameBuffer.append(" ");
        String paddedName = paddedNameBuffer.toString();
        System.err.println(paddedName + ": " +
                           (failCount==0 ? "Passed":"Failed("+failCount+")"));
        if (failCount > 0) {
            failure = true;

            if (firstFailure == null) {
                firstFailure = testName;
            }
        }

        failCount = 0;
    }

    /**
     * Converts ASCII alphabet characters [A-Za-z] in the given 's' to
     * supplementary characters. This method does NOT fully take care
     * of the regex syntax.
     */
    private static String toSupplementaries(String s) {
        int length = s.length();
        StringBuffer sb = new StringBuffer(length * 2);

        for (int i = 0; i < length; ) {
            char c = s.charAt(i++);
            if (c == '\\') {
                sb.append(c);
                if (i < length) {
                    c = s.charAt(i++);
                    sb.append(c);
                    if (c == 'u') {
                        // assume no syntax error
                        sb.append(s.charAt(i++));
                        sb.append(s.charAt(i++));
                        sb.append(s.charAt(i++));
                        sb.append(s.charAt(i++));
                    }
                }
            } else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                sb.append('\ud800').append((char)('\udc00'+c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // Regular expression tests

    // This is for bug 6178785
    // Test if an expected NPE gets thrown when passing in a null argument
    private static boolean check(Runnable test) {
        try {
            test.run();
            failCount++;
            return false;
        } catch (NullPointerException npe) {
            return true;
        }
    }

    private static void nullArgumentTest() {
        check(new Runnable() { public void run() { Pattern.compile(null); }});
        check(new Runnable() { public void run() { Pattern.matches(null, null); }});
        check(new Runnable() { public void run() { Pattern.matches("xyz", null);}});
        check(new Runnable() { public void run() { Pattern.quote(null);}});
        check(new Runnable() { public void run() { Pattern.compile("xyz").split(null);}});
        check(new Runnable() { public void run() { Pattern.compile("xyz").matcher(null);}});

        final Matcher m = Pattern.compile("xyz").matcher("xyz");
        m.matches();
        check(new Runnable() { public void run() { m.appendTail(null);}});
        check(new Runnable() { public void run() { m.replaceAll(null);}});
        check(new Runnable() { public void run() { m.replaceFirst(null);}});
        check(new Runnable() { public void run() { m.appendReplacement(null, null);}});
        check(new Runnable() { public void run() { m.reset(null);}});
        check(new Runnable() { public void run() { Matcher.quoteReplacement(null);}});
        //check(new Runnable() { public void run() { m.usePattern(null);}});

        report("Null Argument");
    }

    // This is for bug6635133
    // Test if surrogate pair in Unicode escapes can be handled correctly.
    private static void surrogatesInClassTest() throws Exception {
        Pattern pattern = Pattern.compile("[\\ud834\\udd21-\\ud834\\udd24]");
        Matcher matcher = pattern.matcher("\ud834\udd22");
        if (!matcher.find())
            failCount++;

        report("Surrogate pair in Unicode escape");
    }

    // This is for bug6990617
    // Test if Pattern.RemoveQEQuoting works correctly if the octal unicode
    // char encoding is only 2 or 3 digits instead of 4 and the first quoted
    // char is an octal digit.
    private static void removeQEQuotingTest() throws Exception {
        Pattern pattern =
            Pattern.compile("\\011\\Q1sometext\\E\\011\\Q2sometext\\E");
        Matcher matcher = pattern.matcher("\t1sometext\t2sometext");
        if (!matcher.find())
            failCount++;

        report("Remove Q/E Quoting");
    }

    // This is for bug 4988891
    // Test toMatchResult to see that it is a copy of the Matcher
    // that is not affected by subsequent operations on the original
    private static void toMatchResultTest() throws Exception {
        Pattern pattern = Pattern.compile("squid");
        Matcher matcher = pattern.matcher(
            "agiantsquidofdestinyasmallsquidoffate");
        matcher.find();
        int matcherStart1 = matcher.start();
        MatchResult mr = matcher.toMatchResult();
        if (mr == matcher)
            failCount++;
        int resultStart1 = mr.start();
        if (matcherStart1 != resultStart1)
            failCount++;
        matcher.find();
        int matcherStart2 = matcher.start();
        int resultStart2 = mr.start();
        if (matcherStart2 == resultStart2)
            failCount++;
        if (resultStart1 != resultStart2)
            failCount++;
        MatchResult mr2 = matcher.toMatchResult();
        if (mr == mr2)
            failCount++;
        if (mr2.start() != matcherStart2)
            failCount++;
        report("toMatchResult is a copy");
    }

    // This is for bug 5013885
    // Must test a slice to see if it reports hitEnd correctly
    private static void hitEndTest() throws Exception {
        // Basic test of Slice node
        Pattern p = Pattern.compile("^squidattack");
        Matcher m = p.matcher("squack");
        m.find();
        if (m.hitEnd())
            failCount++;
        m.reset("squid");
        m.find();
        if (!m.hitEnd())
            failCount++;

        // Test Slice, SliceA and SliceU nodes
        for (int i=0; i<3; i++) {
            int flags = 0;
            if (i==1) flags = Pattern.CASE_INSENSITIVE;
            if (i==2) flags = Pattern.UNICODE_CASE;
            p = Pattern.compile("^abc", flags);
            m = p.matcher("ad");
            m.find();
            if (m.hitEnd())
                failCount++;
            m.reset("ab");
            m.find();
            if (!m.hitEnd())
                failCount++;
        }

        // Test Boyer-Moore node
        p = Pattern.compile("catattack");
        m = p.matcher("attack");
        m.find();
        if (!m.hitEnd())
            failCount++;

        p = Pattern.compile("catattack");
        m = p.matcher("attackattackattackcatatta");
        m.find();
        if (!m.hitEnd())
            failCount++;
        report("hitEnd from a Slice");
    }

    // This is for bug 4997476
    // It is weird code submitted by customer demonstrating a regression
    private static void wordSearchTest() throws Exception {
        String testString = new String("word1 word2 word3");
        Pattern p = Pattern.compile("\\b");
        Matcher m = p.matcher(testString);
        int position = 0;
        int start = 0;
        while (m.find(position)) {
            start = m.start();
            if (start == testString.length())
                break;
            if (m.find(start+1)) {
                position = m.start();
            } else {
                position = testString.length();
            }
            if (testString.substring(start, position).equals(" "))
                continue;
            if (!testString.substring(start, position-1).startsWith("word"))
                failCount++;
        }
        report("Customer word search");
    }

    // This is for bug 4994840
    private static void caretAtEndTest() throws Exception {
        // Problem only occurs with multiline patterns
        // containing a beginning-of-line caret "^" followed
        // by an expression that also matches the empty string.
        Pattern pattern = Pattern.compile("^x?", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher("\r");
        matcher.find();
        matcher.find();
        report("Caret at end");
    }

    // This test is for 4979006
    // Check to see if word boundary construct properly handles unicode
    // non spacing marks
    private static void unicodeWordBoundsTest() throws Exception {
        String spaces = "  ";
        String wordChar = "a";
        String nsm = "\u030a";

        assert (Character.getType('\u030a') == Character.NON_SPACING_MARK);

        Pattern pattern = Pattern.compile("\\b");
        Matcher matcher = pattern.matcher("");
        // S=other B=word character N=non spacing mark .=word boundary
        // SS.BB.SS
        String input = spaces + wordChar + wordChar + spaces;
        twoFindIndexes(input, matcher, 2, 4);
        // SS.BBN.SS
        input = spaces + wordChar +wordChar + nsm + spaces;
        twoFindIndexes(input, matcher, 2, 5);
        // SS.BN.SS
        input = spaces + wordChar + nsm + spaces;
        twoFindIndexes(input, matcher, 2, 4);
        // SS.BNN.SS
        input = spaces + wordChar + nsm + nsm + spaces;
        twoFindIndexes(input, matcher, 2, 5);
        // SSN.BB.SS
        input = spaces + nsm + wordChar + wordChar + spaces;
        twoFindIndexes(input, matcher, 3, 5);
        // SS.BNB.SS
        input = spaces + wordChar + nsm + wordChar + spaces;
        twoFindIndexes(input, matcher, 2, 5);
        // SSNNSS
        input = spaces + nsm + nsm + spaces;
        matcher.reset(input);
        if (matcher.find())
            failCount++;
        // SSN.BBN.SS
        input = spaces + nsm + wordChar + wordChar + nsm + spaces;
        twoFindIndexes(input, matcher, 3, 6);

        report("Unicode word boundary");
    }

    private static void twoFindIndexes(String input, Matcher matcher, int a,
                                       int b) throws Exception
    {
        matcher.reset(input);
        matcher.find();
        if (matcher.start() != a)
            failCount++;
        matcher.find();
        if (matcher.start() != b)
            failCount++;
    }

    // This test is for 6284152
    static void check(String regex, String input, String[] expected) {
        List<String> result = new ArrayList<String>();
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(input);
        while (m.find()) {
            result.add(m.group());
        }
        if (!Arrays.asList(expected).equals(result))
            failCount++;
    }

    private static void lookbehindTest() throws Exception {
        //Positive
        check("(?<=%.{0,5})foo\\d",
              "%foo1\n%bar foo2\n%bar  foo3\n%blahblah foo4\nfoo5",
              new String[]{"foo1", "foo2", "foo3"});

        //boundary at end of the lookbehind sub-regex should work consistently
        //with the boundary just after the lookbehind sub-regex
        check("(?<=.*\\b)foo", "abcd foo", new String[]{"foo"});
        check("(?<=.*)\\bfoo", "abcd foo", new String[]{"foo"});
        check("(?<!abc )\\bfoo", "abc foo", new String[0]);
        check("(?<!abc \\b)foo", "abc foo", new String[0]);

        //Negative
        check("(?<!%.{0,5})foo\\d",
              "%foo1\n%bar foo2\n%bar  foo3\n%blahblah foo4\nfoo5",
              new String[] {"foo4", "foo5"});

        //Positive greedy
        check("(?<=%b{1,4})foo", "%bbbbfoo", new String[] {"foo"});

        //Positive reluctant
        check("(?<=%b{1,4}?)foo", "%bbbbfoo", new String[] {"foo"});

        //supplementary
        check("(?<=%b{1,4})fo\ud800\udc00o", "%bbbbfo\ud800\udc00o",
              new String[] {"fo\ud800\udc00o"});
        check("(?<=%b{1,4}?)fo\ud800\udc00o", "%bbbbfo\ud800\udc00o",
              new String[] {"fo\ud800\udc00o"});
        check("(?<!%b{1,4})fo\ud800\udc00o", "%afo\ud800\udc00o",
              new String[] {"fo\ud800\udc00o"});
        check("(?<!%b{1,4}?)fo\ud800\udc00o", "%afo\ud800\udc00o",
              new String[] {"fo\ud800\udc00o"});
        report("Lookbehind");
    }

    // This test is for 4938995
    // Check to see if weak region boundaries are transparent to
    // lookahead and lookbehind constructs
    private static void boundsTest() throws Exception {
        String fullMessage = "catdogcat";
        Pattern pattern = Pattern.compile("(?<=cat)dog(?=cat)");
        Matcher matcher = pattern.matcher("catdogca");
        matcher.useTransparentBounds(true);
        if (matcher.find())
            failCount++;
        matcher.reset("atdogcat");
        if (matcher.find())
            failCount++;
        matcher.reset(fullMessage);
        if (!matcher.find())
            failCount++;
        matcher.reset(fullMessage);
        matcher.region(0,9);
        if (!matcher.find())
            failCount++;
        matcher.reset(fullMessage);
        matcher.region(0,6);
        if (!matcher.find())
            failCount++;
        matcher.reset(fullMessage);
        matcher.region(3,6);
        if (!matcher.find())
            failCount++;
        matcher.useTransparentBounds(false);
        if (matcher.find())
            failCount++;

        // Negative lookahead/lookbehind
        pattern = Pattern.compile("(?<!cat)dog(?!cat)");
        matcher = pattern.matcher("dogcat");
        matcher.useTransparentBounds(true);
        matcher.region(0,3);
        if (matcher.find())
            failCount++;
        matcher.reset("catdog");
        matcher.region(3,6);
        if (matcher.find())
            failCount++;
        matcher.useTransparentBounds(false);
        matcher.reset("dogcat");
        matcher.region(0,3);
        if (!matcher.find())
            failCount++;
        matcher.reset("catdog");
        matcher.region(3,6);
        if (!matcher.find())
            failCount++;

        report("Region bounds transparency");
    }

    // This test is for 4945394
    private static void findFromTest() throws Exception {
        String message = "This is 40 $0 message.";
        Pattern pat = Pattern.compile("\\$0");
        Matcher match = pat.matcher(message);
        if (!match.find())
            failCount++;
        if (match.find())
            failCount++;
        if (match.find())
            failCount++;
        report("Check for alternating find");
    }

    // This test is for 4872664 and 4892980
    private static void negatedCharClassTest() throws Exception {
        Pattern pattern = Pattern.compile("[^>]");
        Matcher matcher = pattern.matcher("\u203A");
        if (!matcher.matches())
            failCount++;
        pattern = Pattern.compile("[^fr]");
        matcher = pattern.matcher("a");
        if (!matcher.find())
            failCount++;
        matcher.reset("\u203A");
        if (!matcher.find())
            failCount++;
        String s = "for";
        String result[] = s.split("[^fr]");
        if (!result[0].equals("f"))
            failCount++;
        if (!result[1].equals("r"))
            failCount++;
        s = "f\u203Ar";
        result = s.split("[^fr]");
        if (!result[0].equals("f"))
            failCount++;
        if (!result[1].equals("r"))
            failCount++;

        // Test adding to bits, subtracting a node, then adding to bits again
        pattern = Pattern.compile("[^f\u203Ar]");
        matcher = pattern.matcher("a");
        if (!matcher.find())
            failCount++;
        matcher.reset("f");
        if (matcher.find())
            failCount++;
        matcher.reset("\u203A");
        if (matcher.find())
            failCount++;
        matcher.reset("r");
        if (matcher.find())
            failCount++;
        matcher.reset("\u203B");
        if (!matcher.find())
            failCount++;

        // Test subtracting a node, adding to bits, subtracting again
        pattern = Pattern.compile("[^\u203Ar\u203B]");
        matcher = pattern.matcher("a");
        if (!matcher.find())
            failCount++;
        matcher.reset("\u203A");
        if (matcher.find())
            failCount++;
        matcher.reset("r");
        if (matcher.find())
            failCount++;
        matcher.reset("\u203B");
        if (matcher.find())
            failCount++;
        matcher.reset("\u203C");
        if (!matcher.find())
            failCount++;

        report("Negated Character Class");
    }

    // This test is for 4628291
    private static void toStringTest() throws Exception {
        Pattern pattern = Pattern.compile("b+");
        if (pattern.toString() != "b+")
            failCount++;
        Matcher matcher = pattern.matcher("aaabbbccc");
        String matcherString = matcher.toString(); // unspecified
        matcher.find();
        matcherString = matcher.toString(); // unspecified
        matcher.region(0,3);
        matcherString = matcher.toString(); // unspecified
        matcher.reset();
        matcherString = matcher.toString(); // unspecified
        report("toString");
    }

    // This test is for 4808962
    private static void literalPatternTest() throws Exception {
        int flags = Pattern.LITERAL;

        Pattern pattern = Pattern.compile("abc\\t$^", flags);
        check(pattern, "abc\\t$^", true);

        pattern = Pattern.compile(Pattern.quote("abc\\t$^"));
        check(pattern, "abc\\t$^", true);

        pattern = Pattern.compile("\\Qa^$bcabc\\E", flags);
        check(pattern, "\\Qa^$bcabc\\E", true);
        check(pattern, "a^$bcabc", false);

        pattern = Pattern.compile("\\\\Q\\\\E");
        check(pattern, "\\Q\\E", true);

        pattern = Pattern.compile("\\Qabc\\Eefg\\\\Q\\\\Ehij");
        check(pattern, "abcefg\\Q\\Ehij", true);

        pattern = Pattern.compile("\\\\\\Q\\\\E");
        check(pattern, "\\\\\\\\", true);

        pattern = Pattern.compile(Pattern.quote("\\Qa^$bcabc\\E"));
        check(pattern, "\\Qa^$bcabc\\E", true);
        check(pattern, "a^$bcabc", false);

        pattern = Pattern.compile(Pattern.quote("\\Qabc\\Edef"));
        check(pattern, "\\Qabc\\Edef", true);
        check(pattern, "abcdef", false);

        pattern = Pattern.compile(Pattern.quote("abc\\Edef"));
        check(pattern, "abc\\Edef", true);
        check(pattern, "abcdef", false);

        pattern = Pattern.compile(Pattern.quote("\\E"));
        check(pattern, "\\E", true);

        pattern = Pattern.compile("((((abc.+?:)", flags);
        check(pattern, "((((abc.+?:)", true);

        flags |= Pattern.MULTILINE;

        pattern = Pattern.compile("^cat$", flags);
        check(pattern, "abc^cat$def", true);
        check(pattern, "cat", false);

        flags |= Pattern.CASE_INSENSITIVE;

        pattern = Pattern.compile("abcdef", flags);
        check(pattern, "ABCDEF", true);
        check(pattern, "AbCdEf", true);

        flags |= Pattern.DOTALL;

        pattern = Pattern.compile("a...b", flags);
        check(pattern, "A...b", true);
        check(pattern, "Axxxb", false);

        flags |= Pattern.CANON_EQ;

        Pattern p = Pattern.compile("testa\u030a", flags);
        check(pattern, "testa\u030a", false);
        check(pattern, "test\u00e5", false);

        // Supplementary character test
        flags = Pattern.LITERAL;

        pattern = Pattern.compile(toSupplementaries("abc\\t$^"), flags);
        check(pattern, toSupplementaries("abc\\t$^"), true);

        pattern = Pattern.compile(Pattern.quote(toSupplementaries("abc\\t$^")));
        check(pattern, toSupplementaries("abc\\t$^"), true);

        pattern = Pattern.compile(toSupplementaries("\\Qa^$bcabc\\E"), flags);
        check(pattern, toSupplementaries("\\Qa^$bcabc\\E"), true);
        check(pattern, toSupplementaries("a^$bcabc"), false);

        pattern = Pattern.compile(Pattern.quote(toSupplementaries("\\Qa^$bcabc\\E")));
        check(pattern, toSupplementaries("\\Qa^$bcabc\\E"), true);
        check(pattern, toSupplementaries("a^$bcabc"), false);

        pattern = Pattern.compile(Pattern.quote(toSupplementaries("\\Qabc\\Edef")));
        check(pattern, toSupplementaries("\\Qabc\\Edef"), true);
        check(pattern, toSupplementaries("abcdef"), false);

        pattern = Pattern.compile(Pattern.quote(toSupplementaries("abc\\Edef")));
        check(pattern, toSupplementaries("abc\\Edef"), true);
        check(pattern, toSupplementaries("abcdef"), false);

        pattern = Pattern.compile(toSupplementaries("((((abc.+?:)"), flags);
        check(pattern, toSupplementaries("((((abc.+?:)"), true);

        flags |= Pattern.MULTILINE;

        pattern = Pattern.compile(toSupplementaries("^cat$"), flags);
        check(pattern, toSupplementaries("abc^cat$def"), true);
        check(pattern, toSupplementaries("cat"), false);

        flags |= Pattern.DOTALL;

        // note: this is case-sensitive.
        pattern = Pattern.compile(toSupplementaries("a...b"), flags);
        check(pattern, toSupplementaries("a...b"), true);
        check(pattern, toSupplementaries("axxxb"), false);

        flags |= Pattern.CANON_EQ;

        String t = toSupplementaries("test");
        p = Pattern.compile(t + "a\u030a", flags);
        check(pattern, t + "a\u030a", false);
        check(pattern, t + "\u00e5", false);

        report("Literal pattern");
    }

    // This test is for 4803179
    // This test is also for 4808962, replacement parts
    private static void literalReplacementTest() throws Exception {
        int flags = Pattern.LITERAL;

        Pattern pattern = Pattern.compile("abc", flags);
        Matcher matcher = pattern.matcher("zzzabczzz");
        String replaceTest = "$0";
        String result = matcher.replaceAll(replaceTest);
        if (!result.equals("zzzabczzz"))
            failCount++;

        matcher.reset();
        String literalReplacement = matcher.quoteReplacement(replaceTest);
        result = matcher.replaceAll(literalReplacement);
        if (!result.equals("zzz$0zzz"))
            failCount++;

        matcher.reset();
        replaceTest = "\\t$\\$";
        literalReplacement = matcher.quoteReplacement(replaceTest);
        result = matcher.replaceAll(literalReplacement);
        if (!result.equals("zzz\\t$\\$zzz"))
            failCount++;

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("abc"), flags);
        matcher = pattern.matcher(toSupplementaries("zzzabczzz"));
        replaceTest = "$0";
        result = matcher.replaceAll(replaceTest);
        if (!result.equals(toSupplementaries("zzzabczzz")))
            failCount++;

        matcher.reset();
        literalReplacement = matcher.quoteReplacement(replaceTest);
        result = matcher.replaceAll(literalReplacement);
        if (!result.equals(toSupplementaries("zzz$0zzz")))
            failCount++;

        matcher.reset();
        replaceTest = "\\t$\\$";
        literalReplacement = matcher.quoteReplacement(replaceTest);
        result = matcher.replaceAll(literalReplacement);
        if (!result.equals(toSupplementaries("zzz\\t$\\$zzz")))
            failCount++;

        // IAE should be thrown if backslash or '$' is the last character
        // in replacement string
        try {
            "\uac00".replaceAll("\uac00", "$");
            failCount++;
        } catch (IllegalArgumentException iie) {
        } catch (Exception e) {
            failCount++;
        }
        try {
            "\uac00".replaceAll("\uac00", "\\");
            failCount++;
        } catch (IllegalArgumentException iie) {
        } catch (Exception e) {
            failCount++;
        }
        report("Literal replacement");
    }

    // This test is for 4757029
    private static void regionTest() throws Exception {
        Pattern pattern = Pattern.compile("abc");
        Matcher matcher = pattern.matcher("abcdefabc");

        matcher.region(0,9);
        if (!matcher.find())
            failCount++;
        if (!matcher.find())
            failCount++;
        matcher.region(0,3);
        if (!matcher.find())
           failCount++;
        matcher.region(3,6);
        if (matcher.find())
           failCount++;
        matcher.region(0,2);
        if (matcher.find())
           failCount++;

        expectRegionFail(matcher, 1, -1);
        expectRegionFail(matcher, -1, -1);
        expectRegionFail(matcher, -1, 1);
        expectRegionFail(matcher, 5, 3);
        expectRegionFail(matcher, 5, 12);
        expectRegionFail(matcher, 12, 12);

        pattern = Pattern.compile("^abc$");
        matcher = pattern.matcher("zzzabczzz");
        matcher.region(0,9);
        if (matcher.find())
            failCount++;
        matcher.region(3,6);
        if (!matcher.find())
           failCount++;
        matcher.region(3,6);
        matcher.useAnchoringBounds(false);
        if (matcher.find())
           failCount++;

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("abc"));
        matcher = pattern.matcher(toSupplementaries("abcdefabc"));
        matcher.region(0,9*2);
        if (!matcher.find())
            failCount++;
        if (!matcher.find())
            failCount++;
        matcher.region(0,3*2);
        if (!matcher.find())
           failCount++;
        matcher.region(1,3*2);
        if (matcher.find())
           failCount++;
        matcher.region(3*2,6*2);
        if (matcher.find())
           failCount++;
        matcher.region(0,2*2);
        if (matcher.find())
           failCount++;
        matcher.region(0,2*2+1);
        if (matcher.find())
           failCount++;

        expectRegionFail(matcher, 1*2, -1);
        expectRegionFail(matcher, -1, -1);
        expectRegionFail(matcher, -1, 1*2);
        expectRegionFail(matcher, 5*2, 3*2);
        expectRegionFail(matcher, 5*2, 12*2);
        expectRegionFail(matcher, 12*2, 12*2);

        pattern = Pattern.compile(toSupplementaries("^abc$"));
        matcher = pattern.matcher(toSupplementaries("zzzabczzz"));
        matcher.region(0,9*2);
        if (matcher.find())
            failCount++;
        matcher.region(3*2,6*2);
        if (!matcher.find())
           failCount++;
        matcher.region(3*2+1,6*2);
        if (matcher.find())
           failCount++;
        matcher.region(3*2,6*2-1);
        if (matcher.find())
           failCount++;
        matcher.region(3*2,6*2);
        matcher.useAnchoringBounds(false);
        if (matcher.find())
           failCount++;
        report("Regions");
    }

    private static void expectRegionFail(Matcher matcher, int index1,
                                         int index2)
    {
        try {
            matcher.region(index1, index2);
            failCount++;
        } catch (IndexOutOfBoundsException ioobe) {
            // Correct result
        } catch (IllegalStateException ise) {
            // Correct result
        }
    }

    // This test is for 4803197
    private static void escapedSegmentTest() throws Exception {

        Pattern pattern = Pattern.compile("\\Qdir1\\dir2\\E");
        check(pattern, "dir1\\dir2", true);

        pattern = Pattern.compile("\\Qdir1\\dir2\\\\E");
        check(pattern, "dir1\\dir2\\", true);

        pattern = Pattern.compile("(\\Qdir1\\dir2\\\\E)");
        check(pattern, "dir1\\dir2\\", true);

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("\\Qdir1\\dir2\\E"));
        check(pattern, toSupplementaries("dir1\\dir2"), true);

        pattern = Pattern.compile(toSupplementaries("\\Qdir1\\dir2")+"\\\\E");
        check(pattern, toSupplementaries("dir1\\dir2\\"), true);

        pattern = Pattern.compile(toSupplementaries("(\\Qdir1\\dir2")+"\\\\E)");
        check(pattern, toSupplementaries("dir1\\dir2\\"), true);

        report("Escaped segment");
    }

    // This test is for 4792284
    private static void nonCaptureRepetitionTest() throws Exception {
        String input = "abcdefgh;";

        String[] patterns = new String[] {
            "(?:\\w{4})+;",
            "(?:\\w{8})*;",
            "(?:\\w{2}){2,4};",
            "(?:\\w{4}){2,};",   // only matches the
            ".*?(?:\\w{5})+;",   //     specified minimum
            ".*?(?:\\w{9})*;",   //     number of reps - OK
            "(?:\\w{4})+?;",     // lazy repetition - OK
            "(?:\\w{4})++;",     // possessive repetition - OK
            "(?:\\w{2,}?)+;",    // non-deterministic - OK
            "(\\w{4})+;",        // capturing group - OK
        };

        for (int i = 0; i < patterns.length; i++) {
            // Check find()
            check(patterns[i], 0, input, input, true);
            // Check matches()
            Pattern p = Pattern.compile(patterns[i]);
            Matcher m = p.matcher(input);

            if (m.matches()) {
                if (!m.group(0).equals(input))
                    failCount++;
            } else {
                failCount++;
            }
        }

        report("Non capturing repetition");
    }

    // This test is for 6358731
    private static void notCapturedGroupCurlyMatchTest() throws Exception {
        Pattern pattern = Pattern.compile("(abc)+|(abcd)+");
        Matcher matcher = pattern.matcher("abcd");
        if (!matcher.matches() ||
             matcher.group(1) != null ||
             !matcher.group(2).equals("abcd")) {
            failCount++;
        }
        report("Not captured GroupCurly");
    }

    // This test is for 4706545
    private static void javaCharClassTest() throws Exception {
        for (int i=0; i<1000; i++) {
            char c = (char)generator.nextInt();
            check("{javaLowerCase}", c, Character.isLowerCase(c));
            check("{javaUpperCase}", c, Character.isUpperCase(c));
            check("{javaUpperCase}+", c, Character.isUpperCase(c));
            check("{javaTitleCase}", c, Character.isTitleCase(c));
            check("{javaDigit}", c, Character.isDigit(c));
            check("{javaDefined}", c, Character.isDefined(c));
            check("{javaLetter}", c, Character.isLetter(c));
            check("{javaLetterOrDigit}", c, Character.isLetterOrDigit(c));
            check("{javaJavaIdentifierStart}", c,
                  Character.isJavaIdentifierStart(c));
            check("{javaJavaIdentifierPart}", c,
                  Character.isJavaIdentifierPart(c));
            check("{javaUnicodeIdentifierStart}", c,
                  Character.isUnicodeIdentifierStart(c));
            check("{javaUnicodeIdentifierPart}", c,
                  Character.isUnicodeIdentifierPart(c));
            check("{javaIdentifierIgnorable}", c,
                  Character.isIdentifierIgnorable(c));
            check("{javaSpaceChar}", c, Character.isSpaceChar(c));
            check("{javaWhitespace}", c, Character.isWhitespace(c));
            check("{javaISOControl}", c, Character.isISOControl(c));
            check("{javaMirrored}", c, Character.isMirrored(c));

        }

        // Supplementary character test
        for (int i=0; i<1000; i++) {
            int c = generator.nextInt(Character.MAX_CODE_POINT
                                      - Character.MIN_SUPPLEMENTARY_CODE_POINT)
                        + Character.MIN_SUPPLEMENTARY_CODE_POINT;
            check("{javaLowerCase}", c, Character.isLowerCase(c));
            check("{javaUpperCase}", c, Character.isUpperCase(c));
            check("{javaUpperCase}+", c, Character.isUpperCase(c));
            check("{javaTitleCase}", c, Character.isTitleCase(c));
            check("{javaDigit}", c, Character.isDigit(c));
            check("{javaDefined}", c, Character.isDefined(c));
            check("{javaLetter}", c, Character.isLetter(c));
            check("{javaLetterOrDigit}", c, Character.isLetterOrDigit(c));
            check("{javaJavaIdentifierStart}", c,
                  Character.isJavaIdentifierStart(c));
            check("{javaJavaIdentifierPart}", c,
                  Character.isJavaIdentifierPart(c));
            check("{javaUnicodeIdentifierStart}", c,
                  Character.isUnicodeIdentifierStart(c));
            check("{javaUnicodeIdentifierPart}", c,
                  Character.isUnicodeIdentifierPart(c));
            check("{javaIdentifierIgnorable}", c,
                  Character.isIdentifierIgnorable(c));
            check("{javaSpaceChar}", c, Character.isSpaceChar(c));
            check("{javaWhitespace}", c, Character.isWhitespace(c));
            check("{javaISOControl}", c, Character.isISOControl(c));
            check("{javaMirrored}", c, Character.isMirrored(c));
        }

        report("Java character classes");
    }

    // This test is for 4523620
    /*
    private static void numOccurrencesTest() throws Exception {
        Pattern pattern = Pattern.compile("aaa");

        if (pattern.numOccurrences("aaaaaa", false) != 2)
            failCount++;
        if (pattern.numOccurrences("aaaaaa", true) != 4)
            failCount++;

        pattern = Pattern.compile("^");
        if (pattern.numOccurrences("aaaaaa", false) != 1)
            failCount++;
        if (pattern.numOccurrences("aaaaaa", true) != 1)
            failCount++;

        report("Number of Occurrences");
    }
    */

    // This test is for 4776374
    private static void caretBetweenTerminatorsTest() throws Exception {
        int flags1 = Pattern.DOTALL;
        int flags2 = Pattern.DOTALL | Pattern.UNIX_LINES;
        int flags3 = Pattern.DOTALL | Pattern.UNIX_LINES | Pattern.MULTILINE;
        int flags4 = Pattern.DOTALL | Pattern.MULTILINE;

        check("^....", flags1, "test\ntest", "test", true);
        check(".....^", flags1, "test\ntest", "test", false);
        check(".....^", flags1, "test\n", "test", false);
        check("....^", flags1, "test\r\n", "test", false);

        check("^....", flags2, "test\ntest", "test", true);
        check("....^", flags2, "test\ntest", "test", false);
        check(".....^", flags2, "test\n", "test", false);
        check("....^", flags2, "test\r\n", "test", false);

        check("^....", flags3, "test\ntest", "test", true);
        check(".....^", flags3, "test\ntest", "test\n", true);
        check(".....^", flags3, "test\u0085test", "test\u0085", false);
        check(".....^", flags3, "test\n", "test", false);
        check(".....^", flags3, "test\r\n", "test", false);
        check("......^", flags3, "test\r\ntest", "test\r\n", true);

        check("^....", flags4, "test\ntest", "test", true);
        check(".....^", flags3, "test\ntest", "test\n", true);
        check(".....^", flags4, "test\u0085test", "test\u0085", true);
        check(".....^", flags4, "test\n", "test\n", false);
        check(".....^", flags4, "test\r\n", "test\r", false);

        // Supplementary character test
        String t = toSupplementaries("test");
        check("^....", flags1, t+"\n"+t, t, true);
        check(".....^", flags1, t+"\n"+t, t, false);
        check(".....^", flags1, t+"\n", t, false);
        check("....^", flags1, t+"\r\n", t, false);

        check("^....", flags2, t+"\n"+t, t, true);
        check("....^", flags2, t+"\n"+t, t, false);
        check(".....^", flags2, t+"\n", t, false);
        check("....^", flags2, t+"\r\n", t, false);

        check("^....", flags3, t+"\n"+t, t, true);
        check(".....^", flags3, t+"\n"+t, t+"\n", true);
        check(".....^", flags3, t+"\u0085"+t, t+"\u0085", false);
        check(".....^", flags3, t+"\n", t, false);
        check(".....^", flags3, t+"\r\n", t, false);
        check("......^", flags3, t+"\r\n"+t, t+"\r\n", true);

        check("^....", flags4, t+"\n"+t, t, true);
        check(".....^", flags3, t+"\n"+t, t+"\n", true);
        check(".....^", flags4, t+"\u0085"+t, t+"\u0085", true);
        check(".....^", flags4, t+"\n", t+"\n", false);
        check(".....^", flags4, t+"\r\n", t+"\r", false);

        report("Caret between terminators");
    }

    // This test is for 4727935
    private static void dollarAtEndTest() throws Exception {
        int flags1 = Pattern.DOTALL;
        int flags2 = Pattern.DOTALL | Pattern.UNIX_LINES;
        int flags3 = Pattern.DOTALL | Pattern.MULTILINE;

        check("....$", flags1, "test\n", "test", true);
        check("....$", flags1, "test\r\n", "test", true);
        check(".....$", flags1, "test\n", "test\n", true);
        check(".....$", flags1, "test\u0085", "test\u0085", true);
        check("....$", flags1, "test\u0085", "test", true);

        check("....$", flags2, "test\n", "test", true);
        check(".....$", flags2, "test\n", "test\n", true);
        check(".....$", flags2, "test\u0085", "test\u0085", true);
        check("....$", flags2, "test\u0085", "est\u0085", true);

        check("....$.blah", flags3, "test\nblah", "test\nblah", true);
        check(".....$.blah", flags3, "test\n\nblah", "test\n\nblah", true);
        check("....$blah", flags3, "test\nblah", "!!!!", false);
        check(".....$blah", flags3, "test\nblah", "!!!!", false);

        // Supplementary character test
        String t = toSupplementaries("test");
        String b = toSupplementaries("blah");
        check("....$", flags1, t+"\n", t, true);
        check("....$", flags1, t+"\r\n", t, true);
        check(".....$", flags1, t+"\n", t+"\n", true);
        check(".....$", flags1, t+"\u0085", t+"\u0085", true);
        check("....$", flags1, t+"\u0085", t, true);

        check("....$", flags2, t+"\n", t, true);
        check(".....$", flags2, t+"\n", t+"\n", true);
        check(".....$", flags2, t+"\u0085", t+"\u0085", true);
        check("....$", flags2, t+"\u0085", toSupplementaries("est\u0085"), true);

        check("....$."+b, flags3, t+"\n"+b, t+"\n"+b, true);
        check(".....$."+b, flags3, t+"\n\n"+b, t+"\n\n"+b, true);
        check("....$"+b, flags3, t+"\n"+b, "!!!!", false);
        check(".....$"+b, flags3, t+"\n"+b, "!!!!", false);

        report("Dollar at End");
    }

    // This test is for 4711773
    private static void multilineDollarTest() throws Exception {
        Pattern findCR = Pattern.compile("$", Pattern.MULTILINE);
        Matcher matcher = findCR.matcher("first bit\nsecond bit");
        matcher.find();
        if (matcher.start(0) != 9)
            failCount++;
        matcher.find();
        if (matcher.start(0) != 20)
            failCount++;

        // Supplementary character test
        matcher = findCR.matcher(toSupplementaries("first  bit\n second  bit")); // double BMP chars
        matcher.find();
        if (matcher.start(0) != 9*2)
            failCount++;
        matcher.find();
        if (matcher.start(0) != 20*2)
            failCount++;

        report("Multiline Dollar");
    }

    private static void reluctantRepetitionTest() throws Exception {
        Pattern p = Pattern.compile("1(\\s\\S+?){1,3}?[\\s,]2");
        check(p, "1 word word word 2", true);
        check(p, "1 wor wo w 2", true);
        check(p, "1 word word 2", true);
        check(p, "1 word 2", true);
        check(p, "1 wo w w 2", true);
        check(p, "1 wo w 2", true);
        check(p, "1 wor w 2", true);

        p = Pattern.compile("([a-z])+?c");
        Matcher m = p.matcher("ababcdefdec");
        check(m, "ababc");

        // Supplementary character test
        p = Pattern.compile(toSupplementaries("([a-z])+?c"));
        m = p.matcher(toSupplementaries("ababcdefdec"));
        check(m, toSupplementaries("ababc"));

        report("Reluctant Repetition");
    }

    private static void serializeTest() throws Exception {
        String patternStr = "(b)";
        String matchStr = "b";
        Pattern pattern = Pattern.compile(patternStr);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(pattern);
        oos.close();
        ObjectInputStream ois = new ObjectInputStream(
            new ByteArrayInputStream(baos.toByteArray()));
        Pattern serializedPattern = (Pattern)ois.readObject();
        ois.close();
        Matcher matcher = serializedPattern.matcher(matchStr);
        if (!matcher.matches())
            failCount++;
        if (matcher.groupCount() != 1)
            failCount++;

        report("Serialization");
    }

    private static void gTest() {
        Pattern pattern = Pattern.compile("\\G\\w");
        Matcher matcher = pattern.matcher("abc#x#x");
        matcher.find();
        matcher.find();
        matcher.find();
        if (matcher.find())
            failCount++;

        pattern = Pattern.compile("\\GA*");
        matcher = pattern.matcher("1A2AA3");
        matcher.find();
        if (matcher.find())
            failCount++;

        pattern = Pattern.compile("\\GA*");
        matcher = pattern.matcher("1A2AA3");
        if (!matcher.find(1))
            failCount++;
        matcher.find();
        if (matcher.find())
            failCount++;

        report("\\G");
    }

    private static void zTest() {
        Pattern pattern = Pattern.compile("foo\\Z");
        // Positives
        check(pattern, "foo\u0085", true);
        check(pattern, "foo\u2028", true);
        check(pattern, "foo\u2029", true);
        check(pattern, "foo\n", true);
        check(pattern, "foo\r", true);
        check(pattern, "foo\r\n", true);
        // Negatives
        check(pattern, "fooo", false);
        check(pattern, "foo\n\r", false);

        pattern = Pattern.compile("foo\\Z", Pattern.UNIX_LINES);
        // Positives
        check(pattern, "foo", true);
        check(pattern, "foo\n", true);
        // Negatives
        check(pattern, "foo\r", false);
        check(pattern, "foo\u0085", false);
        check(pattern, "foo\u2028", false);
        check(pattern, "foo\u2029", false);

        report("\\Z");
    }

    private static void replaceFirstTest() {
        Pattern pattern = Pattern.compile("(ab)(c*)");
        Matcher matcher = pattern.matcher("abccczzzabcczzzabccc");
        if (!matcher.replaceFirst("test").equals("testzzzabcczzzabccc"))
            failCount++;

        matcher.reset("zzzabccczzzabcczzzabccczzz");
        if (!matcher.replaceFirst("test").equals("zzztestzzzabcczzzabccczzz"))
            failCount++;

        matcher.reset("zzzabccczzzabcczzzabccczzz");
        String result = matcher.replaceFirst("$1");
        if (!result.equals("zzzabzzzabcczzzabccczzz"))
            failCount++;

        matcher.reset("zzzabccczzzabcczzzabccczzz");
        result = matcher.replaceFirst("$2");
        if (!result.equals("zzzccczzzabcczzzabccczzz"))
            failCount++;

        pattern = Pattern.compile("a*");
        matcher = pattern.matcher("aaaaaaaaaa");
        if (!matcher.replaceFirst("test").equals("test"))
            failCount++;

        pattern = Pattern.compile("a+");
        matcher = pattern.matcher("zzzaaaaaaaaaa");
        if (!matcher.replaceFirst("test").equals("zzztest"))
            failCount++;

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("(ab)(c*)"));
        matcher = pattern.matcher(toSupplementaries("abccczzzabcczzzabccc"));
        if (!matcher.replaceFirst(toSupplementaries("test"))
                .equals(toSupplementaries("testzzzabcczzzabccc")))
            failCount++;

        matcher.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        if (!matcher.replaceFirst(toSupplementaries("test")).
            equals(toSupplementaries("zzztestzzzabcczzzabccczzz")))
            failCount++;

        matcher.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        result = matcher.replaceFirst("$1");
        if (!result.equals(toSupplementaries("zzzabzzzabcczzzabccczzz")))
            failCount++;

        matcher.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        result = matcher.replaceFirst("$2");
        if (!result.equals(toSupplementaries("zzzccczzzabcczzzabccczzz")))
            failCount++;

        pattern = Pattern.compile(toSupplementaries("a*"));
        matcher = pattern.matcher(toSupplementaries("aaaaaaaaaa"));
        if (!matcher.replaceFirst(toSupplementaries("test")).equals(toSupplementaries("test")))
            failCount++;

        pattern = Pattern.compile(toSupplementaries("a+"));
        matcher = pattern.matcher(toSupplementaries("zzzaaaaaaaaaa"));
        if (!matcher.replaceFirst(toSupplementaries("test")).equals(toSupplementaries("zzztest")))
            failCount++;

        report("Replace First");
    }

    private static void unixLinesTest() {
        Pattern pattern = Pattern.compile(".*");
        Matcher matcher = pattern.matcher("aa\u2028blah");
        matcher.find();
        if (!matcher.group(0).equals("aa"))
            failCount++;

        pattern = Pattern.compile(".*", Pattern.UNIX_LINES);
        matcher = pattern.matcher("aa\u2028blah");
        matcher.find();
        if (!matcher.group(0).equals("aa\u2028blah"))
            failCount++;

        pattern = Pattern.compile("[az]$",
                                  Pattern.MULTILINE | Pattern.UNIX_LINES);
        matcher = pattern.matcher("aa\u2028zz");
        check(matcher, "a\u2028", false);

        // Supplementary character test
        pattern = Pattern.compile(".*");
        matcher = pattern.matcher(toSupplementaries("aa\u2028blah"));
        matcher.find();
        if (!matcher.group(0).equals(toSupplementaries("aa")))
            failCount++;

        pattern = Pattern.compile(".*", Pattern.UNIX_LINES);
        matcher = pattern.matcher(toSupplementaries("aa\u2028blah"));
        matcher.find();
        if (!matcher.group(0).equals(toSupplementaries("aa\u2028blah")))
            failCount++;

        pattern = Pattern.compile(toSupplementaries("[az]$"),
                                  Pattern.MULTILINE | Pattern.UNIX_LINES);
        matcher = pattern.matcher(toSupplementaries("aa\u2028zz"));
        check(matcher, toSupplementaries("a\u2028"), false);

        report("Unix Lines");
    }

    private static void commentsTest() {
        int flags = Pattern.COMMENTS;

        Pattern pattern = Pattern.compile("aa \\# aa", flags);
        Matcher matcher = pattern.matcher("aa#aa");
        if (!matcher.matches())
            failCount++;

        pattern = Pattern.compile("aa  # blah", flags);
        matcher = pattern.matcher("aa");
        if (!matcher.matches())
            failCount++;

        pattern = Pattern.compile("aa blah", flags);
        matcher = pattern.matcher("aablah");
        if (!matcher.matches())
             failCount++;

        pattern = Pattern.compile("aa  # blah blech  ", flags);
        matcher = pattern.matcher("aa");
        if (!matcher.matches())
            failCount++;

        pattern = Pattern.compile("aa  # blah\n  ", flags);
        matcher = pattern.matcher("aa");
        if (!matcher.matches())
            failCount++;

        pattern = Pattern.compile("aa  # blah\nbc # blech", flags);
        matcher = pattern.matcher("aabc");
        if (!matcher.matches())
             failCount++;

        pattern = Pattern.compile("aa  # blah\nbc# blech", flags);
        matcher = pattern.matcher("aabc");
        if (!matcher.matches())
             failCount++;

        pattern = Pattern.compile("aa  # blah\nbc\\# blech", flags);
        matcher = pattern.matcher("aabc#blech");
        if (!matcher.matches())
             failCount++;

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("aa \\# aa"), flags);
        matcher = pattern.matcher(toSupplementaries("aa#aa"));
        if (!matcher.matches())
            failCount++;

        pattern = Pattern.compile(toSupplementaries("aa  # blah"), flags);
        matcher = pattern.matcher(toSupplementaries("aa"));
        if (!matcher.matches())
            failCount++;

        pattern = Pattern.compile(toSupplementaries("aa blah"), flags);
        matcher = pattern.matcher(toSupplementaries("aablah"));
        if (!matcher.matches())
             failCount++;

        pattern = Pattern.compile(toSupplementaries("aa  # blah blech  "), flags);
        matcher = pattern.matcher(toSupplementaries("aa"));
        if (!matcher.matches())
            failCount++;

        pattern = Pattern.compile(toSupplementaries("aa  # blah\n  "), flags);
        matcher = pattern.matcher(toSupplementaries("aa"));
        if (!matcher.matches())
            failCount++;

        pattern = Pattern.compile(toSupplementaries("aa  # blah\nbc # blech"), flags);
        matcher = pattern.matcher(toSupplementaries("aabc"));
        if (!matcher.matches())
             failCount++;

        pattern = Pattern.compile(toSupplementaries("aa  # blah\nbc# blech"), flags);
        matcher = pattern.matcher(toSupplementaries("aabc"));
        if (!matcher.matches())
             failCount++;

        pattern = Pattern.compile(toSupplementaries("aa  # blah\nbc\\# blech"), flags);
        matcher = pattern.matcher(toSupplementaries("aabc#blech"));
        if (!matcher.matches())
             failCount++;

        report("Comments");
    }

    private static void caseFoldingTest() { // bug 4504687
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern pattern = Pattern.compile("aa", flags);
        Matcher matcher = pattern.matcher("ab");
        if (matcher.matches())
            failCount++;

        pattern = Pattern.compile("aA", flags);
        matcher = pattern.matcher("ab");
        if (matcher.matches())
            failCount++;

        pattern = Pattern.compile("aa", flags);
        matcher = pattern.matcher("aB");
        if (matcher.matches())
            failCount++;
        matcher = pattern.matcher("Ab");
        if (matcher.matches())
            failCount++;

        // ASCII               "a"
        // Latin-1 Supplement  "a" + grave
        // Cyrillic            "a"
        String[] patterns = new String[] {
            //single
            "a", "\u00e0", "\u0430",
            //slice
            "ab", "\u00e0\u00e1", "\u0430\u0431",
            //class single
            "[a]", "[\u00e0]", "[\u0430]",
            //class range
            "[a-b]", "[\u00e0-\u00e5]", "[\u0430-\u0431]",
            //back reference
            "(a)\\1", "(\u00e0)\\1", "(\u0430)\\1"
        };

        String[] texts = new String[] {
            "A", "\u00c0", "\u0410",
            "AB", "\u00c0\u00c1", "\u0410\u0411",
            "A", "\u00c0", "\u0410",
            "B", "\u00c2", "\u0411",
            "aA", "\u00e0\u00c0", "\u0430\u0410"
        };

        boolean[] expected = new boolean[] {
            true, false, false,
            true, false, false,
            true, false, false,
            true, false, false,
            true, false, false
        };

        flags = Pattern.CASE_INSENSITIVE;
        for (int i = 0; i < patterns.length; i++) {
            pattern = Pattern.compile(patterns[i], flags);
            matcher = pattern.matcher(texts[i]);
            if (matcher.matches() != expected[i]) {
                System.out.println("<1> Failed at " + i);
                failCount++;
            }
        }

        flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        for (int i = 0; i < patterns.length; i++) {
            pattern = Pattern.compile(patterns[i], flags);
            matcher = pattern.matcher(texts[i]);
            if (!matcher.matches()) {
                System.out.println("<2> Failed at " + i);
                failCount++;
            }
        }
        // flag unicode_case alone should do nothing
        flags = Pattern.UNICODE_CASE;
        for (int i = 0; i < patterns.length; i++) {
            pattern = Pattern.compile(patterns[i], flags);
            matcher = pattern.matcher(texts[i]);
            if (matcher.matches()) {
                System.out.println("<3> Failed at " + i);
                failCount++;
            }
        }

        // Special cases: i, I, u+0131 and u+0130
        flags = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE;
        pattern = Pattern.compile("[h-j]+", flags);
        if (!pattern.matcher("\u0131\u0130").matches())
            failCount++;
        report("Case Folding");
    }

    private static void appendTest() {
        Pattern pattern = Pattern.compile("(ab)(cd)");
        Matcher matcher = pattern.matcher("abcd");
        String result = matcher.replaceAll("$2$1");
        if (!result.equals("cdab"))
            failCount++;

        String  s1 = "Swap all: first = 123, second = 456";
        String  s2 = "Swap one: first = 123, second = 456";
        String  r  = "$3$2$1";
        pattern = Pattern.compile("([a-z]+)( *= *)([0-9]+)");
        matcher = pattern.matcher(s1);

        result = matcher.replaceAll(r);
        if (!result.equals("Swap all: 123 = first, 456 = second"))
            failCount++;

        matcher = pattern.matcher(s2);

        if (matcher.find()) {
            StringBuffer sb = new StringBuffer();
            matcher.appendReplacement(sb, r);
            matcher.appendTail(sb);
            result = sb.toString();
            if (!result.equals("Swap one: 123 = first, second = 456"))
                failCount++;
        }

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("(ab)(cd)"));
        matcher = pattern.matcher(toSupplementaries("abcd"));
        result = matcher.replaceAll("$2$1");
        if (!result.equals(toSupplementaries("cdab")))
            failCount++;

        s1 = toSupplementaries("Swap all: first = 123, second = 456");
        s2 = toSupplementaries("Swap one: first = 123, second = 456");
        r  = toSupplementaries("$3$2$1");
        pattern = Pattern.compile(toSupplementaries("([a-z]+)( *= *)([0-9]+)"));
        matcher = pattern.matcher(s1);

        result = matcher.replaceAll(r);
        if (!result.equals(toSupplementaries("Swap all: 123 = first, 456 = second")))
            failCount++;

        matcher = pattern.matcher(s2);

        if (matcher.find()) {
            StringBuffer sb = new StringBuffer();
            matcher.appendReplacement(sb, r);
            matcher.appendTail(sb);
            result = sb.toString();
            if (!result.equals(toSupplementaries("Swap one: 123 = first, second = 456")))
                failCount++;
        }
        report("Append");
    }

    private static void splitTest() {
        Pattern pattern = Pattern.compile(":");
        String[] result = pattern.split("foo:and:boo", 2);
        if (!result[0].equals("foo"))
            failCount++;
        if (!result[1].equals("and:boo"))
            failCount++;
        // Supplementary character test
        Pattern patternX = Pattern.compile(toSupplementaries("X"));
        result = patternX.split(toSupplementaries("fooXandXboo"), 2);
        if (!result[0].equals(toSupplementaries("foo")))
            failCount++;
        if (!result[1].equals(toSupplementaries("andXboo")))
            failCount++;

        CharBuffer cb = CharBuffer.allocate(100);
        cb.put("foo:and:boo");
        cb.flip();
        result = pattern.split(cb);
        if (!result[0].equals("foo"))
            failCount++;
        if (!result[1].equals("and"))
            failCount++;
        if (!result[2].equals("boo"))
            failCount++;

        // Supplementary character test
        CharBuffer cbs = CharBuffer.allocate(100);
        cbs.put(toSupplementaries("fooXandXboo"));
        cbs.flip();
        result = patternX.split(cbs);
        if (!result[0].equals(toSupplementaries("foo")))
            failCount++;
        if (!result[1].equals(toSupplementaries("and")))
            failCount++;
        if (!result[2].equals(toSupplementaries("boo")))
            failCount++;

        String source = "0123456789";
        for (int limit=-2; limit<3; limit++) {
            for (int x=0; x<10; x++) {
                result = source.split(Integer.toString(x), limit);
                int expectedLength = limit < 1 ? 2 : limit;

                if ((limit == 0) && (x == 9)) {
                    // expected dropping of ""
                    if (result.length != 1)
                        failCount++;
                    if (!result[0].equals("012345678")) {
                        failCount++;
                    }
                } else {
                    if (result.length != expectedLength) {
                        failCount++;
                    }
                    if (!result[0].equals(source.substring(0,x))) {
                        if (limit != 1) {
                            failCount++;
                        } else {
                            if (!result[0].equals(source.substring(0,10))) {
                                failCount++;
                            }
                        }
                    }
                    if (expectedLength > 1) { // Check segment 2
                        if (!result[1].equals(source.substring(x+1,10)))
                            failCount++;
                    }
                }
            }
        }
        // Check the case for no match found
        for (int limit=-2; limit<3; limit++) {
            result = source.split("e", limit);
            if (result.length != 1)
                failCount++;
            if (!result[0].equals(source))
                failCount++;
        }
        // Check the case for limit == 0, source = "";
        // split() now returns 0-length for empty source "" see #6559590
        source = "";
        result = source.split("e", 0);
        if (result.length != 1)
            failCount++;
        if (!result[0].equals(source))
            failCount++;

        // Check both split() and splitAsStraem(), especially for zero-lenth
        // input and zero-lenth match cases
        String[][] input = new String[][] {
            { " ",           "Abc Efg Hij" },   // normal non-zero-match
            { " ",           " Abc Efg Hij" },  // leading empty str for non-zero-match
            { " ",           "Abc  Efg Hij" },  // non-zero-match in the middle
            { "(?=\\p{Lu})", "AbcEfgHij" },     // no leading empty str for zero-match
            { "(?=\\p{Lu})", "AbcEfg" },
            { "(?=\\p{Lu})", "Abc" },
            { " ",           "" },              // zero-length input
            { ".*",          "" },

            // some tests from PatternStreamTest.java
            { "4",       "awgqwefg1fefw4vssv1vvv1" },
            { "\u00a3a", "afbfq\u00a3abgwgb\u00a3awngnwggw\u00a3a\u00a3ahjrnhneerh" },
            { "1",       "awgqwefg1fefw4vssv1vvv1" },
            { "1",       "a\u4ebafg1fefw\u4eba4\u9f9cvssv\u9f9c1v\u672c\u672cvv" },
            { "\u56da",  "1\u56da23\u56da456\u56da7890" },
            { "\u56da",  "1\u56da23\u9f9c\u672c\u672c\u56da456\u56da\u9f9c\u672c7890" },
            { "\u56da",  "" },
            { "[ \t,:.]","This is,testing: with\tdifferent separators." }, //multiple septs
            { "o",       "boo:and:foo" },
            { "o",       "booooo:and:fooooo" },
            { "o",       "fooooo:" },
        };

        String[][] expected = new String[][] {
            { "Abc", "Efg", "Hij" },
            { "", "Abc", "Efg", "Hij" },
            { "Abc", "", "Efg", "Hij" },
            { "Abc", "Efg", "Hij" },
            { "Abc", "Efg" },
            { "Abc" },
            { "" },
            { "" },

            { "awgqwefg1fefw", "vssv1vvv1" },
            { "afbfq", "bgwgb", "wngnwggw", "", "hjrnhneerh" },
            { "awgqwefg", "fefw4vssv", "vvv" },
            { "a\u4ebafg", "fefw\u4eba4\u9f9cvssv\u9f9c", "v\u672c\u672cvv" },
            { "1", "23", "456", "7890" },
            { "1", "23\u9f9c\u672c\u672c", "456", "\u9f9c\u672c7890" },
            { "" },
            { "This", "is", "testing", "", "with", "different", "separators" },
            { "b", "", ":and:f" },
            { "b", "", "", "", "", ":and:f" },
            { "f", "", "", "", "", ":" },
        };
        for (int i = 0; i < input.length; i++) {
            pattern = Pattern.compile(input[i][0]);
            if (!Arrays.equals(pattern.split(input[i][1]), expected[i])) {
                failCount++;
            }
            if (input[i][1].length() > 0 &&  // splitAsStream() return empty resulting
                                             // array for zero-length input for now
                !Arrays.equals(pattern.splitAsStream(input[i][1]).toArray(),
                               expected[i])) {
                failCount++;
            }
        }
        report("Split");
    }

    private static void negationTest() {
        Pattern pattern = Pattern.compile("[\\[@^]+");
        Matcher matcher = pattern.matcher("@@@@[[[[^^^^");
        if (!matcher.find())
            failCount++;
        if (!matcher.group(0).equals("@@@@[[[[^^^^"))
            failCount++;
        pattern = Pattern.compile("[@\\[^]+");
        matcher = pattern.matcher("@@@@[[[[^^^^");
        if (!matcher.find())
            failCount++;
        if (!matcher.group(0).equals("@@@@[[[[^^^^"))
            failCount++;
        pattern = Pattern.compile("[@\\[^@]+");
        matcher = pattern.matcher("@@@@[[[[^^^^");
        if (!matcher.find())
            failCount++;
        if (!matcher.group(0).equals("@@@@[[[[^^^^"))
            failCount++;

        pattern = Pattern.compile("\\)");
        matcher = pattern.matcher("xxx)xxx");
        if (!matcher.find())
            failCount++;

        report("Negation");
    }

    private static void ampersandTest() {
        Pattern pattern = Pattern.compile("[&@]+");
        check(pattern, "@@@@&&&&", true);

        pattern = Pattern.compile("[@&]+");
        check(pattern, "@@@@&&&&", true);

        pattern = Pattern.compile("[@\\&]+");
        check(pattern, "@@@@&&&&", true);

        report("Ampersand");
    }

    private static void octalTest() throws Exception {
        Pattern pattern = Pattern.compile("\\u0007");
        Matcher matcher = pattern.matcher("\u0007");
        if (!matcher.matches())
            failCount++;
        pattern = Pattern.compile("\\07");
        matcher = pattern.matcher("\u0007");
        if (!matcher.matches())
            failCount++;
        pattern = Pattern.compile("\\007");
        matcher = pattern.matcher("\u0007");
        if (!matcher.matches())
            failCount++;
        pattern = Pattern.compile("\\0007");
        matcher = pattern.matcher("\u0007");
        if (!matcher.matches())
            failCount++;
        pattern = Pattern.compile("\\040");
        matcher = pattern.matcher("\u0020");
        if (!matcher.matches())
            failCount++;
        pattern = Pattern.compile("\\0403");
        matcher = pattern.matcher("\u00203");
        if (!matcher.matches())
            failCount++;
        pattern = Pattern.compile("\\0103");
        matcher = pattern.matcher("\u0043");
        if (!matcher.matches())
            failCount++;

        report("Octal");
    }

    private static void longPatternTest() throws Exception {
        try {
            Pattern pattern = Pattern.compile(
                "a 32-character-long pattern xxxx");
            pattern = Pattern.compile("a 33-character-long pattern xxxxx");
            pattern = Pattern.compile("a thirty four character long regex");
            StringBuffer patternToBe = new StringBuffer(101);
            for (int i=0; i<100; i++)
                patternToBe.append((char)(97 + i%26));
            pattern = Pattern.compile(patternToBe.toString());
        } catch (PatternSyntaxException e) {
            failCount++;
        }

        // Supplementary character test
        try {
            Pattern pattern = Pattern.compile(
                toSupplementaries("a 32-character-long pattern xxxx"));
            pattern = Pattern.compile(toSupplementaries("a 33-character-long pattern xxxxx"));
            pattern = Pattern.compile(toSupplementaries("a thirty four character long regex"));
            StringBuffer patternToBe = new StringBuffer(101*2);
            for (int i=0; i<100; i++)
                patternToBe.append(Character.toChars(Character.MIN_SUPPLEMENTARY_CODE_POINT
                                                     + 97 + i%26));
            pattern = Pattern.compile(patternToBe.toString());
        } catch (PatternSyntaxException e) {
            failCount++;
        }
        report("LongPattern");
    }

    private static void group0Test() throws Exception {
        Pattern pattern = Pattern.compile("(tes)ting");
        Matcher matcher = pattern.matcher("testing");
        check(matcher, "testing");

        matcher.reset("testing");
        if (matcher.lookingAt()) {
            if (!matcher.group(0).equals("testing"))
                failCount++;
        } else {
            failCount++;
        }

        matcher.reset("testing");
        if (matcher.matches()) {
            if (!matcher.group(0).equals("testing"))
                failCount++;
        } else {
            failCount++;
        }

        pattern = Pattern.compile("(tes)ting");
        matcher = pattern.matcher("testing");
        if (matcher.lookingAt()) {
            if (!matcher.group(0).equals("testing"))
                failCount++;
        } else {
            failCount++;
        }

        pattern = Pattern.compile("^(tes)ting");
        matcher = pattern.matcher("testing");
        if (matcher.matches()) {
            if (!matcher.group(0).equals("testing"))
                failCount++;
        } else {
            failCount++;
        }

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("(tes)ting"));
        matcher = pattern.matcher(toSupplementaries("testing"));
        check(matcher, toSupplementaries("testing"));

        matcher.reset(toSupplementaries("testing"));
        if (matcher.lookingAt()) {
            if (!matcher.group(0).equals(toSupplementaries("testing")))
                failCount++;
        } else {
            failCount++;
        }

        matcher.reset(toSupplementaries("testing"));
        if (matcher.matches()) {
            if (!matcher.group(0).equals(toSupplementaries("testing")))
                failCount++;
        } else {
            failCount++;
        }

        pattern = Pattern.compile(toSupplementaries("(tes)ting"));
        matcher = pattern.matcher(toSupplementaries("testing"));
        if (matcher.lookingAt()) {
            if (!matcher.group(0).equals(toSupplementaries("testing")))
                failCount++;
        } else {
            failCount++;
        }

        pattern = Pattern.compile(toSupplementaries("^(tes)ting"));
        matcher = pattern.matcher(toSupplementaries("testing"));
        if (matcher.matches()) {
            if (!matcher.group(0).equals(toSupplementaries("testing")))
                failCount++;
        } else {
            failCount++;
        }

        report("Group0");
    }

    private static void findIntTest() throws Exception {
        Pattern p = Pattern.compile("blah");
        Matcher m = p.matcher("zzzzblahzzzzzblah");
        boolean result = m.find(2);
        if (!result)
            failCount++;

        p = Pattern.compile("$");
        m = p.matcher("1234567890");
        result = m.find(10);
        if (!result)
            failCount++;
        try {
            result = m.find(11);
            failCount++;
        } catch (IndexOutOfBoundsException e) {
            // correct result
        }

        // Supplementary character test
        p = Pattern.compile(toSupplementaries("blah"));
        m = p.matcher(toSupplementaries("zzzzblahzzzzzblah"));
        result = m.find(2);
        if (!result)
            failCount++;

        report("FindInt");
    }

    private static void emptyPatternTest() throws Exception {
        Pattern p = Pattern.compile("");
        Matcher m = p.matcher("foo");

        // Should find empty pattern at beginning of input
        boolean result = m.find();
        if (result != true)
            failCount++;
        if (m.start() != 0)
            failCount++;

        // Should not match entire input if input is not empty
        m.reset();
        result = m.matches();
        if (result == true)
            failCount++;

        try {
            m.start(0);
            failCount++;
        } catch (IllegalStateException e) {
            // Correct result
        }

        // Should match entire input if input is empty
        m.reset("");
        result = m.matches();
        if (result != true)
            failCount++;

        result = Pattern.matches("", "");
        if (result != true)
            failCount++;

        result = Pattern.matches("", "foo");
        if (result == true)
            failCount++;
        report("EmptyPattern");
    }

    private static void charClassTest() throws Exception {
        Pattern pattern = Pattern.compile("blah[ab]]blech");
        check(pattern, "blahb]blech", true);

        pattern = Pattern.compile("[abc[def]]");
        check(pattern, "b", true);

        // Supplementary character tests
        pattern = Pattern.compile(toSupplementaries("blah[ab]]blech"));
        check(pattern, toSupplementaries("blahb]blech"), true);

        pattern = Pattern.compile(toSupplementaries("[abc[def]]"));
        check(pattern, toSupplementaries("b"), true);

        try {
            // u00ff when UNICODE_CASE
            pattern = Pattern.compile("[ab\u00ffcd]",
                                      Pattern.CASE_INSENSITIVE|
                                      Pattern.UNICODE_CASE);
            check(pattern, "ab\u00ffcd", true);
            check(pattern, "Ab\u0178Cd", true);

            // u00b5 when UNICODE_CASE
            pattern = Pattern.compile("[ab\u00b5cd]",
                                      Pattern.CASE_INSENSITIVE|
                                      Pattern.UNICODE_CASE);
            check(pattern, "ab\u00b5cd", true);
            check(pattern, "Ab\u039cCd", true);
        } catch (Exception e) { failCount++; }

        /* Special cases
           (1)LatinSmallLetterLongS u+017f
           (2)LatinSmallLetterDotlessI u+0131
           (3)LatineCapitalLetterIWithDotAbove u+0130
           (4)KelvinSign u+212a
           (5)AngstromSign u+212b
        */
        int flags = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE;
        pattern = Pattern.compile("[sik\u00c5]+", flags);
        if (!pattern.matcher("\u017f\u0130\u0131\u212a\u212b").matches())
            failCount++;

        try {
            Pattern.compile("[", Pattern.CANON_EQ);
            failCount++;
        } catch (PatternSyntaxException expected) {
        } catch (Exception unexpected) {
            failCount++;
        }

        report("CharClass");
    }

    private static void caretTest() throws Exception {
        Pattern pattern = Pattern.compile("\\w*");
        Matcher matcher = pattern.matcher("a#bc#def##g");
        check(matcher, "a");
        check(matcher, "");
        check(matcher, "bc");
        check(matcher, "");
        check(matcher, "def");
        check(matcher, "");
        check(matcher, "");
        check(matcher, "g");
        check(matcher, "");
        if (matcher.find())
            failCount++;

        pattern = Pattern.compile("^\\w*");
        matcher = pattern.matcher("a#bc#def##g");
        check(matcher, "a");
        if (matcher.find())
            failCount++;

        pattern = Pattern.compile("\\w");
        matcher = pattern.matcher("abc##x");
        check(matcher, "a");
        check(matcher, "b");
        check(matcher, "c");
        check(matcher, "x");
        if (matcher.find())
            failCount++;

        pattern = Pattern.compile("^\\w");
        matcher = pattern.matcher("abc##x");
        check(matcher, "a");
        if (matcher.find())
            failCount++;

        pattern = Pattern.compile("\\A\\p{Alpha}{3}");
        matcher = pattern.matcher("abcdef-ghi\njklmno");
        check(matcher, "abc");
        if (matcher.find())
            failCount++;

        pattern = Pattern.compile("^\\p{Alpha}{3}", Pattern.MULTILINE);
        matcher = pattern.matcher("abcdef-ghi\njklmno");
        check(matcher, "abc");
        check(matcher, "jkl");
        if (matcher.find())
            failCount++;

        pattern = Pattern.compile("^", Pattern.MULTILINE);
        matcher = pattern.matcher("this is some text");
        String result = matcher.replaceAll("X");
        if (!result.equals("Xthis is some text"))
            failCount++;

        pattern = Pattern.compile("^");
        matcher = pattern.matcher("this is some text");
        result = matcher.replaceAll("X");
        if (!result.equals("Xthis is some text"))
            failCount++;

        pattern = Pattern.compile("^", Pattern.MULTILINE | Pattern.UNIX_LINES);
        matcher = pattern.matcher("this is some text\n");
        result = matcher.replaceAll("X");
        if (!result.equals("Xthis is some text\n"))
            failCount++;

        report("Caret");
    }

    private static void groupCaptureTest() throws Exception {
        // Independent group
        Pattern pattern = Pattern.compile("x+(?>y+)z+");
        Matcher matcher = pattern.matcher("xxxyyyzzz");
        matcher.find();
        try {
            String blah = matcher.group(1);
            failCount++;
        } catch (IndexOutOfBoundsException ioobe) {
            // Good result
        }
        // Pure group
        pattern = Pattern.compile("x+(?:y+)z+");
        matcher = pattern.matcher("xxxyyyzzz");
        matcher.find();
        try {
            String blah = matcher.group(1);
            failCount++;
        } catch (IndexOutOfBoundsException ioobe) {
            // Good result
        }

        // Supplementary character tests
        // Independent group
        pattern = Pattern.compile(toSupplementaries("x+(?>y+)z+"));
        matcher = pattern.matcher(toSupplementaries("xxxyyyzzz"));
        matcher.find();
        try {
            String blah = matcher.group(1);
            failCount++;
        } catch (IndexOutOfBoundsException ioobe) {
            // Good result
        }
        // Pure group
        pattern = Pattern.compile(toSupplementaries("x+(?:y+)z+"));
        matcher = pattern.matcher(toSupplementaries("xxxyyyzzz"));
        matcher.find();
        try {
            String blah = matcher.group(1);
            failCount++;
        } catch (IndexOutOfBoundsException ioobe) {
            // Good result
        }

        report("GroupCapture");
    }

    private static void backRefTest() throws Exception {
        Pattern pattern = Pattern.compile("(a*)bc\\1");
        check(pattern, "zzzaabcazzz", true);

        pattern = Pattern.compile("(a*)bc\\1");
        check(pattern, "zzzaabcaazzz", true);

        pattern = Pattern.compile("(abc)(def)\\1");
        check(pattern, "abcdefabc", true);

        pattern = Pattern.compile("(abc)(def)\\3");
        check(pattern, "abcdefabc", false);

        try {
            for (int i = 1; i < 10; i++) {
                // Make sure backref 1-9 are always accepted
                pattern = Pattern.compile("abcdef\\" + i);
                // and fail to match if the target group does not exit
                check(pattern, "abcdef", false);
            }
        } catch(PatternSyntaxException e) {
            failCount++;
        }

        pattern = Pattern.compile("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\11");
        check(pattern, "abcdefghija", false);
        check(pattern, "abcdefghija1", true);

        pattern = Pattern.compile("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)\\11");
        check(pattern, "abcdefghijkk", true);

        pattern = Pattern.compile("(a)bcdefghij\\11");
        check(pattern, "abcdefghija1", true);

        // Supplementary character tests
        pattern = Pattern.compile(toSupplementaries("(a*)bc\\1"));
        check(pattern, toSupplementaries("zzzaabcazzz"), true);

        pattern = Pattern.compile(toSupplementaries("(a*)bc\\1"));
        check(pattern, toSupplementaries("zzzaabcaazzz"), true);

        pattern = Pattern.compile(toSupplementaries("(abc)(def)\\1"));
        check(pattern, toSupplementaries("abcdefabc"), true);

        pattern = Pattern.compile(toSupplementaries("(abc)(def)\\3"));
        check(pattern, toSupplementaries("abcdefabc"), false);

        pattern = Pattern.compile(toSupplementaries("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\11"));
        check(pattern, toSupplementaries("abcdefghija"), false);
        check(pattern, toSupplementaries("abcdefghija1"), true);

        pattern = Pattern.compile(toSupplementaries("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)\\11"));
        check(pattern, toSupplementaries("abcdefghijkk"), true);

        report("BackRef");
    }

    /**
     * Unicode Technical Report #18, section 2.6 End of Line
     * There is no empty line to be matched in the sequence \u000D\u000A
     * but there is an empty line in the sequence \u000A\u000D.
     */
    private static void anchorTest() throws Exception {
        Pattern p = Pattern.compile("^.*$", Pattern.MULTILINE);
        Matcher m = p.matcher("blah1\r\nblah2");
        m.find();
        m.find();
        if (!m.group().equals("blah2"))
            failCount++;

        m.reset("blah1\n\rblah2");
        m.find();
        m.find();
        m.find();
        if (!m.group().equals("blah2"))
            failCount++;

        // Test behavior of $ with \r\n at end of input
        p = Pattern.compile(".+$");
        m = p.matcher("blah1\r\n");
        if (!m.find())
            failCount++;
       if (!m.group().equals("blah1"))
            failCount++;
        if (m.find())
            failCount++;

        // Test behavior of $ with \r\n at end of input in multiline
        p = Pattern.compile(".+$", Pattern.MULTILINE);
        m = p.matcher("blah1\r\n");
        if (!m.find())
            failCount++;
        if (m.find())
            failCount++;

        // Test for $ recognition of \u0085 for bug 4527731
        p = Pattern.compile(".+$", Pattern.MULTILINE);
        m = p.matcher("blah1\u0085");
        if (!m.find())
            failCount++;

        // Supplementary character test
        p = Pattern.compile("^.*$", Pattern.MULTILINE);
        m = p.matcher(toSupplementaries("blah1\r\nblah2"));
        m.find();
        m.find();
        if (!m.group().equals(toSupplementaries("blah2")))
            failCount++;

        m.reset(toSupplementaries("blah1\n\rblah2"));
        m.find();
        m.find();
        m.find();
        if (!m.group().equals(toSupplementaries("blah2")))
            failCount++;

        // Test behavior of $ with \r\n at end of input
        p = Pattern.compile(".+$");
        m = p.matcher(toSupplementaries("blah1\r\n"));
        if (!m.find())
            failCount++;
        if (!m.group().equals(toSupplementaries("blah1")))
            failCount++;
        if (m.find())
            failCount++;

        // Test behavior of $ with \r\n at end of input in multiline
        p = Pattern.compile(".+$", Pattern.MULTILINE);
        m = p.matcher(toSupplementaries("blah1\r\n"));
        if (!m.find())
            failCount++;
        if (m.find())
            failCount++;

        // Test for $ recognition of \u0085 for bug 4527731
        p = Pattern.compile(".+$", Pattern.MULTILINE);
        m = p.matcher(toSupplementaries("blah1\u0085"));
        if (!m.find())
            failCount++;

        report("Anchors");
    }

    /**
     * A basic sanity test of Matcher.lookingAt().
     */
    private static void lookingAtTest() throws Exception {
        Pattern p = Pattern.compile("(ab)(c*)");
        Matcher m = p.matcher("abccczzzabcczzzabccc");

        if (!m.lookingAt())
            failCount++;

        if (!m.group().equals(m.group(0)))
            failCount++;

        m = p.matcher("zzzabccczzzabcczzzabccczzz");
        if (m.lookingAt())
            failCount++;

        // Supplementary character test
        p = Pattern.compile(toSupplementaries("(ab)(c*)"));
        m = p.matcher(toSupplementaries("abccczzzabcczzzabccc"));

        if (!m.lookingAt())
            failCount++;

        if (!m.group().equals(m.group(0)))
            failCount++;

        m = p.matcher(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        if (m.lookingAt())
            failCount++;

        report("Looking At");
    }

    /**
     * A basic sanity test of Matcher.matches().
     */
    private static void matchesTest() throws Exception {
        // matches()
        Pattern p = Pattern.compile("ulb(c*)");
        Matcher m = p.matcher("ulbcccccc");
        if (!m.matches())
            failCount++;

        // find() but not matches()
        m.reset("zzzulbcccccc");
        if (m.matches())
            failCount++;

        // lookingAt() but not matches()
        m.reset("ulbccccccdef");
        if (m.matches())
            failCount++;

        // matches()
        p = Pattern.compile("a|ad");
        m = p.matcher("ad");
        if (!m.matches())
            failCount++;

        // Supplementary character test
        // matches()
        p = Pattern.compile(toSupplementaries("ulb(c*)"));
        m = p.matcher(toSupplementaries("ulbcccccc"));
        if (!m.matches())
            failCount++;

        // find() but not matches()
        m.reset(toSupplementaries("zzzulbcccccc"));
        if (m.matches())
            failCount++;

        // lookingAt() but not matches()
        m.reset(toSupplementaries("ulbccccccdef"));
        if (m.matches())
            failCount++;

        // matches()
        p = Pattern.compile(toSupplementaries("a|ad"));
        m = p.matcher(toSupplementaries("ad"));
        if (!m.matches())
            failCount++;

        report("Matches");
    }

    /**
     * A basic sanity test of Pattern.matches().
     */
    private static void patternMatchesTest() throws Exception {
        // matches()
        if (!Pattern.matches(toSupplementaries("ulb(c*)"),
                             toSupplementaries("ulbcccccc")))
            failCount++;

        // find() but not matches()
        if (Pattern.matches(toSupplementaries("ulb(c*)"),
                            toSupplementaries("zzzulbcccccc")))
            failCount++;

        // lookingAt() but not matches()
        if (Pattern.matches(toSupplementaries("ulb(c*)"),
                            toSupplementaries("ulbccccccdef")))
            failCount++;

        // Supplementary character test
        // matches()
        if (!Pattern.matches(toSupplementaries("ulb(c*)"),
                             toSupplementaries("ulbcccccc")))
            failCount++;

        // find() but not matches()
        if (Pattern.matches(toSupplementaries("ulb(c*)"),
                            toSupplementaries("zzzulbcccccc")))
            failCount++;

        // lookingAt() but not matches()
        if (Pattern.matches(toSupplementaries("ulb(c*)"),
                            toSupplementaries("ulbccccccdef")))
            failCount++;

        report("Pattern Matches");
    }

    /**
     * Canonical equivalence testing. Tests the ability of the engine
     * to match sequences that are not explicitly specified in the
     * pattern when they are considered equivalent by the Unicode Standard.
     */
    private static void ceTest() throws Exception {
        // Decomposed char outside char classes
        Pattern p = Pattern.compile("testa\u030a", Pattern.CANON_EQ);
        Matcher m = p.matcher("test\u00e5");
        if (!m.matches())
            failCount++;

        m.reset("testa\u030a");
        if (!m.matches())
            failCount++;

        // Composed char outside char classes
        p = Pattern.compile("test\u00e5", Pattern.CANON_EQ);
        m = p.matcher("test\u00e5");
        if (!m.matches())
            failCount++;

        m.reset("testa\u030a");
        if (!m.find())
            failCount++;

        // Decomposed char inside a char class
        p = Pattern.compile("test[abca\u030a]", Pattern.CANON_EQ);
        m = p.matcher("test\u00e5");
        if (!m.find())
            failCount++;

        m.reset("testa\u030a");
        if (!m.find())
            failCount++;

        // Composed char inside a char class
        p = Pattern.compile("test[abc\u00e5def\u00e0]", Pattern.CANON_EQ);
        m = p.matcher("test\u00e5");
        if (!m.find())
            failCount++;

        m.reset("testa\u0300");
        if (!m.find())
            failCount++;

        m.reset("testa\u030a");
        if (!m.find())
            failCount++;

        // Marks that cannot legally change order and be equivalent
        p = Pattern.compile("testa\u0308\u0300", Pattern.CANON_EQ);
        check(p, "testa\u0308\u0300", true);
        check(p, "testa\u0300\u0308", false);

        // Marks that can legally change order and be equivalent
        p = Pattern.compile("testa\u0308\u0323", Pattern.CANON_EQ);
        check(p, "testa\u0308\u0323", true);
        check(p, "testa\u0323\u0308", true);

        // Test all equivalences of the sequence a\u0308\u0323\u0300
        p = Pattern.compile("testa\u0308\u0323\u0300", Pattern.CANON_EQ);
        check(p, "testa\u0308\u0323\u0300", true);
        check(p, "testa\u0323\u0308\u0300", true);
        check(p, "testa\u0308\u0300\u0323", true);
        check(p, "test\u00e4\u0323\u0300", true);
        check(p, "test\u00e4\u0300\u0323", true);

        /*
         * The following canonical equivalence tests don't work. Bug id: 4916384.
         *
        // Decomposed hangul (jamos)
        p = Pattern.compile("\u1100\u1161", Pattern.CANON_EQ);
        m = p.matcher("\u1100\u1161");
        if (!m.matches())
            failCount++;

        m.reset("\uac00");
        if (!m.matches())
            failCount++;

        // Composed hangul
        p = Pattern.compile("\uac00", Pattern.CANON_EQ);
        m = p.matcher("\u1100\u1161");
        if (!m.matches())
            failCount++;

        m.reset("\uac00");
        if (!m.matches())
            failCount++;

        // Decomposed supplementary outside char classes
        p = Pattern.compile("test\ud834\uddbc\ud834\udd6f", Pattern.CANON_EQ);
        m = p.matcher("test\ud834\uddc0");
        if (!m.matches())
            failCount++;

        m.reset("test\ud834\uddbc\ud834\udd6f");
        if (!m.matches())
            failCount++;

        // Composed supplementary outside char classes
        p = Pattern.compile("test\ud834\uddc0", Pattern.CANON_EQ);
        m.reset("test\ud834\uddbc\ud834\udd6f");
        if (!m.matches())
            failCount++;

        m = p.matcher("test\ud834\uddc0");
        if (!m.matches())
            failCount++;

        */

        report("Canonical Equivalence");
    }

    /**
     * A basic sanity test of Matcher.replaceAll().
     */
    private static void globalSubstitute() throws Exception {
        // Global substitution with a literal
        Pattern p = Pattern.compile("(ab)(c*)");
        Matcher m = p.matcher("abccczzzabcczzzabccc");
        if (!m.replaceAll("test").equals("testzzztestzzztest"))
            failCount++;

        m.reset("zzzabccczzzabcczzzabccczzz");
        if (!m.replaceAll("test").equals("zzztestzzztestzzztestzzz"))
            failCount++;

        // Global substitution with groups
        m.reset("zzzabccczzzabcczzzabccczzz");
        String result = m.replaceAll("$1");
        if (!result.equals("zzzabzzzabzzzabzzz"))
            failCount++;

        // Supplementary character test
        // Global substitution with a literal
        p = Pattern.compile(toSupplementaries("(ab)(c*)"));
        m = p.matcher(toSupplementaries("abccczzzabcczzzabccc"));
        if (!m.replaceAll(toSupplementaries("test")).
            equals(toSupplementaries("testzzztestzzztest")))
            failCount++;

        m.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        if (!m.replaceAll(toSupplementaries("test")).
            equals(toSupplementaries("zzztestzzztestzzztestzzz")))
            failCount++;

        // Global substitution with groups
        m.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        result = m.replaceAll("$1");
        if (!result.equals(toSupplementaries("zzzabzzzabzzzabzzz")))
            failCount++;

        report("Global Substitution");
    }

    /**
     * Tests the usage of Matcher.appendReplacement() with literal
     * and group substitutions.
     */
    private static void stringbufferSubstitute() throws Exception {
        // SB substitution with literal
        String blah = "zzzblahzzz";
        Pattern p = Pattern.compile("blah");
        Matcher m = p.matcher(blah);
        StringBuffer result = new StringBuffer();
        try {
            m.appendReplacement(result, "blech");
            failCount++;
        } catch (IllegalStateException e) {
        }
        m.find();
        m.appendReplacement(result, "blech");
        if (!result.toString().equals("zzzblech"))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals("zzzblechzzz"))
            failCount++;

        // SB substitution with groups
        blah = "zzzabcdzzz";
        p = Pattern.compile("(ab)(cd)*");
        m = p.matcher(blah);
        result = new StringBuffer();
        try {
            m.appendReplacement(result, "$1");
            failCount++;
        } catch (IllegalStateException e) {
        }
        m.find();
        m.appendReplacement(result, "$1");
        if (!result.toString().equals("zzzab"))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals("zzzabzzz"))
            failCount++;

        // SB substitution with 3 groups
        blah = "zzzabcdcdefzzz";
        p = Pattern.compile("(ab)(cd)*(ef)");
        m = p.matcher(blah);
        result = new StringBuffer();
        try {
            m.appendReplacement(result, "$1w$2w$3");
            failCount++;
        } catch (IllegalStateException e) {
        }
        m.find();
        m.appendReplacement(result, "$1w$2w$3");
        if (!result.toString().equals("zzzabwcdwef"))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals("zzzabwcdwefzzz"))
            failCount++;

        // SB substitution with groups and three matches
        // skipping middle match
        blah = "zzzabcdzzzabcddzzzabcdzzz";
        p = Pattern.compile("(ab)(cd*)");
        m = p.matcher(blah);
        result = new StringBuffer();
        try {
            m.appendReplacement(result, "$1");
            failCount++;
        } catch (IllegalStateException e) {
        }
        m.find();
        m.appendReplacement(result, "$1");
        if (!result.toString().equals("zzzab"))
            failCount++;

        m.find();
        m.find();
        m.appendReplacement(result, "$2");
        if (!result.toString().equals("zzzabzzzabcddzzzcd"))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals("zzzabzzzabcddzzzcdzzz"))
            failCount++;

        // Check to make sure escaped $ is ignored
        blah = "zzzabcdcdefzzz";
        p = Pattern.compile("(ab)(cd)*(ef)");
        m = p.matcher(blah);
        result = new StringBuffer();
        m.find();
        m.appendReplacement(result, "$1w\\$2w$3");
        if (!result.toString().equals("zzzabw$2wef"))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals("zzzabw$2wefzzz"))
            failCount++;

        // Check to make sure a reference to nonexistent group causes error
        blah = "zzzabcdcdefzzz";
        p = Pattern.compile("(ab)(cd)*(ef)");
        m = p.matcher(blah);
        result = new StringBuffer();
        m.find();
        try {
            m.appendReplacement(result, "$1w$5w$3");
            failCount++;
        } catch (IndexOutOfBoundsException ioobe) {
            // Correct result
        }

        // Check double digit group references
        blah = "zzz123456789101112zzz";
        p = Pattern.compile("(1)(2)(3)(4)(5)(6)(7)(8)(9)(10)(11)");
        m = p.matcher(blah);
        result = new StringBuffer();
        m.find();
        m.appendReplacement(result, "$1w$11w$3");
        if (!result.toString().equals("zzz1w11w3"))
            failCount++;

        // Check to make sure it backs off $15 to $1 if only three groups
        blah = "zzzabcdcdefzzz";
        p = Pattern.compile("(ab)(cd)*(ef)");
        m = p.matcher(blah);
        result = new StringBuffer();
        m.find();
        m.appendReplacement(result, "$1w$15w$3");
        if (!result.toString().equals("zzzabwab5wef"))
            failCount++;


        // Supplementary character test
        // SB substitution with literal
        blah = toSupplementaries("zzzblahzzz");
        p = Pattern.compile(toSupplementaries("blah"));
        m = p.matcher(blah);
        result = new StringBuffer();
        try {
            m.appendReplacement(result, toSupplementaries("blech"));
            failCount++;
        } catch (IllegalStateException e) {
        }
        m.find();
        m.appendReplacement(result, toSupplementaries("blech"));
        if (!result.toString().equals(toSupplementaries("zzzblech")))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals(toSupplementaries("zzzblechzzz")))
            failCount++;

        // SB substitution with groups
        blah = toSupplementaries("zzzabcdzzz");
        p = Pattern.compile(toSupplementaries("(ab)(cd)*"));
        m = p.matcher(blah);
        result = new StringBuffer();
        try {
            m.appendReplacement(result, "$1");
            failCount++;
        } catch (IllegalStateException e) {
        }
        m.find();
        m.appendReplacement(result, "$1");
        if (!result.toString().equals(toSupplementaries("zzzab")))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals(toSupplementaries("zzzabzzz")))
            failCount++;

        // SB substitution with 3 groups
        blah = toSupplementaries("zzzabcdcdefzzz");
        p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        m = p.matcher(blah);
        result = new StringBuffer();
        try {
            m.appendReplacement(result, toSupplementaries("$1w$2w$3"));
            failCount++;
        } catch (IllegalStateException e) {
        }
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w$2w$3"));
        if (!result.toString().equals(toSupplementaries("zzzabwcdwef")))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals(toSupplementaries("zzzabwcdwefzzz")))
            failCount++;

        // SB substitution with groups and three matches
        // skipping middle match
        blah = toSupplementaries("zzzabcdzzzabcddzzzabcdzzz");
        p = Pattern.compile(toSupplementaries("(ab)(cd*)"));
        m = p.matcher(blah);
        result = new StringBuffer();
        try {
            m.appendReplacement(result, "$1");
            failCount++;
        } catch (IllegalStateException e) {
        }
        m.find();
        m.appendReplacement(result, "$1");
        if (!result.toString().equals(toSupplementaries("zzzab")))
            failCount++;

        m.find();
        m.find();
        m.appendReplacement(result, "$2");
        if (!result.toString().equals(toSupplementaries("zzzabzzzabcddzzzcd")))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals(toSupplementaries("zzzabzzzabcddzzzcdzzz")))
            failCount++;

        // Check to make sure escaped $ is ignored
        blah = toSupplementaries("zzzabcdcdefzzz");
        p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        m = p.matcher(blah);
        result = new StringBuffer();
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w\\$2w$3"));
        if (!result.toString().equals(toSupplementaries("zzzabw$2wef")))
            failCount++;

        m.appendTail(result);
        if (!result.toString().equals(toSupplementaries("zzzabw$2wefzzz")))
            failCount++;

        // Check to make sure a reference to nonexistent group causes error
        blah = toSupplementaries("zzzabcdcdefzzz");
        p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        m = p.matcher(blah);
        result = new StringBuffer();
        m.find();
        try {
            m.appendReplacement(result, toSupplementaries("$1w$5w$3"));
            failCount++;
        } catch (IndexOutOfBoundsException ioobe) {
            // Correct result
        }

        // Check double digit group references
        blah = toSupplementaries("zzz123456789101112zzz");
        p = Pattern.compile("(1)(2)(3)(4)(5)(6)(7)(8)(9)(10)(11)");
        m = p.matcher(blah);
        result = new StringBuffer();
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w$11w$3"));
        if (!result.toString().equals(toSupplementaries("zzz1w11w3")))
            failCount++;

        // Check to make sure it backs off $15 to $1 if only three groups
        blah = toSupplementaries("zzzabcdcdefzzz");
        p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        m = p.matcher(blah);
        result = new StringBuffer();
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w$15w$3"));
        if (!result.toString().equals(toSupplementaries("zzzabwab5wef")))
            failCount++;

        // Check nothing has been appended into the output buffer if
        // the replacement string triggers IllegalArgumentException.
        p = Pattern.compile("(abc)");
        m = p.matcher("abcd");
        result = new StringBuffer();
        m.find();
        try {
            m.appendReplacement(result, ("xyz$g"));
            failCount++;
        } catch (IllegalArgumentException iae) {
            if (result.length() != 0)
                failCount++;
        }

        report("SB Substitution");
    }

    /*
     * 5 groups of characters are created to make a substitution string.
     * A base string will be created including random lead chars, the
     * substitution string, and random trailing chars.
     * A pattern containing the 5 groups is searched for and replaced with:
     * random group + random string + random group.
     * The results are checked for correctness.
     */
    private static void substitutionBasher() {
        for (int runs = 0; runs<1000; runs++) {
            // Create a base string to work in
            int leadingChars = generator.nextInt(10);
            StringBuffer baseBuffer = new StringBuffer(100);
            String leadingString = getRandomAlphaString(leadingChars);
            baseBuffer.append(leadingString);

            // Create 5 groups of random number of random chars
            // Create the string to substitute
            // Create the pattern string to search for
            StringBuffer bufferToSub = new StringBuffer(25);
            StringBuffer bufferToPat = new StringBuffer(50);
            String[] groups = new String[5];
            for(int i=0; i<5; i++) {
                int aGroupSize = generator.nextInt(5)+1;
                groups[i] = getRandomAlphaString(aGroupSize);
                bufferToSub.append(groups[i]);
                bufferToPat.append('(');
                bufferToPat.append(groups[i]);
                bufferToPat.append(')');
            }
            String stringToSub = bufferToSub.toString();
            String pattern = bufferToPat.toString();

            // Place sub string into working string at random index
            baseBuffer.append(stringToSub);

            // Append random chars to end
            int trailingChars = generator.nextInt(10);
            String trailingString = getRandomAlphaString(trailingChars);
            baseBuffer.append(trailingString);
            String baseString = baseBuffer.toString();

            // Create test pattern and matcher
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(baseString);

            // Reject candidate if pattern happens to start early
            m.find();
            if (m.start() < leadingChars)
                continue;

            // Reject candidate if more than one match
            if (m.find())
                continue;

            // Construct a replacement string with :
            // random group + random string + random group
            StringBuffer bufferToRep = new StringBuffer();
            int groupIndex1 = generator.nextInt(5);
            bufferToRep.append("$" + (groupIndex1 + 1));
            String randomMidString = getRandomAlphaString(5);
            bufferToRep.append(randomMidString);
            int groupIndex2 = generator.nextInt(5);
            bufferToRep.append("$" + (groupIndex2 + 1));
            String replacement = bufferToRep.toString();

            // Do the replacement
            String result = m.replaceAll(replacement);

            // Construct expected result
            StringBuffer bufferToRes = new StringBuffer();
            bufferToRes.append(leadingString);
            bufferToRes.append(groups[groupIndex1]);
            bufferToRes.append(randomMidString);
            bufferToRes.append(groups[groupIndex2]);
            bufferToRes.append(trailingString);
            String expectedResult = bufferToRes.toString();

            // Check results
            if (!result.equals(expectedResult))
                failCount++;
        }

        report("Substitution Basher");
    }

    /**
     * Checks the handling of some escape sequences that the Pattern
     * class should process instead of the java compiler. These are
     * not in the file because the escapes should be be processed
     * by the Pattern class when the regex is compiled.
     */
    private static void escapes() throws Exception {
        Pattern p = Pattern.compile("\\043");
        Matcher m = p.matcher("#");
        if (!m.find())
            failCount++;

        p = Pattern.compile("\\x23");
        m = p.matcher("#");
        if (!m.find())
            failCount++;

        p = Pattern.compile("\\u0023");
        m = p.matcher("#");
        if (!m.find())
            failCount++;

        report("Escape sequences");
    }

    /**
     * Checks the handling of blank input situations. These
     * tests are incompatible with my test file format.
     */
    private static void blankInput() throws Exception {
        Pattern p = Pattern.compile("abc", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher("");
        if (m.find())
            failCount++;

        p = Pattern.compile("a*", Pattern.CASE_INSENSITIVE);
        m = p.matcher("");
        if (!m.find())
            failCount++;

        p = Pattern.compile("abc");
        m = p.matcher("");
        if (m.find())
            failCount++;

        p = Pattern.compile("a*");
        m = p.matcher("");
        if (!m.find())
            failCount++;

        report("Blank input");
    }

    /**
     * Tests the Boyer-Moore pattern matching of a character sequence
     * on randomly generated patterns.
     */
    private static void bm() throws Exception {
        doBnM('a');
        report("Boyer Moore (ASCII)");

        doBnM(Character.MIN_SUPPLEMENTARY_CODE_POINT - 10);
        report("Boyer Moore (Supplementary)");
    }

    private static void doBnM(int baseCharacter) throws Exception {
        int achar=0;

        for (int i=0; i<100; i++) {
            // Create a short pattern to search for
            int patternLength = generator.nextInt(7) + 4;
            StringBuffer patternBuffer = new StringBuffer(patternLength);
            String pattern;
            retry: for (;;) {
                for (int x=0; x<patternLength; x++) {
                    int ch = baseCharacter + generator.nextInt(26);
                    if (Character.isSupplementaryCodePoint(ch)) {
                        patternBuffer.append(Character.toChars(ch));
                    } else {
                        patternBuffer.append((char)ch);
                    }
                }
                pattern = patternBuffer.toString();

                // Avoid patterns that start and end with the same substring
                // See JDK-6854417
                for (int x=1; x <patternLength; x++) {
                    if (pattern.startsWith(pattern.substring(x)))
                        continue retry;
                }
                break;
            }
            Pattern p = Pattern.compile(pattern);

            // Create a buffer with random ASCII chars that does
            // not match the sample
            String toSearch = null;
            StringBuffer s = null;
            Matcher m = p.matcher("");
            do {
                s = new StringBuffer(100);
                for (int x=0; x<100; x++) {
                    int ch = baseCharacter + generator.nextInt(26);
                    if (Character.isSupplementaryCodePoint(ch)) {
                        s.append(Character.toChars(ch));
                    } else {
                        s.append((char)ch);
                    }
                }
                toSearch = s.toString();
                m.reset(toSearch);
            } while (m.find());

            // Insert the pattern at a random spot
            int insertIndex = generator.nextInt(99);
            if (Character.isLowSurrogate(s.charAt(insertIndex)))
                insertIndex++;
            s = s.insert(insertIndex, pattern);
            toSearch = s.toString();

            // Make sure that the pattern is found
            m.reset(toSearch);
            if (!m.find())
                failCount++;

            // Make sure that the match text is the pattern
            if (!m.group().equals(pattern))
                failCount++;

            // Make sure match occured at insertion point
            if (m.start() != insertIndex)
                failCount++;
        }
    }

    /**
     * Tests the matching of slices on randomly generated patterns.
     * The Boyer-Moore optimization is not done on these patterns
     * because it uses unicode case folding.
     */
    private static void slice() throws Exception {
        doSlice(Character.MAX_VALUE);
        report("Slice");

        doSlice(Character.MAX_CODE_POINT);
        report("Slice (Supplementary)");
    }

    private static void doSlice(int maxCharacter) throws Exception {
        Random generator = new Random();
        int achar=0;

        for (int i=0; i<100; i++) {
            // Create a short pattern to search for
            int patternLength = generator.nextInt(7) + 4;
            StringBuffer patternBuffer = new StringBuffer(patternLength);
            for (int x=0; x<patternLength; x++) {
                int randomChar = 0;
                while (!Character.isLetterOrDigit(randomChar))
                    randomChar = generator.nextInt(maxCharacter);
                if (Character.isSupplementaryCodePoint(randomChar)) {
                    patternBuffer.append(Character.toChars(randomChar));
                } else {
                    patternBuffer.append((char) randomChar);
                }
            }
            String pattern =  patternBuffer.toString();
            Pattern p = Pattern.compile(pattern, Pattern.UNICODE_CASE);

            // Create a buffer with random chars that does not match the sample
            String toSearch = null;
            StringBuffer s = null;
            Matcher m = p.matcher("");
            do {
                s = new StringBuffer(100);
                for (int x=0; x<100; x++) {
                    int randomChar = 0;
                    while (!Character.isLetterOrDigit(randomChar))
                        randomChar = generator.nextInt(maxCharacter);
                    if (Character.isSupplementaryCodePoint(randomChar)) {
                        s.append(Character.toChars(randomChar));
                    } else {
                        s.append((char) randomChar);
                    }
                }
                toSearch = s.toString();
                m.reset(toSearch);
            } while (m.find());

            // Insert the pattern at a random spot
            int insertIndex = generator.nextInt(99);
            if (Character.isLowSurrogate(s.charAt(insertIndex)))
                insertIndex++;
            s = s.insert(insertIndex, pattern);
            toSearch = s.toString();

            // Make sure that the pattern is found
            m.reset(toSearch);
            if (!m.find())
                failCount++;

            // Make sure that the match text is the pattern
            if (!m.group().equals(pattern))
                failCount++;

            // Make sure match occured at insertion point
            if (m.start() != insertIndex)
                failCount++;
        }
    }

    private static void explainFailure(String pattern, String data,
                                       String expected, String actual) {
        System.err.println("----------------------------------------");
        System.err.println("Pattern = "+pattern);
        System.err.println("Data = "+data);
        System.err.println("Expected = " + expected);
        System.err.println("Actual   = " + actual);
    }

    private static void explainFailure(String pattern, String data,
                                       Throwable t) {
        System.err.println("----------------------------------------");
        System.err.println("Pattern = "+pattern);
        System.err.println("Data = "+data);
        t.printStackTrace(System.err);
    }

    // Testing examples from a file

    /**
     * Goes through the file "TestCases.txt" and creates many patterns
     * described in the file, matching the patterns against input lines in
     * the file, and comparing the results against the correct results
     * also found in the file. The file format is described in comments
     * at the head of the file.
     */
    private static void processFile(String fileName) throws Exception {
        File testCases = new File(System.getProperty("test.src", "."),
                                  fileName);
        FileInputStream in = new FileInputStream(testCases);
        BufferedReader r = new BufferedReader(new InputStreamReader(in));

        // Process next test case.
        String aLine;
        while((aLine = r.readLine()) != null) {
            // Read a line for pattern
            String patternString = grabLine(r);
            Pattern p = null;
            try {
                p = compileTestPattern(patternString);
            } catch (PatternSyntaxException e) {
                String dataString = grabLine(r);
                String expectedResult = grabLine(r);
                if (expectedResult.startsWith("error"))
                    continue;
                explainFailure(patternString, dataString, e);
                failCount++;
                continue;
            }

            // Read a line for input string
            String dataString = grabLine(r);
            Matcher m = p.matcher(dataString);
            StringBuffer result = new StringBuffer();

            // Check for IllegalStateExceptions before a match
            failCount += preMatchInvariants(m);

            boolean found = m.find();

            if (found)
                failCount += postTrueMatchInvariants(m);
            else
                failCount += postFalseMatchInvariants(m);

            if (found) {
                result.append("true ");
                result.append(m.group(0) + " ");
            } else {
                result.append("false ");
            }

            result.append(m.groupCount());

            if (found) {
                for (int i=1; i<m.groupCount()+1; i++)
                    if (m.group(i) != null)
                        result.append(" " +m.group(i));
            }

            // Read a line for the expected result
            String expectedResult = grabLine(r);

            if (!result.toString().equals(expectedResult)) {
                explainFailure(patternString, dataString, expectedResult, result.toString());
                failCount++;
            }
        }

        report(fileName);
    }

    private static int preMatchInvariants(Matcher m) {
        int failCount = 0;
        try {
            m.start();
            failCount++;
        } catch (IllegalStateException ise) {}
        try {
            m.end();
            failCount++;
        } catch (IllegalStateException ise) {}
        try {
            m.group();
            failCount++;
        } catch (IllegalStateException ise) {}
        return failCount;
    }

    private static int postFalseMatchInvariants(Matcher m) {
        int failCount = 0;
        try {
            m.group();
            failCount++;
        } catch (IllegalStateException ise) {}
        try {
            m.start();
            failCount++;
        } catch (IllegalStateException ise) {}
        try {
            m.end();
            failCount++;
        } catch (IllegalStateException ise) {}
        return failCount;
    }

    private static int postTrueMatchInvariants(Matcher m) {
        int failCount = 0;
        //assert(m.start() = m.start(0);
        if (m.start() != m.start(0))
            failCount++;
        //assert(m.end() = m.end(0);
        if (m.start() != m.start(0))
            failCount++;
        //assert(m.group() = m.group(0);
        if (!m.group().equals(m.group(0)))
            failCount++;
        try {
            m.group(50);
            failCount++;
        } catch (IndexOutOfBoundsException ise) {}

        return failCount;
    }

    private static Pattern compileTestPattern(String patternString) {
        if (!patternString.startsWith("'")) {
            return Pattern.compile(patternString);
        }

        int break1 = patternString.lastIndexOf("'");
        String flagString = patternString.substring(
                                          break1+1, patternString.length());
        patternString = patternString.substring(1, break1);

        if (flagString.equals("i"))
            return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);

        if (flagString.equals("m"))
            return Pattern.compile(patternString, Pattern.MULTILINE);

        return Pattern.compile(patternString);
    }

    /**
     * Reads a line from the input file. Keeps reading lines until a non
     * empty non comment line is read. If the line contains a \n then
     * these two characters are replaced by a newline char. If a \\uxxxx
     * sequence is read then the sequence is replaced by the unicode char.
     */
    private static String grabLine(BufferedReader r) throws Exception {
        int index = 0;
        String line = r.readLine();
        while (line.startsWith("//") || line.length() < 1)
            line = r.readLine();
        while ((index = line.indexOf("\\n")) != -1) {
            StringBuffer temp = new StringBuffer(line);
            temp.replace(index, index+2, "\n");
            line = temp.toString();
        }
        while ((index = line.indexOf("\\u")) != -1) {
            StringBuffer temp = new StringBuffer(line);
            String value = temp.substring(index+2, index+6);
            char aChar = (char)Integer.parseInt(value, 16);
            String unicodeChar = "" + aChar;
            temp.replace(index, index+6, unicodeChar);
            line = temp.toString();
        }

        return line;
    }

    private static void check(Pattern p, String s, String g, String expected) {
        Matcher m = p.matcher(s);
        m.find();
        if (!m.group(g).equals(expected) ||
            s.charAt(m.start(g)) != expected.charAt(0) ||
            s.charAt(m.end(g) - 1) != expected.charAt(expected.length() - 1))
            failCount++;
    }

    private static void checkReplaceFirst(String p, String s, String r, String expected)
    {
        if (!expected.equals(Pattern.compile(p)
                                    .matcher(s)
                                    .replaceFirst(r)))
            failCount++;
    }

    private static void checkReplaceAll(String p, String s, String r, String expected)
    {
        if (!expected.equals(Pattern.compile(p)
                                    .matcher(s)
                                    .replaceAll(r)))
            failCount++;
    }

    private static void checkExpectedFail(String p) {
        try {
            Pattern.compile(p);
        } catch (PatternSyntaxException pse) {
            //pse.printStackTrace();
            return;
        }
        failCount++;
    }

    private static void checkExpectedIAE(Matcher m, String g) {
        m.find();
        try {
            m.group(g);
        } catch (IllegalArgumentException x) {
            //iae.printStackTrace();
            try {
                m.start(g);
            } catch (IllegalArgumentException xx) {
                try {
                    m.start(g);
                } catch (IllegalArgumentException xxx) {
                    return;
                }
            }
        }
        failCount++;
    }

    private static void checkExpectedNPE(Matcher m) {
        m.find();
        try {
            m.group(null);
        } catch (NullPointerException x) {
            try {
                m.start(null);
            } catch (NullPointerException xx) {
                try {
                    m.end(null);
                } catch (NullPointerException xxx) {
                    return;
                }
            }
        }
        failCount++;
    }

    private static void namedGroupCaptureTest() throws Exception {
        check(Pattern.compile("x+(?<gname>y+)z+"),
              "xxxyyyzzz",
              "gname",
              "yyy");

        check(Pattern.compile("x+(?<gname8>y+)z+"),
              "xxxyyyzzz",
              "gname8",
              "yyy");

        //backref
        Pattern pattern = Pattern.compile("(a*)bc\\1");
        check(pattern, "zzzaabcazzz", true);  // found "abca"

        check(Pattern.compile("(?<gname>a*)bc\\k<gname>"),
              "zzzaabcaazzz", true);

        check(Pattern.compile("(?<gname>abc)(def)\\k<gname>"),
              "abcdefabc", true);

        check(Pattern.compile("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(?<gname>k)\\k<gname>"),
              "abcdefghijkk", true);

        // Supplementary character tests
        check(Pattern.compile("(?<gname>" + toSupplementaries("a*)bc") + "\\k<gname>"),
              toSupplementaries("zzzaabcazzz"), true);

        check(Pattern.compile("(?<gname>" + toSupplementaries("a*)bc") + "\\k<gname>"),
              toSupplementaries("zzzaabcaazzz"), true);

        check(Pattern.compile("(?<gname>" + toSupplementaries("abc)(def)") + "\\k<gname>"),
              toSupplementaries("abcdefabc"), true);

        check(Pattern.compile(toSupplementaries("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)") +
                              "(?<gname>" +
                              toSupplementaries("k)") + "\\k<gname>"),
              toSupplementaries("abcdefghijkk"), true);

        check(Pattern.compile("x+(?<gname>y+)z+\\k<gname>"),
              "xxxyyyzzzyyy",
              "gname",
              "yyy");

        //replaceFirst/All
        checkReplaceFirst("(?<gn>ab)(c*)",
                          "abccczzzabcczzzabccc",
                          "${gn}",
                          "abzzzabcczzzabccc");

        checkReplaceAll("(?<gn>ab)(c*)",
                        "abccczzzabcczzzabccc",
                        "${gn}",
                        "abzzzabzzzab");


        checkReplaceFirst("(?<gn>ab)(c*)",
                          "zzzabccczzzabcczzzabccczzz",
                          "${gn}",
                          "zzzabzzzabcczzzabccczzz");

        checkReplaceAll("(?<gn>ab)(c*)",
                        "zzzabccczzzabcczzzabccczzz",
                        "${gn}",
                        "zzzabzzzabzzzabzzz");

        checkReplaceFirst("(?<gn1>ab)(?<gn2>c*)",
                          "zzzabccczzzabcczzzabccczzz",
                          "${gn2}",
                          "zzzccczzzabcczzzabccczzz");

        checkReplaceAll("(?<gn1>ab)(?<gn2>c*)",
                        "zzzabccczzzabcczzzabccczzz",
                        "${gn2}",
                        "zzzccczzzcczzzccczzz");

        //toSupplementaries("(ab)(c*)"));
        checkReplaceFirst("(?<gn1>" + toSupplementaries("ab") +
                           ")(?<gn2>" + toSupplementaries("c") + "*)",
                          toSupplementaries("abccczzzabcczzzabccc"),
                          "${gn1}",
                          toSupplementaries("abzzzabcczzzabccc"));


        checkReplaceAll("(?<gn1>" + toSupplementaries("ab") +
                        ")(?<gn2>" + toSupplementaries("c") + "*)",
                        toSupplementaries("abccczzzabcczzzabccc"),
                        "${gn1}",
                        toSupplementaries("abzzzabzzzab"));

        checkReplaceFirst("(?<gn1>" + toSupplementaries("ab") +
                           ")(?<gn2>" + toSupplementaries("c") + "*)",
                          toSupplementaries("abccczzzabcczzzabccc"),
                          "${gn2}",
                          toSupplementaries("ccczzzabcczzzabccc"));


        checkReplaceAll("(?<gn1>" + toSupplementaries("ab") +
                        ")(?<gn2>" + toSupplementaries("c") + "*)",
                        toSupplementaries("abccczzzabcczzzabccc"),
                        "${gn2}",
                        toSupplementaries("ccczzzcczzzccc"));

        checkReplaceFirst("(?<dog>Dog)AndCat",
                          "zzzDogAndCatzzzDogAndCatzzz",
                          "${dog}",
                          "zzzDogzzzDogAndCatzzz");


        checkReplaceAll("(?<dog>Dog)AndCat",
                          "zzzDogAndCatzzzDogAndCatzzz",
                          "${dog}",
                          "zzzDogzzzDogzzz");

        // backref in Matcher & String
        if (!"abcdefghij".replaceFirst("cd(?<gn>ef)gh", "${gn}").equals("abefij") ||
            !"abbbcbdbefgh".replaceAll("(?<gn>[a-e])b", "${gn}").equals("abcdefgh"))
            failCount++;

        // negative
        checkExpectedFail("(?<groupnamehasnoascii.in>abc)(def)");
        checkExpectedFail("(?<groupnamehasnoascii_in>abc)(def)");
        checkExpectedFail("(?<6groupnamestartswithdigit>abc)(def)");
        checkExpectedFail("(?<gname>abc)(def)\\k<gnameX>");
        checkExpectedFail("(?<gname>abc)(?<gname>def)\\k<gnameX>");
        checkExpectedIAE(Pattern.compile("(?<gname>abc)(def)").matcher("abcdef"),
                         "gnameX");
        checkExpectedNPE(Pattern.compile("(?<gname>abc)(def)").matcher("abcdef"));
        report("NamedGroupCapture");
    }

    // This is for bug 6969132
    private static void nonBmpClassComplementTest() throws Exception {
        Pattern p = Pattern.compile("\\P{Lu}");
        Matcher m = p.matcher(new String(new int[] {0x1d400}, 0, 1));
        if (m.find() && m.start() == 1)
            failCount++;

        // from a unicode category
        p = Pattern.compile("\\P{Lu}");
        m = p.matcher(new String(new int[] {0x1d400}, 0, 1));
        if (m.find())
            failCount++;
        if (!m.hitEnd())
            failCount++;

        // block
        p = Pattern.compile("\\P{InMathematicalAlphanumericSymbols}");
        m = p.matcher(new String(new int[] {0x1d400}, 0, 1));
        if (m.find() && m.start() == 1)
            failCount++;

        report("NonBmpClassComplement");
    }

    private static void unicodePropertiesTest() throws Exception {
        // different forms
        if (!Pattern.compile("\\p{IsLu}").matcher("A").matches() ||
            !Pattern.compile("\\p{Lu}").matcher("A").matches() ||
            !Pattern.compile("\\p{gc=Lu}").matcher("A").matches() ||
            !Pattern.compile("\\p{general_category=Lu}").matcher("A").matches() ||
            !Pattern.compile("\\p{IsLatin}").matcher("B").matches() ||
            !Pattern.compile("\\p{sc=Latin}").matcher("B").matches() ||
            !Pattern.compile("\\p{script=Latin}").matcher("B").matches() ||
            !Pattern.compile("\\p{InBasicLatin}").matcher("c").matches() ||
            !Pattern.compile("\\p{blk=BasicLatin}").matcher("c").matches() ||
            !Pattern.compile("\\p{block=BasicLatin}").matcher("c").matches())
            failCount++;

        Matcher common  = Pattern.compile("\\p{script=Common}").matcher("");
        Matcher unknown = Pattern.compile("\\p{IsUnknown}").matcher("");
        Matcher lastSM  = common;
        Character.UnicodeScript lastScript = Character.UnicodeScript.of(0);

        Matcher latin  = Pattern.compile("\\p{block=basic_latin}").matcher("");
        Matcher greek  = Pattern.compile("\\p{InGreek}").matcher("");
        Matcher lastBM = latin;
        Character.UnicodeBlock lastBlock = Character.UnicodeBlock.of(0);

        for (int cp = 1; cp < Character.MAX_CODE_POINT; cp++) {
            if (cp >= 0x30000 && (cp & 0x70) == 0){
                continue;  // only pick couple code points, they are the same
            }

            // Unicode Script
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            Matcher m;
            String str = new String(Character.toChars(cp));
            if (script == lastScript) {
                 m = lastSM;
                 m.reset(str);
            } else {
                 m  = Pattern.compile("\\p{Is" + script.name() + "}").matcher(str);
            }
            if (!m.matches()) {
                failCount++;
            }
            Matcher other = (script == Character.UnicodeScript.COMMON)? unknown : common;
            other.reset(str);
            if (other.matches()) {
                failCount++;
            }
            lastSM = m;
            lastScript = script;

            // Unicode Block
            Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
            if (block == null) {
                //System.out.printf("Not a Block: cp=%x%n", cp);
                continue;
            }
            if (block == lastBlock) {
                 m = lastBM;
                 m.reset(str);
            } else {
                 m  = Pattern.compile("\\p{block=" + block.toString() + "}").matcher(str);
            }
            if (!m.matches()) {
                failCount++;
            }
            other = (block == Character.UnicodeBlock.BASIC_LATIN)? greek : latin;
            other.reset(str);
            if (other.matches()) {
                failCount++;
            }
            lastBM = m;
            lastBlock = block;
        }
        report("unicodeProperties");
    }

    private static void unicodeHexNotationTest() throws Exception {

        // negative
        checkExpectedFail("\\x{-23}");
        checkExpectedFail("\\x{110000}");
        checkExpectedFail("\\x{}");
        checkExpectedFail("\\x{AB[ef]");

        // codepoint
        check("^\\x{1033c}$",              "\uD800\uDF3C", true);
        check("^\\xF0\\x90\\x8C\\xBC$",    "\uD800\uDF3C", false);
        check("^\\x{D800}\\x{DF3c}+$",     "\uD800\uDF3C", false);
        check("^\\xF0\\x90\\x8C\\xBC$",    "\uD800\uDF3C", false);

        // in class
        check("^[\\x{D800}\\x{DF3c}]+$",   "\uD800\uDF3C", false);
        check("^[\\xF0\\x90\\x8C\\xBC]+$", "\uD800\uDF3C", false);
        check("^[\\x{D800}\\x{DF3C}]+$",   "\uD800\uDF3C", false);
        check("^[\\x{DF3C}\\x{D800}]+$",   "\uD800\uDF3C", false);
        check("^[\\x{D800}\\x{DF3C}]+$",   "\uDF3C\uD800", true);
        check("^[\\x{DF3C}\\x{D800}]+$",   "\uDF3C\uD800", true);

        for (int cp = 0; cp <= 0x10FFFF; cp++) {
             String s = "A" + new String(Character.toChars(cp)) + "B";
             String hexUTF16 = (cp <= 0xFFFF)? String.format("\\u%04x", cp)
                                             : String.format("\\u%04x\\u%04x",
                                               (int) Character.toChars(cp)[0],
                                               (int) Character.toChars(cp)[1]);
             String hexCodePoint = "\\x{" + Integer.toHexString(cp) + "}";
             if (!Pattern.matches("A" + hexUTF16 + "B", s))
                 failCount++;
             if (!Pattern.matches("A[" + hexUTF16 + "]B", s))
                 failCount++;
             if (!Pattern.matches("A" + hexCodePoint + "B", s))
                 failCount++;
             if (!Pattern.matches("A[" + hexCodePoint + "]B", s))
                 failCount++;
         }
         report("unicodeHexNotation");
    }

    private static void unicodeClassesTest() throws Exception {

        Matcher lower  = Pattern.compile("\\p{Lower}").matcher("");
        Matcher upper  = Pattern.compile("\\p{Upper}").matcher("");
        Matcher ASCII  = Pattern.compile("\\p{ASCII}").matcher("");
        Matcher alpha  = Pattern.compile("\\p{Alpha}").matcher("");
        Matcher digit  = Pattern.compile("\\p{Digit}").matcher("");
        Matcher alnum  = Pattern.compile("\\p{Alnum}").matcher("");
        Matcher punct  = Pattern.compile("\\p{Punct}").matcher("");
        Matcher graph  = Pattern.compile("\\p{Graph}").matcher("");
        Matcher print  = Pattern.compile("\\p{Print}").matcher("");
        Matcher blank  = Pattern.compile("\\p{Blank}").matcher("");
        Matcher cntrl  = Pattern.compile("\\p{Cntrl}").matcher("");
        Matcher xdigit = Pattern.compile("\\p{XDigit}").matcher("");
        Matcher space  = Pattern.compile("\\p{Space}").matcher("");
        Matcher bound  = Pattern.compile("\\b").matcher("");
        Matcher word   = Pattern.compile("\\w++").matcher("");
        // UNICODE_CHARACTER_CLASS
        Matcher lowerU  = Pattern.compile("\\p{Lower}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher upperU  = Pattern.compile("\\p{Upper}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher ASCIIU  = Pattern.compile("\\p{ASCII}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher alphaU  = Pattern.compile("\\p{Alpha}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher digitU  = Pattern.compile("\\p{Digit}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher alnumU  = Pattern.compile("\\p{Alnum}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher punctU  = Pattern.compile("\\p{Punct}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher graphU  = Pattern.compile("\\p{Graph}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher printU  = Pattern.compile("\\p{Print}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher blankU  = Pattern.compile("\\p{Blank}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher cntrlU  = Pattern.compile("\\p{Cntrl}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher xdigitU = Pattern.compile("\\p{XDigit}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher spaceU  = Pattern.compile("\\p{Space}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher boundU  = Pattern.compile("\\b", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher wordU   = Pattern.compile("\\w", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        // embedded flag (?U)
        Matcher lowerEU  = Pattern.compile("(?U)\\p{Lower}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher graphEU  = Pattern.compile("(?U)\\p{Graph}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher wordEU   = Pattern.compile("(?U)\\w", Pattern.UNICODE_CHARACTER_CLASS).matcher("");

        Matcher bwb    = Pattern.compile("\\b\\w\\b").matcher("");
        Matcher bwbU   = Pattern.compile("\\b\\w++\\b", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher bwbEU  = Pattern.compile("(?U)\\b\\w++\\b", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        // properties
        Matcher lowerP  = Pattern.compile("\\p{IsLowerCase}").matcher("");
        Matcher upperP  = Pattern.compile("\\p{IsUpperCase}").matcher("");
        Matcher titleP  = Pattern.compile("\\p{IsTitleCase}").matcher("");
        Matcher letterP = Pattern.compile("\\p{IsLetter}").matcher("");
        Matcher alphaP  = Pattern.compile("\\p{IsAlphabetic}").matcher("");
        Matcher ideogP  = Pattern.compile("\\p{IsIdeographic}").matcher("");
        Matcher cntrlP  = Pattern.compile("\\p{IsControl}").matcher("");
        Matcher spaceP  = Pattern.compile("\\p{IsWhiteSpace}").matcher("");
        Matcher definedP = Pattern.compile("\\p{IsAssigned}").matcher("");
        Matcher nonCCPP = Pattern.compile("\\p{IsNoncharacterCodePoint}").matcher("");
        Matcher joinCrtl = Pattern.compile("\\p{IsJoinControl}").matcher("");

        // javaMethod
        Matcher lowerJ  = Pattern.compile("\\p{javaLowerCase}").matcher("");
        Matcher upperJ  = Pattern.compile("\\p{javaUpperCase}").matcher("");
        Matcher alphaJ  = Pattern.compile("\\p{javaAlphabetic}").matcher("");
        Matcher ideogJ  = Pattern.compile("\\p{javaIdeographic}").matcher("");

        for (int cp = 1; cp < 0x30000; cp++) {
            String str = new String(Character.toChars(cp));
            int type = Character.getType(cp);
            if (// lower
                POSIX_ASCII.isLower(cp)   != lower.reset(str).matches()  ||
                Character.isLowerCase(cp) != lowerU.reset(str).matches() ||
                Character.isLowerCase(cp) != lowerP.reset(str).matches() ||
                Character.isLowerCase(cp) != lowerEU.reset(str).matches()||
                Character.isLowerCase(cp) != lowerJ.reset(str).matches()||
                // upper
                POSIX_ASCII.isUpper(cp)   != upper.reset(str).matches()  ||
                POSIX_Unicode.isUpper(cp) != upperU.reset(str).matches() ||
                Character.isUpperCase(cp) != upperP.reset(str).matches() ||
                Character.isUpperCase(cp) != upperJ.reset(str).matches() ||
                // alpha
                POSIX_ASCII.isAlpha(cp)   != alpha.reset(str).matches()  ||
                POSIX_Unicode.isAlpha(cp) != alphaU.reset(str).matches() ||
                Character.isAlphabetic(cp)!= alphaP.reset(str).matches() ||
                Character.isAlphabetic(cp)!= alphaJ.reset(str).matches() ||
                // digit
                POSIX_ASCII.isDigit(cp)   != digit.reset(str).matches()  ||
                Character.isDigit(cp)     != digitU.reset(str).matches() ||
                // alnum
                POSIX_ASCII.isAlnum(cp)   != alnum.reset(str).matches()  ||
                POSIX_Unicode.isAlnum(cp) != alnumU.reset(str).matches() ||
                // punct
                POSIX_ASCII.isPunct(cp)   != punct.reset(str).matches()  ||
                POSIX_Unicode.isPunct(cp) != punctU.reset(str).matches() ||
                // graph
                POSIX_ASCII.isGraph(cp)   != graph.reset(str).matches()  ||
                POSIX_Unicode.isGraph(cp) != graphU.reset(str).matches() ||
                POSIX_Unicode.isGraph(cp) != graphEU.reset(str).matches()||
                // blank
                POSIX_ASCII.isType(cp, POSIX_ASCII.BLANK)
                                          != blank.reset(str).matches()  ||
                POSIX_Unicode.isBlank(cp) != blankU.reset(str).matches() ||
                // print
                POSIX_ASCII.isPrint(cp)   != print.reset(str).matches()  ||
                POSIX_Unicode.isPrint(cp) != printU.reset(str).matches() ||
                // cntrl
                POSIX_ASCII.isCntrl(cp)   != cntrl.reset(str).matches()  ||
                POSIX_Unicode.isCntrl(cp) != cntrlU.reset(str).matches() ||
                (Character.CONTROL == type) != cntrlP.reset(str).matches() ||
                // hexdigit
                POSIX_ASCII.isHexDigit(cp)   != xdigit.reset(str).matches()  ||
                POSIX_Unicode.isHexDigit(cp) != xdigitU.reset(str).matches() ||
                // space
                POSIX_ASCII.isSpace(cp)   != space.reset(str).matches()  ||
                POSIX_Unicode.isSpace(cp) != spaceU.reset(str).matches() ||
                POSIX_Unicode.isSpace(cp) != spaceP.reset(str).matches() ||
                // word
                POSIX_ASCII.isWord(cp)   != word.reset(str).matches()  ||
                POSIX_Unicode.isWord(cp) != wordU.reset(str).matches() ||
                POSIX_Unicode.isWord(cp) != wordEU.reset(str).matches()||
                // bwordb
                POSIX_ASCII.isWord(cp) != bwb.reset(str).matches() ||
                POSIX_Unicode.isWord(cp) != bwbU.reset(str).matches() ||
                // properties
                Character.isTitleCase(cp) != titleP.reset(str).matches() ||
                Character.isLetter(cp)    != letterP.reset(str).matches()||
                Character.isIdeographic(cp) != ideogP.reset(str).matches() ||
                Character.isIdeographic(cp) != ideogJ.reset(str).matches() ||
                (Character.UNASSIGNED == type) == definedP.reset(str).matches() ||
                POSIX_Unicode.isNoncharacterCodePoint(cp) != nonCCPP.reset(str).matches() ||
                POSIX_Unicode.isJoinControl(cp) != joinCrtl.reset(str).matches())
                failCount++;
        }

        // bounds/word align
        twoFindIndexes(" \u0180sherman\u0400 ", bound, 1, 10);
        if (!bwbU.reset("\u0180sherman\u0400").matches())
            failCount++;
        twoFindIndexes(" \u0180sh\u0345erman\u0400 ", bound, 1, 11);
        if (!bwbU.reset("\u0180sh\u0345erman\u0400").matches())
            failCount++;
        twoFindIndexes(" \u0724\u0739\u0724 ", bound, 1, 4);
        if (!bwbU.reset("\u0724\u0739\u0724").matches())
            failCount++;
        if (!bwbEU.reset("\u0724\u0739\u0724").matches())
            failCount++;
        report("unicodePredefinedClasses");
    }

    private static void horizontalAndVerticalWSTest() throws Exception {
        String hws = new String (new char[] {
                                     0x09, 0x20, 0xa0, 0x1680, 0x180e,
                                     0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005,
                                     0x2006, 0x2007, 0x2008, 0x2009, 0x200a,
                                     0x202f, 0x205f, 0x3000 });
        String vws = new String (new char[] {
                                     0x0a, 0x0b, 0x0c, 0x0d, 0x85, 0x2028, 0x2029 });
        if (!Pattern.compile("\\h+").matcher(hws).matches() ||
            !Pattern.compile("[\\h]+").matcher(hws).matches())
            failCount++;
        if (Pattern.compile("\\H").matcher(hws).find() ||
            Pattern.compile("[\\H]").matcher(hws).find())
            failCount++;
        if (!Pattern.compile("\\v+").matcher(vws).matches() ||
            !Pattern.compile("[\\v]+").matcher(vws).matches())
            failCount++;
        if (Pattern.compile("\\V").matcher(vws).find() ||
            Pattern.compile("[\\V]").matcher(vws).find())
            failCount++;
        String prefix = "abcd";
        String suffix = "efgh";
        String ng = "A";
        for (int i = 0; i < hws.length(); i++) {
            String c = String.valueOf(hws.charAt(i));
            Matcher m = Pattern.compile("\\h").matcher(prefix + c + suffix);
            if (!m.find() || !c.equals(m.group()))
                failCount++;
            m = Pattern.compile("[\\h]").matcher(prefix + c + suffix);
            if (!m.find() || !c.equals(m.group()))
                failCount++;

            m = Pattern.compile("\\H").matcher(hws.substring(0, i) + ng + hws.substring(i));
            if (!m.find() || !ng.equals(m.group()))
                failCount++;
            m = Pattern.compile("[\\H]").matcher(hws.substring(0, i) + ng + hws.substring(i));
            if (!m.find() || !ng.equals(m.group()))
                failCount++;
        }
        for (int i = 0; i < vws.length(); i++) {
            String c = String.valueOf(vws.charAt(i));
            Matcher m = Pattern.compile("\\v").matcher(prefix + c + suffix);
            if (!m.find() || !c.equals(m.group()))
                failCount++;
            m = Pattern.compile("[\\v]").matcher(prefix + c + suffix);
            if (!m.find() || !c.equals(m.group()))
                failCount++;

            m = Pattern.compile("\\V").matcher(vws.substring(0, i) + ng + vws.substring(i));
            if (!m.find() || !ng.equals(m.group()))
                failCount++;
            m = Pattern.compile("[\\V]").matcher(vws.substring(0, i) + ng + vws.substring(i));
            if (!m.find() || !ng.equals(m.group()))
                failCount++;
        }
        // \v in range is interpreted as 0x0B. This is the undocumented behavior
        if (!Pattern.compile("[\\v-\\v]").matcher(String.valueOf((char)0x0B)).matches())
            failCount++;
        report("horizontalAndVerticalWSTest");
    }

    private static void linebreakTest() throws Exception {
        String linebreaks = new String (new char[] {
            0x0A, 0x0B, 0x0C, 0x0D, 0x85, 0x2028, 0x2029 });
        String crnl = "\r\n";
        if (!Pattern.compile("\\R+").matcher(linebreaks).matches() ||
            !Pattern.compile("\\R").matcher(crnl).matches() ||
            Pattern.compile("\\R\\R").matcher(crnl).matches())
            failCount++;
        report("linebreakTest");
    }

    // #7189363
    private static void branchTest() throws Exception {
        if (!Pattern.compile("(a)?bc|d").matcher("d").find() ||     // greedy
            !Pattern.compile("(a)+bc|d").matcher("d").find() ||
            !Pattern.compile("(a)*bc|d").matcher("d").find() ||
            !Pattern.compile("(a)??bc|d").matcher("d").find() ||    // reluctant
            !Pattern.compile("(a)+?bc|d").matcher("d").find() ||
            !Pattern.compile("(a)*?bc|d").matcher("d").find() ||
            !Pattern.compile("(a)?+bc|d").matcher("d").find() ||    // possessive
            !Pattern.compile("(a)++bc|d").matcher("d").find() ||
            !Pattern.compile("(a)*+bc|d").matcher("d").find() ||
            !Pattern.compile("(a)?bc|d").matcher("d").matches() ||  // greedy
            !Pattern.compile("(a)+bc|d").matcher("d").matches() ||
            !Pattern.compile("(a)*bc|d").matcher("d").matches() ||
            !Pattern.compile("(a)??bc|d").matcher("d").matches() || // reluctant
            !Pattern.compile("(a)+?bc|d").matcher("d").matches() ||
            !Pattern.compile("(a)*?bc|d").matcher("d").matches() ||
            !Pattern.compile("(a)?+bc|d").matcher("d").matches() || // possessive
            !Pattern.compile("(a)++bc|d").matcher("d").matches() ||
            !Pattern.compile("(a)*+bc|d").matcher("d").matches() ||
            !Pattern.compile("(a)?bc|de").matcher("de").find() ||   // others
            !Pattern.compile("(a)??bc|de").matcher("de").find() ||
            !Pattern.compile("(a)?bc|de").matcher("de").matches() ||
            !Pattern.compile("(a)??bc|de").matcher("de").matches())
            failCount++;
        report("branchTest");
    }

    // This test is for 8007395
    private static void groupCurlyNotFoundSuppTest() throws Exception {
        String input = "test this as \ud83d\ude0d";
        for (String pStr : new String[] { "test(.)+(@[a-zA-Z.]+)",
                                          "test(.)*(@[a-zA-Z.]+)",
                                          "test([^B])+(@[a-zA-Z.]+)",
                                          "test([^B])*(@[a-zA-Z.]+)",
                                          "test(\\P{IsControl})+(@[a-zA-Z.]+)",
                                          "test(\\P{IsControl})*(@[a-zA-Z.]+)",
                                        }) {
            Matcher m = Pattern.compile(pStr, Pattern.CASE_INSENSITIVE)
                               .matcher(input);
            try {
                if (m.find()) {
                    failCount++;
                }
            } catch (Exception x) {
                failCount++;
            }
        }
        report("GroupCurly NotFoundSupp");
    }

    // This test is for 8023647
    private static void groupCurlyBackoffTest() throws Exception {
        if (!"abc1c".matches("(\\w)+1\\1") ||
            "abc11".matches("(\\w)+1\\1")) {
            failCount++;
        }
        report("GroupCurly backoff");
    }

    // This test is for 8012646
    private static void patternAsPredicate() throws Exception {
        Predicate<String> p = Pattern.compile("[a-z]+").asPredicate();

        if (p.test("")) {
            failCount++;
        }
        if (!p.test("word")) {
            failCount++;
        }
        if (p.test("1234")) {
            failCount++;
        }
        report("Pattern.asPredicate");
    }
}
