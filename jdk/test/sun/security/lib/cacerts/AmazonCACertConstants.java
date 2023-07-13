/*
 * Copyright (c) 2023, Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Amazon designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class AmazonCACertConstants {
    public static final int AMAZON_CA_CERT_COUNT = 49;
    public static final Map<String, String> AMAZON_CA_FINGERPRINT_MAP = new HashMap<String, String>() {
        {
            put("accvraiz1",
                    "9A:6E:C0:12:E1:A7:DA:9D:BE:34:19:4D:47:8A:D7:C0:DB:18:22:FB:07:1D:F1:29:81:49:6E:D1:04:38:41:13");
            put("acraizfnmt-rcm",
                    "EB:C5:57:0C:29:01:8C:4D:67:B1:AA:12:7B:AF:12:F7:03:B4:61:1E:BC:17:B7:DA:B5:57:38:94:17:9B:93:FA");
            put("acraizfnmt-rcmservidoresseguros",
                    "55:41:53:B1:3D:2C:F9:DD:B7:53:BF:BE:1A:4E:0A:E0:8D:0A:A4:18:70:58:FE:60:A2:B8:62:B2:E4:B8:7B:CB");
            put("anfsecureserverrootca",
                    "FB:8F:EC:75:91:69:B9:10:6B:1E:51:16:44:C6:18:C5:13:04:37:3F:6C:06:43:08:8D:8B:EF:FD:1B:99:75:99");
            put("atostrustedroot2011",
                    "F3:56:BE:A2:44:B7:A9:1E:B3:5D:53:CA:9A:D7:86:4A:CE:01:8E:2D:35:D5:F8:F9:6D:DF:68:A6:F4:1A:A4:74");
            put("autoridaddecertificacionfirmaprofesionalcifa62634068",
                    "04:04:80:28:BF:1F:28:64:D4:8F:9A:D4:D8:32:94:36:6A:82:88:56:55:3F:3B:14:30:3F:90:14:7F:5D:40:EF");
            put("cadisigrootr2",
                    "E2:3D:4A:03:6D:7B:70:E9:F5:95:B1:42:20:79:D2:B9:1E:DF:BB:1F:B6:51:A0:63:3E:AA:8A:9D:C5:F8:07:03");
            put("certignarootca",
                    "D4:8D:3D:23:EE:DB:50:A4:59:E5:51:97:60:1C:27:77:4B:9D:7B:18:C9:4D:5A:05:95:11:A1:02:50:B9:31:68");
            put("certsignrootca",
                    "EA:A9:62:C4:FA:4A:6B:AF:EB:E4:15:19:6D:35:1C:CD:88:8D:4F:53:F3:FA:8A:E6:D7:C4:66:A9:4E:60:42:BB");
            put("certsignrootcag2",
                    "65:7C:FE:2F:A7:3F:AA:38:46:25:71:F3:32:A2:36:3A:46:FC:E7:02:09:51:71:07:02:CD:FB:B6:EE:DA:33:05");
            put("certumec-384ca",
                    "6B:32:80:85:62:53:18:AA:50:D1:73:C9:8D:8B:DA:09:D5:7E:27:41:3D:11:4C:F7:87:A0:F5:D0:6C:03:0C:F6");
            put("certumtrustednetworkca2",
                    "B6:76:F2:ED:DA:E8:77:5C:D3:6C:B0:F6:3C:D1:D4:60:39:61:F4:9E:62:65:BA:01:3A:2F:03:07:B6:D0:B8:04");
            put("certumtrustedrootca",
                    "FE:76:96:57:38:55:77:3E:37:A9:5E:7A:D4:D9:CC:96:C3:01:57:C1:5D:31:76:5B:A9:B1:57:04:E1:AE:78:FD");
            put("cfcaevroot",
                    "5C:C3:D7:8E:4E:1D:5E:45:54:7A:04:E6:87:3E:64:F9:0C:F9:53:6D:1C:CC:2E:F8:00:F3:55:C4:C5:FD:70:FD");
            put("comodocertificationauthority",
                    "0C:2C:D6:3D:F7:80:6F:A3:99:ED:E8:09:11:6B:57:5B:F8:79:89:F0:65:18:F9:80:8C:86:05:03:17:8B:AF:66");
            put("e-szignorootca2017",
                    "BE:B0:0B:30:83:9B:9B:C3:2C:32:E4:44:79:05:95:06:41:F2:64:21:B1:5E:D0:89:19:8B:51:8A:E2:EA:1B:99");
            put("emsigneccrootca-c3",
                    "BC:4D:80:9B:15:18:9D:78:DB:3E:1D:8C:F4:F9:72:6A:79:5D:A1:64:3C:A5:F1:35:8E:1D:DB:0E:DC:0D:7E:B3");
            put("emsigneccrootca-g3",
                    "86:A1:EC:BA:08:9C:4A:8D:3B:BE:27:34:C6:12:BA:34:1D:81:3E:04:3C:F9:E8:A8:62:CD:5C:57:A3:6B:BE:6B");
            put("emsignrootca-c1",
                    "12:56:09:AA:30:1D:A0:A2:49:B9:7A:82:39:CB:6A:34:21:6F:44:DC:AC:9F:39:54:B1:42:92:F2:E8:C8:60:8F");
            put("emsignrootca-g1",
                    "40:F6:AF:03:46:A9:9A:A1:CD:1D:55:5A:4E:9C:CE:62:C7:F9:63:46:03:EE:40:66:15:83:3D:C8:C8:D0:03:67");
            put("gdcatrustauthr5root",
                    "BF:FF:8F:D0:44:33:48:7D:6A:8A:A6:0C:1A:29:76:7A:9F:C2:BB:B0:5E:42:0F:71:3A:13:B9:92:89:1D:38:93");
            put("globalsignroote46",
                    "CB:B9:C4:4D:84:B8:04:3E:10:50:EA:31:A6:9F:51:49:55:D7:BF:D2:E2:C6:B4:93:01:01:9A:D6:1D:9F:50:58");
            put("globalsignrootr46",
                    "4F:A3:12:6D:8D:3A:11:D1:C4:85:5A:4F:80:7C:BA:D6:CF:91:9D:3A:5A:88:B0:3B:EA:2C:63:72:D9:3C:40:C9");
            put("globaltrust2020",
                    "9A:29:6A:51:82:D1:D4:51:A2:E3:7F:43:9B:74:DA:AF:A2:67:52:33:29:F9:0F:9A:0D:20:07:C3:34:E2:3C:9A");
            put("gtsrootr1",
                    "D9:47:43:2A:BD:E7:B7:FA:90:FC:2E:6B:59:10:1B:12:80:E0:E1:C7:E4:E4:0F:A3:C6:88:7F:FF:57:A7:F4:CF");
            put("gtsrootr2",
                    "8D:25:CD:97:22:9D:BF:70:35:6B:DA:4E:B3:CC:73:40:31:E2:4C:F0:0F:AF:CF:D3:2D:C7:6E:B5:84:1C:7E:A8");
            put("gtsrootr3",
                    "34:D8:A7:3E:E2:08:D9:BC:DB:0D:95:65:20:93:4B:4E:40:E6:94:82:59:6E:8B:6F:73:C8:42:6B:01:0A:6F:48");
            put("gtsrootr4",
                    "34:9D:FA:40:58:C5:E2:63:12:3B:39:8A:E7:95:57:3C:4E:13:13:C8:3F:E6:8F:93:55:6C:D5:E8:03:1B:3C:7D");
            put("hongkongpostrootca3",
                    "5A:2F:C0:3F:0C:83:B0:90:BB:FA:40:60:4B:09:88:44:6C:76:36:18:3D:F9:84:6E:17:10:1A:44:7F:B8:EF:D6");
            put("izenpe.com",
                    "25:30:CC:8E:98:32:15:02:BA:D9:6F:9B:1F:BA:1B:09:9E:2D:29:9E:0F:45:48:BB:91:4F:36:3B:C0:D4:53:1F");
            put("microsece-szignorootca2009",
                    "3C:5F:81:FE:A5:FA:B8:2C:64:BF:A2:EA:EC:AF:CD:E8:E0:77:FC:86:20:A7:CA:E5:37:16:3D:F3:6E:DB:F3:78");
            put("microsofteccrootcertificateauthority2017",
                    "35:8D:F3:9D:76:4A:F9:E1:B7:66:E9:C9:72:DF:35:2E:E1:5C:FA:C2:27:AF:6A:D1:D7:0E:8E:4A:6E:DC:BA:02");
            put("microsoftrsarootcertificateauthority2017",
                    "C7:41:F7:0F:4B:2A:8D:88:BF:2E:71:C1:41:22:EF:53:EF:10:EB:A0:CF:A5:E6:4C:FA:20:F4:18:85:30:73:E0");
            put("naverglobalrootcertificationauthority",
                    "88:F4:38:DC:F8:FF:D1:FA:8F:42:91:15:FF:E5:F8:2A:E1:E0:6E:0C:70:C3:75:FA:AD:71:7B:34:A4:9E:72:65");
            put("netlockarany(classgold)ftanstvny",
                    "6C:61:DA:C3:A2:DE:F0:31:50:6B:E0:36:D2:A6:FE:40:19:94:FB:D1:3D:F9:C8:D4:66:59:92:74:C4:46:EC:98");
            put("oistewisekeyglobalrootgbca",
                    "6B:9C:08:E8:6E:B0:F7:67:CF:AD:65:CD:98:B6:21:49:E5:49:4A:67:F5:84:5E:7B:D1:ED:01:9F:27:B8:6B:D6");
            put("oistewisekeyglobalrootgcca",
                    "85:60:F9:1C:36:24:DA:BA:95:70:B5:FE:A0:DB:E3:6F:F1:1A:83:23:BE:94:86:85:4F:B3:F3:4A:55:71:19:8D");
            put("secureglobalca",
                    "42:00:F5:04:3A:C8:59:0E:BB:52:7D:20:9E:D1:50:30:29:FB:CB:D4:1C:A1:B5:06:EC:27:F1:5A:DE:7D:AC:69");
            put("securesignrootca11",
                    "BF:0F:EE:FB:9E:3A:58:1A:D5:F9:E9:DB:75:89:98:57:43:D2:61:08:5C:4D:31:4F:6F:5D:72:59:AA:42:16:12");
            put("ssl.comevrootcertificationauthorityecc",
                    "22:A2:C1:F7:BD:ED:70:4C:C1:E7:01:B5:F4:08:C3:10:88:0F:E9:56:B5:DE:2A:4A:44:F9:9C:87:3A:25:A7:C8");
            put("szafirrootca2",
                    "A1:33:9D:33:28:1A:0B:56:E5:57:D3:D3:2B:1C:E7:F9:36:7E:B0:94:BD:5F:A7:2A:7E:50:04:C8:DE:D7:CA:FE");
            put("trustwaveglobalcertificationauthority",
                    "97:55:20:15:F5:DD:FC:3C:87:88:C0:06:94:45:55:40:88:94:45:00:84:F1:00:86:70:86:BC:1A:2B:B5:8D:C8");
            put("trustwaveglobaleccp256certificationauthority",
                    "94:5B:BC:82:5E:A5:54:F4:89:D1:FD:51:A7:3D:DF:2E:A6:24:AC:70:19:A0:52:05:22:5C:22:A7:8C:CF:A8:B4");
            put("trustwaveglobaleccp384certificationauthority",
                    "55:90:38:59:C8:C0:C3:EB:B8:75:9E:CE:4E:25:57:22:5F:F5:75:8B:BD:38:EB:D4:82:76:60:1E:1B:D5:80:97");
            put("tubitakkamusmsslkoksertifikasi-surum1",
                    "46:ED:C3:68:90:46:D5:3A:45:3F:B3:10:4A:B8:0D:CA:EC:65:8B:26:60:EA:16:29:DD:7E:86:79:90:64:87:16");
            put("twcaglobalrootca",
                    "59:76:90:07:F7:68:5D:0F:CD:50:87:2F:9F:95:D5:75:5A:5B:2B:45:7D:81:F3:69:2B:61:0A:98:67:2F:0E:1B");
            put("twcarootcertificationauthority",
                    "BF:D8:8F:E1:10:1C:41:AE:3E:80:1B:F8:BE:56:35:0E:E9:BA:D1:A6:B9:BD:51:5E:DC:5C:6D:5B:87:11:AC:44");
            put("ucaextendedvalidationroot",
                    "D4:3A:F9:B3:54:73:75:5C:96:84:FC:06:D7:D8:CB:70:EE:5C:28:E7:73:FB:29:4E:B4:1E:E7:17:22:92:4D:24");
            put("ucaglobalg2root",
                    "9B:EA:11:C9:76:FE:01:47:64:C1:BE:56:A6:F9:14:B5:A5:60:31:7A:BD:99:88:39:33:82:E5:16:1A:A0:49:3C");
        }
    };

    public static final HashSet<String> AMAZON_CA_EXPIRY_EXC_ENTRIES = new HashSet<String>() {
        {
            add("globalsignrootca-r2");
            add("cybertrustglobalroot");
        }
    };
}
