/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.wsdl.parser;

import com.sun.istack.internal.NotNull;
import com.sun.tools.internal.ws.util.xml.XmlUtil;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaConstants;
import com.sun.tools.internal.xjc.reader.internalizer.LocatorTable;
import com.sun.xml.internal.bind.marshaller.DataWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.*;
import java.util.*;

/**
 * @author Vivek Pandey
 */
public class DOMForest {
    /**
     * To correctly feed documents to a schema parser, we need to remember
     * which documents (of the forest) were given as the root
     * documents, and which of them are read as included/imported
     * documents.
     * <p/>
     * <p/>
     * Set of system ids as strings.
     */
    protected final Set<String> rootDocuments = new HashSet<String>();

    /**
     * Contains wsdl:import(s)
     */
    protected final Set<String> externalReferences = new HashSet<String>();

    /**
     * actual data storage map&lt;SystemId,Document>.
     */
    protected final Map<String, Document> core = new HashMap<String, Document>();
    protected final ErrorReceiver errorReceiver;

    private final DocumentBuilder documentBuilder;
    private final SAXParserFactory parserFactory;

    /**
     * inlined schema elements inside wsdl:type section
     */
    protected final List<Element> inlinedSchemaElements = new ArrayList<Element>();


    /**
     * Stores location information for all the trees in this forest.
     */
    public final LocatorTable locatorTable = new LocatorTable();

    protected final EntityResolver entityResolver;
    /**
     * Stores all the outer-most &lt;jaxb:bindings> customizations.
     */
    public final Set<Element> outerMostBindings = new HashSet<Element>();

    /**
     * Schema language dependent part of the processing.
     */
    protected final InternalizationLogic logic;
    protected final WsimportOptions options;

    public DOMForest(InternalizationLogic logic, @NotNull EntityResolver entityResolver, WsimportOptions options, ErrorReceiver errReceiver) {
        this.options = options;
        this.entityResolver = entityResolver;
        this.errorReceiver = errReceiver;
        this.logic = logic;
        // secure xml processing can be switched off if input requires it
        boolean disableXmlSecurity = options == null ? false : options.disableXmlSecurity;

        DocumentBuilderFactory dbf = XmlUtil.newDocumentBuilderFactory(disableXmlSecurity);
        this.parserFactory = XmlUtil.newSAXParserFactory(disableXmlSecurity);
        try {
            this.documentBuilder = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new AssertionError(e);
        }
    }

    public List<Element> getInlinedSchemaElement() {
        return inlinedSchemaElements;
    }

    public @NotNull Document parse(InputSource source, boolean root) throws SAXException, IOException {
        if (source.getSystemId() == null)
            throw new IllegalArgumentException();
        return parse(source.getSystemId(), source, root);
    }

    /**
     * Parses an XML at the given location (
     * and XMLs referenced by it) into DOM trees
     * and stores them to this forest.
     *
     * @return the parsed DOM document object.
     */
    public Document parse(String systemId, boolean root) throws SAXException, IOException{

        systemId = normalizeSystemId(systemId);

        InputSource is = null;

        // allow entity resolver to find the actual byte stream.
        is = entityResolver.resolveEntity(null, systemId);
        if (is == null)
            is = new InputSource(systemId);
        else {
            resolvedCache.put(systemId, is.getSystemId());
            systemId=is.getSystemId();
        }

        if (core.containsKey(systemId)) {
            // this document has already been parsed. Just ignore.
            return core.get(systemId);
        }

        if(!root)
            addExternalReferences(systemId);

        // but we still use the original system Id as the key.
        return parse(systemId, is, root);
    }
    protected Map<String,String> resolvedCache = new HashMap<String,String>();

    public Map<String,String> getReferencedEntityMap() {
        return resolvedCache;
    }
    /**
     * Parses the given document and add it to the DOM forest.
     *
     * @return null if there was a parse error. otherwise non-null.
     */
    private @NotNull Document parse(String systemId, InputSource inputSource, boolean root) throws SAXException, IOException{
        Document dom = documentBuilder.newDocument();

        systemId = normalizeSystemId(systemId);

        // put into the map before growing a tree, to
        // prevent recursive reference from causing infinite loop.
        core.put(systemId, dom);

        dom.setDocumentURI(systemId);
        if (root)
            rootDocuments.add(systemId);

        try {
            XMLReader reader = createReader(dom);

            InputStream is = null;
            if(inputSource.getByteStream() == null){
                inputSource = entityResolver.resolveEntity(null, systemId);
            }
            reader.parse(inputSource);
            Element doc = dom.getDocumentElement();
            if (doc == null) {
                return null;
            }
            NodeList schemas = doc.getElementsByTagNameNS(SchemaConstants.NS_XSD, "schema");
            for (int i = 0; i < schemas.getLength(); i++) {
                inlinedSchemaElements.add((Element) schemas.item(i));
            }
        } catch (ParserConfigurationException e) {
            errorReceiver.error(e);
            throw new SAXException(e.getMessage());
        }
        resolvedCache.put(systemId, dom.getDocumentURI());
        return dom;
    }

