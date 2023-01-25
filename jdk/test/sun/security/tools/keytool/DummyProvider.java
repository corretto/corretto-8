/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * @bug 4906490
 * @summary Dummy security service provider.
 *          It is cited by the AltProviderPath.sh script.
 */
package org.test.dummy;

import java.util.*;
import java.security.*;

public class DummyProvider extends Provider {
    public DummyProvider() {
        super("Dummy", 0.1, "Dummy Provider");

        //
        // KeyStore
        //
        put("KeyStore.DummyKS", "sun.security.provider.JavaKeyStore$JKS");

        //
        // Signature engines
        //
        put("Signature.SHA1withDSA",
            "sun.security.provider.DSA$SHA1withDSA");
        put("Alg.Alias.Signature.DSA", "SHA1withDSA");

        //
        // Key Pair Generator engines
        //
        put("KeyPairGenerator.DSA",
            "sun.security.provider.DSAKeyPairGenerator");

        //
        // Digest engines
        //
        put("MessageDigest.SHA", "sun.security.provider.SHA");
        put("Alg.Alias.MessageDigest.SHA1", "SHA");

        //
        // Algorithm Parameter Generator engines
        //
        put("AlgorithmParameterGenerator.DSA",
            "sun.security.provider.DSAParameterGenerator");

        //
        // Algorithm Parameter engines
        //
        put("AlgorithmParameters.DSA",
            "sun.security.provider.DSAParameters");

        //
        // Key factories
        //
        put("KeyFactory.DSA", "sun.security.provider.DSAKeyFactory");

        //
        // Certificate factories
        //
        put("CertificateFactory.X.509",
            "sun.security.provider.X509Factory");
        put("Alg.Alias.CertificateFactory.X509", "X.509");
    }
}
