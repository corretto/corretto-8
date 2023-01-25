
/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

package sun.nio.cs.ext;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import static sun.nio.cs.CharsetMapping.*;

// EBCDIC DBCS-only Korean
public class IBM834 extends Charset
{
    public IBM834() {
        super("x-IBM834", ExtendedCharsets.aliasesFor("x-IBM834"));
    }

    public boolean contains(Charset cs) {
        return (cs instanceof IBM834);
    }

    public CharsetDecoder newDecoder() {
        IBM933.initb2c();
        return new DoubleByte.Decoder_DBCSONLY(
            this, IBM933.b2c, null, 0x40, 0xfe);  // hardcode the b2min/max
    }

    public CharsetEncoder newEncoder() {
        IBM933.initc2b();
        return new Encoder(this);
    }

    protected static class Encoder extends DoubleByte.Encoder_DBCSONLY {
        public Encoder(Charset cs) {
            super(cs, new byte[] {(byte)0xfe, (byte)0xfe},
                  IBM933.c2b, IBM933.c2bIndex);
        }

        public int encodeChar(char ch) {
            int bb = super.encodeChar(ch);
            if (bb == UNMAPPABLE_ENCODING) {
                // Cp834 has 6 additional non-roundtrip char->bytes
                // mappings, see#6379808
                if (ch == '\u00b7') {
                    return 0x4143;
                } else if (ch == '\u00ad') {
                    return 0x4148;
                } else if (ch == '\u2015') {
                    return 0x4149;
                } else if (ch == '\u223c') {
                    return 0x42a1;
                } else if (ch == '\uff5e') {
                    return 0x4954;
                } else if (ch == '\u2299') {
                    return 0x496f;
                }
            }
            return bb;
        }

        public boolean isLegalReplacement(byte[] repl) {
            if (repl.length == 2 &&
                repl[0] == (byte)0xfe && repl[1] == (byte)0xfe)
                return true;
            return super.isLegalReplacement(repl);
        }

    }
}
