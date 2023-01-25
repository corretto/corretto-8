/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import javax.net.ssl.*;
import sun.net.util.IPAddressUtil;
import sun.security.action.GetPropertyAction;

/**
 * A utility class to share the static methods.
 */
final class Utilities {
    static final char[] hexDigits = "0123456789ABCDEF".toCharArray();
    private static final String indent = "  ";
    private static final Pattern lineBreakPatern =
                Pattern.compile("\\r\\n|\\n|\\r");

    /**
     * Puts {@code hostname} into the {@code serverNames} list.
     * <P>
     * If the {@code serverNames} does not look like a legal FQDN, it will
     * not be put into the returned list.
     * <P>
     * Note that the returned list does not allow duplicated name type.
     *
     * @return a list of {@link SNIServerName}
     */
    static List<SNIServerName> addToSNIServerNameList(
            List<SNIServerName> serverNames, String hostname) {

        SNIHostName sniHostName = rawToSNIHostName(hostname);
        if (sniHostName == null) {
            return serverNames;
        }

        int size = serverNames.size();
        List<SNIServerName> sniList = (size != 0) ?
                new ArrayList<SNIServerName>(serverNames) :
                new ArrayList<SNIServerName>(1);

        boolean reset = false;
        for (int i = 0; i < size; i++) {
            SNIServerName serverName = sniList.get(i);
            if (serverName.getType() == StandardConstants.SNI_HOST_NAME) {
                sniList.set(i, sniHostName);
                if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                     SSLLogger.fine(
                        "the previous server name in SNI (" + serverName +
                        ") was replaced with (" + sniHostName + ")");
                }
                reset = true;
                break;
            }
        }

        if (!reset) {
            sniList.add(sniHostName);
        }

        return Collections.<SNIServerName>unmodifiableList(sniList);
    }

    /**
     * Converts string hostname to {@code SNIHostName}.
     * <P>
     * Note that to check whether a hostname is a valid domain name, we cannot
     * use the hostname resolved from name services.  For virtual hosting,
     * multiple hostnames may be bound to the same IP address, so the hostname
     * resolved from name services is not always reliable.
     *
     * @param  hostname
     *         the raw hostname
     * @return an instance of {@link SNIHostName}, or null if the hostname does
     *         not look like a FQDN
     */
    private static SNIHostName rawToSNIHostName(String hostname) {
        SNIHostName sniHostName = null;
        if (hostname != null && hostname.indexOf('.') > 0 &&
                !hostname.endsWith(".") &&
                !IPAddressUtil.isIPv4LiteralAddress(hostname) &&
                !IPAddressUtil.isIPv6LiteralAddress(hostname)) {

            try {
                sniHostName = new SNIHostName(hostname);
            } catch (IllegalArgumentException iae) {
                // don't bother to handle illegal host_name
                if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                     SSLLogger.fine(hostname + "\" " +
                        "is not a legal HostName for  server name indication");
                }
            }
        }

        return sniHostName;
    }

    /**
     * Return the value of the boolean System property propName.
     *
     * Note use of privileged action. Do NOT make accessible to applications.
     */
    static boolean getBooleanProperty(String propName, boolean defaultValue) {
        // if set, require value of either true or false
        String b = GetPropertyAction.privilegedGetProperty(propName);
        if (b == null) {
            return defaultValue;
        } else if (b.equalsIgnoreCase("false")) {
            return false;
        } else if (b.equalsIgnoreCase("true")) {
            return true;
        } else {
            throw new RuntimeException("Value of " + propName
                + " must either be 'true' or 'false'");
        }
    }

    static String indent(String source) {
        return Utilities.indent(source, indent);
    }

    static String indent(String source, String prefix) {
        StringBuilder builder = new StringBuilder();
        if (source == null) {
             builder.append("\n" + prefix + "<blank message>");
        } else {
            String[] lines = lineBreakPatern.split(source);
            boolean isFirst = true;
            for (String line : lines) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    builder.append("\n");
                }
                builder.append(prefix).append(line);
            }
        }

        return builder.toString();
    }

    static String toHexString(byte b) {
        return String.valueOf(hexDigits[(b >> 4) & 0x0F]) +
                String.valueOf(hexDigits[b & 0x0F]);
    }

    static String byte16HexString(int id) {
        return "0x" +
                hexDigits[(id >> 12) & 0x0F] + hexDigits[(id >> 8) & 0x0F] +
                hexDigits[(id >> 4) & 0x0F] + hexDigits[id & 0x0F];
    }

    static String toHexString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder(bytes.length * 3);
        boolean isFirst = true;
        for (byte b : bytes) {
            if (isFirst) {
                isFirst = false;
            } else {
                builder.append(' ');
            }

            builder.append(hexDigits[(b >> 4) & 0x0F]);
            builder.append(hexDigits[b & 0x0F]);
        }
        return builder.toString();
    }

    static String toHexString(long lv) {
        StringBuilder builder = new StringBuilder(128);

        boolean isFirst = true;
        do {
            if (isFirst) {
                isFirst = false;
            } else {
                builder.append(' ');
            }

            builder.append(hexDigits[(int)(lv & 0x0F)]);
            lv >>>= 4;
            builder.append(hexDigits[(int)(lv & 0x0F)]);
            lv >>>= 4;
        } while (lv != 0);
        builder.reverse();

        return builder.toString();
    }

    /**
     * Utility method to convert a BigInteger to a byte array in unsigned
     * format as needed in the handshake messages. BigInteger uses
     * 2's complement format, i.e. it prepends an extra zero if the MSB
     * is set. We remove that.
     */
    static byte[] toByteArray(BigInteger bi) {
        byte[] b = bi.toByteArray();
        if ((b.length > 1) && (b[0] == 0)) {
            int n = b.length - 1;
            byte[] newarray = new byte[n];
            System.arraycopy(b, 1, newarray, 0, n);
            b = newarray;
        }
        return b;
    }

    /**
     * Checks that {@code fromIndex} and {@code toIndex} are in
     * the range and throws an exception if they aren't.
     */
    private static void rangeCheck(int arrayLength, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException(
                    "fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        if (fromIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(fromIndex);
        }
        if (toIndex > arrayLength) {
            throw new ArrayIndexOutOfBoundsException(toIndex);
        }
    }

}
