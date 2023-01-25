/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6442088
 * @summary Change default DNS caching behavior for code not running under
 *          security manager.
 * @compile -XDignore.symbol.file=true SimpleNameService.java
 *                                     SimpleNameServiceDescriptor.java
 * @run main/othervm/timeout=200 -Dsun.net.inetaddr.ttl=20 -Dsun.net.spi.nameservice.provider.1=simple,sun DefaultCaching
 */
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Security;

public class DefaultCaching {

    public static void main(String args[]) throws Exception {

        // name service needs to resolve this.
        SimpleNameService.put("theclub", "129.156.220.219");

        test ("theclub", "129.156.220.219", true);      // lk: 1
        test ("luster", "1.16.20.2", false);            // lk: 2

        // name service now needs to know about luster
        SimpleNameService.put("luster", "10.5.18.21");

        test ("luster", "1.16.20.2", false);            // lk: 2
        sleep (10+1);
        test("luster", "10.5.18.21", true, 3);          // lk: 3
        sleep (5);

        SimpleNameService.put("foo", "10.5.18.22");
        SimpleNameService.put("theclub", "129.156.220.1");

        test ("theclub", "129.156.220.219", true, 3);
        test ("luster", "10.5.18.21", true, 3);
        test ("bar", "10.5.18.22", false, 4);
        test ("foo", "10.5.18.22", true, 5);

        // now delay to see if theclub has expired
        sleep (5);

        test ("foo", "10.5.18.22", true, 5);
        test ("theclub", "129.156.220.1", true, 6);

        sleep (11);
        // now see if luster has expired
        test ("luster", "10.5.18.21", true, 7);
        test ("theclub", "129.156.220.1", true, 7);

        // now delay to see if 3rd has expired
        sleep (10+6);

        test ("theclub", "129.156.220.1", true, 8);
        test ("luster", "10.5.18.21", true, 8);
        test ("foo", "10.5.18.22", true, 9);
    }

    /* throws RuntimeException if it fails */

    static void test (String host, String address,
                        boolean shouldSucceed, int count) {
        test (host, address, shouldSucceed);
        int got = SimpleNameService.lookupCalls();
        if (got != count) {
            throw new RuntimeException ("lookups exp/got: " + count+"/"+got);
        }
    }

    static void sleep (int seconds) {
        try {
            Thread.sleep (seconds * 1000);
        } catch (InterruptedException e) {}
    }

    static void test (String host, String address, boolean shouldSucceed) {
        InetAddress addr = null;
        try {
            addr = InetAddress.getByName (host);
            if (!shouldSucceed) {
                throw new RuntimeException (host+":"+address+": should fail");

            }
            if (!address.equals(addr.getHostAddress())) {
                throw new RuntimeException(host+":"+address+": compare failed");
            }
        } catch (UnknownHostException e) {
            if (shouldSucceed) {
                throw new RuntimeException(host+":"+address+": should succeed");
            }
        }
    }

}
