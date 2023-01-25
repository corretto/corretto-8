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
/*
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMURIDereferencer.java 1854026 2019-02-21 09:30:01Z coheigea $
 */
package org.jcp.xml.dsig.internal.dom;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.sun.org.apache.xml.internal.security.Init;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolver;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.*;
import java.net.URI;

/**
 * DOM-based implementation of URIDereferencer.
 *
 */
public final class DOMURIDereferencer implements URIDereferencer {

    static final URIDereferencer INSTANCE = new DOMURIDereferencer();

    private DOMURIDereferencer() {
        // need to call com.sun.org.apache.xml.internal.security.Init.init()
        // before calling any apache security code
        Init.init();
    }

    public Data dereference(URIReference uriRef, XMLCryptoContext context)
        throws URIReferenceException {

        if (uriRef == null) {
            throw new NullPointerException("uriRef cannot be null");
        }
        if (context == null) {
            throw new NullPointerException("context cannot be null");
        }

        DOMURIReference domRef = (DOMURIReference) uriRef;
        Attr uriAttr = (Attr) domRef.getHere();
        String uri = uriRef.getURI();
        DOMCryptoContext dcc = (DOMCryptoContext) context;
        String baseURI = context.getBaseURI();

        boolean secVal = Utils.secureValidation(context);

        if (secVal) {
            try {
                if (Policy.restrictReferenceUriScheme(uri)) {
                    throw new URIReferenceException(
                            "URI " + uri + " is forbidden when secure validation is enabled");
                }

                if (uri != null && !uri.isEmpty() && uri.charAt(0) != '#' && URI.create(uri).getScheme() == null) {
                    // beseURI will be used to dereference a relative uri
                    try {
                        if (Policy.restrictReferenceUriScheme(baseURI)) {
                            throw new URIReferenceException(
                                    "Base URI " + baseURI + " is forbidden when secure validation is enabled");
                        }
                    } catch (IllegalArgumentException e) { // thrown by Policy.restrictReferenceUriScheme
                        throw new URIReferenceException("Invalid base URI " + baseURI);
                    }
                }
            } catch (IllegalArgumentException e) { // thrown by Policy.restrictReferenceUriScheme or URI.create
                throw new URIReferenceException("Invalid URI " + uri);
            }
        }

        // Check if same-document URI and already registered on the context
        if (uri != null && uri.length() != 0 && uri.charAt(0) == '#') {
            String id = uri.substring(1);

            if (id.startsWith("xpointer(id(")) {
                int i1 = id.indexOf('\'');
                int i2 = id.indexOf('\'', i1+1);
                id = id.substring(i1+1, i2);
            }

            // check if element is registered by Id
            Node referencedElem = uriAttr.getOwnerDocument().getElementById(id);
            if (referencedElem == null) {
               // see if element is registered in DOMCryptoContext
               referencedElem = dcc.getElementById(id);
            }
            if (referencedElem != null) {
                if (secVal && Policy.restrictDuplicateIds()) {
                    Element start = referencedElem.getOwnerDocument().getDocumentElement();
                    if (!XMLUtils.protectAgainstWrappingAttack(start, (Element)referencedElem, id)) {
                        String error = "Multiple Elements with the same ID "
                            + id + " detected when secure validation"
                            + " is enabled";
                        throw new URIReferenceException(error);
                    }
                }

                XMLSignatureInput result = new XMLSignatureInput(referencedElem);
                result.setSecureValidation(secVal);
                if (!uri.substring(1).startsWith("xpointer(id(")) {
                    result.setExcludeComments(true);
                }

                result.setMIMEType("text/xml");
                if (baseURI != null && baseURI.length() > 0) {
                    result.setSourceURI(baseURI.concat(uriAttr.getNodeValue()));
                } else {
                    result.setSourceURI(uriAttr.getNodeValue());
                }
                return new ApacheNodeSetData(result);
            }
        }

        try {
            ResourceResolver apacheResolver =
                ResourceResolver.getInstance(uriAttr, baseURI, secVal);
            XMLSignatureInput in = apacheResolver.resolve(uriAttr, baseURI, secVal);
            if (in.isOctetStream()) {
                return new ApacheOctetStreamData(in);
            } else {
                return new ApacheNodeSetData(in);
            }
        } catch (Exception e) {
            throw new URIReferenceException(e);
        }
    }
}
