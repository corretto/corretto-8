/*
 * Copyright (c) 1996, 2020, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.util.*;

import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;

import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import javax.security.auth.x500.X500Principal;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import javax.net.ssl.*;

import javax.security.auth.Subject;

import sun.security.ssl.HandshakeMessage.*;
import static sun.security.ssl.CipherSuite.KeyExchange.*;

/**
 * ClientHandshaker does the protocol handshaking from the point
 * of view of a client.  It is driven asychronously by handshake messages
 * as delivered by the parent Handshaker class, and also uses
 * common functionality (e.g. key generation) that is provided there.
 *
 * @author David Brownell
 */
final class ClientHandshaker extends Handshaker {

    // constants for subject alt names of type DNS and IP
    private final static int ALTNAME_DNS = 2;
    private final static int ALTNAME_IP  = 7;

    // the server's public key from its certificate.
    private PublicKey serverKey;

    // the server's ephemeral public key from the server key exchange message
    // for ECDHE/ECDH_anon and RSA_EXPORT.
    private PublicKey ephemeralServerKey;

    // server's ephemeral public value for DHE/DH_anon key exchanges
    private BigInteger          serverDH;

    private DHCrypt             dh;

    private ECDHCrypt ecdh;

    private CertificateRequest  certRequest;

    private boolean serverKeyExchangeReceived;

    /*
     * The RSA PreMasterSecret needs to know the version of
     * ClientHello that was used on this handshake.  This represents
     * the "max version" this client is supporting.  In the
     * case of an initial handshake, it's the max version enabled,
     * but in the case of a resumption attempt, it's the version
     * of the session we're trying to resume.
     */
    private ProtocolVersion maxProtocolVersion;

    // To switch off the SNI extension.
    private final static boolean enableSNIExtension =
            Debug.getBooleanProperty("jsse.enableSNIExtension", true);

    /*
     * Allow unsafe server certificate change?
     *
     * Server certificate change during SSL/TLS renegotiation may be considered
     * unsafe, as described in the Triple Handshake attacks:
     *
     *     https://secure-resumption.com/tlsauth.pdf
     *
     * Endpoint identification (See
     * SSLParameters.getEndpointIdentificationAlgorithm()) is a pretty nice
     * guarantee that the server certificate change in renegotiation is legal.
     * However, endpoing identification is only enabled for HTTPS and LDAP
     * over SSL/TLS by default.  It is not enough to protect SSL/TLS
     * connections other than HTTPS and LDAP.
     *
     * The renegotiation indication extension (See RFC 5764) is a pretty
     * strong guarantee that the endpoints on both client and server sides
     * are identical on the same connection.  However, the Triple Handshake
     * attacks can bypass this guarantee if there is a session-resumption
     * handshake between the initial full handshake and the renegotiation
     * full handshake.
     *
     * Server certificate change may be unsafe and should be restricted if
     * endpoint identification is not enabled and the previous handshake is
     * a session-resumption abbreviated initial handshake, unless the
     * identities represented by both certificates can be regraded as the
     * same (See isIdentityEquivalent()).
     *
     * Considering the compatibility impact and the actual requirements to
     * support server certificate change in practice, the system property,
     * jdk.tls.allowUnsafeServerCertChange, is used to define whether unsafe
     * server certificate change in renegotiation is allowed or not.  The
     * default value of the system property is "false".  To mitigate the
     * compactibility impact, applications may want to set the system
     * property to "true" at their own risk.
     *
     * If the value of the system property is "false", server certificate
     * change in renegotiation after a session-resumption abbreviated initial
     * handshake is restricted (See isIdentityEquivalent()).
     *
     * If the system property is set to "true" explicitly, the restriction on
     * server certificate change in renegotiation is disabled.
     */
    private final static boolean allowUnsafeServerCertChange =
        Debug.getBooleanProperty("jdk.tls.allowUnsafeServerCertChange", false);

    // Whether an ALPN extension was sent in the ClientHello
    private boolean alpnActive = false;

    private List<SNIServerName> requestedServerNames =
            Collections.<SNIServerName>emptyList();

    private boolean serverNamesAccepted = false;

    /*
     * the reserved server certificate chain in previous handshaking
     *
     * The server certificate chain is only reserved if the previous
     * handshake is a session-resumption abbreviated initial handshake.
     */
    private X509Certificate[] reservedServerCerts = null;

    /*
     * Constructors
     */
    ClientHandshaker(SSLSocketImpl socket, SSLContextImpl context,
            ProtocolList enabledProtocols,
            ProtocolVersion activeProtocolVersion,
            boolean isInitialHandshake, boolean secureRenegotiation,
            byte[] clientVerifyData, byte[] serverVerifyData) {

        super(socket, context, enabledProtocols, true, true,
            activeProtocolVersion, isInitialHandshake, secureRenegotiation,
            clientVerifyData, serverVerifyData);
    }

    ClientHandshaker(SSLEngineImpl engine, SSLContextImpl context,
            ProtocolList enabledProtocols,
            ProtocolVersion activeProtocolVersion,
            boolean isInitialHandshake, boolean secureRenegotiation,
            byte[] clientVerifyData, byte[] serverVerifyData) {

        super(engine, context, enabledProtocols, true, true,
            activeProtocolVersion, isInitialHandshake, secureRenegotiation,
            clientVerifyData, serverVerifyData);
    }

