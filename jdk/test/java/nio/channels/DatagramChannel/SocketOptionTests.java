/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4640544
 * @summary Unit test for setOption/getOption/options methods
 */

import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.io.IOException;
import java.util.*;
import static java.net.StandardSocketOptions.*;

public class SocketOptionTests {

    static <T> void checkOption(DatagramChannel dc,
                                SocketOption<T> name,
                                T expectedValue)
        throws IOException
    {
        T value = dc.getOption(name);
        if (!value.equals(expectedValue))
            throw new RuntimeException("value not as expected");
    }

    public static void main(String[] args) throws IOException {
        DatagramChannel dc = DatagramChannel.open();

        // check supported options
        Set<SocketOption<?>> options = dc.supportedOptions();
        List<? extends SocketOption<?>> expected = Arrays.asList(SO_SNDBUF, SO_RCVBUF,
            SO_REUSEADDR, SO_BROADCAST, IP_TOS, IP_MULTICAST_IF, IP_MULTICAST_TTL,
            IP_MULTICAST_LOOP);
        for (SocketOption opt: expected) {
            if (!options.contains(opt))
                throw new RuntimeException(opt.name() + " should be supported");
        }

        // check specified defaults
        checkOption(dc, SO_BROADCAST, false);
        checkOption(dc, IP_MULTICAST_TTL, 1);           // true on supported platforms
        checkOption(dc, IP_MULTICAST_LOOP, true);       // true on supported platforms

        // allowed to change when not bound
        dc.setOption(SO_BROADCAST, true);
        checkOption(dc, SO_BROADCAST, true);
        dc.setOption(SO_BROADCAST, false);
        checkOption(dc, SO_BROADCAST, false);
        dc.setOption(SO_SNDBUF, 128*1024);       // can't check
        dc.setOption(SO_RCVBUF, 128*1024);       // can't check
        int before, after;
        before = dc.getOption(SO_SNDBUF);
        after = dc.setOption(SO_SNDBUF, Integer.MAX_VALUE).getOption(SO_SNDBUF);
        if (after < before)
            throw new RuntimeException("setOption caused SO_SNDBUF to decrease");
        before = dc.getOption(SO_RCVBUF);
        after = dc.setOption(SO_RCVBUF, Integer.MAX_VALUE).getOption(SO_RCVBUF);
        if (after < before)
            throw new RuntimeException("setOption caused SO_RCVBUF to decrease");
        dc.setOption(SO_REUSEADDR, true);
        checkOption(dc, SO_REUSEADDR, true);
        dc.setOption(SO_REUSEADDR, false);
        checkOption(dc, SO_REUSEADDR, false);

        // bind socket
        dc.bind(new InetSocketAddress(0));

        // allow to change when bound
        dc.setOption(SO_BROADCAST, true);
        checkOption(dc, SO_BROADCAST, true);
        dc.setOption(SO_BROADCAST, false);
        checkOption(dc, SO_BROADCAST, false);
        dc.setOption(IP_TOS, 0x08);     // can't check
        dc.setOption(IP_MULTICAST_TTL, 2);
        checkOption(dc, IP_MULTICAST_TTL, 2);
        dc.setOption(IP_MULTICAST_LOOP, false);
        checkOption(dc, IP_MULTICAST_LOOP, false);
        dc.setOption(IP_MULTICAST_LOOP, true);
        checkOption(dc, IP_MULTICAST_LOOP, true);


        // NullPointerException
        try {
            dc.setOption(null, "value");
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException x) {
        }
        try {
            dc.getOption(null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException x) {
        }

        // ClosedChannelException
        dc.close();
        try {
            dc.setOption(IP_MULTICAST_LOOP, true);
            throw new RuntimeException("ClosedChannelException not thrown");
        } catch (ClosedChannelException x) {
        }
    }
}
