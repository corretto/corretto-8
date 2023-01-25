/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import sun.misc.IOUtils;
import jdk.testlibrary.SecurityTools;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.JarUtils;
import sun.security.pkcs.ContentInfo;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.PKCS9Attribute;
import sun.security.pkcs.SignerInfo;
import sun.security.timestamp.TimestampToken;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;

import jdk.testlibrary.Utils;

/*
 * @test
 * @bug 6543842 6543440 6939248 8009636 8024302 8163304 8169911 8169688 8171121
 *      8180289 8172404 8269039 8275887
 * @summary checking response of timestamp
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.timestamp
 *          java.base/sun.security.x509
 *          java.base/sun.security.util
 *          java.base/sun.security.tools.keytool
 * @library /lib/testlibrary
 * @compile -XDignore.symbol.file TimestampCheck.java
 * @run main/othervm/timeout=600 TimestampCheck
 */
public class TimestampCheck {

    static final String defaultPolicyId = "2.3.4";
    static String host = null;

    static class Handler implements HttpHandler, AutoCloseable {

        private final HttpServer httpServer;
        private final String keystore;

        @Override
        public void handle(HttpExchange t) throws IOException {
            int len = 0;
            for (String h: t.getRequestHeaders().keySet()) {
                if (h.equalsIgnoreCase("Content-length")) {
                    len = Integer.valueOf(t.getRequestHeaders().get(h).get(0));
                }
            }
            byte[] input = new byte[len];
            t.getRequestBody().read(input);

            try {
                String path = t.getRequestURI().getPath().substring(1);
                byte[] output = sign(input, path);
                Headers out = t.getResponseHeaders();
                out.set("Content-Type", "application/timestamp-reply");

                t.sendResponseHeaders(200, output.length);
                OutputStream os = t.getResponseBody();
                os.write(output);
            } catch (Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
            }
            t.close();
        }

        /**
         * @param input The data to sign
         * @param path different cases to simulate, impl on URL path
         * @returns the signed
         */
        byte[] sign(byte[] input, String path) throws Exception {
            DerValue value = new DerValue(input);
            System.out.println("#\n# Incoming Request\n===================");
            System.out.println("# Version: " + value.data.getInteger());
            DerValue messageImprint = value.data.getDerValue();
            AlgorithmId aid = AlgorithmId.parse(
                    messageImprint.data.getDerValue());
            System.out.println("# AlgorithmId: " + aid);

            ObjectIdentifier policyId = new ObjectIdentifier(defaultPolicyId);
            BigInteger nonce = null;
            while (value.data.available() > 0) {
                DerValue v = value.data.getDerValue();
                if (v.tag == DerValue.tag_Integer) {
                    nonce = v.getBigInteger();
                    System.out.println("# nonce: " + nonce);
                } else if (v.tag == DerValue.tag_Boolean) {
                    System.out.println("# certReq: " + v.getBoolean());
                } else if (v.tag == DerValue.tag_ObjectId) {
                    policyId = v.getOID();
                    System.out.println("# PolicyID: " + policyId);
                }
            }

            System.out.println("#\n# Response\n===================");
            FileInputStream is = new FileInputStream(keystore);
            KeyStore ks = KeyStore.getInstance("JCEKS");
            ks.load(is, "changeit".toCharArray());
            is.close();

            // If path starts with "ts", use the TSA it points to.
            // Otherwise, always use "ts".
            String alias = path.startsWith("ts") ? path : "ts";

            if (path.equals("diffpolicy")) {
                policyId = new ObjectIdentifier(defaultPolicyId);
            }

            DerOutputStream statusInfo = new DerOutputStream();
            statusInfo.putInteger(0);

            AlgorithmId[] algorithms = {aid};
            Certificate[] chain = ks.getCertificateChain(alias);
            X509Certificate[] signerCertificateChain;
            X509Certificate signer = (X509Certificate)chain[0];

            if (path.equals("fullchain")) {   // Only case 5 uses full chain
                signerCertificateChain = new X509Certificate[chain.length];
                for (int i=0; i<chain.length; i++) {
                    signerCertificateChain[i] = (X509Certificate)chain[i];
                }
            } else if (path.equals("nocert")) {
                signerCertificateChain = new X509Certificate[0];
            } else {
                signerCertificateChain = new X509Certificate[1];
                signerCertificateChain[0] = (X509Certificate)chain[0];
            }

            DerOutputStream tst = new DerOutputStream();

            tst.putInteger(1);
            tst.putOID(policyId);

            if (!path.equals("baddigest") && !path.equals("diffalg")) {
                tst.putDerValue(messageImprint);
            } else {
                byte[] data = messageImprint.toByteArray();
                if (path.equals("diffalg")) {
                    data[6] = (byte)0x01;
                } else {
                    data[data.length-1] = (byte)0x01;
                    data[data.length-2] = (byte)0x02;
                    data[data.length-3] = (byte)0x03;
                }
                tst.write(data);
            }

            tst.putInteger(1);

            Instant instant = Instant.now();
            if (path.equals("tsold")) {
                instant = instant.minus(20, ChronoUnit.DAYS);
            } else if (path.equals("tsbefore2019")) {
                // Saturday, August 18, 2018 7:04:58 PM
                instant = Instant.ofEpochSecond(1534619098l);
            }
            tst.putGeneralizedTime(Date.from(instant));

            if (path.equals("diffnonce")) {
                tst.putInteger(1234);
            } else if (path.equals("nononce")) {
                // no noce
            } else {
                tst.putInteger(nonce);
            }

            DerOutputStream tstInfo = new DerOutputStream();
            tstInfo.write(DerValue.tag_Sequence, tst);

            DerOutputStream tstInfo2 = new DerOutputStream();
            tstInfo2.putOctetString(tstInfo.toByteArray());

            // Always use the same algorithm at timestamp signing
            // so it is different from the hash algorithm.
            String sigAlg = "SHA256withRSA";
            Signature sig = Signature.getInstance(sigAlg);
            sig.initSign((PrivateKey)(ks.getKey(
                    alias, "changeit".toCharArray())));
            sig.update(tstInfo.toByteArray());

            ContentInfo contentInfo = new ContentInfo(new ObjectIdentifier(
                    "1.2.840.113549.1.9.16.1.4"),
                    new DerValue(tstInfo2.toByteArray()));

            System.out.println("# Signing...");
            System.out.println("# " + new X500Name(signer
                    .getIssuerX500Principal().getName()));
            System.out.println("# " + signer.getSerialNumber());

            SignerInfo signerInfo = new SignerInfo(
                    new X500Name(signer.getIssuerX500Principal().getName()),
                    signer.getSerialNumber(),
                    AlgorithmId.get(AlgorithmId.getDigAlgFromSigAlg(sigAlg)),
                    AlgorithmId.get(AlgorithmId.getEncAlgFromSigAlg(sigAlg)),
                    sig.sign());

            SignerInfo[] signerInfos = {signerInfo};
            PKCS7 p7 = new PKCS7(algorithms, contentInfo,
                    signerCertificateChain, signerInfos);
            ByteArrayOutputStream p7out = new ByteArrayOutputStream();
            p7.encodeSignedData(p7out);

            DerOutputStream response = new DerOutputStream();
            response.write(DerValue.tag_Sequence, statusInfo);
            response.putDerValue(new DerValue(p7out.toByteArray()));

            DerOutputStream out = new DerOutputStream();
            out.write(DerValue.tag_Sequence, response);

            return out.toByteArray();
        }

