/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package jdk.nashorn.internal.runtime.regexp.joni;

import jdk.nashorn.internal.runtime.regexp.joni.exception.ErrorMessages;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;

@SuppressWarnings("javadoc")
public final class CodeRangeBuffer implements Cloneable {
    private static final int INIT_MULTI_BYTE_RANGE_SIZE = 5;
    private static final int ALL_MULTI_BYTE_RANGE = 0x7fffffff;

    int[] p;
    int used;


    public CodeRangeBuffer() {
        p = new int[INIT_MULTI_BYTE_RANGE_SIZE];
        writeCodePoint(0, 0);
    }

    // CodeRange.isInCodeRange
    public boolean isInCodeRange(final int code) {
        int low = 0;
        final int n = p[0];
        int high = n;

        while (low < high) {
            final int x = (low + high) >> 1;
            if (code > p[(x << 1) + 2]) {
                low = x + 1;
            } else {
                high = x;
            }
        }
        return low < n && code >= p[(low << 1) + 1];
    }

    private CodeRangeBuffer(final CodeRangeBuffer orig) {
        p = new int[orig.p.length];
        System.arraycopy(orig.p, 0, p, 0, p.length);
        used = orig.used;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("CodeRange");
        buf.append("\n  used: ").append(used);
        buf.append("\n  code point: ").append(p[0]);
        buf.append("\n  ranges: ");

        for (int i=0; i<p[0]; i++) {
            buf.append("[").append(rangeNumToString(p[i * 2 + 1])).append("..").append(rangeNumToString(p[i * 2 + 2])).append("]");
            if (i > 0 && i % 6 == 0) {
                buf.append("\n          ");
            }
        }

        return buf.toString();
    }

    private static String rangeNumToString(final int num){
        return "0x" + Integer.toString(num, 16);
    }

    public void expand(final int low) {
        int length = p.length;
        do { length <<= 1; } while (length < low);
        final int[]tmp = new int[length];
        System.arraycopy(p, 0, tmp, 0, used);
        p = tmp;
    }

    public void ensureSize(final int size) {
        int length = p.length;
        while (length < size ) { length <<= 1; }
        if (p.length != length) {
            final int[]tmp = new int[length];
            System.arraycopy(p, 0, tmp, 0, used);
            p = tmp;
        }
    }

    private void moveRight(final int from, final int to, final int n) {
        if (to + n > p.length) {
            expand(to + n);
        }
        System.arraycopy(p, from, p, to, n);
        if (to + n > used) {
            used = to + n;
        }
    }

    protected void moveLeft(final int from, final int to, final int n) {
        System.arraycopy(p, from, p, to, n);
    }

    private void moveLeftAndReduce(final int from, final int to) {
        System.arraycopy(p, from, p, to, used - from);
        used -= from - to;
    }

    public void writeCodePoint(final int pos, final int b) {
        final int u = pos + 1;
        if (p.length < u) {
            expand(u);
        }
        p[pos] = b;
        if (used < u) {
            used = u;
        }
    }

    @Override
    public CodeRangeBuffer clone() {
        return new CodeRangeBuffer(this);
    }

    // ugly part: these methods should be made OO
    // add_code_range_to_buf
    public static CodeRangeBuffer addCodeRangeToBuff(final CodeRangeBuffer pbufp, final int fromp, final int top) {
        int from = fromp, to = top;
        CodeRangeBuffer pbuf = pbufp;

        if (from > to) {
            final int n = from;
            from = to;
            to = n;
        }

        if (pbuf == null) {
            pbuf = new CodeRangeBuffer(); // move to CClassNode
        }

        final int[]p = pbuf.p;
        int n = p[0];

        int low = 0;
        int bound = n;

        while (low < bound) {
            final int x = (low + bound) >>> 1;
            if (from > p[x * 2 + 2]) {
                low = x + 1;
            } else {
                bound = x;
            }
        }

        int high = low;
        bound = n;

        while (high < bound) {
            final int x = (high + bound) >>> 1;
            if (to >= p[x * 2 + 1] - 1) {
                high = x + 1;
            } else {
                bound = x;
            }
        }

        final int incN = low + 1 - high;

        if (n + incN > Config.MAX_MULTI_BYTE_RANGES_NUM) {
            throw new ValueException(ErrorMessages.ERR_TOO_MANY_MULTI_BYTE_RANGES);
        }

        if (incN != 1) {
            if (from > p[low * 2 + 1]) {
                from = p[low * 2 + 1];
            }
            if (to < p[(high - 1) * 2 + 2]) {
                to = p[(high - 1) * 2 + 2];
            }
        }

        if (incN != 0 && high < n) {
            final int fromPos = 1 + high * 2;
            final int toPos = 1 + (low + 1) * 2;
            final int size = (n - high) * 2;

            if (incN > 0) {
                pbuf.moveRight(fromPos, toPos, size);
            } else {
                pbuf.moveLeftAndReduce(fromPos, toPos);
            }
        }

        final int pos = 1 + low * 2;
        // pbuf.ensureSize(pos + 2);
        pbuf.writeCodePoint(pos, from);
        pbuf.writeCodePoint(pos + 1, to);
        n += incN;
        pbuf.writeCodePoint(0, n);

        return pbuf;
    }

