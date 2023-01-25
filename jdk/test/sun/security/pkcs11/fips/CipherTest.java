/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;

import javax.net.ssl.*;

/**
 * Test that all ciphersuites work in all versions and all client
 * authentication types. The way this is setup the server is stateless and
 * all checking is done on the client side.
 *
 * The test is multithreaded to speed it up, especially on multiprocessor
 * machines. To simplify debugging, run with -DnumThreads=1.
 *
 * @author Andreas Sterbenz
 */
public class CipherTest {

    // use any available port for the server socket
    static int serverPort = 0;

    final int THREADS;

    // assume that if we do not read anything for 20 seconds, something
    // has gone wrong
    final static int TIMEOUT = 20 * 1000;

    static KeyStore /* trustStore, */ keyStore;
    static X509ExtendedKeyManager keyManager;
    static X509TrustManager trustManager;
    static SecureRandom secureRandom;

    private static PeerFactory peerFactory;

    static abstract class Server implements Runnable {

        final CipherTest cipherTest;

        Server(CipherTest cipherTest) throws Exception {
            this.cipherTest = cipherTest;
        }

        public abstract void run();

        void handleRequest(InputStream in, OutputStream out) throws IOException {
            boolean newline = false;
            StringBuilder sb = new StringBuilder();
            while (true) {
                int ch = in.read();
                if (ch < 0) {
                    throw new EOFException();
                }
                sb.append((char)ch);
                if (ch == '\r') {
                    // empty
                } else if (ch == '\n') {
                    if (newline) {
                        // 2nd newline in a row, end of request
                        break;
                    }
                    newline = true;
                } else {
                    newline = false;
                }
            }
            String request = sb.toString();
            if (request.startsWith("GET / HTTP/1.") == false) {
                throw new IOException("Invalid request: " + request);
            }
            out.write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
        }

    }

    public static class TestParameters {

        String cipherSuite;
        String protocol;
        String clientAuth;

        TestParameters(String cipherSuite, String protocol,
                String clientAuth) {
            this.cipherSuite = cipherSuite;
            this.protocol = protocol;
            this.clientAuth = clientAuth;
        }

        boolean isEnabled() {
            return TLSCipherStatus.isEnabled(cipherSuite, protocol);
        }

        public String toString() {
            String s = cipherSuite + " in " + protocol + " mode";
            if (clientAuth != null) {
                s += " with " + clientAuth + " client authentication";
            }
            return s;
        }

        static enum TLSCipherStatus {
            // cipher suites supported since TLS 1.2
            CS_01("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", 0x0303, 0xFFFF),
            CS_02("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",   0x0303, 0xFFFF),
            CS_03("TLS_RSA_WITH_AES_256_CBC_SHA256",         0x0303, 0xFFFF),
            CS_04("TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",  0x0303, 0xFFFF),
            CS_05("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",    0x0303, 0xFFFF),
            CS_06("TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",     0x0303, 0xFFFF),
            CS_07("TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",     0x0303, 0xFFFF),

            CS_08("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", 0x0303, 0xFFFF),
            CS_09("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",   0x0303, 0xFFFF),
            CS_10("TLS_RSA_WITH_AES_128_CBC_SHA256",         0x0303, 0xFFFF),
            CS_11("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",  0x0303, 0xFFFF),
            CS_12("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",    0x0303, 0xFFFF),
            CS_13("TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",     0x0303, 0xFFFF),
            CS_14("TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",     0x0303, 0xFFFF),

            CS_15("TLS_DH_anon_WITH_AES_256_CBC_SHA256",     0x0303, 0xFFFF),
            CS_16("TLS_DH_anon_WITH_AES_128_CBC_SHA256",     0x0303, 0xFFFF),
            CS_17("TLS_RSA_WITH_NULL_SHA256",                0x0303, 0xFFFF),

            CS_20("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", 0x0303, 0xFFFF),
            CS_21("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", 0x0303, 0xFFFF),
            CS_22("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",   0x0303, 0xFFFF),
            CS_23("TLS_RSA_WITH_AES_256_GCM_SHA384",         0x0303, 0xFFFF),
            CS_24("TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",  0x0303, 0xFFFF),
            CS_25("TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",    0x0303, 0xFFFF),
            CS_26("TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",     0x0303, 0xFFFF),
            CS_27("TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",     0x0303, 0xFFFF),