        private Handler(HttpServer httpServer, String keystore) {
            this.httpServer = httpServer;
            this.keystore = keystore;
        }

        /**
         * Initialize TSA instance.
         *
         * Extended Key Info extension of certificate that is used for
         * signing TSA responses should contain timeStamping value.
         */
        static Handler init(int port, String keystore) throws IOException {
            HttpServer httpServer = HttpServer.create(
                    new InetSocketAddress(port), 0);
            Handler tsa = new Handler(httpServer, keystore);
            httpServer.createContext("/", tsa);
            return tsa;
        }

        /**
         * Start TSA service.
         */
        void start() {
            httpServer.start();
        }

        /**
         * Stop TSA service.
         */
        void stop() {
            httpServer.stop(0);
        }

        /**
         * Return server port number.
         */
        int getPort() {
            return httpServer.getAddress().getPort();
        }

        @Override
        public void close() throws Exception {
            stop();
        }
    }

    public static void main(String[] args) throws Throwable {

        try (Handler tsa = Handler.init(0, "ks");) {
            tsa.start();
            int port = tsa.getPort();

            host = "http://localhost:" + port + "/";

            if (args.length == 0) {         // Run this test

                prepare();

                sign("normal")
                        .shouldNotContain("Warning")
                        .shouldContain("The signer certificate will expire on")
                        .shouldContain("The timestamp will expire on")
                        .shouldHaveExitValue(0);

                verify("normal.jar")
                        .shouldNotContain("Warning")
                        .shouldHaveExitValue(0);

                verify("normal.jar", "-verbose")
                        .shouldNotContain("Warning")
                        .shouldContain("The signer certificate will expire on")
                        .shouldContain("The timestamp will expire on")
                        .shouldHaveExitValue(0);

                // Simulate signing at a previous date:
                // 1. tsold will create a timestamp of 20 days ago.
                // 2. oldsigner expired 10 days ago.
                signVerbose("tsold", "unsigned.jar", "tsold.jar", "oldsigner")
                        .shouldNotContain("Warning")
                        .shouldMatch("signer certificate expired on .*. "
                                + "However, the JAR will be valid")
                        .shouldHaveExitValue(0);

                // It verifies perfectly.
                verify("tsold.jar", "-verbose", "-certs")
                        .shouldNotContain("Warning")
                        .shouldMatch("signer certificate expired on .*. "
                                + "However, the JAR will be valid")
                        .shouldHaveExitValue(0);

                // should not be disabled because timestamped before 2019
                signVerbose("tsbefore2019", "unsigned.jar", "tsbefore2019.jar", "pre2019signer",
                        "-digestalg", "SHA-1")
                        .shouldHaveExitValue(4);

                verify("tsbefore2019.jar", "-verbose")
                        .shouldHaveExitValue(0)
                        .shouldMatch("Digest.*SHA-1.*(weak)")
                        .shouldMatch("signer certificate expired on .*. "
                                + "However, the JAR will be valid");

                // No timestamp
                signVerbose(null, "unsigned.jar", "none.jar", "signer")
                        .shouldContain("is not timestamped")
                        .shouldContain("The signer certificate will expire on")
                        .shouldHaveExitValue(0);

                verify("none.jar", "-verbose")
                        .shouldContain("do not include a timestamp")
                        .shouldContain("The signer certificate will expire on")
                        .shouldHaveExitValue(0);

                // Error cases

                signVerbose(null, "unsigned.jar", "badku.jar", "badku")
                        .shouldContain("KeyUsage extension doesn't allow code signing")
                        .shouldHaveExitValue(8);
                checkBadKU("badku.jar");

                // 8180289: unvalidated TSA cert chain
                sign("tsnoca")
                        .shouldContain("The TSA certificate chain is invalid. "
                                + "Reason: Path does not chain with any of the trust anchors")
                        .shouldHaveExitValue(64);

                verify("tsnoca.jar", "-verbose", "-certs")
                        .shouldHaveExitValue(64)
                        .shouldContain("jar verified")
                        .shouldContain("Invalid TSA certificate chain: "
                                + "Path does not chain with any of the trust anchors")
                        .shouldContain("TSA certificate chain is invalid."
                                + " Reason: Path does not chain with any of the trust anchors");

                sign("nononce")
                        .shouldContain("Nonce missing in timestamp token")
                        .shouldHaveExitValue(1);
                sign("diffnonce")
                        .shouldContain("Nonce changed in timestamp token")
                        .shouldHaveExitValue(1);
                sign("baddigest")
                        .shouldContain("Digest octets changed in timestamp token")
                        .shouldHaveExitValue(1);
                sign("diffalg")
                        .shouldContain("Digest algorithm not")
                        .shouldHaveExitValue(1);

                sign("fullchain")
                        .shouldHaveExitValue(0);   // Success, 6543440 solved.

                sign("tsbad1")
                        .shouldContain("Certificate is not valid for timestamping")
                        .shouldHaveExitValue(1);
                sign("tsbad2")
                        .shouldContain("Certificate is not valid for timestamping")
                        .shouldHaveExitValue(1);
                sign("tsbad3")
                        .shouldContain("Certificate is not valid for timestamping")
                        .shouldHaveExitValue(1);
                sign("nocert")
                        .shouldContain("Certificate not included in timestamp token")
                        .shouldHaveExitValue(1);

                sign("policy", "-tsapolicyid",  "1.2.3")
                        .shouldHaveExitValue(0);
                checkTimestamp("policy.jar", "1.2.3", "SHA-256");

                sign("diffpolicy", "-tsapolicyid", "1.2.3")
                        .shouldContain("TSAPolicyID changed in timestamp token")
                        .shouldHaveExitValue(1);

                sign("sha384alg", "-tsadigestalg", "SHA-384")
                        .shouldHaveExitValue(0);
                checkTimestamp("sha384alg.jar", defaultPolicyId, "SHA-384");

                // Disabled algorithms
                signVerbose(null, "unsigned.jar", "sha1alg.jar", "signer",
                        "-digestalg", "SHA-1")
                        .shouldHaveExitValue(4)
                        .shouldContain("jar signed")
                        .shouldContain("with signer errors")
                        .shouldMatch("SHA-1.*-digestalg.*is disabled");
                verify("sha1alg.jar", "-verbose")
                        .shouldHaveExitValue(16)
                        .shouldContain("treated as unsigned")
                        .shouldMatch("Digest.*SHA-1.*(disabled)");

                sign("sha1tsaalg", "-tsadigestalg", "SHA-1", "-strict")
                        .shouldHaveExitValue(4)
                        .shouldContain("jar signed")
                        .shouldContain("with signer errors")
                        .shouldMatch("SHA-1.*-tsadigestalg.*is disabled");
                verify("sha1tsaalg.jar", "-verbose")
                        .shouldHaveExitValue(16)
                        .shouldContain("treated as unsigned")
                        .shouldMatch("Timestamp.*digest.*SHA-1.*(disabled)");

                sign("tsdisabled", "-digestalg", "MD5",
                                "-sigalg", "MD5withRSA", "-tsadigestalg", "MD5")
                        .shouldHaveExitValue(68)
                        .shouldContain("TSA certificate chain is invalid")
                        .shouldMatch("MD5.*-digestalg.*is disabled")
                        .shouldMatch("MD5.*-tsadigestalg.*is disabled")
                        .shouldMatch("MD5withRSA.*-sigalg.*is disabled");
                checkDisabled("tsdisabled.jar");

                signVerbose("tsdisabled", "unsigned.jar", "tsdisabled2.jar", "signer")
                        .shouldHaveExitValue(64)
                        .shouldContain("TSA certificate chain is invalid");

                // Disabled timestamp is an error and jar treated unsigned
                verify("tsdisabled2.jar", "-verbose")
                        .shouldHaveExitValue(16)
                        .shouldContain("treated as unsigned")
                        .shouldNotMatch("Signature.*(disabled)")
                        .shouldMatch("Timestamp.*512.*(disabled)");

                // Algorithm used in signing is disabled
                signVerbose("normal", "unsigned.jar", "halfDisabled.jar", "signer",
                        "-digestalg", "MD5")
                        .shouldContain("-digestalg option is considered a security risk and is disabled")
                        .shouldHaveExitValue(4);
                checkHalfDisabled("halfDisabled.jar");

                // sign with DSA key
                signVerbose("normal", "unsigned.jar", "sign1.jar", "dsakey")
                        .shouldHaveExitValue(0);

                // sign with RSAkeysize < 1024
                signVerbose("normal", "sign1.jar", "sign2.jar", "disabledkeysize")
                        .shouldContain("Algorithm constraints check failed on keysize")
                        .shouldNotContain("option is considered a security " +
                            "risk and is disabled")
                        .shouldHaveExitValue(4);
                checkMultiple("sign2.jar");

                // Disabled algorithms
                sign("tsweak", "-digestalg", "SHA1",
                                "-sigalg", "SHA1withRSA", "-tsadigestalg", "SHA1")
                        .shouldHaveExitValue(4)
                        .shouldMatch("SHA1.*-digestalg.*is disabled")
                        .shouldMatch("SHA1.*-tsadigestalg.*is disabled")
                        .shouldMatch("SHA1withRSA.*-sigalg.*is disabled");
                verify("tsweak.jar", "-verbose")
                        .shouldHaveExitValue(16)
                        .shouldContain("treated as unsigned")
                        .shouldMatch("Digest algorithm: .*(disabled)")
                        .shouldMatch("Signature algorithm: .*(disabled)")
                        .shouldMatch("Timestamp digest algorithm: .*(disabled)");

                // Legacy algorithms (1024-bit key)
                signVerbose("tsweak", "unsigned.jar", "tsweak2.jar", "signer")
                        .shouldHaveExitValue(0);

                verify("tsweak2.jar", "-verbose")
                        .shouldHaveExitValue(0)
                        .shouldContain("jar verified")
                        .shouldMatch("Timestamp.*1024.*(weak)");

                // Algorithm used in signing is disabled
                signVerbose("normal", "unsigned.jar", "halfWeak.jar", "signer",
                        "-digestalg", "SHA1")
                        .shouldContain("-digestalg option is considered a security risk and is disabled.")
                        .shouldHaveExitValue(4);
                verify("halfWeak.jar", "-verbose")
                        .shouldHaveExitValue(16)
                        .shouldContain("treated as unsigned")
                        .shouldMatch("Digest algorithm: .*(disabled)");

                // sign with DSA key
                signVerbose("normal", "unsigned.jar", "sign1.jar", "dsakey")
                        .shouldHaveExitValue(0);

                // sign with RSAkeysize < 2048
                signVerbose("normal", "sign1.jar", "sign2.jar", "weakkeysize")
                        .shouldNotContain("Algorithm constraints check failed on keysize")
                        .shouldNotContain("The SHA-256 algorithm specified " +
                            "for the -digestalg option is considered a " +
                            "security risk")
                        .shouldNotContain("The SHA256withRSA algorithm " +
                            "specified for the -sigalg option is considered " +
                            "a security risk")
                        .shouldNotContain("The SHA-256 algorithm specified " +
                            "for the -tsadigestalg option is considered a " +
                            "security risk")
                        .shouldContain("The RSA signing key has a keysize " +
                            "of 1024 which is considered a security risk")
                        .shouldHaveExitValue(0);
                checkMultipleWeak("sign2.jar");


                // 8191438: jarsigner should print when a timestamp will expire
                checkExpiration();

                // When .SF or .RSA is missing or invalid
                checkMissingOrInvalidFiles("normal.jar");

                if (Files.exists(Paths.get("ts2.cert"))) {
                    checkInvalidTsaCertKeyUsage();
                }
            } else {                        // Run as a standalone server
                System.out.println("TSA started at " + host
                        + ". Press Enter to quit server");
                System.in.read();
            }
        }
    }