    // add_code_range, be aware of it returning null!
    public static CodeRangeBuffer addCodeRange(final CodeRangeBuffer pbuf, final ScanEnvironment env, final int from, final int to) {
        if (from > to) {
            if (env.syntax.allowEmptyRangeInCC()) {
                return pbuf;
            }
            throw new ValueException(ErrorMessages.ERR_EMPTY_RANGE_IN_CHAR_CLASS);
        }
        return addCodeRangeToBuff(pbuf, from, to);
    }

    // SET_ALL_MULTI_BYTE_RANGE
    protected static CodeRangeBuffer setAllMultiByteRange(final CodeRangeBuffer pbuf) {
        return addCodeRangeToBuff(pbuf, EncodingHelper.mbcodeStartPosition(), ALL_MULTI_BYTE_RANGE);
    }

    // ADD_ALL_MULTI_BYTE_RANGE
    public static CodeRangeBuffer addAllMultiByteRange(final CodeRangeBuffer pbuf) {
        return setAllMultiByteRange(pbuf);
    }

    // not_code_range_buf
    public static CodeRangeBuffer notCodeRangeBuff(final CodeRangeBuffer bbuf) {
        CodeRangeBuffer pbuf = null;

        if (bbuf == null) {
            return setAllMultiByteRange(pbuf);
        }

        final int[]p = bbuf.p;
        final int n = p[0];

        if (n <= 0) {
            return setAllMultiByteRange(pbuf);
        }

        int pre = EncodingHelper.mbcodeStartPosition();

        int from;
        int to = 0;
        for (int i=0; i<n; i++) {
            from = p[i * 2 + 1];
            to = p[i * 2 + 2];
            if (pre <= from - 1) {
                pbuf = addCodeRangeToBuff(pbuf, pre, from - 1);
            }
            if (to == ALL_MULTI_BYTE_RANGE) {
                break;
            }
            pre = to + 1;
        }

        if (to < ALL_MULTI_BYTE_RANGE) {
            pbuf = addCodeRangeToBuff(pbuf, to + 1, ALL_MULTI_BYTE_RANGE);
        }
        return pbuf;
    }

    // or_code_range_buf
    public static CodeRangeBuffer orCodeRangeBuff(final CodeRangeBuffer bbuf1p, final boolean not1p,
                                                  final CodeRangeBuffer bbuf2p, final boolean not2p) {
        CodeRangeBuffer pbuf = null;
        CodeRangeBuffer bbuf1 = bbuf1p;
        CodeRangeBuffer bbuf2 = bbuf2p;
        boolean not1 = not1p;
        boolean not2 = not2p;

        if (bbuf1 == null && bbuf2 == null) {
            if (not1 || not2) {
                return setAllMultiByteRange(pbuf);
            }
            return null;
        }

        if (bbuf2 == null) {
            CodeRangeBuffer tbuf;
            boolean tnot;
            // swap
            tnot = not1; not1 = not2; not2 = tnot;
            tbuf = bbuf1; bbuf1 = bbuf2; bbuf2 = tbuf;
        }

        if (bbuf1 == null) {
            if (not1) {
                return setAllMultiByteRange(pbuf);
            }
            if (!not2) {
                return bbuf2.clone();
            }
            return notCodeRangeBuff(bbuf2);
        }

        if (not1) {
            CodeRangeBuffer tbuf;
            boolean tnot;
            // swap
            tnot = not1; not1 = not2; not2 = tnot;
            tbuf = bbuf1; bbuf1 = bbuf2; bbuf2 = tbuf;
        }

        if (!not2 && !not1) { /* 1 OR 2 */
            pbuf = bbuf2.clone();
        } else if (!not1) { /* 1 OR (not 2) */
            pbuf = notCodeRangeBuff(bbuf2);
        }

        final int[]p1 = bbuf1.p;
        final int n1 = p1[0];

        for (int i=0; i<n1; i++) {
            final int from = p1[i * 2 + 1];
            final int to = p1[i * 2 + 2];
            pbuf = addCodeRangeToBuff(pbuf, from, to);
        }

        return pbuf;
    }

