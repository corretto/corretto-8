/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4417152 4481572 6248930 6725399 6884800
 * @summary Test Channels basic functionality
 */

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.channels.*;


public class Basic {

    static String message;

    static String encoding;

    static File blah;

    static int ITERATIONS = 500;

    public static void main(String[] args) throws Exception {
        message = "ascii data for a test";
        encoding = "ISO-8859-1";
        test();
        message = "\ucafe\ubabe\ucafe\ubabe\ucafe\ubabe";
        encoding = "UTF-8";
        test();
    }

    static void failNpeExpected() {
        throw new RuntimeException("Did not get the expected NullPointerException.");
    }

    private static void test() throws Exception {
        //Test if methods of Channels throw NPE with null argument(s)
        try {
            Channels.newInputStream((ReadableByteChannel)null);
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newOutputStream((WritableByteChannel)null);
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            ReadableByteChannel channel = Channels.newChannel((InputStream)null);
            failNpeExpected();
        } catch (NullPointerException ne) {}  // OK. As expected.

        try {
            WritableByteChannel channel = Channels.newChannel((OutputStream)null);
            failNpeExpected();
        } catch (NullPointerException ne) {}  // OK. As expected.

        WritableByteChannel wbc = new WritableByteChannel() {
            public int write(ByteBuffer src) { return 0; }
            public void close() throws IOException { }
            public boolean isOpen() { return true; }
        };

        ReadableByteChannel rbc = new ReadableByteChannel() {
            public int read(ByteBuffer dst) { return 0; }
            public void close() {}
            public boolean isOpen() { return true; }
        };

        try {
            Channels.newReader((ReadableByteChannel)null,
                               Charset.defaultCharset().newDecoder(),
                               -1);
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newReader(rbc, (CharsetDecoder)null, -1);
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newReader((ReadableByteChannel)null,
                               Charset.defaultCharset().name());
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newReader(rbc, null);
            failNpeExpected();
        } catch (NullPointerException npe) {}


        try {
            Channels.newReader(null, null);
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newWriter((WritableByteChannel)null,
                               Charset.defaultCharset().newEncoder(),
                               -1);
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newWriter(null, null, -1);
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newWriter(wbc, null, -1);
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newWriter((WritableByteChannel)null,
                               Charset.defaultCharset().name());
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newWriter(wbc, null);
            failNpeExpected();
        } catch (NullPointerException npe) {}

        try {
            Channels.newWriter(null, null);
            failNpeExpected();
        } catch (NullPointerException npe) {}


        try {
            blah = File.createTempFile("blah", null);

            testNewOutputStream(blah);
            readAndCheck(blah);
            blah.delete();

            writeOut(blah, ITERATIONS);
            testNewInputStream(blah);
            blah.delete();

            testNewChannelOut(blah);
            readAndCheck(blah);
            blah.delete();

            writeOut(blah, ITERATIONS);
            testNewChannelIn(blah);
            test4481572(blah);
            blah.delete();

            testNewWriter(blah);
            readAndCheck(blah);
            blah.delete();

            writeOut(blah, ITERATIONS);
            testNewReader(blah);

        } finally {
            blah.delete();
        }
    }

    private static void readAndCheck(File blah) throws Exception {
        FileInputStream fis = new FileInputStream(blah);
        int messageSize = message.length() * ITERATIONS * 3 + 1;
        byte bb[] = new byte[messageSize];
        int bytesRead = 0;
        int totalRead = 0;
        while (bytesRead != -1) {
            totalRead += bytesRead;
            bytesRead = fis.read(bb, totalRead, messageSize - totalRead);
        }
        String result = new String(bb, 0, totalRead, encoding);
        int len = message.length();
        for (int i=0; i<ITERATIONS; i++) {
            String segment = result.substring(i++ * len, i * len);
            if (!segment.equals(message))
                throw new RuntimeException("Test failed");
        }
        fis.close();
    }

    private static void writeOut(File blah, int limit) throws Exception {
        FileOutputStream fos = new FileOutputStream(blah);
        for (int i=0; i<limit; i++)
            fos.write(message.getBytes(encoding));
        fos.close();
    }

    private static void testNewOutputStream(File blah) throws Exception {
        FileOutputStream fos = new FileOutputStream(blah);
        FileChannel fc = fos.getChannel();
        WritableByteChannel wbc = (WritableByteChannel)fc;
        OutputStream os = Channels.newOutputStream(wbc);
        for (int i=0; i<ITERATIONS; i++)
            os.write(message.getBytes(encoding));
        os.close();
        fos.close();
    }

