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
package com.sun.org.apache.xml.internal.security.keys.keyresolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import javax.crypto.SecretKey;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * This class is an abstract class for a child KeyInfo Element.
 *
 * If you want the your KeyResolver, at firstly you must extend this class, and register
 * as following in config.xml
 * <PRE>
 *  &lt;KeyResolver URI="http://www.w3.org/2000/09/xmldsig#KeyValue"
 *   JAVACLASS="MyPackage.MyKeyValueImpl"//gt;
 * </PRE>
 */
public abstract class KeyResolverSpi {

    /** Field properties */
    protected java.util.Map<String, String> properties;

    protected boolean globalResolver = false;

    protected boolean secureValidation;

    /**
     * Set whether secure validation is enabled or not. The default is false.
     */
    public void setSecureValidation(boolean secureValidation) {
        this.secureValidation = secureValidation;
    }

    /**
     * This method returns whether the KeyResolverSpi is able to perform the requested action.
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return whether the KeyResolverSpi is able to perform the requested action.
     */
    public boolean engineCanResolve(Element element, String baseURI, StorageResolver storage) {
        throw new UnsupportedOperationException();
    }

    /**
     * Method engineResolvePublicKey
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return resolved public key from the registered from the element.
     *
     * @throws KeyResolverException
     */
    public PublicKey engineResolvePublicKey(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method engineLookupAndResolvePublicKey
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return resolved public key from the registered from the element.
     *
     * @throws KeyResolverException
     */
    public PublicKey engineLookupAndResolvePublicKey(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        KeyResolverSpi tmp = cloneIfNeeded();
        if (!tmp.engineCanResolve(element, baseURI, storage)) {
            return null;
        }
        return tmp.engineResolvePublicKey(element, baseURI, storage);
    }

    private KeyResolverSpi cloneIfNeeded() throws KeyResolverException {
        if (globalResolver) {
            try {
                KeyResolverSpi tmp = getClass().newInstance();
                return tmp;
            } catch (InstantiationException e) {
                throw new KeyResolverException(e, "");
            } catch (IllegalAccessException e) {
                throw new KeyResolverException(e, "");
            }
        }
        return this;
    }

    /**
     * Method engineResolveCertificate
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return resolved X509Certificate key from the registered from the elements
     *
     * @throws KeyResolverException
     */
    public X509Certificate engineResolveX509Certificate(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException{
        throw new UnsupportedOperationException();
    }

    /**
     * Method engineLookupResolveX509Certificate
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return resolved X509Certificate key from the registered from the elements
     *
     * @throws KeyResolverException
     */
    public X509Certificate engineLookupResolveX509Certificate(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        KeyResolverSpi tmp = cloneIfNeeded();
        if (!tmp.engineCanResolve(element, baseURI, storage)) {
            return null;
        }
        return tmp.engineResolveX509Certificate(element, baseURI, storage);

    }
    /**
     * Method engineResolveSecretKey
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return resolved SecretKey key from the registered from the elements
     *
     * @throws KeyResolverException
     */
    public SecretKey engineResolveSecretKey(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException{
        throw new UnsupportedOperationException();
    }

    /**
     * Method engineLookupAndResolveSecretKey
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return resolved SecretKey key from the registered from the elements
     *
     * @throws KeyResolverException
     */
    public SecretKey engineLookupAndResolveSecretKey(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        KeyResolverSpi tmp = cloneIfNeeded();
        if (!tmp.engineCanResolve(element, baseURI, storage)) {
            return null;
        }
        return tmp.engineResolveSecretKey(element, baseURI, storage);
    }

    /**
     * Method engineLookupAndResolvePrivateKey
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return resolved PrivateKey key from the registered from the elements
     *
     * @throws KeyResolverException
     */
    public PrivateKey engineLookupAndResolvePrivateKey(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        // This method was added later, it has no equivalent
        // engineResolvePrivateKey() in the old API.
        // We cannot throw UnsupportedOperationException because
        // KeyResolverSpi implementations who don't know about
        // this method would stop the search too early.
        return null;
    }

    /**
     * Method engineSetProperty
     *
     * @param key
     * @param value
     */
    public void engineSetProperty(String key, String value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
    }

    /**
     * Method engineGetProperty
     *
     * @param key
     * @return obtain the property appointed by key
     */
    public String engineGetProperty(String key) {
        if (properties == null) {
            return null;
        }

        return properties.get(key);
    }

    /**
     * Method understandsProperty
     *
     * @param propertyToTest
     * @return true if understood the property
     */
    public boolean understandsProperty(String propertyToTest) {
        if (properties == null) {
            return false;
        }

        return properties.get(propertyToTest) != null;
    }

    public void setGlobalResolver(boolean globalResolver) {
        this.globalResolver = globalResolver;
    }


    /**
     * Parses a byte array and returns the parsed Element.
     *
     * @param bytes
     * @return the Document Element after parsing bytes
     * @throws KeyResolverException if something goes wrong
     */
    protected static Element getDocFromBytes(byte[] bytes, boolean secureValidation) throws KeyResolverException {
        DocumentBuilder db = null;
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            db = XMLUtils.createDocumentBuilder(false, secureValidation);
            Document doc = db.parse(is);
            return doc.getDocumentElement();
        } catch (SAXException ex) {
            throw new KeyResolverException(ex);
        } catch (IOException ex) {
            throw new KeyResolverException(ex);
        } catch (ParserConfigurationException ex) {
            throw new KeyResolverException(ex);
        }
    }

}
