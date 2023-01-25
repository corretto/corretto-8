/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import sun.security.ssl.SSLExtension.ExtensionConsumer;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.ssl.SignatureAlgorithmsExtension.SignatureSchemesSpec;

/**
 * Pack of the "signature_algorithms_cert" extensions.
 */
final class CertSignAlgsExtension {
    static final HandshakeProducer chNetworkProducer =
            new CHCertSignatureSchemesProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new CHCertSignatureSchemesConsumer();
    static final HandshakeConsumer chOnTradeConsumer =
            new CHCertSignatureSchemesUpdate();

    static final HandshakeProducer crNetworkProducer =
            new CRCertSignatureSchemesProducer();
    static final ExtensionConsumer crOnLoadConsumer =
            new CRCertSignatureSchemesConsumer();
    static final HandshakeConsumer crOnTradeConsumer =
            new CRCertSignatureSchemesUpdate();

    static final SSLStringizer ssStringizer =
            new CertSignatureSchemesStringizer();

    private static final
            class CertSignatureSchemesStringizer implements SSLStringizer {
        @Override
        public String toString(ByteBuffer buffer) {
            try {
                return (new SignatureSchemesSpec(buffer)).toString();
            } catch (IOException ioe) {
                // For debug logging only, so please swallow exceptions.
                return ioe.getMessage();
            }
        }
    }