            CS_28("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",   0x0303, 0xFFFF),
            CS_29("TLS_RSA_WITH_AES_128_GCM_SHA256",         0x0303, 0xFFFF),
            CS_30("TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",  0x0303, 0xFFFF),
            CS_31("TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",    0x0303, 0xFFFF),
            CS_32("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",     0x0303, 0xFFFF),
            CS_33("TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",     0x0303, 0xFFFF),

            CS_34("TLS_DH_anon_WITH_AES_256_GCM_SHA384",     0x0303, 0xFFFF),
            CS_35("TLS_DH_anon_WITH_AES_128_GCM_SHA256",     0x0303, 0xFFFF),

            // cipher suites obsoleted since TLS 1.2
            CS_50("SSL_RSA_WITH_DES_CBC_SHA",                0x0000, 0x0303),
            CS_51("SSL_DHE_RSA_WITH_DES_CBC_SHA",            0x0000, 0x0303),
            CS_52("SSL_DHE_DSS_WITH_DES_CBC_SHA",            0x0000, 0x0303),
            CS_53("SSL_DH_anon_WITH_DES_CBC_SHA",            0x0000, 0x0303),
            CS_54("TLS_KRB5_WITH_DES_CBC_SHA",               0x0000, 0x0303),
            CS_55("TLS_KRB5_WITH_DES_CBC_MD5",               0x0000, 0x0303),

            // cipher suites obsoleted since TLS 1.1
            CS_60("SSL_RSA_EXPORT_WITH_RC4_40_MD5",          0x0000, 0x0302),
            CS_61("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",      0x0000, 0x0302),
            CS_62("SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",       0x0000, 0x0302),
            CS_63("SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",   0x0000, 0x0302),
            CS_64("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",   0x0000, 0x0302),
            CS_65("SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",   0x0000, 0x0302),
            CS_66("TLS_KRB5_EXPORT_WITH_RC4_40_SHA",         0x0000, 0x0302),
            CS_67("TLS_KRB5_EXPORT_WITH_RC4_40_MD5",         0x0000, 0x0302),
            CS_68("TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",     0x0000, 0x0302),
            CS_69("TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5",     0x0000, 0x0302),

            // ignore TLS_EMPTY_RENEGOTIATION_INFO_SCSV always
            CS_99("TLS_EMPTY_RENEGOTIATION_INFO_SCSV",       0xFFFF, 0x0000);

            // the cipher suite name
            final String cipherSuite;

            // supported since protocol version
            final int supportedSince;

            // obsoleted since protocol version
            final int obsoletedSince;

            TLSCipherStatus(String cipherSuite,
                    int supportedSince, int obsoletedSince) {
                this.cipherSuite = cipherSuite;
                this.supportedSince = supportedSince;
                this.obsoletedSince = obsoletedSince;
            }

            static boolean isEnabled(String cipherSuite, String protocol) {
                int versionNumber = toVersionNumber(protocol);

                if (versionNumber < 0) {
                    return true;  // unlikely to happen
                }

                for (TLSCipherStatus status : TLSCipherStatus.values()) {
                    if (cipherSuite.equals(status.cipherSuite)) {
                        if ((versionNumber < status.supportedSince) ||
                            (versionNumber >= status.obsoletedSince)) {
                            return false;
                        }

                        return true;
                    }
                }

                return true;
            }

            private static int toVersionNumber(String protocol) {
                int versionNumber = -1;

                switch (protocol) {
                    case "SSLv2Hello":
                        versionNumber = 0x0002;
                        break;
                    case "SSLv3":
                        versionNumber = 0x0300;
                        break;
                    case "TLSv1":
                        versionNumber = 0x0301;
                        break;
                    case "TLSv1.1":
                        versionNumber = 0x0302;
                        break;
                    case "TLSv1.2":
                        versionNumber = 0x0303;
                        break;
                    default:
                        // unlikely to happen
                }

                return versionNumber;
            }
        }
    }

    private List<TestParameters> tests;
    private Iterator<TestParameters> testIterator;
    private SSLSocketFactory factory;
    private boolean failed;

