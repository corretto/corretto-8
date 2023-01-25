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
package com.sun.org.apache.xml.internal.security;

import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;

import com.sun.org.apache.xml.internal.security.algorithms.JCEMapper;
import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithm;
import com.sun.org.apache.xml.internal.security.c14n.Canonicalizer;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolver;
import com.sun.org.apache.xml.internal.security.transforms.Transform;
import com.sun.org.apache.xml.internal.security.utils.ElementProxy;
import com.sun.org.apache.xml.internal.security.utils.I18n;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolver;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * This class does the configuration of the library. This includes creating
 * the mapping of Canonicalization and Transform algorithms. Initialization is
 * done by calling {@link Init#init} which should be done in any static block
 * of the files of this library. We ensure that this call is only executed once.
 */
public class Init {

    /** The namespace for CONF file **/
    public static final String CONF_NS = "http://www.xmlsecurity.org/NS/#configuration";

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(Init.class);

    /** Field alreadyInitialized */
    private static boolean alreadyInitialized = false;

    /**
     * Method isInitialized
     * @return true if the library is already initialized.
     */
    public static final synchronized boolean isInitialized() {
        return Init.alreadyInitialized;
    }

    /**
     * Method init
     *
     */
    public static synchronized void init() {
        if (alreadyInitialized) {
            return;
        }

        InputStream is =
            AccessController.doPrivileged(
                (PrivilegedAction<InputStream>)
                    () -> {
                        String cfile =
                            System.getProperty("com.sun.org.apache.xml.internal.security.resource.config");
                        if (cfile == null) {
                            return null;
                        }
                        return Init.class.getResourceAsStream(cfile);
                    }
                );
        if (is == null) {
            dynamicInit();
        } else {
            fileInit(is);
        }

        alreadyInitialized = true;
    }

    /**
     * Dynamically initialise the library by registering the default algorithms/implementations
     */
    private static void dynamicInit() {
        //
        // Load the Resource Bundle - the default is the English resource bundle.
        // To load another resource bundle, call I18n.init(...) before calling this
        // method.
        //
        I18n.init("en", "US");

        LOG.debug("Registering default algorithms");

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>(){
                @Override public Void run() throws XMLSecurityException {
                    //
                    // Bind the default prefixes
                    //
                    ElementProxy.registerDefaultPrefixes();

                    //
                    // Set the default Transforms
                    //
                    Transform.registerDefaultAlgorithms();

                    //
                    // Set the default signature algorithms
                    //
                    SignatureAlgorithm.registerDefaultAlgorithms();

                    //
                    // Set the default JCE algorithms
                    //
                    JCEMapper.registerDefaultAlgorithms();

                    //
                    // Set the default c14n algorithms
                    //
                    Canonicalizer.registerDefaultAlgorithms();

                    //
                    // Register the default resolvers
                    //
                    ResourceResolver.registerDefaultResolvers();

                    //
                    // Register the default key resolvers
                    //
                    KeyResolver.registerDefaultResolvers();

                    return null;
                }
            });
        } catch (PrivilegedActionException ex) {
            XMLSecurityException xse = (XMLSecurityException)ex.getException();
            LOG.error(xse.getMessage(), xse);
            xse.printStackTrace();
        }
    }

    /**
     * Initialise the library from a configuration file
     */
    private static void fileInit(InputStream is) {
        try {
            /* read library configuration file */
            DocumentBuilder db = XMLUtils.createDocumentBuilder(false);
            Document doc = db.parse(is);
            Node config = doc.getFirstChild();
            for (; config != null; config = config.getNextSibling()) {
                if ("Configuration".equals(config.getLocalName())) {
                    break;
                }
            }
            if (config == null) {
                LOG.error("Error in reading configuration file - Configuration element not found");
                return;
            }
            for (Node el = config.getFirstChild(); el != null; el = el.getNextSibling()) {
                if (Node.ELEMENT_NODE != el.getNodeType()) {
                    continue;
                }
                String tag = el.getLocalName();
                if ("ResourceBundles".equals(tag)) {
                    Element resource = (Element)el;
                    /* configure internationalization */
                    Attr langAttr = resource.getAttributeNodeNS(null, "defaultLanguageCode");
                    Attr countryAttr = resource.getAttributeNodeNS(null, "defaultCountryCode");
                    String languageCode =
                        (langAttr == null) ? null : langAttr.getNodeValue();
                    String countryCode =
                        (countryAttr == null) ? null : countryAttr.getNodeValue();
                    I18n.init(languageCode, countryCode);
                }

                if ("CanonicalizationMethods".equals(tag)) {
                    Element[] list =
                        XMLUtils.selectNodes(el.getFirstChild(), CONF_NS, "CanonicalizationMethod");

                    for (Element element : list) {
                        String uri = element.getAttributeNS(null, "URI");
                        String javaClass =
                            element.getAttributeNS(null, "JAVACLASS");
                        try {
                            Canonicalizer.register(uri, javaClass);
                            LOG.debug("Canonicalizer.register({}, {})", uri, javaClass);
                        } catch (ClassNotFoundException e) {
                            Object exArgs[] = { uri, javaClass };
                            LOG.error(I18n.translate("algorithm.classDoesNotExist", exArgs));
                        }
                    }
                }

                if ("TransformAlgorithms".equals(tag)) {
                    Element[] tranElem =
                        XMLUtils.selectNodes(el.getFirstChild(), CONF_NS, "TransformAlgorithm");

                    for (Element element : tranElem) {
                        String uri = element.getAttributeNS(null, "URI");
                        String javaClass =
                            element.getAttributeNS(null, "JAVACLASS");
                        try {
                            Transform.register(uri, javaClass);
                            LOG.debug("Transform.register({}, {})", uri, javaClass);
                        } catch (ClassNotFoundException e) {
                            Object exArgs[] = { uri, javaClass };

                            LOG.error(I18n.translate("algorithm.classDoesNotExist", exArgs));
                        } catch (NoClassDefFoundError ex) {
                            LOG.warn("Not able to found dependencies for algorithm, I'll keep working.");
                        }
                    }
                }

                if ("JCEAlgorithmMappings".equals(tag)) {
                    Node algorithmsNode = ((Element)el).getElementsByTagName("Algorithms").item(0);
                    if (algorithmsNode != null) {
                        Element[] algorithms =
                            XMLUtils.selectNodes(algorithmsNode.getFirstChild(), CONF_NS, "Algorithm");
                        for (Element element : algorithms) {
                            String id = element.getAttributeNS(null, "URI");
                            JCEMapper.register(id, new JCEMapper.Algorithm(element));
                        }
                    }
                }

                if ("SignatureAlgorithms".equals(tag)) {
                    Element[] sigElems =
                        XMLUtils.selectNodes(el.getFirstChild(), CONF_NS, "SignatureAlgorithm");

                    for (Element sigElem : sigElems) {
                        String uri = sigElem.getAttributeNS(null, "URI");
                        String javaClass =
                            sigElem.getAttributeNS(null, "JAVACLASS");

                        /** $todo$ handle registering */

                        try {
                            SignatureAlgorithm.register(uri, javaClass);
                            LOG.debug("SignatureAlgorithm.register({}, {})", uri, javaClass);
                        } catch (ClassNotFoundException e) {
                            Object exArgs[] = { uri, javaClass };

                            LOG.error(I18n.translate("algorithm.classDoesNotExist", exArgs));
                        }
                    }
                }

                if ("ResourceResolvers".equals(tag)) {
                    Element[] resolverElem =
                        XMLUtils.selectNodes(el.getFirstChild(), CONF_NS, "Resolver");

                    for (Element element : resolverElem) {
                        String javaClass =
                            element.getAttributeNS(null, "JAVACLASS");
                        String description =
                            element.getAttributeNS(null, "DESCRIPTION");

                        if (description != null && description.length() > 0) {
                            LOG.debug("Register Resolver: {}: {}", javaClass, description);
                        } else {
                            LOG.debug("Register Resolver: {}: For unknown purposes", javaClass);
                        }
                        try {
                            ResourceResolver.register(javaClass);
                        } catch (Throwable e) {
                            LOG.warn(
                                 "Cannot register:" + javaClass
                                 + " perhaps some needed jars are not installed",
                                 e
                             );
                        }
                    }
                }

                if ("KeyResolver".equals(tag)){
                    Element[] resolverElem =
                        XMLUtils.selectNodes(el.getFirstChild(), CONF_NS, "Resolver");
                    List<String> classNames = new ArrayList<>(resolverElem.length);
                    for (Element element : resolverElem) {
                        String javaClass =
                            element.getAttributeNS(null, "JAVACLASS");
                        String description =
                            element.getAttributeNS(null, "DESCRIPTION");

                        if (description != null && description.length() > 0) {
                            LOG.debug("Register Resolver: {}: {}", javaClass, description);
                        } else {
                            LOG.debug("Register Resolver: {}: For unknown purposes", javaClass);
                        }
                        classNames.add(javaClass);
                    }
                    KeyResolver.registerClassNames(classNames);
                }


                if ("PrefixMappings".equals(tag)){
                    LOG.debug("Now I try to bind prefixes:");

                    Element[] nl =
                        XMLUtils.selectNodes(el.getFirstChild(), CONF_NS, "PrefixMapping");

                    for (Element element : nl) {
                        String namespace = element.getAttributeNS(null, "namespace");
                        String prefix = element.getAttributeNS(null, "prefix");
                        LOG.debug("Now I try to bind {} to {}", prefix, namespace);
                        ElementProxy.setDefaultPrefix(namespace, prefix);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Bad: ", e);
        }
    }

}

