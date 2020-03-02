/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 * @author Sean Mullan
 * @author Steve Hanna
 *
 */
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.CRLException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Static utility methods useful for testing certificate/certpath APIs.
 */
public class CertUtils {

    private CertUtils() {}

    /**
     * Get a DER-encoded X.509 certificate from a file.
     *
     * @param certFilePath path to file containing DER-encoded certificate
     * @return the X509Certificate
     * @throws CertificateException if the certificate type is not supported
     *                              or cannot be parsed
     * @throws IOException if the file cannot be opened
     */
    public static X509Certificate getCertFromFile(String certFilePath)
            throws CertificateException, IOException {
        File certFile = new File(System.getProperty("test.src", "."),
                                 certFilePath);
        try (FileInputStream fis = new FileInputStream(certFile)) {
            return (X509Certificate)
                CertificateFactory.getInstance("X.509")
                                  .generateCertificate(fis);
        }
    }

    /**
     * Get a PEM-encoded X.509 certificate from a string.
     *
     * @param cert string containing the PEM-encoded certificate
     * @return the X509Certificate
     * @throws CertificateException if the certificate type is not supported
     *                              or cannot be parsed
     */
    public static X509Certificate getCertFromString(String cert)
            throws CertificateException {
        byte[] certBytes = cert.getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(certBytes);
        return (X509Certificate)
            CertificateFactory.getInstance("X.509").generateCertificate(bais);
     }

    /**
     * Get a DER-encoded X.509 CRL from a file.
     *
     * @param crlFilePath path to file containing DER-encoded CRL
     * @return the X509CRL
     * @throws CertificateException if the crl type is not supported
     * @throws CRLException if the crl cannot be parsed
     * @throws IOException if the file cannot be opened
     */
    public static X509CRL getCRLFromFile(String crlFilePath)
            throws CertificateException, CRLException, IOException {
        File crlFile = new File(System.getProperty("test.src", "."),
                                crlFilePath);
        try (FileInputStream fis = new FileInputStream(crlFile)) {
            return (X509CRL)
                CertificateFactory.getInstance("X.509").generateCRL(fis);
        }
    }

    /**
     * Get a PEM-encoded X.509 crl from a string.
     *
     * @param crl string containing the PEM-encoded crl
     * @return the X509CRL
     * @throws CertificateException if the crl type is not supported
     * @throws CRLException if the crl cannot be parsed
     */
    public static X509CRL getCRLFromString(String crl)
            throws CertificateException, CRLException {
        byte[] crlBytes = crl.getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(crlBytes);
        return (X509CRL)
            CertificateFactory.getInstance("X.509").generateCRL(bais);
    }

    /**
     * Read a bunch of certs from files and create a CertPath from them.
     *
     * @param fileNames an array of <code>String</code>s that are file names
     * @throws Exception on error
     */
    public static CertPath buildPath(String [] fileNames) throws Exception {
        return buildPath("", fileNames);
    }

    /**
     * Read a bunch of certs from files and create a CertPath from them.
     *
     * @param relPath relative path containing certs (must end in
     *    file.separator)
     * @param fileNames an array of <code>String</code>s that are file names
     * @throws Exception on error
     */
    public static CertPath buildPath(String relPath, String [] fileNames)
        throws Exception {
        List<X509Certificate> list = new ArrayList<X509Certificate>();
        for (int i = 0; i < fileNames.length; i++) {
            list.add(0, getCertFromFile(relPath + fileNames[i]));
        }
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        return(cf.generateCertPath(list));
    }


    /**
     * Read a bunch of certs from files and create a CertStore from them.
     *
     * @param fileNames an array of <code>String</code>s that are file names
     * @return the <code>CertStore</code> created
     * @throws Exception on error
     */
    public static CertStore createStore(String [] fileNames) throws Exception {
        return createStore("", fileNames);
    }

    /**
     * Read a bunch of certs from files and create a CertStore from them.
     *
     * @param relPath relative path containing certs (must end in
     *    file.separator)
     * @param fileNames an array of <code>String</code>s that are file names
     * @return the <code>CertStore</code> created
     * @throws Exception on error
     */
    public static CertStore createStore(String relPath, String [] fileNames)
        throws Exception {
        Set<X509Certificate> certs = new HashSet<X509Certificate>();
        for (int i = 0; i < fileNames.length; i++) {
            certs.add(getCertFromFile(relPath + fileNames[i]));
        }
        return CertStore.getInstance("Collection",
            new CollectionCertStoreParameters(certs));
    }

    /**
     * Read a bunch of CRLs from files and create a CertStore from them.
     *
     * @param fileNames an array of <code>String</code>s that are file names
     * @return the <code>CertStore</code> created
     * @throws Exception on error
     */
    public static CertStore createCRLStore(String [] fileNames)
        throws Exception {
        return createCRLStore("", fileNames);
    }

    /**
     * Read a bunch of CRLs from files and create a CertStore from them.
     *
     * @param relPath relative path containing CRLs (must end in file.separator)
     * @param fileNames an array of <code>String</code>s that are file names
     * @return the <code>CertStore</code> created
     * @throws Exception on error
     */
    public static CertStore createCRLStore(String relPath, String [] fileNames)
        throws Exception {
        Set<X509CRL> crls = new HashSet<X509CRL>();
        for (int i = 0; i < fileNames.length; i++) {
            crls.add(getCRLFromFile(relPath + fileNames[i]));
        }
        return CertStore.getInstance("Collection",
            new CollectionCertStoreParameters(crls));
    }

    /**
     * Perform a PKIX path build. On failure, throw an exception.
     *
     * @param params PKIXBuilderParameters to use in validation
     * @throws Exception on error
     */
    public static PKIXCertPathBuilderResult build(PKIXBuilderParameters params)
        throws Exception {
        CertPathBuilder builder =
            CertPathBuilder.getInstance("PKIX");
        return (PKIXCertPathBuilderResult) builder.build(params);
    }

    /**
     * Perform a PKIX validation. On failure, throw an exception.
     *
     * @param path CertPath to validate
     * @param params PKIXParameters to use in validation
     * @throws Exception on error
     */
    public static PKIXCertPathValidatorResult validate
        (CertPath path, PKIXParameters params) throws Exception {
        CertPathValidator validator =
            CertPathValidator.getInstance("PKIX");
        return (PKIXCertPathValidatorResult) validator.validate(path, params);
    }

    /*
     * Reads the entire input stream into a byte array.
     */
    private static byte[] getTotalBytes(InputStream is) throws IOException {
           byte[] buffer = new byte[8192];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
        int n;
        baos.reset();
        while ((n = is.read(buffer, 0, buffer.length)) != -1) {
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }
}
