/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/**
 * @test
 * @bug 6840752
 * @summary  Provide out-of-the-box support for ECC algorithms
 * @library ../pkcs11
 * @library ../pkcs11/ec
 * @library ../pkcs11/sslecc
 * @library ../../../java/security/testlibrary
 * @library ../../../javax/net/ssl/TLSCommon
 * @compile -XDignore.symbol.file TestEC.java
 * @run main/othervm -Djdk.tls.namedGroups="secp256r1,sect193r1" TestEC
 */

import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;

/*
 * Leverage the collection of EC tests used by PKCS11
 *
 * NOTE: the following 6 files were copied here from the PKCS11 EC Test area
 *       and must be kept in sync with the originals:
 *
 *           ../pkcs11/ec/p12passwords.txt
 *           ../pkcs11/ec/certs/sunlabscerts.pem
 *           ../pkcs11/ec/pkcs12/secp256r1server-secp384r1ca.p12
 *           ../pkcs11/ec/pkcs12/sect193r1server-rsa1024ca.p12
 *           ../pkcs11/sslecc/keystore
 *           ../pkcs11/sslecc/truststore
 */

public class TestEC {

    public static void main(String[] args) throws Exception {
        // reset security properties to make sure that the algorithms
        // and keys used in this test are not disabled.
        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        Security.setProperty("jdk.certpath.disabledAlgorithms", "");

        ProvidersSnapshot snapshot = ProvidersSnapshot.create();
        try {
            main0(args);
        } finally {
            snapshot.restore();
        }
    }

    public static void main0(String[] args) throws Exception {
        Provider p = Security.getProvider("SunEC");

        if (p == null) {
            throw new NoSuchProviderException("Can't get SunEC provider");
        }

        System.out.println("Running tests with " + p.getName() +
            " provider...\n");
        long start = System.currentTimeMillis();

        /*
         * The entry point used for each test is its instance method
         * called main (not its static method called main).
         */
        new TestECDH().main(p);
        new TestECDSA().main(p);
        new TestCurves().main(p);
        new TestKeyFactory().main(p);
        new TestECGenSpec().main(p);
        new ReadPKCS12().main(p);
        new ReadCertificates().main(p);

        // ClientJSSEServerJSSE fails on Solaris 11 when both SunEC and
        // SunPKCS11-Solaris providers are enabled.
        // Workaround:
        // Security.removeProvider("SunPKCS11-Solaris");
        new ClientJSSEServerJSSE().main(p);

        long stop = System.currentTimeMillis();
        System.out.println("\nCompleted tests with " + p.getName() +
            " provider (" + ((stop - start) / 1000.0) + " seconds).");
    }
}
