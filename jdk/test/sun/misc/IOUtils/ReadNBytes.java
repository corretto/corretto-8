/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import jdk.testlibrary.RandomFactory;

import sun.misc.IOUtils;

/*
 * @test
 * @bug 8080835 8139206
 * @library /lib/testlibrary
 * @build jdk.testlibrary.*
 * @run main ReadNBytes
 * @summary Basic test for IOUtils.readNBytes
 * @key randomness
 */

public class ReadNBytes {

    private static Random generator = RandomFactory.getRandom();

    public static void main(String[] args) throws IOException {
        test(new byte[]{1, 2, 3});
        test(createRandomBytes(1024));
        for (int shift : new int[] {13, 15, 17}) {
            for (int offset : new int[] {-1, 0, 1}) {
                test(createRandomBytes((1 << shift) + offset));
            }
        }

        test(-1);
        test(0);
        for (int shift : new int[] {13, 15, 17}) {
            for (int offset : new int[] {-1, 0, 1}) {
                test((1 << shift) + offset);
            }
        }
    }

    static void test(byte[] inputBytes) throws IOException {
        int length = inputBytes.length;
        WrapperInputStream in = new WrapperInputStream(new ByteArrayInputStream(inputBytes));
        byte[] readBytes = new byte[(length / 2) + 1];
        int nread = IOUtils.readNBytes(in, readBytes, 0, readBytes.length);

        int x;
        byte[] tmp;
        check(nread == readBytes.length,
              "Expected number of bytes read: " + readBytes.length + ", got: " + nread);
        check(Arrays.equals((tmp = Arrays.copyOf(inputBytes, nread)), readBytes),
              "Expected[" + tmp + "], got:[" + readBytes + "]");
        check(!in.isClosed(), "Stream unexpectedly closed");

        // Read again
        nread = IOUtils.readNBytes(in, readBytes, 0, readBytes.length);

        check(nread == length - readBytes.length,
              "Expected number of bytes read: " + (length - readBytes.length) + ", got: " + nread);
        check(Arrays.equals((tmp = Arrays.copyOfRange(inputBytes, readBytes.length, length)),
                            Arrays.copyOf(readBytes, nread)),
              "Expected[" + tmp + "], got:[" + readBytes + "]");
        // Expect end of stream
        check((x = in.read()) == -1,
              "Expected end of stream from read(), got " + x);
        check((x = in.read(tmp)) == -1,
              "Expected end of stream from read(byte[]), got " + x);
        check((x = in.read(tmp, 0, tmp.length)) == -1,
              "Expected end of stream from read(byte[], int, int), got " + x);
        check((x = IOUtils.readNBytes(in, tmp, 0, tmp.length)) == 0,
              "Expected end of stream, 0, from readNBytes(byte[], int, int), got " + x);
        check(!in.isClosed(), "Stream unexpectedly closed");
    }

    static void test(int max) throws IOException {
        byte[] subset1, subset2;
        byte[] inputBytes = max <= 0 ? new byte[0] : createRandomBytes(max);
        WrapperInputStream in =
            new WrapperInputStream(new ByteArrayInputStream(inputBytes));

        if (max < 0) {
            try {
                IOUtils.readNBytes(in, max);
                check(false, "Expected IllegalArgumentException not thrown");
            } catch (IllegalArgumentException iae) {
                return;
            }
        } else if (max == 0) {
            int x;
            check((x = IOUtils.readNBytes(in, max).length) == 0,
                  "Expected zero bytes, got " + x);
            return;
        }

        int off = Math.toIntExact(in.skip(generator.nextInt(max/2)));
        int len = generator.nextInt(max - 1 - off);
        byte[] readBytes = IOUtils.readNBytes(in, len);
        check(readBytes.length == len,
              "Expected " + len + " bytes, got " + readBytes.length);
        subset1 = Arrays.copyOfRange(inputBytes, off, off + len);
        subset2 = Arrays.copyOfRange(readBytes, 0, len);
        check(Arrays.equals(subset1, subset2), "Expected[" + subset1 +
              "], got:[" + readBytes + "]");

        int remaining = max - (off + len);
        readBytes = IOUtils.readNBytes(in, remaining);
        check(readBytes.length == remaining,
              "Expected " + remaining + "bytes, got " + readBytes.length);
        subset1 = Arrays.copyOfRange(inputBytes, off + len, max);
        subset2 = Arrays.copyOfRange(readBytes, 0, remaining);
        check(Arrays.equals(subset1, subset2), "Expected[" + subset1 +
          "], got:[" + readBytes + "]");

        check(!in.isClosed(), "Stream unexpectedly closed");
    }

    static byte[] createRandomBytes(int size) {
        byte[] bytes = new byte[size];
        generator.nextBytes(bytes);
        return bytes;
    }

    static void check(boolean cond, Object ... failedArgs) {
        if (cond)
            return;
        StringBuilder sb = new StringBuilder();
        for (Object o : failedArgs)
            sb.append(o);
        throw new RuntimeException(sb.toString());
    }


    static class WrapperInputStream extends FilterInputStream {
        private boolean closed;
        WrapperInputStream(InputStream in) { super(in); }
        @Override public void close() throws IOException { closed = true; in.close(); }
        boolean isClosed() { return closed; }
    }
}