    /*
     * This routine handles all the client side handshake messages, one at
     * a time.  Given the message type (and in some cases the pending cipher
     * spec) it parses the type-specific message.  Then it calls a function
     * that handles that specific message.
     *
     * It updates the state machine (need to verify it) as each message
     * is processed, and writes responses as needed using the connection
     * in the constructor.
     */
    @Override
    void processMessage(byte type, int messageLen) throws IOException {

        // check the handshake state
        List<Byte> ignoredOptStates = handshakeState.check(type);

        switch (type) {
        case HandshakeMessage.ht_hello_request:
            HelloRequest helloRequest = new HelloRequest(input);
            handshakeState.update(helloRequest, resumingSession);
            this.serverHelloRequest(helloRequest);
            break;

        case HandshakeMessage.ht_server_hello:
            ServerHello serverHello = new ServerHello(input, messageLen);
            this.serverHello(serverHello);

            // This handshake state update needs the resumingSession value
            // set by serverHello().
            handshakeState.update(serverHello, resumingSession);
            break;

        case HandshakeMessage.ht_certificate:
            if (keyExchange == K_DH_ANON || keyExchange == K_ECDH_ANON
                    || keyExchange == K_KRB5 || keyExchange == K_KRB5_EXPORT) {
                fatalSE(Alerts.alert_unexpected_message,
                    "unexpected server cert chain");
                // NOTREACHED
            }
            CertificateMsg certificateMsg = new CertificateMsg(input);
            handshakeState.update(certificateMsg, resumingSession);
            this.serverCertificate(certificateMsg);
            serverKey =
                session.getPeerCertificates()[0].getPublicKey();
            break;

        case HandshakeMessage.ht_server_key_exchange:
            serverKeyExchangeReceived = true;
            switch (keyExchange) {
            case K_RSA_EXPORT:
                /**
                 * The server key exchange message is sent by the server only
                 * when the server certificate message does not contain the
                 * proper amount of data to allow the client to exchange a
                 * premaster secret, such as when RSA_EXPORT is used and the
                 * public key in the server certificate is longer than 512 bits.
                 */
                if (serverKey == null) {
                    throw new SSLProtocolException
                        ("Server did not send certificate message");
                }

                if (!(serverKey instanceof RSAPublicKey)) {
                    throw new SSLProtocolException("Protocol violation:" +
                        " the certificate type must be appropriate for the" +
                        " selected cipher suite's key exchange algorithm");
                }

                if (JsseJce.getRSAKeyLength(serverKey) <= 512) {
                    throw new SSLProtocolException("Protocol violation:" +
                        " server sent a server key exchange message for" +
                        " key exchange " + keyExchange +
                        " when the public key in the server certificate" +
                        " is less than or equal to 512 bits in length");
                }

                try {
                    RSA_ServerKeyExchange rsaSrvKeyExchange =
                                    new RSA_ServerKeyExchange(input);
                    handshakeState.update(rsaSrvKeyExchange, resumingSession);
                    this.serverKeyExchange(rsaSrvKeyExchange);
                } catch (GeneralSecurityException e) {
                    throwSSLException("Server key", e);
                }
                break;
            case K_DH_ANON:
                try {
                    DH_ServerKeyExchange dhSrvKeyExchange =
                            new DH_ServerKeyExchange(input, protocolVersion);
                    handshakeState.update(dhSrvKeyExchange, resumingSession);
                    this.serverKeyExchange(dhSrvKeyExchange);
                } catch (GeneralSecurityException e) {
                    throwSSLException("Server key", e);
                }
                break;
            case K_DHE_DSS:
            case K_DHE_RSA:
                try {
                    DH_ServerKeyExchange dhSrvKeyExchange =
                        new DH_ServerKeyExchange(
                        input, serverKey,
                        clnt_random.random_bytes, svr_random.random_bytes,
                        messageLen,
                            getLocalSupportedSignAlgs(), protocolVersion);
                    handshakeState.update(dhSrvKeyExchange, resumingSession);
                    this.serverKeyExchange(dhSrvKeyExchange);
                } catch (GeneralSecurityException e) {
                    throwSSLException("Server key", e);
                }
                break;
            case K_ECDHE_ECDSA:
            case K_ECDHE_RSA:
            case K_ECDH_ANON:
                try {
                    ECDH_ServerKeyExchange ecdhSrvKeyExchange =
                        new ECDH_ServerKeyExchange
                        (input, serverKey, clnt_random.random_bytes,
                        svr_random.random_bytes,
                            getLocalSupportedSignAlgs(), protocolVersion);
                    handshakeState.update(ecdhSrvKeyExchange, resumingSession);
                    this.serverKeyExchange(ecdhSrvKeyExchange);
                } catch (GeneralSecurityException e) {
                    throwSSLException("Server key", e);
                }
                break;
            case K_RSA:
            case K_DH_RSA:
            case K_DH_DSS:
            case K_ECDH_ECDSA:
            case K_ECDH_RSA:
                throw new SSLProtocolException(
                    "Protocol violation: server sent a server key exchange"
                    + " message for key exchange " + keyExchange);
            case K_KRB5:
            case K_KRB5_EXPORT:
                throw new SSLProtocolException(
                    "unexpected receipt of server key exchange algorithm");
            default:
                throw new SSLProtocolException(
                    "unsupported key exchange algorithm = "
                    + keyExchange);
            }
            break;

        case HandshakeMessage.ht_certificate_request:
            // save for later, it's handled by serverHelloDone
            if ((keyExchange == K_DH_ANON) || (keyExchange == K_ECDH_ANON)) {
                throw new SSLHandshakeException(
                    "Client authentication requested for "+
                    "anonymous cipher suite.");
            } else if (keyExchange == K_KRB5 || keyExchange == K_KRB5_EXPORT) {
                throw new SSLHandshakeException(
                    "Client certificate requested for "+
                    "kerberos cipher suite.");
            }
            certRequest = new CertificateRequest(input, protocolVersion);
            if (debug != null && Debug.isOn("handshake")) {
                certRequest.print(System.out);
            }
            handshakeState.update(certRequest, resumingSession);

            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                Collection<SignatureAndHashAlgorithm> peerSignAlgs =
                                        certRequest.getSignAlgorithms();
                if (peerSignAlgs == null || peerSignAlgs.isEmpty()) {
                    throw new SSLHandshakeException(
                        "No peer supported signature algorithms");
                }

                Collection<SignatureAndHashAlgorithm> supportedPeerSignAlgs =
                    SignatureAndHashAlgorithm.getSupportedAlgorithms(
                            algorithmConstraints, peerSignAlgs);
                if (supportedPeerSignAlgs.isEmpty()) {
                    throw new SSLHandshakeException(
                        "No supported signature and hash algorithm in common");
                }

                setPeerSupportedSignAlgs(supportedPeerSignAlgs);
                session.setPeerSupportedSignatureAlgorithms(
                                                supportedPeerSignAlgs);
            }

            break;

        case HandshakeMessage.ht_server_hello_done:
            ServerHelloDone serverHelloDone = new ServerHelloDone(input);
            handshakeState.update(serverHelloDone, resumingSession);
            this.serverHelloDone(serverHelloDone);
            break;

        case HandshakeMessage.ht_finished:
            Finished serverFinished =
                    new Finished(protocolVersion, input, cipherSuite);
            handshakeState.update(serverFinished, resumingSession);
            this.serverFinished(serverFinished);

            break;

        default:
            throw new SSLProtocolException(
                "Illegal client handshake msg, " + type);
        }
    }

    /*
     * Used by the server to kickstart negotiations -- this requests a
     * "client hello" to renegotiate current cipher specs (e.g. maybe lots
     * of data has been encrypted with the same keys, or the server needs
     * the client to present a certificate).
     */
    private void serverHelloRequest(HelloRequest mesg) throws IOException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        //
        // Could be (e.g. at connection setup) that we already
        // sent the "client hello" but the server's not seen it.
        //
        if (!clientHelloDelivered) {
            if (!secureRenegotiation && !allowUnsafeRenegotiation) {
                // renegotiation is not allowed.
                if (activeProtocolVersion.v >= ProtocolVersion.TLS10.v) {
                    // response with a no_renegotiation warning,
                    warningSE(Alerts.alert_no_renegotiation);

                    // invalidate the handshake so that the caller can
                    // dispose this object.
                    invalidated = true;

                    // If there is still unread block in the handshake
                    // input stream, it would be truncated with the disposal
                    // and the next handshake message will become incomplete.
                    //
                    // However, according to SSL/TLS specifications, no more
                    // handshake message should immediately follow ClientHello
                    // or HelloRequest. So just let it be.
                } else {
                    // For SSLv3, send the handshake_failure fatal error.
                    // Note that SSLv3 does not define a no_renegotiation
                    // alert like TLSv1. However we cannot ignore the message
                    // simply, otherwise the other side was waiting for a
                    // response that would never come.
                    fatalSE(Alerts.alert_handshake_failure,
                        "Renegotiation is not allowed");
                }
            } else {
                if (!secureRenegotiation) {
                    if (debug != null && Debug.isOn("handshake")) {
                        System.out.println(
                            "Warning: continue with insecure renegotiation");
                    }
                }
                kickstart();
            }
        }
    }


    /*
     * Server chooses session parameters given options created by the
     * client -- basically, cipher options, session id, and someday a
     * set of compression options.
     *
     * There are two branches of the state machine, decided by the
     * details of this message.  One is the "fast" handshake, where we
     * can resume the pre-existing session we asked resume.  The other
     * is a more expensive "full" handshake, with key exchange and
     * probably authentication getting done.
     */
    private void serverHello(ServerHello mesg) throws IOException {
        serverKeyExchangeReceived = false;
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        // check if the server selected protocol version is OK for us
        ProtocolVersion mesgVersion = mesg.protocolVersion;
        if (!isNegotiable(mesgVersion)) {
            throw new SSLHandshakeException(
                "Server chose " + mesgVersion +
                ", but that protocol version is not enabled or not supported " +
                "by the client.");
        }

        handshakeHash.protocolDetermined(mesgVersion);

        // Set protocolVersion and propagate to SSLSocket and the
        // Handshake streams
        setVersion(mesgVersion);

        // check the "renegotiation_info" extension
        RenegotiationInfoExtension serverHelloRI = (RenegotiationInfoExtension)
                    mesg.extensions.get(ExtensionType.EXT_RENEGOTIATION_INFO);
        if (serverHelloRI != null) {
            if (isInitialHandshake) {
                // verify the length of the "renegotiated_connection" field
                if (!serverHelloRI.isEmpty()) {
                    // abort the handshake with a fatal handshake_failure alert
                    fatalSE(Alerts.alert_handshake_failure,
                        "The renegotiation_info field is not empty");
                }

                secureRenegotiation = true;
            } else {
                // For a legacy renegotiation, the client MUST verify that
                // it does not contain the "renegotiation_info" extension.
                if (!secureRenegotiation) {
                    fatalSE(Alerts.alert_handshake_failure,
                        "Unexpected renegotiation indication extension");
                }

                // verify the client_verify_data and server_verify_data values
                byte[] verifyData =
                    new byte[clientVerifyData.length + serverVerifyData.length];
                System.arraycopy(clientVerifyData, 0, verifyData,
                        0, clientVerifyData.length);
                System.arraycopy(serverVerifyData, 0, verifyData,
                        clientVerifyData.length, serverVerifyData.length);
                if (!MessageDigest.isEqual(verifyData,
                                serverHelloRI.getRenegotiatedConnection())) {
                    fatalSE(Alerts.alert_handshake_failure,
                        "Incorrect verify data in ServerHello " +
                        "renegotiation_info message");
                }
            }
        } else {
            // no renegotiation indication extension
            if (isInitialHandshake) {
                if (!allowLegacyHelloMessages) {
                    // abort the handshake with a fatal handshake_failure alert
                    fatalSE(Alerts.alert_handshake_failure,
                        "Failed to negotiate the use of secure renegotiation");
                }

                secureRenegotiation = false;
                if (debug != null && Debug.isOn("handshake")) {
                    System.out.println("Warning: No renegotiation " +
                                    "indication extension in ServerHello");
                }
            } else {
                // For a secure renegotiation, the client must abort the
                // handshake if no "renegotiation_info" extension is present.
                if (secureRenegotiation) {
                    fatalSE(Alerts.alert_handshake_failure,
                        "No renegotiation indication extension");
                }

                // we have already allowed unsafe renegotation before request
                // the renegotiation.
            }
        }

        //
        // Save server nonce, we always use it to compute connection
        // keys and it's also used to create the master secret if we're
        // creating a new session (i.e. in the full handshake).
        //
        svr_random = mesg.svr_random;

        if (isNegotiable(mesg.cipherSuite) == false) {
            fatalSE(Alerts.alert_illegal_parameter,
                "Server selected improper ciphersuite " + mesg.cipherSuite);
        }

        setCipherSuite(mesg.cipherSuite);
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            handshakeHash.setFinishedAlg(cipherSuite.prfAlg.getPRFHashAlg());
        }

        if (mesg.compression_method != 0) {
            fatalSE(Alerts.alert_illegal_parameter,
                "compression type not supported, "
                + mesg.compression_method);
            // NOTREACHED
        }

        // so far so good, let's look at the session
        if (session != null) {
            // we tried to resume, let's see what the server decided
            if (session.getSessionId().equals(mesg.sessionId)) {
                // server resumed the session, let's make sure everything
                // checks out

                // Verify that the session ciphers are unchanged.
                CipherSuite sessionSuite = session.getSuite();
                if (cipherSuite != sessionSuite) {
                    throw new SSLProtocolException
                        ("Server returned wrong cipher suite for session");
                }

                // verify protocol version match
                ProtocolVersion sessionVersion = session.getProtocolVersion();
                if (protocolVersion != sessionVersion) {
                    throw new SSLProtocolException
                        ("Server resumed session with wrong protocol version");
                }

                // validate subject identity
                if (sessionSuite.keyExchange == K_KRB5 ||
                    sessionSuite.keyExchange == K_KRB5_EXPORT) {
                    Principal localPrincipal = session.getLocalPrincipal();

                    Subject subject = null;
                    try {
                        subject = AccessController.doPrivileged(
                            new PrivilegedExceptionAction<Subject>() {
                            @Override
                            public Subject run() throws Exception {
                                return Krb5Helper.getClientSubject(getAccSE());
                            }});
                    } catch (PrivilegedActionException e) {
                        subject = null;
                        if (debug != null && Debug.isOn("session")) {
                            System.out.println("Attempt to obtain" +
                                        " subject failed!");
                        }
                    }

                    if (subject != null) {
                        // Eliminate dependency on KerberosPrincipal
                        Set<Principal> principals =
                            subject.getPrincipals(Principal.class);
                        if (!principals.contains(localPrincipal)) {
                            throw new SSLProtocolException("Server resumed" +
                                " session with wrong subject identity");
                        } else {
                            if (debug != null && Debug.isOn("session"))
                                System.out.println("Subject identity is same");
                        }
                    } else {
                        if (debug != null && Debug.isOn("session"))
                            System.out.println("Kerberos credentials are not" +
                                " present in the current Subject; check if " +
                                " javax.security.auth.useSubjectAsCreds" +
                                " system property has been set to false");
                        throw new SSLProtocolException
                            ("Server resumed session with no subject");
                    }
                }

                // looks fine; resume it, and update the state machine.
                resumingSession = true;
                calculateConnectionKeys(session.getMasterSecret());
                if (debug != null && Debug.isOn("session")) {
                    System.out.println("%% Server resumed " + session);
                }
            } else {
                // we wanted to resume, but the server refused
                //
                // Invalidate the session for initial handshake in case
                // of reusing next time.
                if (isInitialHandshake) {
                    session.invalidate();
                }
                session = null;
                if (!enableNewSession) {
                    throw new SSLException("New session creation is disabled");
                }
            }
        }

        // check the "extended_master_secret" extension
        ExtendedMasterSecretExtension extendedMasterSecretExt =
                (ExtendedMasterSecretExtension)mesg.extensions.get(
                        ExtensionType.EXT_EXTENDED_MASTER_SECRET);
        if (extendedMasterSecretExt != null) {
            // Is it the expected server extension?
            if (!useExtendedMasterSecret ||
                    !(mesgVersion.v >= ProtocolVersion.TLS10.v) || !requestedToUseEMS) {
                fatalSE(Alerts.alert_unsupported_extension,
                        "Server sent the extended_master_secret " +
                        "extension improperly");
            }

            // For abbreviated handshake, if the original session did not use
            // the "extended_master_secret" extension but the new ServerHello
            // contains the extension, the client MUST abort the handshake.
            if (resumingSession && (session != null) &&
                    !session.getUseExtendedMasterSecret()) {
                fatalSE(Alerts.alert_unsupported_extension,
                        "Server sent an unexpected extended_master_secret " +
                        "extension on session resumption");
            }
        } else {
            if (useExtendedMasterSecret && !allowLegacyMasterSecret) {
                // For full handshake, if a client receives a ServerHello
                // without the extension, it SHOULD abort the handshake if
                // it does not wish to interoperate with legacy servers.
                fatalSE(Alerts.alert_handshake_failure,
                    "Extended Master Secret extension is required");
            }

            if (resumingSession && (session != null)) {
                if (session.getUseExtendedMasterSecret()) {
                    // For abbreviated handshake, if the original session used
                    // the "extended_master_secret" extension but the new
                    // ServerHello does not contain the extension, the client
                    // MUST abort the handshake.
                    fatalSE(Alerts.alert_handshake_failure,
                            "Missing Extended Master Secret extension " +
                            "on session resumption");
                } else if (useExtendedMasterSecret && !allowLegacyResumption) {
                    // Unlikely, abbreviated handshake should be discarded.
                    fatalSE(Alerts.alert_handshake_failure,
                        "Extended Master Secret extension is required");
                }
            }
        }

        // check the ALPN extension
        ALPNExtension serverHelloALPN =
            (ALPNExtension) mesg.extensions.get(ExtensionType.EXT_ALPN);

        if (serverHelloALPN != null) {
            // Check whether an ALPN extension was sent in ClientHello message
            if (!alpnActive) {
                fatalSE(Alerts.alert_unsupported_extension,
                    "Server sent " + ExtensionType.EXT_ALPN +
                    " extension when not requested by client");
            }

            List<String> protocols = serverHelloALPN.getPeerAPs();
            // Only one application protocol name should be present
            String p;
            if ((protocols.size() == 1) &&
                    !((p = protocols.get(0)).isEmpty())) {
                int i;
                for (i = 0; i < localApl.length; i++) {
                    if (localApl[i].equals(p)) {
                        break;
                    }
                }
                if (i == localApl.length) {
                    fatalSE(Alerts.alert_handshake_failure,
                        "Server has selected an application protocol name " +
                        "which was not offered by the client: " + p);

                }
                applicationProtocol = p;
            } else {
                fatalSE(Alerts.alert_handshake_failure,
                    "Incorrect data in ServerHello " + ExtensionType.EXT_ALPN +
                    " message");
            }
        } else {
            applicationProtocol = "";
        }

        if (resumingSession && session != null) {
            setHandshakeSessionSE(session);
            // Reserve the handshake state if this is a session-resumption
            // abbreviated initial handshake.
            if (isInitialHandshake) {
                session.setAsSessionResumption(true);
            }

            return;
        }

        // check extensions
        for (HelloExtension ext : mesg.extensions.list()) {
            ExtensionType type = ext.type;
            if (type == ExtensionType.EXT_SERVER_NAME) {
                serverNamesAccepted = true;
            } else if ((type != ExtensionType.EXT_ELLIPTIC_CURVES)
                    && (type != ExtensionType.EXT_EC_POINT_FORMATS)
                    && (type != ExtensionType.EXT_SERVER_NAME)
                    && (type != ExtensionType.EXT_ALPN)
                    && (type != ExtensionType.EXT_RENEGOTIATION_INFO)
                    && (type != ExtensionType.EXT_EXTENDED_MASTER_SECRET)){
                fatalSE(Alerts.alert_unsupported_extension,
                    "Server sent an unsupported extension: " + type);
            }
        }

        // Create a new session, we need to do the full handshake
        session = new SSLSessionImpl(protocolVersion, cipherSuite,
                            getLocalSupportedSignAlgs(),
                            mesg.sessionId, getHostSE(), getPortSE(),
                            (extendedMasterSecretExt != null),
                            getEndpointIdentificationAlgorithmSE());
        session.setRequestedServerNames(requestedServerNames);
        setHandshakeSessionSE(session);
        if (debug != null && Debug.isOn("handshake")) {
            System.out.println("** " + cipherSuite);
        }
    }

    /*
     * Server's own key was either a signing-only key, or was too
     * large for export rules ... this message holds an ephemeral
     * RSA key to use for key exchange.
     */
    private void serverKeyExchange(RSA_ServerKeyExchange mesg)
            throws IOException, GeneralSecurityException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }
        if (!mesg.verify(serverKey, clnt_random, svr_random)) {
            fatalSE(Alerts.alert_handshake_failure,
                "server key exchange invalid");
            // NOTREACHED
        }
        ephemeralServerKey = mesg.getPublicKey();

        // check constraints of RSA PublicKey
        if (!algorithmConstraints.permits(
            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT), ephemeralServerKey)) {

            throw new SSLHandshakeException("RSA ServerKeyExchange " +
                    "does not comply to algorithm constraints");
        }
    }


    /*
     * Diffie-Hellman key exchange.  We save the server public key and
     * our own D-H algorithm object so we can defer key calculations
     * until after we've sent the client key exchange message (which
     * gives client and server some useful parallelism).
     */
    private void serverKeyExchange(DH_ServerKeyExchange mesg)
            throws IOException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }
        dh = new DHCrypt(mesg.getModulus(), mesg.getBase(),
                                            sslContext.getSecureRandom());
        serverDH = mesg.getServerPublicKey();

        // check algorithm constraints
        dh.checkConstraints(algorithmConstraints, serverDH);
    }

    private void serverKeyExchange(ECDH_ServerKeyExchange mesg)
            throws IOException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }
        ECPublicKey key = mesg.getPublicKey();
        ecdh = new ECDHCrypt(key.getParams(), sslContext.getSecureRandom());
        ephemeralServerKey = key;

        // check constraints of EC PublicKey
        if (!algorithmConstraints.permits(
            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT), ephemeralServerKey)) {

            throw new SSLHandshakeException("ECDH ServerKeyExchange " +
                    "does not comply to algorithm constraints");
        }
    }

    /*
     * The server's "Hello Done" message is the client's sign that
     * it's time to do all the hard work.
     */
    private void serverHelloDone(ServerHelloDone mesg) throws IOException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }
        /*
         * Always make sure the input has been digested before we
         * start emitting data, to ensure the hashes are correctly
         * computed for the Finished and CertificateVerify messages
         * which we send (here).
         */
        input.digestNow();

        /*
         * FIRST ... if requested, send an appropriate Certificate chain
         * to authenticate the client, and remember the associated private
         * key to sign the CertificateVerify message.
         */
        PrivateKey signingKey = null;

        if (certRequest != null) {
            X509ExtendedKeyManager km = sslContext.getX509KeyManager();

            ArrayList<String> keytypesTmp = new ArrayList<>(4);

            for (int i = 0; i < certRequest.types.length; i++) {
                String typeName;

                switch (certRequest.types[i]) {
                    case CertificateRequest.cct_rsa_sign:
                        typeName = "RSA";
                        break;

                    case CertificateRequest.cct_dss_sign:
                        typeName = "DSA";
                            break;

                    case CertificateRequest.cct_ecdsa_sign:
                        // ignore if we do not have EC crypto available
                        typeName = JsseJce.isEcAvailable() ? "EC" : null;
                        break;

                    // Fixed DH/ECDH client authentication not supported
                    //
                    // case CertificateRequest.cct_rsa_fixed_dh:
                    // case CertificateRequest.cct_dss_fixed_dh:
                    // case CertificateRequest.cct_rsa_fixed_ecdh:
                    // case CertificateRequest.cct_ecdsa_fixed_ecdh:
                    //
                    // Any other values (currently not used in TLS)
                    //
                    // case CertificateRequest.cct_rsa_ephemeral_dh:
                    // case CertificateRequest.cct_dss_ephemeral_dh:
                    default:
                        typeName = null;
                        break;
                }

                if ((typeName != null) && (!keytypesTmp.contains(typeName))) {
                    keytypesTmp.add(typeName);
                }
            }

            String alias = null;
            int keytypesTmpSize = keytypesTmp.size();
            if (keytypesTmpSize != 0) {
                String keytypes[] =
                        keytypesTmp.toArray(new String[keytypesTmpSize]);

                if (conn != null) {
                    alias = km.chooseClientAlias(keytypes,
                        certRequest.getAuthorities(), conn);
                } else {
                    alias = km.chooseEngineClientAlias(keytypes,
                        certRequest.getAuthorities(), engine);
                }
            }

            CertificateMsg m1 = null;
            if (alias != null) {
                X509Certificate[] certs = km.getCertificateChain(alias);
                if ((certs != null) && (certs.length != 0)) {
                    PublicKey publicKey = certs[0].getPublicKey();
                    if (publicKey != null) {
                        m1 = new CertificateMsg(certs);
                        signingKey = km.getPrivateKey(alias);
                        session.setLocalPrivateKey(signingKey);
                        session.setLocalCertificates(certs);
                    }
                }
            }
            if (m1 == null) {
                //
                // No appropriate cert was found ... report this to the
                // server.  For SSLv3, send the no_certificate alert;
                // TLS uses an empty cert chain instead.
                //
                if (protocolVersion.v >= ProtocolVersion.TLS10.v) {
                    m1 = new CertificateMsg(new X509Certificate [0]);
                } else {
                    warningSE(Alerts.alert_no_certificate);
                }
                if (debug != null && Debug.isOn("handshake")) {
                    System.out.println(
                        "Warning: no suitable certificate found - " +
                        "continuing without client authentication");
                }
            }

            //
            // At last ... send any client certificate chain.
            //
            if (m1 != null) {
                if (debug != null && Debug.isOn("handshake")) {
                    m1.print(System.out);
                }
                m1.write(output);
                handshakeState.update(m1, resumingSession);
            }
        }

        /*
         * SECOND ... send the client key exchange message.  The
         * procedure used is a function of the cipher suite selected;
         * one is always needed.
         */
        HandshakeMessage m2;

        switch (keyExchange) {

        case K_RSA:
        case K_RSA_EXPORT:
            if (serverKey == null) {
                throw new SSLProtocolException
                        ("Server did not send certificate message");
            }

            if (!(serverKey instanceof RSAPublicKey)) {
                throw new SSLProtocolException
                        ("Server certificate does not include an RSA key");
            }

            /*
             * For RSA key exchange, we randomly generate a new
             * pre-master secret and encrypt it with the server's
             * public key.  Then we save that pre-master secret
             * so that we can calculate the keying data later;
             * it's a performance speedup not to do that until
             * the client's waiting for the server response, but
             * more of a speedup for the D-H case.
             *
             * If the RSA_EXPORT scheme is active, when the public
             * key in the server certificate is less than or equal
             * to 512 bits in length, use the cert's public key,
             * otherwise, the ephemeral one.
             */
            PublicKey key;
            if (keyExchange == K_RSA) {
                key = serverKey;
            } else {    // K_RSA_EXPORT
                if (JsseJce.getRSAKeyLength(serverKey) <= 512) {
                    // extraneous ephemeralServerKey check done
                    // above in processMessage()
                    key = serverKey;
                } else {
                    if (ephemeralServerKey == null) {
                        throw new SSLProtocolException("Server did not send" +
                            " a RSA_EXPORT Server Key Exchange message");
                    }
                    key = ephemeralServerKey;
                }
            }

            m2 = new RSAClientKeyExchange(protocolVersion, maxProtocolVersion,
                                sslContext.getSecureRandom(), key);
            break;
        case K_DH_RSA:
        case K_DH_DSS:
            /*
             * For DH Key exchange, we only need to make sure the server
             * knows our public key, so we calculate the same pre-master
             * secret.
             *
             * For certs that had DH keys in them, we send an empty
             * handshake message (no key) ... we flag this case by
             * passing a null "dhPublic" value.
             *
             * Otherwise we send ephemeral DH keys, unsigned.
             */
            // if (useDH_RSA || useDH_DSS)
            m2 = new DHClientKeyExchange();
            break;
        case K_DHE_RSA:
        case K_DHE_DSS:
        case K_DH_ANON:
            if (dh == null) {
                throw new SSLProtocolException
                    ("Server did not send a DH Server Key Exchange message");
            }
            m2 = new DHClientKeyExchange(dh.getPublicKey());
            break;
        case K_ECDHE_RSA:
        case K_ECDHE_ECDSA:
        case K_ECDH_ANON:
            if (ecdh == null) {
                throw new SSLProtocolException
                    ("Server did not send a ECDH Server Key Exchange message");
            }
            m2 = new ECDHClientKeyExchange(ecdh.getPublicKey());
            break;
        case K_ECDH_RSA:
        case K_ECDH_ECDSA:
            if (serverKey == null) {
                throw new SSLProtocolException
                        ("Server did not send certificate message");
            }
            if (serverKey instanceof ECPublicKey == false) {
                throw new SSLProtocolException
                        ("Server certificate does not include an EC key");
            }
            ECParameterSpec params = ((ECPublicKey)serverKey).getParams();
            ecdh = new ECDHCrypt(params, sslContext.getSecureRandom());
            m2 = new ECDHClientKeyExchange(ecdh.getPublicKey());
            break;
        case K_KRB5:
        case K_KRB5_EXPORT:
            String sniHostname = null;
            for (SNIServerName serverName : requestedServerNames) {
                if (serverName instanceof SNIHostName) {
                    sniHostname = ((SNIHostName) serverName).getAsciiName();
                    break;
                }
            }

            KerberosClientKeyExchange kerberosMsg = null;
            if (sniHostname != null) {
                // use first requested SNI hostname
                try {
                    kerberosMsg = new KerberosClientKeyExchange(
                        sniHostname, getAccSE(), protocolVersion,
                        sslContext.getSecureRandom());
                } catch(IOException e) {
                    if (serverNamesAccepted) {
                        // server accepted requested SNI hostname,
                        // so it must be used
                        throw e;
                    }
                    // fallback to using hostname
                    if (debug != null && Debug.isOn("handshake")) {
                        System.out.println(
                            "Warning, cannot use Server Name Indication: "
                                + e.getMessage());
                    }
                }
            }

            if (kerberosMsg == null) {
                String hostname = getHostSE();
                if (hostname == null) {
                    throw new IOException("Hostname is required" +
                        " to use Kerberos cipher suites");
                }
                kerberosMsg = new KerberosClientKeyExchange(
                     hostname, getAccSE(), protocolVersion,
                     sslContext.getSecureRandom());
            }

            // Record the principals involved in exchange
            session.setPeerPrincipal(kerberosMsg.getPeerPrincipal());
            session.setLocalPrincipal(kerberosMsg.getLocalPrincipal());
            m2 = kerberosMsg;
            break;
        default:
            // somethings very wrong
            throw new RuntimeException
                                ("Unsupported key exchange: " + keyExchange);
        }
        if (debug != null && Debug.isOn("handshake")) {
            m2.print(System.out);
        }
        m2.write(output);

        handshakeState.update(m2, resumingSession);

        /*
         * THIRD, send a "change_cipher_spec" record followed by the
         * "Finished" message.  We flush the messages we've queued up, to
         * get concurrency between client and server.  The concurrency is
         * useful as we calculate the master secret, which is needed both
         * to compute the "Finished" message, and to compute the keys used
         * to protect all records following the change_cipher_spec.
         */

        output.doHashes();
        output.flush();

        /*
         * We deferred calculating the master secret and this connection's
         * keying data; we do it now.  Deferring this calculation is good
         * from a performance point of view, since it lets us do it during
         * some time that network delays and the server's own calculations
         * would otherwise cause to be "dead" in the critical path.
         */
        SecretKey preMasterSecret;
        switch (keyExchange) {
        case K_RSA:
        case K_RSA_EXPORT:
            preMasterSecret = ((RSAClientKeyExchange)m2).preMaster;
            break;
        case K_KRB5:
        case K_KRB5_EXPORT:
            byte[] secretBytes =
                ((KerberosClientKeyExchange)m2).getUnencryptedPreMasterSecret();
            preMasterSecret = new SecretKeySpec(secretBytes,
                "TlsPremasterSecret");
            break;
        case K_DHE_RSA:
        case K_DHE_DSS:
        case K_DH_ANON:
            preMasterSecret = dh.getAgreedSecret(serverDH, true);
            break;
        case K_ECDHE_RSA:
        case K_ECDHE_ECDSA:
        case K_ECDH_ANON:
            preMasterSecret = ecdh.getAgreedSecret(ephemeralServerKey);
            break;
        case K_ECDH_RSA:
        case K_ECDH_ECDSA:
            preMasterSecret = ecdh.getAgreedSecret(serverKey);
            break;
        default:
            throw new IOException("Internal error: unknown key exchange "
                + keyExchange);
        }

        calculateKeys(preMasterSecret, null);

        /*
         * FOURTH, if we sent a Certificate, we need to send a signed
         * CertificateVerify (unless the key in the client's certificate
         * was a Diffie-Hellman key).).
         *
         * This uses a hash of the previous handshake messages ... either
         * a nonfinal one (if the particular implementation supports it)
         * or else using the third element in the arrays of hashes being
         * computed.
         */
        if (signingKey != null) {
            CertificateVerify m3;
            try {
                SignatureAndHashAlgorithm preferableSignatureAlgorithm = null;
                if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                    preferableSignatureAlgorithm =
                        SignatureAndHashAlgorithm.getPreferableAlgorithm(
                            getPeerSupportedSignAlgs(),
                            signingKey.getAlgorithm(), signingKey);

                    if (preferableSignatureAlgorithm == null) {
                        throw new SSLHandshakeException(
                            "No supported signature algorithm");
                    }

                    String hashAlg =
                        SignatureAndHashAlgorithm.getHashAlgorithmName(
                                preferableSignatureAlgorithm);
                    if (hashAlg == null || hashAlg.length() == 0) {
                        throw new SSLHandshakeException(
                                "No supported hash algorithm");
                    }
                }

                m3 = new CertificateVerify(protocolVersion, handshakeHash,
                    signingKey, session.getMasterSecret(),
                    sslContext.getSecureRandom(),
                    preferableSignatureAlgorithm);
            } catch (GeneralSecurityException e) {
                fatalSE(Alerts.alert_handshake_failure,
                    "Error signing certificate verify", e);
                // NOTREACHED, make compiler happy
                m3 = null;
            }
            if (debug != null && Debug.isOn("handshake")) {
                m3.print(System.out);
            }
            m3.write(output);
            handshakeState.update(m3, resumingSession);
            output.doHashes();
        }

        /*
         * OK, that's that!
         */
        sendChangeCipherAndFinish(false);
    }


    /*
     * "Finished" is the last handshake message sent.  If we got this
     * far, the MAC has been validated post-decryption.  We validate
     * the two hashes here as an additional sanity check, protecting
     * the handshake against various active attacks.
     */
    private void serverFinished(Finished mesg) throws IOException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        boolean verified = mesg.verify(handshakeHash, Finished.SERVER,
            session.getMasterSecret());

        if (!verified) {
            fatalSE(Alerts.alert_illegal_parameter,
                       "server 'finished' message doesn't verify");
            // NOTREACHED
        }

        /*
         * save server verify data for secure renegotiation
         */
        if (secureRenegotiation) {
            serverVerifyData = mesg.getVerifyData();
        }

        /*
         * Reset the handshake state if this is not an initial handshake.
         */
        if (!isInitialHandshake) {
            session.setAsSessionResumption(false);
        }

        /*
         * OK, it verified.  If we're doing the fast handshake, add that
         * "Finished" message to the hash of handshake messages, then send
         * our own change_cipher_spec and Finished message for the server
         * to verify in turn.  These are the last handshake messages.
         *
         * In any case, update the session cache.  We're done handshaking,
         * so there are no threats any more associated with partially
         * completed handshakes.
         */
        if (resumingSession) {
            input.digestNow();
            sendChangeCipherAndFinish(true);
        } else {
            handshakeFinished = true;
        }
        session.setLastAccessedTime(System.currentTimeMillis());

        if (!resumingSession) {
            if (session.isRejoinable()) {
                ((SSLSessionContextImpl) sslContext
                        .engineGetClientSessionContext())
                        .put(session);
                if (debug != null && Debug.isOn("session")) {
                    System.out.println("%% Cached client session: " + session);
                }
            } else if (debug != null && Debug.isOn("session")) {
                System.out.println(
                    "%% Didn't cache non-resumable client session: "
                    + session);
            }
        }
    }


    /*
     * Send my change-cipher-spec and Finished message ... done as the
     * last handshake act in either the short or long sequences.  In
     * the short one, we've already seen the server's Finished; in the
     * long one, we wait for it now.
     */
    private void sendChangeCipherAndFinish(boolean finishedTag)
            throws IOException {
        Finished mesg = new Finished(protocolVersion, handshakeHash,
            Finished.CLIENT, session.getMasterSecret(), cipherSuite);

        /*
         * Send the change_cipher_spec message, then the Finished message
         * which we just calculated (and protected using the keys we just
         * calculated).  Server responds with its Finished message, except
         * in the "fast handshake" (resume session) case.
         */
        sendChangeCipherSpec(mesg, finishedTag);

        /*
         * save client verify data for secure renegotiation
         */
        if (secureRenegotiation) {
            clientVerifyData = mesg.getVerifyData();
        }
    }


    /*
     * Returns a ClientHello message to kickstart renegotiations
     */
    @Override
    HandshakeMessage getKickstartMessage() throws SSLException {
        // session ID of the ClientHello message
        SessionId sessionId = SSLSessionImpl.nullSession.getSessionId();

        // a list of cipher suites sent by the client
        CipherSuiteList cipherSuites = getActiveCipherSuites();

        // set the max protocol version this client is supporting.
        maxProtocolVersion = protocolVersion;

        //
        // Try to resume an existing session.  This might be mandatory,
        // given certain API options.
        //
        session = ((SSLSessionContextImpl)sslContext
                        .engineGetClientSessionContext())
                        .get(getHostSE(), getPortSE());
        if (debug != null && Debug.isOn("session")) {
            if (session != null) {
                System.out.println("%% Client cached "
                    + session
                    + (session.isRejoinable() ? "" : " (not rejoinable)"));
            } else {
                System.out.println("%% No cached client session");
            }
        }
        if (session != null) {
            // If unsafe server certificate change is not allowed, reserve
            // current server certificates if the previous handshake is a
            // session-resumption abbreviated initial handshake.
            if (!allowUnsafeServerCertChange && session.isSessionResumption()) {
                try {
                    // If existing, peer certificate chain cannot be null.
                    reservedServerCerts =
                        (X509Certificate[])session.getPeerCertificates();
                } catch (SSLPeerUnverifiedException puve) {
                    // Maybe not certificate-based, ignore the exception.
                }
            }

            if (!session.isRejoinable()) {
                session = null;
            }
        }

        if (session != null) {
            CipherSuite sessionSuite = session.getSuite();
            ProtocolVersion sessionVersion = session.getProtocolVersion();
            if (isNegotiable(sessionSuite) == false) {
                if (debug != null && Debug.isOn("session")) {
                    System.out.println("%% can't resume, unavailable cipher");
                }
                session = null;
            }

            if ((session != null) && !isNegotiable(sessionVersion)) {
                if (debug != null && Debug.isOn("session")) {
                    System.out.println("%% can't resume, protocol disabled");
                }
                session = null;
            }

            if ((session != null) && useExtendedMasterSecret) {
                boolean isTLS10Plus = sessionVersion.v >= ProtocolVersion.TLS10.v;
                if (isTLS10Plus && !session.getUseExtendedMasterSecret()) {
                    if (!allowLegacyResumption) {
                        // perform full handshake instead
                        //
                        // The client SHOULD NOT offer an abbreviated handshake
                        // to resume a session that does not use an extended
                        // master secret.  Instead, it SHOULD offer a full
                        // handshake.
                        session = null;
                    }
                }

                if ((session != null) && !allowUnsafeServerCertChange) {
                    // It is fine to move on with abbreviate handshake if
                    // endpoint identification is enabled.
                    String identityAlg = getEndpointIdentificationAlgorithmSE();
                    if ((identityAlg == null || identityAlg.length() == 0)) {
                        if (isTLS10Plus) {
                            if (!session.getUseExtendedMasterSecret()) {
                                // perform full handshake instead
                                session = null;
                            }   // Otherwise, use extended master secret.
                        } else {
                            // The extended master secret extension does not
                            // apply to SSL 3.0.  Perform a full handshake
                            // instead.
                            //
                            // Note that the useExtendedMasterSecret is
                            // extended to protect SSL 3.0 connections,
                            // by discarding abbreviate handshake.
                            session = null;
                        }
                    }
                }
            }

            // ensure that the endpoint identification algorithm matches the
            // one in the session
            String identityAlg = getEndpointIdentificationAlgorithmSE();
            if (session != null && identityAlg != null) {

                String sessionIdentityAlg =
                    session.getEndpointIdentificationAlgorithm();
                if (!identityAlg.equalsIgnoreCase(sessionIdentityAlg)) {

                    if (debug != null && Debug.isOn("session")) {
                        System.out.println("%% can't resume, endpoint id" +
                            " algorithm does not match, requested: " +
                            identityAlg + ", cached: " + sessionIdentityAlg);
                    }
                    session = null;
                }
            }

            if (session != null) {
                if (debug != null) {
                    if (Debug.isOn("handshake") || Debug.isOn("session")) {
                        System.out.println("%% Try resuming " + session
                            + " from port " + getLocalPortSE());
                    }
                }

                sessionId = session.getSessionId();
                maxProtocolVersion = sessionVersion;

                // Update SSL version number in underlying SSL socket and
                // handshake output stream, so that the output records (at the
                // record layer) have the correct version
                setVersion(sessionVersion);
            }

            /*
             * Force use of the previous session ciphersuite, and
             * add the SCSV if enabled.
             */
            if (!enableNewSession) {
                if (session == null) {
                    throw new SSLHandshakeException(
                        "Can't reuse existing SSL client session");
                }

                Collection<CipherSuite> cipherList = new ArrayList<>(2);
                cipherList.add(sessionSuite);
                if (!secureRenegotiation &&
                        cipherSuites.contains(CipherSuite.C_SCSV)) {
                    cipherList.add(CipherSuite.C_SCSV);
                }   // otherwise, renegotiation_info extension will be used

                cipherSuites = new CipherSuiteList(cipherList);
            }
        }

        if (session == null && !enableNewSession) {
            throw new SSLHandshakeException("No existing session to resume");
        }

        // exclude SCSV for secure renegotiation
        if (secureRenegotiation && cipherSuites.contains(CipherSuite.C_SCSV)) {
            Collection<CipherSuite> cipherList =
                        new ArrayList<>(cipherSuites.size() - 1);
            for (CipherSuite suite : cipherSuites.collection()) {
                if (suite != CipherSuite.C_SCSV) {
                    cipherList.add(suite);
                }
            }

            cipherSuites = new CipherSuiteList(cipherList);
        }

        // make sure there is a negotiable cipher suite.
        boolean negotiable = false;
        for (CipherSuite suite : cipherSuites.collection()) {
            if (isNegotiable(suite)) {
                negotiable = true;
                break;
            }
        }

        if (!negotiable) {
            throw new SSLHandshakeException("No negotiable cipher suite");
        }

        // Not a TLS1.2+ handshake
        // For SSLv2Hello, HandshakeHash.reset() will be called, so we
        // cannot call HandshakeHash.protocolDetermined() here. As it does
        // not follow the spec that HandshakeHash.reset() can be only be
        // called before protocolDetermined.
        // if (maxProtocolVersion.v < ProtocolVersion.TLS12.v) {
        //     handshakeHash.protocolDetermined(maxProtocolVersion);
        // }

        // create the ClientHello message
        ClientHello clientHelloMessage = new ClientHello(
                sslContext.getSecureRandom(), maxProtocolVersion,
                sessionId, cipherSuites);

        // add elliptic curves and point format extensions
        if (cipherSuites.containsEC()) {
            EllipticCurvesExtension ece =
                EllipticCurvesExtension.createExtension(algorithmConstraints);
            if (ece != null) {
                clientHelloMessage.extensions.add(ece);
                clientHelloMessage.extensions.add(
                   EllipticPointFormatsExtension.DEFAULT);
            }
        }

        // add signature_algorithm extension
        if (maxProtocolVersion.v >= ProtocolVersion.TLS12.v) {
            // we will always send the signature_algorithm extension
            Collection<SignatureAndHashAlgorithm> localSignAlgs =
                                                getLocalSupportedSignAlgs();
            if (localSignAlgs.isEmpty()) {
                throw new SSLHandshakeException(
                            "No supported signature algorithm");
            }

            clientHelloMessage.addSignatureAlgorithmsExtension(localSignAlgs);
        }

        // add Extended Master Secret extension
        if (useExtendedMasterSecret && (maxProtocolVersion.v >= ProtocolVersion.TLS10.v)) {
            if ((session == null) || session.getUseExtendedMasterSecret()) {
                clientHelloMessage.addExtendedMasterSecretExtension();
                requestedToUseEMS = true;
            }
        }

        // add server_name extension
        if (enableSNIExtension) {
            if (session != null) {
                requestedServerNames = session.getRequestedServerNames();
            } else {
                requestedServerNames = serverNames;
            }

            if (!requestedServerNames.isEmpty()) {
                clientHelloMessage.addSNIExtension(requestedServerNames);
            }
        }

        // Add ALPN extension
        if (localApl != null && localApl.length > 0) {
            clientHelloMessage.addALPNExtension(localApl);
            alpnActive = true;
        }

        // reset the client random cookie
        clnt_random = clientHelloMessage.clnt_random;

        /*
         * need to set the renegotiation_info extension for:
         * 1: secure renegotiation
         * 2: initial handshake and no SCSV in the ClientHello
         * 3: insecure renegotiation and no SCSV in the ClientHello
         */
        if (secureRenegotiation ||
                !cipherSuites.contains(CipherSuite.C_SCSV)) {
            clientHelloMessage.addRenegotiationInfoExtension(clientVerifyData);
        }

        return clientHelloMessage;
    }

    /*
     * Fault detected during handshake.
     */
    @Override
    void handshakeAlert(byte description) throws SSLProtocolException {
        String message = Alerts.alertDescription(description);

        if (debug != null && Debug.isOn("handshake")) {
            System.out.println("SSL - handshake alert: " + message);
        }
        throw new SSLProtocolException("handshake alert:  " + message);
    }

    /*
     * Unless we are using an anonymous ciphersuite, the server always
     * sends a certificate message (for the CipherSuites we currently
     * support). The trust manager verifies the chain for us.
     */
    private void serverCertificate(CertificateMsg mesg) throws IOException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }
        X509Certificate[] peerCerts = mesg.getCertificateChain();
        if (peerCerts.length == 0) {
            fatalSE(Alerts.alert_bad_certificate, "empty certificate chain");
        }

        // Allow server certificate change in client side during renegotiation
        // after a session-resumption abbreviated initial handshake?
        //
        // DO NOT need to check allowUnsafeServerCertChange here.  We only
        // reserve server certificates when allowUnsafeServerCertChange is
        // flase.
        //
        // Allow server certificate change if it is negotiated to use the
        // extended master secret.
        if ((reservedServerCerts != null) &&
                !session.getUseExtendedMasterSecret()) {
            // It is not necessary to check the certificate update if endpoint
            // identification is enabled.
            String identityAlg = getEndpointIdentificationAlgorithmSE();
            if ((identityAlg == null || identityAlg.length() == 0) &&
                !isIdentityEquivalent(peerCerts[0], reservedServerCerts[0])) {

                fatalSE(Alerts.alert_bad_certificate,
                        "server certificate change is restricted " +
                        "during renegotiation");
            }
        }

        // ask the trust manager to verify the chain
        X509TrustManager tm = sslContext.getX509TrustManager();
        try {
            // find out the key exchange algorithm used
            // use "RSA" for non-ephemeral "RSA_EXPORT"
            String keyExchangeString;
            if (keyExchange == K_RSA_EXPORT && !serverKeyExchangeReceived) {
                keyExchangeString = K_RSA.name;
            } else {
                keyExchangeString = keyExchange.name;
            }

            if (tm instanceof X509ExtendedTrustManager) {
                if (conn != null) {
                    ((X509ExtendedTrustManager)tm).checkServerTrusted(
                        peerCerts.clone(),
                        keyExchangeString,
                        conn);
                } else {
                    ((X509ExtendedTrustManager)tm).checkServerTrusted(
                        peerCerts.clone(),
                        keyExchangeString,
                        engine);
                }
            } else {
                // Unlikely to happen, because we have wrapped the old
                // X509TrustManager with the new X509ExtendedTrustManager.
                throw new CertificateException(
                    "Improper X509TrustManager implementation");
            }
        } catch (CertificateException e) {
            // This will throw an exception, so include the original error.
            fatalSE(Alerts.alert_certificate_unknown, e);
        }
        session.setPeerCertificates(peerCerts);
    }

    /*
     * Whether the certificates can represent the same identity?
     *
     * The certificates can be used to represent the same identity:
     *     1. If the subject alternative names of IP address are present in
     *        both certificates, they should be identical; otherwise,
     *     2. if the subject alternative names of DNS name are present in
     *        both certificates, they should be identical; otherwise,
     *     3. if the subject fields are present in both certificates, the
     *        certificate subjects and issuers should be identical.
     */
    private static boolean isIdentityEquivalent(X509Certificate thisCert,
            X509Certificate prevCert) {
        if (thisCert.equals(prevCert)) {
            return true;
        }

        // check subject alternative names
        Collection<List<?>> thisSubjectAltNames = null;
        try {
            thisSubjectAltNames = thisCert.getSubjectAlternativeNames();
        } catch (CertificateParsingException cpe) {
            if (debug != null && Debug.isOn("handshake")) {
                System.out.println(
                        "Attempt to obtain subjectAltNames extension failed!");
            }
        }

        Collection<List<?>> prevSubjectAltNames = null;
        try {
            prevSubjectAltNames = prevCert.getSubjectAlternativeNames();
        } catch (CertificateParsingException cpe) {
            if (debug != null && Debug.isOn("handshake")) {
                System.out.println(
                        "Attempt to obtain subjectAltNames extension failed!");
            }
        }

        if ((thisSubjectAltNames != null) && (prevSubjectAltNames != null)) {
            // check the iPAddress field in subjectAltName extension
            Collection<String> thisSubAltIPAddrs =
                        getSubjectAltNames(thisSubjectAltNames, ALTNAME_IP);
            Collection<String> prevSubAltIPAddrs =
                        getSubjectAltNames(prevSubjectAltNames, ALTNAME_IP);
            if ((thisSubAltIPAddrs != null) && (prevSubAltIPAddrs != null) &&
                (isEquivalent(thisSubAltIPAddrs, prevSubAltIPAddrs))) {

                return true;
            }

            // check the dNSName field in subjectAltName extension
            Collection<String> thisSubAltDnsNames =
                        getSubjectAltNames(thisSubjectAltNames, ALTNAME_DNS);
            Collection<String> prevSubAltDnsNames =
                        getSubjectAltNames(prevSubjectAltNames, ALTNAME_DNS);
            if ((thisSubAltDnsNames != null) && (prevSubAltDnsNames != null) &&
                (isEquivalent(thisSubAltDnsNames, prevSubAltDnsNames))) {

                return true;
            }
        }

        // check the certificate subject and issuer
        X500Principal thisSubject = thisCert.getSubjectX500Principal();
        X500Principal prevSubject = prevCert.getSubjectX500Principal();
        X500Principal thisIssuer = thisCert.getIssuerX500Principal();
        X500Principal prevIssuer = prevCert.getIssuerX500Principal();
        if (!thisSubject.getName().isEmpty() &&
                !prevSubject.getName().isEmpty() &&
                thisSubject.equals(prevSubject) &&
                thisIssuer.equals(prevIssuer)) {
            return true;
        }

        return false;
    }

    /*
     * Returns the subject alternative name of the specified type in the
     * subjectAltNames extension of a certificate.
     *
     * Note that only those subjectAltName types that use String data
     * should be passed into this function.
     */
    private static Collection<String> getSubjectAltNames(
            Collection<List<?>> subjectAltNames, int type) {

        HashSet<String> subAltDnsNames = null;
        for (List<?> subjectAltName : subjectAltNames) {
            int subjectAltNameType = (Integer)subjectAltName.get(0);
            if (subjectAltNameType == type) {
                String subAltDnsName = (String)subjectAltName.get(1);
                if ((subAltDnsName != null) && !subAltDnsName.isEmpty()) {
                    if (subAltDnsNames == null) {
                        subAltDnsNames =
                                new HashSet<>(subjectAltNames.size());
                    }
                    subAltDnsNames.add(subAltDnsName);
                }
            }
        }

        return subAltDnsNames;
    }

    private static boolean isEquivalent(Collection<String> thisSubAltNames,
            Collection<String> prevSubAltNames) {

        for (String thisSubAltName : thisSubAltNames) {
            for (String prevSubAltName : prevSubAltNames) {
                // Only allow the exactly match.  Check no wildcard character.
                if (thisSubAltName.equalsIgnoreCase(prevSubAltName)) {
                    return true;
                }
            }
        }

        return false;
    }
}
