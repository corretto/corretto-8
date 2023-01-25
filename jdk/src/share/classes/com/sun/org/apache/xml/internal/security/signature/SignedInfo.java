/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.signature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithm;
import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.Canonicalizer;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.transforms.params.InclusiveNamespaces;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Handles {@code &lt;ds:SignedInfo&gt;} elements
 * This {@code SignedInfo} element includes the canonicalization algorithm,
 * a signature algorithm, and one or more references.
 *
 */
public class SignedInfo extends Manifest {

    /** Field signatureAlgorithm */
    private SignatureAlgorithm signatureAlgorithm;

    /** Field c14nizedBytes           */
    private byte[] c14nizedBytes;

    private Element c14nMethod;
    private Element signatureMethod;

    /**
     * Overwrites {@link Manifest#addDocument} because it creates another
     * Element.
     *
     * @param doc the {@link Document} in which {@code XMLsignature} will
     *    be placed
     * @throws XMLSecurityException
     */
    public SignedInfo(Document doc) throws XMLSecurityException {
        this(doc, XMLSignature.ALGO_ID_SIGNATURE_DSA,
             Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS);
    }

    /**
     * Constructs {@link SignedInfo} using given Canonicalization algorithm and
     * Signature algorithm.
     *
     * @param doc {@code SignedInfo} is placed in this document
     * @param signatureMethodURI URI representation of the Digest and
     *    Signature algorithm
     * @param canonicalizationMethodURI URI representation of the
     *    Canonicalization method
     * @throws XMLSecurityException
     */
    public SignedInfo(
        Document doc, String signatureMethodURI, String canonicalizationMethodURI
    ) throws XMLSecurityException {
        this(doc, signatureMethodURI, 0, canonicalizationMethodURI);
    }

    /**
     * Constructor SignedInfo
     *
     * @param doc {@code SignedInfo} is placed in this document
     * @param signatureMethodURI URI representation of the Digest and
     *    Signature algorithm
     * @param hMACOutputLength
     * @param canonicalizationMethodURI URI representation of the
     *    Canonicalization method
     * @throws XMLSecurityException
     */
    public SignedInfo(
        Document doc, String signatureMethodURI,
        int hMACOutputLength, String canonicalizationMethodURI
    ) throws XMLSecurityException {
        super(doc);

        c14nMethod =
            XMLUtils.createElementInSignatureSpace(getDocument(), Constants._TAG_CANONICALIZATIONMETHOD);

        c14nMethod.setAttributeNS(null, Constants._ATT_ALGORITHM, canonicalizationMethodURI);
        appendSelf(c14nMethod);
        addReturnToSelf();

        if (hMACOutputLength > 0) {
            this.signatureAlgorithm =
                new SignatureAlgorithm(getDocument(), signatureMethodURI, hMACOutputLength);
        } else {
            this.signatureAlgorithm = new SignatureAlgorithm(getDocument(), signatureMethodURI);
        }

        signatureMethod = this.signatureAlgorithm.getElement();
        appendSelf(signatureMethod);
        addReturnToSelf();
    }

    /**
     * @param doc
     * @param signatureMethodElem
     * @param canonicalizationMethodElem
     * @throws XMLSecurityException
     */
    public SignedInfo(
        Document doc, Element signatureMethodElem, Element canonicalizationMethodElem
    ) throws XMLSecurityException {
        super(doc);
        // Check this?
        this.c14nMethod = canonicalizationMethodElem;
        appendSelf(c14nMethod);
        addReturnToSelf();

        this.signatureAlgorithm =
            new SignatureAlgorithm(signatureMethodElem, null);

        signatureMethod = this.signatureAlgorithm.getElement();
        appendSelf(signatureMethod);

        addReturnToSelf();
    }

    /**
     * Build a {@link SignedInfo} from an {@link Element}
     *
     * @param element {@code SignedInfo}
     * @param baseURI the URI of the resource where the XML instance was stored
     * @throws XMLSecurityException
     * @see <A HREF="http://lists.w3.org/Archives/Public/w3c-ietf-xmldsig/2001OctDec/0033.html">
     * Question</A>
     * @see <A HREF="http://lists.w3.org/Archives/Public/w3c-ietf-xmldsig/2001OctDec/0054.html">
     * Answer</A>
     */
    public SignedInfo(Element element, String baseURI) throws XMLSecurityException {
        this(element, baseURI, true);
    }