    private CipherTest(PeerFactory peerFactory) throws IOException {
        THREADS = Integer.parseInt(System.getProperty("numThreads", "4"));
        factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket)factory.createSocket();
        String[] cipherSuites = socket.getSupportedCipherSuites();
        String[] protocols = socket.getSupportedProtocols();
//      String[] clientAuths = {null, "RSA", "DSA"};
        String[] clientAuths = {null};
        tests = new ArrayList<TestParameters>(
            cipherSuites.length * protocols.length * clientAuths.length);
        for (int i = 0; i < cipherSuites.length; i++) {
            String cipherSuite = cipherSuites[i];

            for (int j = 0; j < protocols.length; j++) {
                String protocol = protocols[j];

                if (!peerFactory.isSupported(cipherSuite, protocol)) {
                    continue;
                }

                for (int k = 0; k < clientAuths.length; k++) {
                    String clientAuth = clientAuths[k];
                    if ((clientAuth != null) &&
                            (cipherSuite.indexOf("DH_anon") != -1)) {
                        // no client with anonymous ciphersuites
                        continue;
                    }
                    tests.add(new TestParameters(cipherSuite, protocol,
                        clientAuth));
                }
            }
        }
        testIterator = tests.iterator();
    }

    synchronized void setFailed() {
        failed = true;
    }

    public void run() throws Exception {
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            try {
                threads[i] = new Thread(peerFactory.newClient(this),
                    "Client " + i);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            threads[i].start();
        }
        try {
            for (int i = 0; i < THREADS; i++) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            setFailed();
            e.printStackTrace();
        }
        if (failed) {
            throw new Exception("*** Test '" + peerFactory.getName() +
                "' failed ***");
        } else {
            System.out.println("Test '" + peerFactory.getName() +
                "' completed successfully");
        }
    }

    synchronized TestParameters getTest() {
        if (failed) {
            return null;
        }
        if (testIterator.hasNext()) {
            return (TestParameters)testIterator.next();
        }
        return null;
    }

    SSLSocketFactory getFactory() {
        return factory;
    }

    static abstract class Client implements Runnable {

        final CipherTest cipherTest;

        Client(CipherTest cipherTest) throws Exception {
            this.cipherTest = cipherTest;
        }

        public final void run() {
            while (true) {
                TestParameters params = cipherTest.getTest();
                if (params == null) {
                    // no more tests
                    break;
                }
                if (params.isEnabled() == false) {
                    System.out.println("Skipping disabled test " + params);
                    continue;
                }
                try {
                    runTest(params);
                    System.out.println("Passed " + params);
                } catch (Exception e) {
                    cipherTest.setFailed();
                    System.out.println("** Failed " + params + "**");
                    e.printStackTrace();
                }
            }
        }

        abstract void runTest(TestParameters params) throws Exception;

        void sendRequest(InputStream in, OutputStream out) throws IOException {
            out.write("GET / HTTP/1.0\r\n\r\n".getBytes());
            out.flush();
            StringBuilder sb = new StringBuilder();
            while (true) {
                int ch = in.read();
                if (ch < 0) {
                    break;
                }
                sb.append((char)ch);
            }
            String response = sb.toString();
            if (response.startsWith("HTTP/1.0 200 ") == false) {
                throw new IOException("Invalid response: " + response);
            }
        }

    }

    // for some reason, ${test.src} has a different value when the
    // test is called from the script and when it is called directly...
    static String pathToStores = ".";
    static String pathToStoresSH = ".";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static char[] passwd = "passphrase".toCharArray();

    static File PATH;

    private static KeyStore readKeyStore(String name) throws Exception {
        File file = new File(PATH, name);
        InputStream in = new FileInputStream(file);
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(in, passwd);
        in.close();
        return ks;
    }

    public static void main(PeerFactory peerFactory, KeyStore keyStore,
            String[] args) throws Exception {
        long time = System.currentTimeMillis();
        String relPath;
        if ((args != null) && (args.length > 0) && args[0].equals("sh")) {
            relPath = pathToStoresSH;
        } else {
            relPath = pathToStores;
        }
        PATH = new File(System.getProperty("test.src", "."), relPath);
        CipherTest.peerFactory = peerFactory;
        System.out.print(
            "Initializing test '" + peerFactory.getName() + "'...");
//      secureRandom = new SecureRandom();
//      secureRandom.nextInt();
//      trustStore = readKeyStore(trustStoreFile);
        CipherTest.keyStore = keyStore;
//      keyStore = readKeyStore(keyStoreFile);
        KeyManagerFactory keyFactory =
            KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyFactory.init(keyStore, "test12".toCharArray());
        keyManager = (X509ExtendedKeyManager)keyFactory.getKeyManagers()[0];

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        trustManager = (X509TrustManager)tmf.getTrustManagers()[0];

//      trustManager = new AlwaysTrustManager();
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[] {keyManager},
                new TrustManager[] {trustManager}, null);
        SSLContext.setDefault(context);

        CipherTest cipherTest = new CipherTest(peerFactory);
        Thread serverThread = new Thread(peerFactory.newServer(cipherTest),
            "Server");
        serverThread.setDaemon(true);
        serverThread.start();
        System.out.println("Done");
        cipherTest.run();
        time = System.currentTimeMillis() - time;
        System.out.println("Done. (" + time + " ms)");
    }

    static abstract class PeerFactory {

        abstract String getName();

        abstract Client newClient(CipherTest cipherTest) throws Exception;

        abstract Server newServer(CipherTest cipherTest) throws Exception;

        boolean isSupported(String cipherSuite, String protocol) {
            // skip kerberos cipher suites
            if (cipherSuite.startsWith("TLS_KRB5")) {
                System.out.println("Skipping unsupported test for " +
                                    cipherSuite + " of " + protocol);
                return false;
            }

            // No ECDH-capable certificate in key store. May restructure
            // this in the future.
            if (cipherSuite.contains("ECDHE_ECDSA") ||
                    cipherSuite.contains("ECDH_ECDSA") ||
                    cipherSuite.contains("ECDH_RSA")) {
                System.out.println("Skipping unsupported test for " +
                                    cipherSuite + " of " + protocol);
                return false;
            }

            // skip SSLv2Hello protocol
            //
            // skip TLSv1.2 protocol, we have not implement "SunTls12Prf" and
            // SunTls12RsaPremasterSecret in SunPKCS11 provider
            if (protocol.equals("SSLv2Hello") || protocol.equals("TLSv1.2")) {
                System.out.println("Skipping unsupported test for " +
                                    cipherSuite + " of " + protocol);
                return false;
            }

            // ignore exportable cipher suite for TLSv1.1
            if (protocol.equals("TLSv1.1")) {
                if (cipherSuite.indexOf("_EXPORT_WITH") != -1) {
                    System.out.println("Skipping obsoleted test for " +
                                        cipherSuite + " of " + protocol);
                    return false;
                }
            }

            return true;
        }
    }

}

