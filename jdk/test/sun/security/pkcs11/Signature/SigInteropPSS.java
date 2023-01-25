/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;
import java.security.spec.*;
import java.security.interfaces.*;

/*
 * @test
 * @bug 8080462
 * @summary testing interoperability of PSS signatures of PKCS11 provider
 *         against SunRsaSign provider
 * @library ..
 * @modules jdk.crypto.cryptoki
 * @run main/othervm SigInteropPSS
 */
public class SigInteropPSS extends PKCS11Test {

    private static final byte[] MSG =
        "Interoperability test between SunRsaSign and SunPKCS11".getBytes();

    private static final String[] DIGESTS = {
        "SHA-224", "SHA-256", "SHA-384", "SHA-512"
    };

    public static void main(String[] args) throws Exception {
        main(new SigInteropPSS(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        Signature sigPkcs11;
        try {
            sigPkcs11 = Signature.getInstance("RSASSA-PSS", p);
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Skip testing RSASSA-PSS" +
                " due to no support");
            return;
        }

        Signature sigSunRsaSign =
                Signature.getInstance("RSASSA-PSS", "SunRsaSign");

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", p);
        kpg.initialize(3072);
        KeyPair kp = kpg.generateKeyPair();
        boolean status;
        try {
            status = runTest(sigSunRsaSign, sigPkcs11, kp);
            status &= runTest(sigPkcs11, sigSunRsaSign, kp);
        } catch (Exception e) {
            System.out.println("Unexpected exception: " + e);
            e.printStackTrace(System.out);
            status = false;
        }

        if (!status) {
            throw new RuntimeException("One or more test failed");
        }
        System.out.println("Test passed");
    }

    static boolean runTest(Signature signer, Signature verifier, KeyPair kp) throws Exception {
        System.out.println("\tSign using " + signer.getProvider().getName());
        System.out.println("\tVerify using " + verifier.getProvider().getName());

        boolean status;
        for (String digestAlg : DIGESTS) {
            System.out.println("\tDigest = " + digestAlg);
            PSSParameterSpec params = new PSSParameterSpec(digestAlg, "MGF1",
                    new MGF1ParameterSpec(digestAlg), 0, 1);
            try {
                signer.setParameter(params);
                signer.initSign(kp.getPrivate());
                verifier.setParameter(params);
                verifier.initVerify(kp.getPublic());
            } catch (Exception e) {
                System.out.println("\tERROR: unexpected ex during init" + e);
                status = false;
                continue;
            }
            try {
                signer.update(MSG);
                byte[] sigBytes = signer.sign();
                verifier.update(MSG);
                boolean isValid = verifier.verify(sigBytes);
                if (isValid) {
                    System.out.println("\tPSS Signature verified");
                } else {
                    System.out.println("\tERROR verifying PSS Signature");
                    status = false;
                }
            } catch (Exception e) {
                System.out.println("\tERROR: unexpected ex" + e);
                e.printStackTrace();
                status = false;
            }
        }
        return true;
    }
}