    private static void checkExpiration() throws Exception {

        // Warning when expired or expiring
        signVerbose(null, "unsigned.jar", "expired.jar", "expired")
                .shouldContain("signer certificate has expired")
                .shouldHaveExitValue(4);
        verify("expired.jar")
                .shouldContain("signer certificate has expired")
                .shouldHaveExitValue(4);
        signVerbose(null, "unsigned.jar", "expiring.jar", "expiring")
                .shouldContain("signer certificate will expire within")
                .shouldHaveExitValue(0);
        verify("expiring.jar")
                .shouldContain("signer certificate will expire within")
                .shouldHaveExitValue(0);
        // Info for long
        signVerbose(null, "unsigned.jar", "long.jar", "long")
                .shouldNotContain("signer certificate has expired")
                .shouldNotContain("signer certificate will expire within")
                .shouldContain("signer certificate will expire on")
                .shouldHaveExitValue(0);
        verify("long.jar")
                .shouldNotContain("signer certificate has expired")
                .shouldNotContain("signer certificate will expire within")
                .shouldNotContain("The signer certificate will expire")
                .shouldHaveExitValue(0);
        verify("long.jar", "-verbose")
                .shouldContain("The signer certificate will expire")
                .shouldHaveExitValue(0);

        // Both expired
        signVerbose("tsexpired", "unsigned.jar",
                "tsexpired-expired.jar", "expired")
                .shouldContain("The signer certificate has expired.")
                .shouldContain("The timestamp has expired.")
                .shouldHaveExitValue(4);
        verify("tsexpired-expired.jar")
                .shouldContain("signer certificate has expired")
                .shouldContain("timestamp has expired.")
                .shouldHaveExitValue(4);

        // TS expired but signer still good
        signVerbose("tsexpired", "unsigned.jar",
                "tsexpired-long.jar", "long")
                .shouldContain("The timestamp expired on")
                .shouldHaveExitValue(0);
        verify("tsexpired-long.jar")
                .shouldMatch("timestamp expired on.*However, the JAR will be valid")
                .shouldNotContain("Error")
                .shouldHaveExitValue(0);

        signVerbose("tsexpired", "unsigned.jar",
                "tsexpired-ca.jar", "ca")
                .shouldContain("The timestamp has expired.")
                .shouldHaveExitValue(4);
        verify("tsexpired-ca.jar")
                .shouldNotContain("timestamp has expired")
                .shouldNotContain("Error")
                .shouldHaveExitValue(0);

        // Warning when expiring
        sign("tsexpiring")
                .shouldContain("timestamp will expire within")
                .shouldHaveExitValue(0);
        verify("tsexpiring.jar")
                .shouldContain("timestamp will expire within")
                .shouldNotContain("still valid")
                .shouldHaveExitValue(0);

        signVerbose("tsexpiring", "unsigned.jar",
                "tsexpiring-ca.jar", "ca")
                .shouldContain("self-signed")
                .stderrShouldNotMatch("The.*expir")
                .shouldHaveExitValue(4); // self-signed
        verify("tsexpiring-ca.jar")
                .stderrShouldNotMatch("The.*expir")
                .shouldHaveExitValue(0);

        signVerbose("tsexpiringsoon", "unsigned.jar",
                "tsexpiringsoon-long.jar", "long")
                .shouldContain("The timestamp will expire")
                .shouldHaveExitValue(0);
        verify("tsexpiringsoon-long.jar")
                .shouldMatch("timestamp will expire.*However, the JAR will be valid until")
                .shouldHaveExitValue(0);

        // Info for long
        sign("tslong")
                .shouldNotContain("timestamp has expired")
                .shouldNotContain("timestamp will expire within")
                .shouldContain("timestamp will expire on")
                .shouldContain("signer certificate will expire on")
                .shouldHaveExitValue(0);
        verify("tslong.jar")
                .shouldNotContain("timestamp has expired")
                .shouldNotContain("timestamp will expire within")
                .shouldNotContain("timestamp will expire on")
                .shouldNotContain("signer certificate will expire on")
                .shouldHaveExitValue(0);
        verify("tslong.jar", "-verbose")
                .shouldContain("timestamp will expire on")
                .shouldContain("signer certificate will expire on")
                .shouldHaveExitValue(0);
    }

