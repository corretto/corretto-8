/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.util.HashMap;
import java.util.Map;
import java.security.Security;

import javax.security.auth.login.LoginException;

/*
 * @test
 * @bug 8025123 8208350
 * @summary Checks if an unbound server can handle connections
 *          only for allowed service principals
 * @run main/othervm/java.security.policy=unbound.ssl.policy -Dsun.net.spi.nameservice.provider.1=ns,mock UnboundSSL
 *                              unbound.ssl.jaas.conf server_star
 * @run main/othervm/java.security.policy=unbound.ssl.policy -Dsun.net.spi.nameservice.provider.1=ns,mock UnboundSSL
 *                              unbound.ssl.jaas.conf server_multiple_principals
 */
public class UnboundSSL {

    public static void main(String[] args) throws IOException,
            NoSuchAlgorithmException,LoginException, PrivilegedActionException,
            InterruptedException {
        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        UnboundSSL test = new UnboundSSL();
        test.start(args[0], args[1]);
    }

    private void start(String jaacConfigFile, String serverJaasConfig)
            throws IOException, NoSuchAlgorithmException,LoginException,
            PrivilegedActionException, InterruptedException {

        // define principals
        String service1host = "service1." + UnboundSSLUtils.HOST;
        String service2host = "service2." + UnboundSSLUtils.HOST;
        String service3host = "service3." + UnboundSSLUtils.HOST;
        String service1Principal = "host/" + service1host + "@"
                + UnboundSSLUtils.REALM;
        String service2Principal = "host/" + service2host + "@"
                + UnboundSSLUtils.REALM;
        String service3Principal = "host/" + service3host + "@"
                + UnboundSSLUtils.REALM;

        Map<String, String> principals = new HashMap<>();
        principals.put(UnboundSSLUtils.USER_PRINCIPAL,
                UnboundSSLUtils.USER_PASSWORD);
        principals.put(UnboundSSLUtils.KRBTGT_PRINCIPAL, null);
        principals.put(service1Principal, null);
        principals.put(service2Principal, null);
        principals.put(service3Principal, null);

        System.setProperty("java.security.krb5.conf",
               UnboundSSLUtils.KRB5_CONF_FILENAME);

        // start a local KDC instance
        KDC.startKDC(UnboundSSLUtils.HOST, UnboundSSLUtils.KRB5_CONF_FILENAME,
                UnboundSSLUtils.REALM, principals,
                UnboundSSLUtils.KTAB_FILENAME, KDC.KtabMode.APPEND);

        System.setProperty("java.security.auth.login.config",
                UnboundSSLUtils.TEST_SRC + UnboundSSLUtils.FS + jaacConfigFile);
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

        try (final SSLEchoServer server = SSLEchoServer.init(
                UnboundSSLUtils.TLS_KRB5_FILTER, UnboundSSLUtils.SNI_PATTERN)) {

            // start a server instance
            UnboundSSLUtils.startServerWithJaas(server, serverJaasConfig);

            // wait for the server is ready
            while (!server.isReady()) {
                Thread.sleep(UnboundSSLUtils.DELAY);
            }

            int port = server.getPort();

            // run clients

            // the server should have a permission to handle a request
            // with this service principal (there should be an appropriate
            // javax.security.auth.kerberos.ServicePermission in policy file)
            System.out.println("Connect: SNI hostname = " + service1host
                    + ", successful connection is expected");
            SSLClient.init(UnboundSSLUtils.HOST, port,
                    UnboundSSLUtils.TLS_KRB5_FILTER, service1host).connect();

            // the server should NOT have a permission to handle a request
            // with this service principal (there should be an appropriate
            // javax.security.auth.kerberos.ServicePermission in policy file)
            // handshake failures is expected
            System.out.println("Connect: SNI hostname = " + service2host
                    + ", connection failure is expected");
            try {
                SSLClient.init(UnboundSSLUtils.HOST, port,
                        UnboundSSLUtils.TLS_KRB5_FILTER, service2host)
                            .connect();
                throw new RuntimeException("Test failed: "
                        + "expected IOException not thrown");
            } catch (IOException e) {
                System.out.println("Expected exception: " + e);
            }

            // the server should have a permission to handle a request
            // with this service principal (there should be an appropriate
            // javax.security.auth.kerberos.ServicePermission in policy file)
            System.out.println("Connect: SNI hostname = " + service3host
                    + ", successful connection is expected");
            SSLClient.init(UnboundSSLUtils.HOST, port,
                    UnboundSSLUtils.TLS_KRB5_FILTER, service3host).connect();
        }

        System.out.println("Test passed");
    }
}
