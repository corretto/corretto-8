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
package com.sun.org.apache.xml.internal.security.encryption;

import com.sun.org.apache.xml.internal.security.keys.KeyInfo;

/**
 * EncryptedType is the abstract type from which {@code EncryptedData} and
 * {@code EncryptedKey} are derived. While these two latter element types
 * are very similar with respect to their content models, a syntactical
 * distinction is useful to processing.
 * <p>
 * Its schema definition is as follows:
 * <pre>{@code
 * <complexType name='EncryptedType' abstract='true'>
 *     <sequence>
 *         <element name='EncryptionMethod' type='xenc:EncryptionMethodType'
 *             minOccurs='0'/>
 *         <element ref='ds:KeyInfo' minOccurs='0'/>
 *         <element ref='xenc:CipherData'/>
 *         <element ref='xenc:EncryptionProperties' minOccurs='0'/>
 *     </sequence>
 *     <attribute name='Id' type='ID' use='optional'/>
 *     <attribute name='Type' type='anyURI' use='optional'/>
 *     <attribute name='MimeType' type='string' use='optional'/>
 *     <attribute name='Encoding' type='anyURI' use='optional'/>
 * </complexType>
 * }</pre>
 *
 * @author Axl Mattheus
 */
public interface EncryptedType {

    /**
     * Returns a {@code String} providing for the standard method of
     * assigning an id to the element within the document context.
     *
     * @return the id for the {@code EncryptedType}.
     */
    String getId();

    /**
     * Sets the id.
     *
     * @param id
     */
    void setId(String id);

    /**
     * Returns an {@code URI} identifying type information about the
     * plaintext form of the encrypted content. While optional, this
     * specification takes advantage of it for mandatory processing described in
     * Processing Rules: Decryption (section 4.2). If the
     * {@code EncryptedData} element contains data of Type 'element' or
     * element 'content', and replaces that data in an XML document context, it
     * is strongly recommended the Type attribute be provided. Without this
     * information, the decryptor will be unable to automatically restore the
     * XML document to its original cleartext form.
     *
     * @return the identifier for the type of information in plaintext form of
     *   encrypted content.
     */
    String getType();

    /**
     * Sets the type.
     *
     * @param type an {@code URI} identifying type information about the
     *   plaintext form of the encrypted content.
     */
    void setType(String type);

    /**
     * Returns a {@code String} which describes the media type of the data
     * which has been encrypted. The value of this attribute has values defined
     * by [MIME]. For example, if the data that is encrypted is a base64 encoded
     * PNG, the transfer Encoding may be specified as
     * 'http://www.w3.org/2000/09/xmldsig#base64' and the MimeType as
     * 'image/png'.
     * <br>
     * This attribute is purely advisory; no validation of the MimeType
     * information is required and it does not indicate the encryption
     * application must do any additional processing. Note, this information may
     * not be necessary if it is already bound to the identifier in the Type
     * attribute. For example, the Element and Content types defined in this
     * specification are always UTF-8 encoded text.
     *
     * @return the media type of the data which was encrypted.
     */
    String getMimeType();

    /**
     * Sets the mime type.
     *
     * @param type a {@code String} which describes the media type of the
     *   data which has been encrypted.
     */
    void setMimeType(String type);

    /**
     * Return an {@code URI} representing the encoding of the
     * {@code EncryptedType}.
     *
     * @return the encoding of this {@code EncryptedType}.
     */
    String getEncoding();

    /**
     * Sets the {@code URI} representing the encoding of the
     * {@code EncryptedType}.
     *
     * @param encoding
     */
    void setEncoding(String encoding);

    /**
     * Returns an {@code EncryptionMethod} that describes the encryption
     * algorithm applied to the cipher data. If the element is absent, the
     * encryption algorithm must be known by the recipient or the decryption
     * will fail.
     *
     * @return the method used to encrypt the cipher data.
     */
    EncryptionMethod getEncryptionMethod();

    /**
     * Sets the {@code EncryptionMethod} used to encrypt the cipher data.
     *
     * @param method the {@code EncryptionMethod}.
     */
    void setEncryptionMethod(EncryptionMethod method);

    /**
     * Returns the {@code ds:KeyInfo}, that carries information about the
     * key used to encrypt the data. Subsequent sections of this specification
     * define new elements that may appear as children of
     * {@code ds:KeyInfo}.
     *
     * @return information about the key that encrypted the cipher data.
     */
    KeyInfo getKeyInfo();

    /**
     * Sets the encryption key information.
     *
     * @param info the {@code ds:KeyInfo}, that carries information about
     *   the key used to encrypt the data.
     */
    void setKeyInfo(KeyInfo info);

    /**
     * Returns the {@code CipherReference} that contains the
     * {@code CipherValue} or {@code CipherReference} with the
     * encrypted data.
     *
     * @return the cipher data for the encrypted type.
     */
    CipherData getCipherData();

    /**
     * Returns additional information concerning the generation of the
     * {@code EncryptedType}.
     *
     * @return information relating to the generation of the
     *   {@code EncryptedType}.
     */
    EncryptionProperties getEncryptionProperties();

    /**
     * Sets the {@code EncryptionProperties} that supplies additional
     * information about the generation of the {@code EncryptedType}.
     *
     * @param properties
     */
    void setEncryptionProperties(EncryptionProperties properties);
}