    private static void testNewInputStream(File blah) throws Exception {
        FileInputStream fis = new FileInputStream(blah);
        FileChannel fc = fis.getChannel();
        InputStream is = Channels.newInputStream(fc);
        int messageSize = message.length() * ITERATIONS * 3 + 1;
        byte bb[] = new byte[messageSize];

        int bytesRead = 0;
        int totalRead = 0;
        while (bytesRead != -1) {
            totalRead += bytesRead;
            long rem = Math.min(fc.size() - totalRead, (long)Integer.MAX_VALUE);
            if (is.available() != (int)rem)
                throw new RuntimeException("available not useful or not maximally useful");
            bytesRead = is.read(bb, totalRead, messageSize - totalRead);
        }
        if (is.available() != 0)
           throw new RuntimeException("available() should return 0 at EOF");

        String result = new String(bb, 0, totalRead, encoding);
        int len = message.length();
        for (int i=0; i<ITERATIONS; i++) {
            String segment = result.substring(i++ * len, i * len);
            if (!segment.equals(message))
                throw new RuntimeException("Test failed");
        }
        is.close();
        fis.close();
    }

    private static void testNewChannelOut(File blah) throws Exception {
        ExtendedFileOutputStream fos = new ExtendedFileOutputStream(blah);
        WritableByteChannel wbc = Channels.newChannel(fos);
        for (int i=0; i<ITERATIONS; i++)
            wbc.write(ByteBuffer.wrap(message.getBytes(encoding)));
        wbc.close();
        fos.close();
    }

    private static void testNewChannelIn(File blah) throws Exception {
        ExtendedFileInputStream fis = new ExtendedFileInputStream(blah);
        ReadableByteChannel rbc = Channels.newChannel(fis);

        int messageSize = message.length() * ITERATIONS * 3;
        byte data[] = new byte[messageSize+1];
        ByteBuffer bb = ByteBuffer.wrap(data);

        int bytesRead = 0;
        int totalRead = 0;
        while (bytesRead != -1) {
            totalRead += bytesRead;
            bytesRead = rbc.read(bb);
        }

        String result = new String(data, 0, totalRead, encoding);
        int len = message.length();
        for (int i=0; i<ITERATIONS; i++) {
            String segment = result.substring(i++ * len, i * len);
            if (!segment.equals(message))
                throw new RuntimeException("Test failed");
        }
        rbc.close();
        fis.close();
    }

    // Causes BufferOverflowException if bug 4481572 is present.
    private static void test4481572(File blah) throws Exception {
        ExtendedFileInputStream fis = new ExtendedFileInputStream(blah);
        ReadableByteChannel rbc = Channels.newChannel(fis);

        byte data[] = new byte[9000];
        ByteBuffer bb = ByteBuffer.wrap(data);

        int bytesRead = 1;
        int totalRead = 0;
        while (bytesRead > 0) {
            totalRead += bytesRead;
            bytesRead = rbc.read(bb);
        }
        rbc.close();
        fis.close();
    }

    private static void testNewWriter(File blah) throws Exception {
        FileOutputStream fos = new FileOutputStream(blah);
        WritableByteChannel wbc = (WritableByteChannel)fos.getChannel();
        Writer w = Channels.newWriter(wbc, encoding);
        char data[] = new char[40];
        message.getChars(0, message.length(), data, 0);
        for (int i=0; i<ITERATIONS; i++)
            w.write(data, 0, message.length());
        w.flush();
        w.close();
        fos.close();
    }

    private static void testNewReader(File blah) throws Exception {
        FileInputStream fis = new FileInputStream(blah);
        ReadableByteChannel rbc = (ReadableByteChannel)fis.getChannel();
        Reader r = Channels.newReader(rbc, encoding);

        int messageSize = message.length() * ITERATIONS;
        char data[] = new char[messageSize];

        int totalRead = 0;
        int charsRead = 0;
        while (totalRead < messageSize) {
            totalRead += charsRead;
            charsRead = r.read(data, totalRead, messageSize - totalRead);
        }
        String result = new String(data, 0, totalRead);
        int len = message.length();
        for (int i=0; i<ITERATIONS; i++) {
            String segment = result.substring(i++ * len, i * len);
            if (!segment.equals(message))
                throw new RuntimeException("Test failed");
        }
        r.close();
        fis.close();
    }
}

class ExtendedFileInputStream extends java.io.FileInputStream {
    ExtendedFileInputStream(File file) throws FileNotFoundException {
        super(file);
    }
}

class ExtendedFileOutputStream extends java.io.FileOutputStream {
    ExtendedFileOutputStream(File file) throws FileNotFoundException {
        super(file);
    }
}