    /**
     * Build a {@link SignedInfo} from an {@link Element}
     *
     * @param element {@code SignedInfo}
     * @param baseURI the URI of the resource where the XML instance was stored
     * @param secureValidation whether secure validation is enabled or not
     * @throws XMLSecurityException
     * @see <A HREF="http://lists.w3.org/Archives/Public/w3c-ietf-xmldsig/2001OctDec/0033.html">
     * Question</A>
     * @see <A HREF="http://lists.w3.org/Archives/Public/w3c-ietf-xmldsig/2001OctDec/0054.html">
     * Answer</A>
     */
    public SignedInfo(
        Element element, String baseURI, boolean secureValidation
    ) throws XMLSecurityException {
        // Parse the Reference children and Id attribute in the Manifest
        super(reparseSignedInfoElem(element, secureValidation), baseURI, secureValidation);

        c14nMethod = XMLUtils.getNextElement(element.getFirstChild());
        signatureMethod = XMLUtils.getNextElement(c14nMethod.getNextSibling());
        this.signatureAlgorithm =
            new SignatureAlgorithm(signatureMethod, this.getBaseURI(), secureValidation);
    }

    private static Element reparseSignedInfoElem(Element element, boolean secureValidation)
        throws XMLSecurityException {
        /*
         * If a custom canonicalizationMethod is used, canonicalize
         * ds:SignedInfo, reparse it into a new document
         * and replace the original not-canonicalized ds:SignedInfo by
         * the re-parsed canonicalized one.
         */
        Element c14nMethod = XMLUtils.getNextElement(element.getFirstChild());
        String c14nMethodURI =
            c14nMethod.getAttributeNS(null, Constants._ATT_ALGORITHM);
        if (!(c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS) ||
            c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS) ||
            c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS) ||
            c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS) ||
            c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS) ||
            c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N11_WITH_COMMENTS))) {
            // the c14n is not a secure one and can rewrite the URIs or like
            // so reparse the SignedInfo to be sure
            try {
                Canonicalizer c14nizer =
                    Canonicalizer.getInstance(c14nMethodURI);
                c14nizer.setSecureValidation(secureValidation);

                byte[] c14nizedBytes = c14nizer.canonicalizeSubtree(element);
                javax.xml.parsers.DocumentBuilder db =
                    XMLUtils.createDocumentBuilder(false, secureValidation);
                try (InputStream is = new ByteArrayInputStream(c14nizedBytes)) {
                    Document newdoc = db.parse(is);
                    Node imported = element.getOwnerDocument().importNode(
                            newdoc.getDocumentElement(), true);
                    element.getParentNode().replaceChild(imported, element);
                    return (Element) imported;
                }
            } catch (ParserConfigurationException ex) {
                throw new XMLSecurityException(ex);
            } catch (IOException ex) {
                throw new XMLSecurityException(ex);
            } catch (SAXException ex) {
                throw new XMLSecurityException(ex);
            }
        }
        return element;
    }

    /**
     * Tests core validation process
     *
     * @return true if verification was successful
     * @throws MissingResourceFailureException
     * @throws XMLSecurityException
     */
    public boolean verify()
        throws MissingResourceFailureException, XMLSecurityException {
        return super.verifyReferences(false);
    }

    /**
     * Tests core validation process
     *
     * @param followManifests defines whether the verification process has to verify referenced {@code ds:Manifest}s, too
     * @return true if verification was successful
     * @throws MissingResourceFailureException
     * @throws XMLSecurityException
     */
    public boolean verify(boolean followManifests)
        throws MissingResourceFailureException, XMLSecurityException {
        return super.verifyReferences(followManifests);
    }

    /**
     * Returns getCanonicalizedOctetStream
     *
     * @return the canonicalization result octet stream of {@code SignedInfo} element
     * @throws CanonicalizationException
     * @throws InvalidCanonicalizerException
     * @throws XMLSecurityException
     */
    public byte[] getCanonicalizedOctetStream()
        throws CanonicalizationException, InvalidCanonicalizerException, XMLSecurityException {
        if (this.c14nizedBytes == null) {
            Canonicalizer c14nizer =
                Canonicalizer.getInstance(this.getCanonicalizationMethodURI());
            c14nizer.setSecureValidation(isSecureValidation());

            String inclusiveNamespaces = this.getInclusiveNamespaces();
            if (inclusiveNamespaces == null) {
                this.c14nizedBytes = c14nizer.canonicalizeSubtree(getElement());
            } else {
                this.c14nizedBytes = c14nizer.canonicalizeSubtree(getElement(), inclusiveNamespaces);
            }
        }

        // make defensive copy
        return this.c14nizedBytes.clone();
    }

    /**
     * Output the C14n stream to the given OutputStream.
     * @param os
     * @throws CanonicalizationException
     * @throws InvalidCanonicalizerException
     * @throws XMLSecurityException
     */
    public void signInOctetStream(OutputStream os)
        throws CanonicalizationException, InvalidCanonicalizerException, XMLSecurityException {
        if (this.c14nizedBytes == null) {
            Canonicalizer c14nizer =
                Canonicalizer.getInstance(this.getCanonicalizationMethodURI());
            c14nizer.setSecureValidation(isSecureValidation());
            c14nizer.setWriter(os);
            String inclusiveNamespaces = this.getInclusiveNamespaces();

            if (inclusiveNamespaces == null) {
                c14nizer.canonicalizeSubtree(getElement());
            } else {
                c14nizer.canonicalizeSubtree(getElement(), inclusiveNamespaces);
            }
        } else {
            try {
                os.write(this.c14nizedBytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Returns the Canonicalization method URI
     *
     * @return the Canonicalization method URI
     */
    public String getCanonicalizationMethodURI() {
        return c14nMethod.getAttributeNS(null, Constants._ATT_ALGORITHM);
    }

    /**
     * Returns the Signature method URI
     *
     * @return the Signature method URI
     */
    public String getSignatureMethodURI() {
        Element signatureElement = this.getSignatureMethodElement();

        if (signatureElement != null) {
            return signatureElement.getAttributeNS(null, Constants._ATT_ALGORITHM);
        }

        return null;
    }

    /**
     * Method getSignatureMethodElement
     * @return returns the SignatureMethod Element
     *
     */
    public Element getSignatureMethodElement() {
        return signatureMethod;
    }

    /**
     * Creates a SecretKey for the appropriate Mac algorithm based on a
     * byte[] array password.
     *
     * @param secretKeyBytes
     * @return the secret key for the SignedInfo element.
     */
    public SecretKey createSecretKey(byte[] secretKeyBytes) {
        return new SecretKeySpec(secretKeyBytes, this.signatureAlgorithm.getJCEAlgorithmString());
    }

    public SignatureAlgorithm getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * Method getBaseLocalName
     * {@inheritDoc}
     *
     */
    public String getBaseLocalName() {
        return Constants._TAG_SIGNEDINFO;
    }

    public String getInclusiveNamespaces() {
        String c14nMethodURI = getCanonicalizationMethodURI();
        if (!(c14nMethodURI.equals("http://www.w3.org/2001/10/xml-exc-c14n#") ||
            c14nMethodURI.equals("http://www.w3.org/2001/10/xml-exc-c14n#WithComments"))) {
            return null;
        }

        Element inclusiveElement = XMLUtils.getNextElement(c14nMethod.getFirstChild());

        if (inclusiveElement != null) {
            try {
                String inclusiveNamespaces =
                    new InclusiveNamespaces(
                        inclusiveElement,
                        InclusiveNamespaces.ExclusiveCanonicalizationNamespace
                    ).getInclusiveNamespaces();
                return inclusiveNamespaces;
            } catch (XMLSecurityException e) {
                return null;
            }
        }
        return null;
    }
}