    public void addExternalReferences(String ref) {
        if (!externalReferences.contains(ref))
            externalReferences.add(ref);
    }


    public Set<String> getExternalReferences() {
        return externalReferences;
    }



    public interface Handler extends ContentHandler {
        /**
         * Gets the DOM that was built.
         */
        public Document getDocument();
    }

    /**
         * Returns a {@link org.xml.sax.XMLReader} to parse a document into this DOM forest.
         * <p/>
         * This version requires that the DOM object to be created and registered
         * to the map beforehand.
         */
    private XMLReader createReader(Document dom) throws SAXException, ParserConfigurationException {
        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        DOMBuilder dombuilder = new DOMBuilder(dom, locatorTable, outerMostBindings);
        try {
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", dombuilder);
        } catch(SAXException e) {
            errorReceiver.debug(e.getMessage());
        }

        ContentHandler handler = new WhitespaceStripper(dombuilder, errorReceiver, entityResolver);
        handler = new VersionChecker(handler, errorReceiver, entityResolver);

        // insert the reference finder so that
        // included/imported schemas will be also parsed
        XMLFilterImpl f = logic.createExternalReferenceFinder(this);
        f.setContentHandler(handler);
        if (errorReceiver != null)
            f.setErrorHandler(errorReceiver);
        f.setEntityResolver(entityResolver);

        reader.setContentHandler(f);
        if (errorReceiver != null)
            reader.setErrorHandler(errorReceiver);
        reader.setEntityResolver(entityResolver);
        return reader;
    }

    private String normalizeSystemId(String systemId) {
        try {
            systemId = new URI(systemId).normalize().toString();
        } catch (URISyntaxException e) {
            // leave the system ID untouched. In my experience URI is often too strict
        }
        return systemId;
    }

    boolean isExtensionMode() {
        return options.isExtensionMode();
    }


    /**
     * Gets the DOM tree associated with the specified system ID,
     * or null if none is found.
     */
    public Document get(String systemId) {
        Document doc = core.get(systemId);

        if (doc == null && systemId.startsWith("file:/") && !systemId.startsWith("file://")) {
            // As of JDK1.4, java.net.URL.toExternal method returns URLs like
            // "file:/abc/def/ghi" which is an incorrect file protocol URL according to RFC1738.
            // Some other correctly functioning parts return the correct URLs ("file:///abc/def/ghi"),
            // and this descripancy breaks DOM look up by system ID.

            // this extra check solves this problem.
            doc = core.get("file://" + systemId.substring(5));
        }

        if (doc == null && systemId.startsWith("file:")) {
            // on Windows, filenames are case insensitive.
            // perform case-insensitive search for improved user experience
            String systemPath = getPath(systemId);
            for (String key : core.keySet()) {
                if (key.startsWith("file:") && getPath(key).equalsIgnoreCase(systemPath)) {
                    doc = core.get(key);
                    break;
                }
            }
        }

        return doc;
    }

    /**
     * Strips off the leading 'file:///' portion from an URL.
     */
    private String getPath(String key) {
        key = key.substring(5); // skip 'file:'
        while (key.length() > 0 && key.charAt(0) == '/')
            key = key.substring(1);
        return key;
    }

    /**
     * Gets all the system IDs of the documents.
     */
    public String[] listSystemIDs() {
        return core.keySet().toArray(new String[core.keySet().size()]);
    }

    /**
     * Gets the system ID from which the given DOM is parsed.
     * <p/>
     * Poor-man's base URI.
     */
    public String getSystemId(Document dom) {
        for (Map.Entry<String, Document> e : core.entrySet()) {
            if (e.getValue() == dom)
                return e.getKey();
        }
        return null;
    }

    /**
     * Gets the first one (which is more or less random) in {@link #rootDocuments}.
     */
    public String getFirstRootDocument() {
        if(rootDocuments.isEmpty()) return null;
        return rootDocuments.iterator().next();
    }

    public Set<String> getRootDocuments() {
        return rootDocuments;
    }

    /**
     * Dumps the contents of the forest to the specified stream.
     * <p/>
     * This is a debug method. As such, error handling is sloppy.
     */
    public void dump(OutputStream out) throws IOException {
        try {
            // create identity transformer
            // secure xml processing can be switched off if input requires it
            boolean secureProcessingEnabled = options == null || !options.disableXmlSecurity;
            TransformerFactory tf = XmlUtil.newTransformerFactory(secureProcessingEnabled);
            Transformer it = tf.newTransformer();

            for (Map.Entry<String, Document> e : core.entrySet()) {
                out.write(("---<< " + e.getKey() + '\n').getBytes());

                DataWriter dw = new DataWriter(new OutputStreamWriter(out), null);
                dw.setIndentStep("  ");
                it.transform(new DOMSource(e.getValue()),
                        new SAXResult(dw));

                out.write("\n\n\n".getBytes());
            }
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

}
