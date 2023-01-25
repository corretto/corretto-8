/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.security.CryptoPrimitive;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLProtocolException;
import sun.security.ssl.DHKeyExchange.DHECredentials;
import sun.security.ssl.DHKeyExchange.DHEPossession;
import sun.security.ssl.ECDHKeyExchange.ECDHECredentials;
import sun.security.ssl.ECDHKeyExchange.ECDHEPossession;
import sun.security.ssl.KeyShareExtension.CHKeyShareSpec;
import sun.security.ssl.SSLExtension.ExtensionConsumer;
import sun.security.ssl.SSLExtension.SSLExtensionSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.ssl.SupportedGroupsExtension.NamedGroup;
import sun.security.ssl.SupportedGroupsExtension.NamedGroupType;
import sun.security.ssl.SupportedGroupsExtension.SupportedGroups;
import sun.misc.HexDumpEncoder;

/**
 * Pack of the "key_share" extensions.
 */
final class KeyShareExtension {
    static final HandshakeProducer chNetworkProducer =
            new CHKeyShareProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new CHKeyShareConsumer();
    static final SSLStringizer chStringizer =
            new CHKeyShareStringizer();

    static final HandshakeProducer shNetworkProducer =
            new SHKeyShareProducer();
    static final ExtensionConsumer shOnLoadConsumer =
            new SHKeyShareConsumer();
    static final HandshakeAbsence shOnLoadAbsence =
            new SHKeyShareAbsence();
    static final SSLStringizer shStringizer =
            new SHKeyShareStringizer();

    static final HandshakeProducer hrrNetworkProducer =
            new HRRKeyShareProducer();
    static final ExtensionConsumer hrrOnLoadConsumer =
            new HRRKeyShareConsumer();
    static final HandshakeProducer hrrNetworkReproducer =
            new HRRKeyShareReproducer();
    static final SSLStringizer hrrStringizer =
            new HRRKeyShareStringizer();

    /**
     * The key share entry used in "key_share" extensions.
     */
    private static final class KeyShareEntry {
        final int namedGroupId;
        final byte[] keyExchange;

        private KeyShareEntry(int namedGroupId, byte[] keyExchange) {
            this.namedGroupId = namedGroupId;
            this.keyExchange = keyExchange;
        }