    private static void checkInvalidTsaCertKeyUsage() throws Exception {

        // Hack: Rewrite the TSA cert inside normal.jar into ts2.jar.

        // Both the cert and the serial number must be rewritten.
        byte[] tsCert = Files.readAllBytes(Paths.get("ts.cert"));
        byte[] ts2Cert = Files.readAllBytes(Paths.get("ts2.cert"));
        byte[] tsSerial = getCert(tsCert)
                .getSerialNumber().toByteArray();
        byte[] ts2Serial = getCert(ts2Cert)
                .getSerialNumber().toByteArray();

        byte[] oldBlock;
        try (JarFile normal = new JarFile("normal.jar")) {
            oldBlock = Utils.readAllBytes(normal.getInputStream(
                    normal.getJarEntry("META-INF/SIGNER.RSA")));
        }

        JarUtils.updateJar("normal.jar", "ts2.jar",
                mapOf("META-INF/SIGNER.RSA",
                        updateBytes(updateBytes(oldBlock, tsCert, ts2Cert),
                                tsSerial, ts2Serial)));

        verify("ts2.jar", "-verbose", "-certs")
                .shouldHaveExitValue(64)
                .shouldContain("jar verified")
                .shouldContain("Invalid TSA certificate chain: Extended key usage does not permit use for TSA server");
    }