// we currently don't do any chain verification. we assume that works ok
// and we can speed up the test. we could also just add a plain certificate
// chain comparision with our trusted certificates.
class AlwaysTrustManager implements X509TrustManager {

    public AlwaysTrustManager() {

    }

    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        // empty
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        // empty
    }

    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}

class MyX509KeyManager extends X509ExtendedKeyManager {

    private final X509ExtendedKeyManager keyManager;
    private String authType;

    MyX509KeyManager(X509ExtendedKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    void setAuthType(String authType) {
        this.authType = authType;
    }

    public String[] getClientAliases(String keyType, Principal[] issuers) {
        if (authType == null) {
            return null;
        }
        return keyManager.getClientAliases(authType, issuers);
    }

    public String chooseClientAlias(String[] keyType, Principal[] issuers,
            Socket socket) {
        if (authType == null) {
            return null;
        }
        return keyManager.chooseClientAlias(new String[] {authType},
            issuers, socket);
    }

    public String chooseEngineClientAlias(String[] keyType,
            Principal[] issuers, SSLEngine engine) {
        if (authType == null) {
            return null;
        }
        return keyManager.chooseEngineClientAlias(new String[] {authType},
            issuers, engine);
    }

    public String[] getServerAliases(String keyType, Principal[] issuers) {
        throw new UnsupportedOperationException("Servers not supported");
    }

    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        throw new UnsupportedOperationException("Servers not supported");
    }

    public String chooseEngineServerAlias(String keyType, Principal[] issuers,
            SSLEngine engine) {
        throw new UnsupportedOperationException("Servers not supported");
    }

    public X509Certificate[] getCertificateChain(String alias) {
        return keyManager.getCertificateChain(alias);
    }

    public PrivateKey getPrivateKey(String alias) {
        return keyManager.getPrivateKey(alias);
    }

}

class DaemonThreadFactory implements ThreadFactory {

    final static ThreadFactory INSTANCE = new DaemonThreadFactory();

    private final static ThreadFactory DEFAULT = Executors.defaultThreadFactory();

    public Thread newThread(Runnable r) {
        Thread t = DEFAULT.newThread(r);
        t.setDaemon(true);
        return t;
    }

}