    /**
     * Network data producer of a "signature_algorithms_cert" extension in
     * the ClientHello handshake message.
     */
    private static final
            class CHCertSignatureSchemesProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private CHCertSignatureSchemesProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!chc.sslConfig.isAvailable(
                    SSLExtension.CH_SIGNATURE_ALGORITHMS_CERT)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable " +
                            "signature_algorithms_cert extension");
                }

                return null;    // ignore the extension
            }

            // Produce the extension.
            if (chc.localSupportedSignAlgs == null) {
                chc.localSupportedSignAlgs =
                    SignatureScheme.getSupportedAlgorithms(
                            chc.sslConfig,
                            chc.algorithmConstraints, chc.activeProtocols);
            }

            int vectorLen = SignatureScheme.sizeInRecord() *
                    chc.localSupportedSignAlgs.size();
            byte[] extData = new byte[vectorLen + 2];
            ByteBuffer m = ByteBuffer.wrap(extData);
            Record.putInt16(m, vectorLen);
            for (SignatureScheme ss : chc.localSupportedSignAlgs) {
                Record.putInt16(m, ss.id);
            }

            // Update the context.
            chc.handshakeExtensions.put(
                    SSLExtension.CH_SIGNATURE_ALGORITHMS_CERT,
                    new SignatureSchemesSpec(chc.localSupportedSignAlgs));

            return extData;
        }
    }

    /**
     * Network data consumer of a "signature_algorithms_cert" extension in
     * the ClientHello handshake message.
     */
    private static final
            class CHCertSignatureSchemesConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private CHCertSignatureSchemesConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(
                    SSLExtension.CH_SIGNATURE_ALGORITHMS_CERT)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable " +
                            "signature_algorithms_cert extension");
                }
                return;     // ignore the extension
            }

            // Parse the extension.
            SignatureSchemesSpec spec;
            try {
                spec = new SignatureSchemesSpec(buffer);
            } catch (IOException ioe) {
                throw shc.conContext.fatal(Alert.UNEXPECTED_MESSAGE, ioe);
            }

            // Update the context.
            shc.handshakeExtensions.put(
                    SSLExtension.CH_SIGNATURE_ALGORITHMS_CERT, spec);

            // No impact on session resumption.
        }
    }

    /**
     * After session creation consuming of a "signature_algorithms_cert"
     * extension in the ClientHello handshake message.
     */
    private static final class CHCertSignatureSchemesUpdate
            implements HandshakeConsumer {
        // Prevent instantiation of this class.
        private CHCertSignatureSchemesUpdate() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            SignatureSchemesSpec spec = (SignatureSchemesSpec)
                    shc.handshakeExtensions.get(
                            SSLExtension.CH_SIGNATURE_ALGORITHMS_CERT);
            if (spec == null) {
                // Ignore, no signature_algorithms_cert extension requested.
                return;
            }

            // update the context
            List<SignatureScheme> schemes =
                    SignatureScheme.getSupportedAlgorithms(
                            shc.sslConfig,
                            shc.algorithmConstraints, shc.negotiatedProtocol,
                            spec.signatureSchemes);
            shc.peerRequestedCertSignSchemes = schemes;
            shc.handshakeSession.setPeerSupportedSignatureAlgorithms(schemes);

            if (!shc.isResumption && shc.negotiatedProtocol.useTLS13PlusSpec()) {
                if (shc.sslConfig.clientAuthType !=
                        ClientAuthType.CLIENT_AUTH_NONE) {
                    shc.handshakeProducers.putIfAbsent(
                            SSLHandshake.CERTIFICATE_REQUEST.id,
                            SSLHandshake.CERTIFICATE_REQUEST);
                }
                shc.handshakeProducers.put(SSLHandshake.CERTIFICATE.id,
                        SSLHandshake.CERTIFICATE);
                shc.handshakeProducers.putIfAbsent(
                        SSLHandshake.CERTIFICATE_VERIFY.id,
                        SSLHandshake.CERTIFICATE_VERIFY);
            }
        }
    }

    /**
     * Network data producer of a "signature_algorithms_cert" extension in
     * the CertificateRequest handshake message.
     */
    private static final
            class CRCertSignatureSchemesProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private CRCertSignatureSchemesProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(
                    SSLExtension.CH_SIGNATURE_ALGORITHMS_CERT)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable " +
                            "signature_algorithms_cert extension");
                }
                return null;    // ignore the extension
            }

            // Produce the extension.
            List<ProtocolVersion> protocols = Arrays.asList(shc.negotiatedProtocol);
            protocols = Collections.unmodifiableList(protocols);
            List<SignatureScheme> sigAlgs =
                    SignatureScheme.getSupportedAlgorithms(
                            shc.sslConfig,
                            shc.algorithmConstraints,
                            protocols);

            int vectorLen = SignatureScheme.sizeInRecord() * sigAlgs.size();
            byte[] extData = new byte[vectorLen + 2];
            ByteBuffer m = ByteBuffer.wrap(extData);
            Record.putInt16(m, vectorLen);
            for (SignatureScheme ss : sigAlgs) {
                Record.putInt16(m, ss.id);
            }

            // Update the context.
            shc.handshakeExtensions.put(
                    SSLExtension.CR_SIGNATURE_ALGORITHMS_CERT,
                    new SignatureSchemesSpec(shc.localSupportedSignAlgs));

            return extData;
        }
    }

    /**
     * Network data consumer of a "signature_algorithms_cert" extension in
     * the CertificateRequest handshake message.
     */
    private static final
            class CRCertSignatureSchemesConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private CRCertSignatureSchemesConsumer() {
            // blank
        }
        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            // The consuming happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!chc.sslConfig.isAvailable(
                    SSLExtension.CH_SIGNATURE_ALGORITHMS_CERT)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable " +
                            "signature_algorithms_cert extension");
                }
                return;     // ignore the extension
            }

            // Parse the extension.
            SignatureSchemesSpec spec;
            try {
                spec = new SignatureSchemesSpec(buffer);
            } catch (IOException ioe) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE, ioe);
            }

            // Update the context.
            chc.handshakeExtensions.put(
                    SSLExtension.CR_SIGNATURE_ALGORITHMS_CERT, spec);

            // No impact on session resumption.
        }
    }

    /**
     * After session creation consuming of a "signature_algorithms_cert"
     * extension in the CertificateRequest handshake message.
     */
    private static final class CRCertSignatureSchemesUpdate
            implements HandshakeConsumer {
        // Prevent instantiation of this class.
        private CRCertSignatureSchemesUpdate() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The consuming happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            SignatureSchemesSpec spec = (SignatureSchemesSpec)
                    chc.handshakeExtensions.get(
                            SSLExtension.CR_SIGNATURE_ALGORITHMS_CERT);
            if (spec == null) {
                // Ignore, no "signature_algorithms_cert" extension requested.
                return;
            }

            // update the context
            List<SignatureScheme> schemes =
                    SignatureScheme.getSupportedAlgorithms(
                            chc.sslConfig,
                            chc.algorithmConstraints, chc.negotiatedProtocol,
                            spec.signatureSchemes);
            chc.peerRequestedCertSignSchemes = schemes;
            chc.handshakeSession.setPeerSupportedSignatureAlgorithms(schemes);
        }
    }
}