    public static X509Certificate getCert(byte[] data)
            throws CertificateException, IOException {
        return (X509Certificate)
                CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(data));
    }

    private static byte[] updateBytes(byte[] old, byte[] from, byte[] to) {
        int pos = 0;
        while (true) {
            if (pos + from.length > old.length) {
                return null;
            }
            if (Arrays.equals(Arrays.copyOfRange(old, pos, pos+from.length), from)) {
                byte[] result = old.clone();
                System.arraycopy(to, 0, result, pos, from.length);
                return result;
            }
            pos++;
        }
    }

    private static void checkMissingOrInvalidFiles(String s)
            throws Throwable {

        JarUtils.updateJar(s, "1.jar", mapOf("META-INF/SIGNER.SF", Boolean.FALSE));
        verify("1.jar", "-verbose")
                .shouldHaveExitValue(16)
                .shouldContain("treated as unsigned")
                .shouldContain("Missing signature-related file META-INF/SIGNER.SF");
        JarUtils.updateJar(s, "2.jar", mapOf("META-INF/SIGNER.RSA", Boolean.FALSE));
        verify("2.jar", "-verbose")
                .shouldHaveExitValue(16)
                .shouldContain("treated as unsigned")
                .shouldContain("Missing block file for signature-related file META-INF/SIGNER.SF");
        JarUtils.updateJar(s, "3.jar", mapOf("META-INF/SIGNER.SF", "dummy"));
        verify("3.jar", "-verbose")
                .shouldHaveExitValue(16)
                .shouldContain("treated as unsigned")
                .shouldContain("Unparsable signature-related file META-INF/SIGNER.SF");
        JarUtils.updateJar(s, "4.jar", mapOf("META-INF/SIGNER.RSA", "dummy"));
        verify("4.jar", "-verbose")
                .shouldHaveExitValue(16)
                .shouldContain("treated as unsigned")
                .shouldContain("Unparsable signature-related file META-INF/SIGNER.RSA");
    }

    static OutputAnalyzer jarsigner(List<String> extra)
            throws Exception {
        List<String> args = new ArrayList<>(
                listOf("-keystore", "ks", "-storepass", "changeit"));
        args.addAll(extra);
        return SecurityTools.jarsigner(args);
    }

    static OutputAnalyzer verify(String file, String... extra)
            throws Exception {
        List<String> args = new ArrayList<>();
        args.add("-verify");
        args.add("-strict");
        args.add(file);
        args.addAll(Arrays.asList(extra));
        return jarsigner(args);
    }

    static void checkBadKU(String file) throws Exception {
        System.err.println("BadKU: " + file);
        verify(file)
                .shouldHaveExitValue(16)
                .shouldContain("treated as unsigned")
                .shouldContain("re-run jarsigner with debug enabled");
        verify(file, "-verbose")
                .shouldHaveExitValue(16)
                .shouldContain("Signed by")
                .shouldContain("treated as unsigned")
                .shouldContain("re-run jarsigner with debug enabled");
        verify(file, "-J-Djava.security.debug=jar")
                .shouldHaveExitValue(16)
                .shouldContain("SignatureException: Key usage restricted")
                .shouldContain("treated as unsigned")
                .shouldContain("re-run jarsigner with debug enabled");
    }

    static void checkDisabled(String file) throws Exception {
        verify(file)
                .shouldHaveExitValue(16)
                .shouldContain("treated as unsigned")
                .shouldMatch("weak algorithm that is now disabled.")
                .shouldMatch("Re-run jarsigner with the -verbose option for more details");
        verify(file, "-verbose")
                .shouldHaveExitValue(16)
                .shouldContain("treated as unsigned")
                .shouldMatch("weak algorithm that is now disabled by")
                .shouldMatch("Digest algorithm: .*(disabled)")
                .shouldMatch("Signature algorithm: .*(disabled)")
                .shouldMatch("Timestamp digest algorithm: .*(disabled)")
                .shouldNotMatch("Timestamp signature algorithm: .*(weak).*(weak)")
                .shouldMatch("Timestamp signature algorithm: .*key.*(disabled)");
        verify(file, "-J-Djava.security.debug=jar")
                .shouldHaveExitValue(16)
                .shouldMatch("SignatureException:.*MD5");
    }

    static void checkHalfWeak(String file) throws Exception {
        verify(file)
                .shouldHaveExitValue(0)
                .shouldNotContain("treated as unsigned");
        verify(file, "-verbose")
                .shouldHaveExitValue(0)
                .shouldNotContain("treated as unsigned")
                .shouldMatch("Digest algorithm: .*(weak)")
                .shouldNotMatch("Signature algorithm: .*(weak)")
                .shouldNotMatch("Signature algorithm: .*(disabled)")
                .shouldNotMatch("Timestamp digest algorithm: .*(weak)")
                .shouldNotMatch("Timestamp signature algorithm: .*(weak).*(weak)")
                .shouldNotMatch("Timestamp signature algorithm: .*(disabled).*(disabled)")
                .shouldNotMatch("Timestamp signature algorithm: .*key.*(weak)")
                .shouldNotMatch("Timestamp signature algorithm: .*key.*(disabled)");
     }

    static void checkMultiple(String file) throws Exception {
        verify(file)
                .shouldHaveExitValue(0)
                .shouldContain("jar verified");
        verify(file, "-verbose", "-certs")
                .shouldHaveExitValue(0)
                .shouldContain("jar verified")
                .shouldMatch("X.509.*CN=dsakey")
                .shouldNotMatch("X.509.*CN=disabledkeysize")
                .shouldMatch("Signed by .*CN=dsakey")
                .shouldMatch("Signed by .*CN=disabledkeysize")
                .shouldMatch("Signature algorithm: .*key.*(disabled)");
    }

    static void checkWeak(String file) throws Exception {
        verify(file)
                .shouldHaveExitValue(0)
                .shouldNotContain("treated as unsigned");
        verify(file, "-verbose")
                .shouldHaveExitValue(0)
                .shouldNotContain("treated as unsigned")
                .shouldMatch("Digest algorithm: .*(weak)")
                .shouldMatch("Signature algorithm: .*(weak)")
                .shouldMatch("Timestamp digest algorithm: .*(weak)")
                .shouldNotMatch("Timestamp signature algorithm: .*(weak).*(weak)")
                .shouldMatch("Timestamp signature algorithm: .*key.*(weak)");
        verify(file, "-J-Djava.security.debug=jar")
                .shouldHaveExitValue(0)
                .shouldNotMatch("SignatureException:.*disabled");

        // keytool should print out warnings when reading or
        // generating cert/cert req using legacy algorithms.
        String sout = SecurityTools.keytool("-printcert -jarfile " + file)
                .stderrShouldContain("The TSA certificate uses a 1024-bit RSA key" +
                        " which is considered a security risk." +
                        " This key size will be disabled in a future update.")
                .getStdout();
        if (sout.indexOf("weak", sout.indexOf("Timestamp:")) < 0) {
            throw new RuntimeException("timestamp not weak: " + sout);
        }
    }

    static void checkHalfDisabled(String file) throws Exception {
        verify(file)
                .shouldHaveExitValue(16)
                .shouldContain("treated as unsigned")
                .shouldMatch("weak algorithm that is now disabled.")
                .shouldMatch("Re-run jarsigner with the -verbose option for more details");
        verify(file, "-verbose")
                .shouldHaveExitValue(16)
                .shouldContain("treated as unsigned")
                .shouldMatch("weak algorithm that is now disabled by")
                .shouldMatch("Digest algorithm: .*(disabled)")
                .shouldNotMatch("Signature algorithm: .*(weak)")
                .shouldNotMatch("Signature algorithm: .*(disabled)")
                .shouldNotMatch("Timestamp digest algorithm: .*(disabled)")
                .shouldNotMatch("Timestamp signature algorithm: .*(weak).*(weak)")
                .shouldNotMatch("Timestamp signature algorithm: .*(disabled).*(disabled)")
                .shouldNotMatch("Timestamp signature algorithm: .*key.*(weak)")
                .shouldNotMatch("Timestamp signature algorithm: .*key.*(disabled)");
    }

    static void checkMultipleWeak(String file) throws Exception {
        verify(file)
                .shouldHaveExitValue(0)
                .shouldContain("jar verified");
        verify(file, "-verbose", "-certs")
                .shouldHaveExitValue(0)
                .shouldContain("jar verified")
                .shouldMatch("X.509.*CN=dsakey")
                .shouldMatch("X.509.*CN=weakkeysize")
                .shouldMatch("Signed by .*CN=dsakey")
                .shouldMatch("Signed by .*CN=weakkeysize")
                .shouldMatch("Signature algorithm: .*key.*(weak)");
    }

    static void checkTimestamp(String file, String policyId, String digestAlg)
            throws Exception {
        try (JarFile jf = new JarFile(file)) {
            JarEntry je = jf.getJarEntry("META-INF/SIGNER.RSA");
            try (InputStream is = jf.getInputStream(je)) {
                byte[] content = IOUtils.readAllBytes(is);
                PKCS7 p7 = new PKCS7(content);
                SignerInfo[] si = p7.getSignerInfos();
                if (si == null || si.length == 0) {
                    throw new Exception("Not signed");
                }
                PKCS9Attribute p9 = si[0].getUnauthenticatedAttributes()
                        .getAttribute(PKCS9Attribute.SIGNATURE_TIMESTAMP_TOKEN_OID);
                PKCS7 tsToken = new PKCS7((byte[]) p9.getValue());
                TimestampToken tt =
                        new TimestampToken(tsToken.getContentInfo().getData());
                if (!tt.getHashAlgorithm().toString().equals(digestAlg)) {
                    throw new Exception("Digest alg different");
                }
                if (!tt.getPolicyID().equals(policyId)) {
                    throw new Exception("policyId different");
                }
            }
        }
    }

    static int which = 0;

    /**
     * Sign with a TSA path. Always use alias "signer" to sign "unsigned.jar".
     * The signed jar name is always path.jar.
     *
     * @param extra more args given to jarsigner
     */
    static OutputAnalyzer sign(String path, String... extra)
            throws Exception {
        return signVerbose(
                path,
                "unsigned.jar",
                path + ".jar",
                "signer",
                extra);
    }

    static OutputAnalyzer signVerbose(
            String path,    // TSA URL path
            String oldJar,
            String newJar,
            String alias,   // signer
            String...extra) throws Exception {
        which++;
        System.out.println("\n>> Test #" + which);
        List<String> args = new ArrayList<>();
        args.add("-strict");
        args.add("-verbose");
        args.add("-debug");
        args.add("-signedjar");
        args.add(newJar);
        args.add(oldJar);
        args.add(alias);
        if (path != null) {
            args.add("-tsa");
            args.add(host + path);
         }
        args.addAll(Arrays.asList(extra));
        return jarsigner(args);
    }

    static void prepare() throws Exception {
        JarUtils.createJar("unsigned.jar", "A");
        Files.deleteIfExists(Paths.get("ks"));
        keytool("-alias signer -genkeypair -ext bc -dname CN=signer");
        keytool("-alias oldsigner -genkeypair -dname CN=oldsigner");
        keytool("-alias pre2019signer -genkeypair -dname CN=pre2019signer");
        keytool("-alias dsakey -genkeypair -keyalg DSA -dname CN=dsakey");
        keytool("-alias weakkeysize -genkeypair -keysize 1024 -dname CN=weakkeysize");
        keytool("-alias disabledkeysize -genkeypair -keysize 512 -dname CN=disabledkeysize");
        keytool("-alias badku -genkeypair -dname CN=badku");
        keytool("-alias ts -genkeypair -dname CN=ts");
        keytool("-alias tsold -genkeypair -dname CN=tsold");
        keytool("-alias tsbefore2019 -genkeypair -dname CN=tsbefore2019");
        keytool("-alias tsweak -genkeypair -keysize 1024 -dname CN=tsweak");
        keytool("-alias tsdisabled -genkeypair -keysize 512 -dname CN=tsdisabled");
        keytool("-alias tsbad1 -genkeypair -dname CN=tsbad1");
        keytool("-alias tsbad2 -genkeypair -dname CN=tsbad2");
        keytool("-alias tsbad3 -genkeypair -dname CN=tsbad3");
        keytool("-alias tsnoca -genkeypair -dname CN=tsnoca");

        keytool("-alias expired -genkeypair -dname CN=expired");
        keytool("-alias expiring -genkeypair -dname CN=expiring");
        keytool("-alias long -genkeypair -dname CN=long");
        keytool("-alias tsexpired -genkeypair -dname CN=tsexpired");
        keytool("-alias tsexpiring -genkeypair -dname CN=tsexpiring");
        keytool("-alias tsexpiringsoon -genkeypair -dname CN=tsexpiringsoon");
        keytool("-alias tslong -genkeypair -dname CN=tslong");

        // tsnoca's issuer will be removed from keystore later
        keytool("-alias ca -genkeypair -ext bc -dname CN=CA");
        gencert("tsnoca", "-ext eku:critical=ts");
        keytool("-delete -alias ca");
        keytool("-alias ca -genkeypair -ext bc -dname CN=CA -startdate -40d");

        gencert("signer");
        gencert("oldsigner", "-startdate -30d -validity 20");
        gencert("pre2019signer", "-startdate 2018/06/01 -validity 365");
        gencert("dsakey");
        gencert("weakkeysize");
        gencert("disabledkeysize");
        gencert("badku", "-ext ku:critical=keyAgreement");
        gencert("ts", "-ext eku:critical=ts -validity 500");

        gencert("expired", "-validity 10 -startdate -12d");
        gencert("expiring", "-validity 178");
        gencert("long", "-validity 182");
        gencert("tsexpired", "-ext eku:critical=ts -validity 10 -startdate -12d");
        gencert("tsexpiring", "-ext eku:critical=ts -validity 364");
        gencert("tsexpiringsoon", "-ext eku:critical=ts -validity 170"); // earlier than expiring
        gencert("tslong", "-ext eku:critical=ts -validity 367");


        for (int i = 0; i < 5; i++) {
            // Issue another cert for "ts" with a different EKU.
            // Length might be different because serial number is
            // random. Try several times until a cert with the same
            // length is generated so we can substitute ts.cert
            // embedded in the PKCS7 block with ts2.cert.
            // If cannot create one, related test will be ignored.
            keytool("-gencert -alias ca -infile ts.req -outfile ts2.cert " +
                    "-ext eku:critical=1.3.6.1.5.5.7.3.9");
            if (Files.size(Paths.get("ts.cert")) != Files.size(Paths.get("ts2.cert"))) {
                Files.delete(Paths.get("ts2.cert"));
                System.out.println("Warning: cannot create same length");
            } else {
                break;
            }
        }

        gencert("tsold", "-ext eku:critical=ts -startdate -40d -validity 500");
        gencert("tsbefore2019", "-ext eku:critical=ts -startdate 2018/01/01 -validity 3000");

        gencert("tsweak", "-ext eku:critical=ts");
        gencert("tsdisabled", "-ext eku:critical=ts");
        gencert("tsbad1");
        gencert("tsbad2", "-ext eku=ts");
        gencert("tsbad3", "-ext eku:critical=cs");
    }

    static void gencert(String alias, String... extra) throws Exception {
        keytool("-alias " + alias + " -certreq -file " + alias + ".req");
        String genCmd = "-gencert -alias ca -infile " +
                alias + ".req -outfile " + alias + ".cert";
        for (String s : extra) {
            genCmd += " " + s;
        }
        keytool(genCmd);
        keytool("-alias " + alias + " -importcert -file " + alias + ".cert");
    }

    static void keytool(String cmd) throws Exception {
        cmd = "-keystore ks -storepass changeit -keypass changeit " +
                "-keyalg rsa -validity 200 " + cmd;
        sun.security.tools.keytool.Main.main(cmd.split(" "));
    }

    static <K,V> Map<K,V> mapOf(K k1, V v1) {
        return Collections.singletonMap(k1, v1);
    }

    static <E> List<E> listOf(E... elements) {
        return Arrays.asList(elements);
    }
}