    // and_code_range1
    public static CodeRangeBuffer andCodeRange1(final CodeRangeBuffer pbufp, final int from1p, final int to1p, final int[]data, final int n) {
        CodeRangeBuffer pbuf = pbufp;
        int from1 = from1p, to1 = to1p;

        for (int i=0; i<n; i++) {
            final int from2 = data[i * 2 + 1];
            final int to2 = data[i * 2 + 2];
            if (from2 < from1) {
                if (to2 < from1) {
                    continue;
                }
                from1 = to2 + 1;
            } else if (from2 <= to1) {
                if (to2 < to1) {
                    if (from1 <= from2 - 1) {
                        pbuf = addCodeRangeToBuff(pbuf, from1, from2 - 1);
                    }
                    from1 = to2 + 1;
                } else {
                    to1 = from2 - 1;
                }
            } else {
                from1 = from2;
            }
            if (from1 > to1) {
                break;
            }
        }

        if (from1 <= to1) {
            pbuf = addCodeRangeToBuff(pbuf, from1, to1);
        }

        return pbuf;
    }

    // and_code_range_buf
    public static CodeRangeBuffer andCodeRangeBuff(final CodeRangeBuffer bbuf1p, final boolean not1p,
                                                   final CodeRangeBuffer bbuf2p, final boolean not2p) {
        CodeRangeBuffer pbuf = null;
        CodeRangeBuffer bbuf1 = bbuf1p;
        CodeRangeBuffer bbuf2 = bbuf2p;
        boolean not1 = not1p, not2 = not2p;

        if (bbuf1 == null) {
            if (not1 && bbuf2 != null) {
                return bbuf2.clone(); /* not1 != 0 -> not2 == 0 */
            }
            return null;
        } else if (bbuf2 == null) {
            if (not2) {
                return bbuf1.clone();
            }
            return null;
        }

        if (not1) {
            CodeRangeBuffer tbuf;
            boolean tnot;
            // swap
            tnot = not1; not1 = not2; not2 = tnot;
            tbuf = bbuf1; bbuf1 = bbuf2; bbuf2 = tbuf;
        }

        final int[]p1 = bbuf1.p;
        final int n1 = p1[0];
        final int[]p2 = bbuf2.p;
        final int n2 = p2[0];

        if (!not2 && !not1) { /* 1 AND 2 */
            for (int i=0; i<n1; i++) {
                final int from1 = p1[i * 2 + 1];
                final int to1 = p1[i * 2 + 2];

                for (int j=0; j<n2; j++) {
                    final int from2 = p2[j * 2 + 1];
                    final int to2 = p2[j * 2 + 2];

                    if (from2 > to1) {
                        break;
                    }
                    if (to2 < from1) {
                        continue;
                    }
                    final int from = from1 > from2 ? from1 : from2;
                    final int to = to1 < to2 ? to1 : to2;
                    pbuf = addCodeRangeToBuff(pbuf, from, to);
                }
            }
        } else if (!not1) { /* 1 AND (not 2) */
            for (int i=0; i<n1; i++) {
                final int from1 = p1[i * 2 + 1];
                final int to1 = p1[i * 2 + 2];
                pbuf = andCodeRange1(pbuf, from1, to1, p2, n2);
            }
        }

        return pbuf;
    }
}