        private byte[] getEncoded() {
            byte[] buffer = new byte[keyExchange.length + 4];
                                            //  2: named group id
                                            // +2: key exchange length
            ByteBuffer m = ByteBuffer.wrap(buffer);
            try {
                Record.putInt16(m, namedGroupId);
                Record.putBytes16(m, keyExchange);
            } catch (IOException ioe) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                        "Unlikely IOException", ioe);
                }
            }

            return buffer;
        }

        private int getEncodedSize() {
            return keyExchange.length + 4;  //  2: named group id
                                            // +2: key exchange length
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\n'{'\n" +
                "  \"named group\": {0}\n" +
                "  \"key_exchange\": '{'\n" +
                "{1}\n" +
                "  '}'\n" +
                "'}',", Locale.ENGLISH);

            HexDumpEncoder hexEncoder = new HexDumpEncoder();
            Object[] messageFields = {
                NamedGroup.nameOf(namedGroupId),
                Utilities.indent(hexEncoder.encode(keyExchange), "    ")
            };

            return messageFormat.format(messageFields);
        }
    }

    /**
     * The "key_share" extension in a ClientHello handshake message.
     */
    static final class CHKeyShareSpec implements SSLExtensionSpec {
        final List<KeyShareEntry> clientShares;

        private CHKeyShareSpec(List<KeyShareEntry> clientShares) {
            this.clientShares = clientShares;
        }

        private CHKeyShareSpec(ByteBuffer buffer) throws IOException {
            // struct {
            //      KeyShareEntry client_shares<0..2^16-1>;
            // } KeyShareClientHello;
            if (buffer.remaining() < 2) {
                throw new SSLProtocolException(
                    "Invalid key_share extension: " +
                    "insufficient data (length=" + buffer.remaining() + ")");
            }

            int listLen = Record.getInt16(buffer);
            if (listLen != buffer.remaining()) {
                throw new SSLProtocolException(
                    "Invalid key_share extension: " +
                    "incorrect list length (length=" + listLen + ")");
            }

            List<KeyShareEntry> keyShares = new LinkedList<>();
            while (buffer.hasRemaining()) {
                int namedGroupId = Record.getInt16(buffer);
                byte[] keyExchange = Record.getBytes16(buffer);
                if (keyExchange.length == 0) {
                    throw new SSLProtocolException(
                        "Invalid key_share extension: empty key_exchange");
                }

                keyShares.add(new KeyShareEntry(namedGroupId, keyExchange));
            }

            this.clientShares = Collections.unmodifiableList(keyShares);
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"client_shares\": '['{0}\n']'", Locale.ENGLISH);

            StringBuilder builder = new StringBuilder(512);
            for (KeyShareEntry entry : clientShares) {
                builder.append(entry.toString());
            }

            Object[] messageFields = {
                Utilities.indent(builder.toString())
            };

            return messageFormat.format(messageFields);
        }
    }

    private static final class CHKeyShareStringizer implements SSLStringizer {
        @Override
        public String toString(ByteBuffer buffer) {
            try {
                return (new CHKeyShareSpec(buffer)).toString();
            } catch (IOException ioe) {
                // For debug logging only, so please swallow exceptions.
                return ioe.getMessage();
            }
        }
    }

    /**
     * Network data producer of the extension in a ClientHello
     * handshake message.
     */
    private static final
            class CHKeyShareProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private CHKeyShareProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!chc.sslConfig.isAvailable(SSLExtension.CH_KEY_SHARE)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Ignore unavailable key_share extension");
                }
                return null;
            }

            List<NamedGroup> namedGroups;
            if (chc.serverSelectedNamedGroup != null) {
                // Response to HelloRetryRequest
                namedGroups = Arrays.asList(chc.serverSelectedNamedGroup);
            } else {
                namedGroups = chc.clientRequestedNamedGroups;
                if (namedGroups == null || namedGroups.isEmpty()) {
                    // No supported groups.
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.warning(
                            "Ignore key_share extension, no supported groups");
                    }
                    return null;
                }
            }

            List<KeyShareEntry> keyShares = new LinkedList<>();
            for (NamedGroup ng : namedGroups) {
                SSLKeyExchange ke = SSLKeyExchange.valueOf(ng);
                if (ke == null) {
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.warning(
                            "No key exchange for named group " + ng.name);
                    }
                    continue;
                }

                SSLPossession[] poses = ke.createPossessions(chc);
                for (SSLPossession pos : poses) {
                    // update the context
                    chc.handshakePossessions.add(pos);
                    if (!(pos instanceof ECDHEPossession) &&
                            !(pos instanceof DHEPossession)) {
                        // May need more possesion types in the future.
                        continue;
                    }

                    keyShares.add(new KeyShareEntry(ng.id, pos.encode()));
                }

                // One key share entry only.  Too much key share entries makes
                // the ClientHello handshake message really big.
                if (!keyShares.isEmpty()) {
                    break;
                }
            }

            int listLen = 0;
            for (KeyShareEntry entry : keyShares) {
                listLen += entry.getEncodedSize();
            }
            byte[] extData = new byte[listLen + 2];     //  2: list length
            ByteBuffer m = ByteBuffer.wrap(extData);
            Record.putInt16(m, listLen);
            for (KeyShareEntry entry : keyShares) {
                m.put(entry.getEncoded());
            }

            // update the context
            chc.handshakeExtensions.put(SSLExtension.CH_KEY_SHARE,
                    new CHKeyShareSpec(keyShares));

            return extData;
        }
    }

    /**
     * Network data consumer of the extension in a ClientHello
     * handshake message.
     */
    private static final class CHKeyShareConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private CHKeyShareConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            if (shc.handshakeExtensions.containsKey(SSLExtension.CH_KEY_SHARE)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "The key_share extension has been loaded");
                }
                return;
            }

            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(SSLExtension.CH_KEY_SHARE)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable key_share extension");
                }
                return;     // ignore the extension
            }

            // Parse the extension
            CHKeyShareSpec spec;
            try {
                spec = new CHKeyShareSpec(buffer);
            } catch (IOException ioe) {
                throw shc.conContext.fatal(Alert.UNEXPECTED_MESSAGE, ioe);
            }

            List<SSLCredentials> credentials = new LinkedList<>();
            for (KeyShareEntry entry : spec.clientShares) {
                NamedGroup ng = NamedGroup.valueOf(entry.namedGroupId);
                if (ng == null || !SupportedGroups.isActivatable(
                        shc.algorithmConstraints, ng)) {
                    if (SSLLogger.isOn &&
                            SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.fine(
                                "Ignore unsupported named group: " +
                                NamedGroup.nameOf(entry.namedGroupId));
                    }
                    continue;
                }

                if (ng.type == NamedGroupType.NAMED_GROUP_ECDHE) {
                    try {
                        ECDHECredentials ecdhec =
                            ECDHECredentials.valueOf(ng, entry.keyExchange);
                        if (ecdhec != null) {
                            if (shc.algorithmConstraints != null &&
                                    !shc.algorithmConstraints.permits(
                                            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                                            ecdhec.popPublicKey)) {
                                if (SSLLogger.isOn &&
                                        SSLLogger.isOn("ssl,handshake")) {
                                    SSLLogger.warning(
                                            "ECDHE key share entry does not " +
                                            "comply to algorithm constraints");
                                }
                            } else {
                                credentials.add(ecdhec);
                            }
                        }
                    } catch (IOException | GeneralSecurityException ex) {
                        if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                            SSLLogger.warning(
                                    "Cannot decode named group: " +
                                    NamedGroup.nameOf(entry.namedGroupId));
                        }
                    }
                } else if (ng.type == NamedGroupType.NAMED_GROUP_FFDHE) {
                    try {
                        DHECredentials dhec =
                                DHECredentials.valueOf(ng, entry.keyExchange);
                        if (dhec != null) {
                            if (shc.algorithmConstraints != null &&
                                    !shc.algorithmConstraints.permits(
                                            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                                            dhec.popPublicKey)) {
                                if (SSLLogger.isOn &&
                                        SSLLogger.isOn("ssl,handshake")) {
                                    SSLLogger.warning(
                                            "DHE key share entry does not " +
                                            "comply to algorithm constraints");
                                }
                            } else {
                                credentials.add(dhec);
                            }
                        }
                    } catch (IOException | GeneralSecurityException ex) {
                        if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                            SSLLogger.warning(
                                    "Cannot decode named group: " +
                                    NamedGroup.nameOf(entry.namedGroupId));
                        }
                    }
                }
            }

            if (!credentials.isEmpty()) {
                shc.handshakeCredentials.addAll(credentials);
            } else {
                // New handshake credentials are required from the client side.
                shc.handshakeProducers.put(
                        SSLHandshake.HELLO_RETRY_REQUEST.id,
                        SSLHandshake.HELLO_RETRY_REQUEST);
            }

            // update the context
            shc.handshakeExtensions.put(SSLExtension.CH_KEY_SHARE, spec);
        }
    }

    /**
     * The key share entry used in ServerHello "key_share" extensions.
     */
    static final class SHKeyShareSpec implements SSLExtensionSpec {
        final KeyShareEntry serverShare;

        SHKeyShareSpec(KeyShareEntry serverShare) {
            this.serverShare = serverShare;
        }

        private SHKeyShareSpec(ByteBuffer buffer) throws IOException {
            // struct {
            //      KeyShareEntry server_share;
            // } KeyShareServerHello;
            if (buffer.remaining() < 5) {       // 5: minimal server_share
                throw new SSLProtocolException(
                    "Invalid key_share extension: " +
                    "insufficient data (length=" + buffer.remaining() + ")");
            }

            int namedGroupId = Record.getInt16(buffer);
            byte[] keyExchange = Record.getBytes16(buffer);

            if (buffer.hasRemaining()) {
                throw new SSLProtocolException(
                    "Invalid key_share extension: unknown extra data");
            }

            this.serverShare = new KeyShareEntry(namedGroupId, keyExchange);
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"server_share\": '{'\n" +
                "  \"named group\": {0}\n" +
                "  \"key_exchange\": '{'\n" +
                "{1}\n" +
                "  '}'\n" +
                "'}',", Locale.ENGLISH);

            HexDumpEncoder hexEncoder = new HexDumpEncoder();
            Object[] messageFields = {
                NamedGroup.nameOf(serverShare.namedGroupId),
                Utilities.indent(
                        hexEncoder.encode(serverShare.keyExchange), "    ")
            };

            return messageFormat.format(messageFields);
        }
    }

    private static final class SHKeyShareStringizer implements SSLStringizer {
        @Override
        public String toString(ByteBuffer buffer) {
            try {
                return (new SHKeyShareSpec(buffer)).toString();
            } catch (IOException ioe) {
                // For debug logging only, so please swallow exceptions.
                return ioe.getMessage();
            }
        }
    }

    /**
     * Network data producer of the extension in a ServerHello
     * handshake message.
     */
    private static final class SHKeyShareProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private SHKeyShareProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in client side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // In response to key_share request only
            CHKeyShareSpec kss =
                    (CHKeyShareSpec)shc.handshakeExtensions.get(
                            SSLExtension.CH_KEY_SHARE);
            if (kss == null) {
                // Unlikely, no key_share extension requested.
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                            "Ignore, no client key_share extension");
                }
                return null;
            }

            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(SSLExtension.SH_KEY_SHARE)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                            "Ignore, no available server key_share extension");
                }
                return null;
            }

            // use requested key share entries
            if ((shc.handshakeCredentials == null) ||
                    shc.handshakeCredentials.isEmpty()) {
                // Unlikely, HelloRetryRequest should be used ealier.
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                            "No available client key share entries");
                }
                return null;
            }

            KeyShareEntry keyShare = null;
            for (SSLCredentials cd : shc.handshakeCredentials) {
                NamedGroup ng = null;
                if (cd instanceof ECDHECredentials) {
                    ng = ((ECDHECredentials)cd).namedGroup;
                } else if (cd instanceof DHECredentials) {
                    ng = ((DHECredentials)cd).namedGroup;
                }

                if (ng == null) {
                    continue;
                }

                SSLKeyExchange ke = SSLKeyExchange.valueOf(ng);
                if (ke == null) {
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.warning(
                            "No key exchange for named group " + ng.name);
                    }
                    continue;
                }

                SSLPossession[] poses = ke.createPossessions(shc);
                for (SSLPossession pos : poses) {
                    if (!(pos instanceof ECDHEPossession) &&
                            !(pos instanceof DHEPossession)) {
                        // May need more possesion types in the future.
                        continue;
                    }

                    // update the context
                    shc.handshakeKeyExchange = ke;
                    shc.handshakePossessions.add(pos);
                    keyShare = new KeyShareEntry(ng.id, pos.encode());
                    break;
                }

                if (keyShare != null) {
                    for (Map.Entry<Byte, HandshakeProducer> me :
                            ke.getHandshakeProducers(shc)) {
                        shc.handshakeProducers.put(
                                me.getKey(), me.getValue());
                    }

                    // We have got one! Don't forgor to break.
                    break;
                }
            }

            if (keyShare == null) {
                // Unlikely, HelloRetryRequest should be used instead ealier.
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                            "No available server key_share extension");
                }
                return null;
            }

            byte[] extData = keyShare.getEncoded();

            // update the context
            SHKeyShareSpec spec = new SHKeyShareSpec(keyShare);
            shc.handshakeExtensions.put(SSLExtension.SH_KEY_SHARE, spec);

            return extData;
        }
    }

    /**
     * Network data consumer of the extension in a ServerHello
     * handshake message.
     */
    private static final class SHKeyShareConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private SHKeyShareConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            // Happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;
            if (chc.clientRequestedNamedGroups == null ||
                    chc.clientRequestedNamedGroups.isEmpty()) {
                // No supported groups.
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unexpected key_share extension in ServerHello");
            }

            // Is it a supported and enabled extension?
            if (!chc.sslConfig.isAvailable(SSLExtension.SH_KEY_SHARE)) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unsupported key_share extension in ServerHello");
            }

            // Parse the extension
            SHKeyShareSpec spec;
            try {
                spec = new SHKeyShareSpec(buffer);
            } catch (IOException ioe) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE, ioe);
            }

            KeyShareEntry keyShare = spec.serverShare;
            NamedGroup ng = NamedGroup.valueOf(keyShare.namedGroupId);
            if (ng == null || !SupportedGroups.isActivatable(
                    chc.algorithmConstraints, ng)) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unsupported named group: " +
                        NamedGroup.nameOf(keyShare.namedGroupId));
            }

            SSLKeyExchange ke = SSLKeyExchange.valueOf(ng);
            if (ke == null) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "No key exchange for named group " + ng.name);
            }

            SSLCredentials credentials = null;
            if (ng.type == NamedGroupType.NAMED_GROUP_ECDHE) {
                try {
                    ECDHECredentials ecdhec =
                            ECDHECredentials.valueOf(ng, keyShare.keyExchange);
                    if (ecdhec != null) {
                        if (chc.algorithmConstraints != null &&
                                !chc.algorithmConstraints.permits(
                                        EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                                        ecdhec.popPublicKey)) {
                            throw chc.conContext.fatal(Alert.INSUFFICIENT_SECURITY,
                                    "ECDHE key share entry does not " +
                                    "comply to algorithm constraints");
                        } else {
                            credentials = ecdhec;
                        }
                    }
                } catch (IOException | GeneralSecurityException ex) {
                    throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                            "Cannot decode named group: " +
                            NamedGroup.nameOf(keyShare.namedGroupId));
                }
            } else if (ng.type == NamedGroupType.NAMED_GROUP_FFDHE) {
                try {
                    DHECredentials dhec =
                            DHECredentials.valueOf(ng, keyShare.keyExchange);
                    if (dhec != null) {
                        if (chc.algorithmConstraints != null &&
                                !chc.algorithmConstraints.permits(
                                        EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                                        dhec.popPublicKey)) {
                            throw chc.conContext.fatal(Alert.INSUFFICIENT_SECURITY,
                                    "DHE key share entry does not " +
                                    "comply to algorithm constraints");
                        } else {
                            credentials = dhec;
                        }
                    }
                } catch (IOException | GeneralSecurityException ex) {
                    throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                            "Cannot decode named group: " +
                            NamedGroup.nameOf(keyShare.namedGroupId));
                }
            } else {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unsupported named group: " +
                        NamedGroup.nameOf(keyShare.namedGroupId));
            }

            if (credentials == null) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unsupported named group: " + ng.name);
            }

            // update the context
            chc.handshakeKeyExchange = ke;
            chc.handshakeCredentials.add(credentials);
            chc.handshakeExtensions.put(SSLExtension.SH_KEY_SHARE, spec);
        }
    }

    /**
     * The absence processing if the extension is not present in
     * the ServerHello handshake message.
     */
    private static final class SHKeyShareAbsence implements HandshakeAbsence {
        @Override
        public void absent(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // Cannot use the previous requested key shares any more.
            if (SSLLogger.isOn && SSLLogger.isOn("handshake")) {
                SSLLogger.fine(
                        "No key_share extension in ServerHello, " +
                        "cleanup the key shares if necessary");
            }
            chc.handshakePossessions.clear();
        }
    }

    /**
     * The key share entry used in HelloRetryRequest "key_share" extensions.
     */
    static final class HRRKeyShareSpec implements SSLExtensionSpec {
        final int selectedGroup;

        HRRKeyShareSpec(NamedGroup serverGroup) {
            this.selectedGroup = serverGroup.id;
        }

        private HRRKeyShareSpec(ByteBuffer buffer) throws IOException {
            // struct {
            //     NamedGroup selected_group;
            // } KeyShareHelloRetryRequest;
            if (buffer.remaining() != 2) {
                throw new SSLProtocolException(
                    "Invalid key_share extension: " +
                    "improper data (length=" + buffer.remaining() + ")");
            }

            this.selectedGroup = Record.getInt16(buffer);
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"selected group\": '['{0}']'", Locale.ENGLISH);

            Object[] messageFields = {
                    NamedGroup.nameOf(selectedGroup)
                };
            return messageFormat.format(messageFields);
        }
    }

    private static final class HRRKeyShareStringizer implements SSLStringizer {
        @Override
        public String toString(ByteBuffer buffer) {
            try {
                return (new HRRKeyShareSpec(buffer)).toString();
            } catch (IOException ioe) {
                // For debug logging only, so please swallow exceptions.
                return ioe.getMessage();
            }
        }
    }

    /**
     * Network data producer of the extension in a HelloRetryRequest
     * handshake message.
     */
    private static final
            class HRRKeyShareProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private HRRKeyShareProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext) context;

            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(SSLExtension.HRR_KEY_SHARE)) {
                throw shc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unsupported key_share extension in HelloRetryRequest");
            }

            if (shc.clientRequestedNamedGroups == null ||
                    shc.clientRequestedNamedGroups.isEmpty()) {
                // No supported groups.
                throw shc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unexpected key_share extension in HelloRetryRequest");
            }

            NamedGroup selectedGroup = null;
            for (NamedGroup ng : shc.clientRequestedNamedGroups) {
                if (SupportedGroups.isActivatable(
                        shc.algorithmConstraints, ng)) {
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.fine(
                                "HelloRetryRequest selected named group: " +
                                ng.name);
                    }

                    selectedGroup = ng;
                    break;
                }
            }

            if (selectedGroup == null) {
                throw shc.conContext.fatal(
                        Alert.UNEXPECTED_MESSAGE, "No common named group");
            }

            byte[] extdata = new byte[] {
                    (byte)((selectedGroup.id >> 8) & 0xFF),
                    (byte)(selectedGroup.id & 0xFF)
                };

            // update the context
            shc.serverSelectedNamedGroup = selectedGroup;
            shc.handshakeExtensions.put(SSLExtension.HRR_KEY_SHARE,
                    new HRRKeyShareSpec(selectedGroup));

            return extdata;
        }
    }

    /**
     * Network data producer of the extension for stateless
     * HelloRetryRequest reconstruction.
     */
    private static final
            class HRRKeyShareReproducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private HRRKeyShareReproducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext) context;

            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(SSLExtension.HRR_KEY_SHARE)) {
                throw shc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unsupported key_share extension in HelloRetryRequest");
            }

            CHKeyShareSpec spec = (CHKeyShareSpec)shc.handshakeExtensions.get(
                    SSLExtension.CH_KEY_SHARE);
            if (spec != null && spec.clientShares != null &&
                    spec.clientShares.size() == 1) {
                int namedGroupId = spec.clientShares.get(0).namedGroupId;

                byte[] extdata = new byte[] {
                        (byte)((namedGroupId >> 8) & 0xFF),
                        (byte)(namedGroupId & 0xFF)
                    };

                return extdata;
            }

            return null;
        }
    }

    /**
     * Network data consumer of the extension in a HelloRetryRequest
     * handshake message.
     */
    private static final
            class HRRKeyShareConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private HRRKeyShareConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            // The producing happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!chc.sslConfig.isAvailable(SSLExtension.HRR_KEY_SHARE)) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unsupported key_share extension in HelloRetryRequest");
            }

            if (chc.clientRequestedNamedGroups == null ||
                    chc.clientRequestedNamedGroups.isEmpty()) {
                // No supported groups.
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unexpected key_share extension in HelloRetryRequest");
            }

            // Parse the extension
            HRRKeyShareSpec spec;
            try {
                spec = new HRRKeyShareSpec(buffer);
            } catch (IOException ioe) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE, ioe);
            }

            NamedGroup serverGroup = NamedGroup.valueOf(spec.selectedGroup);
            if (serverGroup == null) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unsupported HelloRetryRequest selected group: " +
                                NamedGroup.nameOf(spec.selectedGroup));
            }

            if (!chc.clientRequestedNamedGroups.contains(serverGroup)) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unexpected HelloRetryRequest selected group: " +
                                serverGroup.name);
            }

            // update the context

            // When sending the new ClientHello, the client MUST replace the
            // original "key_share" extension with one containing only a new
            // KeyShareEntry for the group indicated in the selected_group
            // field of the triggering HelloRetryRequest.
            //
            chc.serverSelectedNamedGroup = serverGroup;
            chc.handshakeExtensions.put(SSLExtension.HRR_KEY_SHARE, spec);
        }
    }
}
