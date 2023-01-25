/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import java.io.*;
import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;

/**
 * Generate the Serialized Form Information Page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 */
public class SerializedFormWriterImpl extends SubWriterHolderWriter
    implements SerializedFormWriter {

    /**
     * @param configuration the configuration data for the doclet
     * @throws IOException
     * @throws DocletAbortException
     */
    public SerializedFormWriterImpl(ConfigurationImpl configuration)
            throws IOException {
        super(configuration, DocPaths.SERIALIZED_FORM);
    }

    /**
     * Get the given header.
     *
     * @param header the header to write
     * @return the body content tree
     */
    public Content getHeader(String header) {
        Content bodyTree = getBody(true, getWindowTitle(header));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        Content h1Content = new StringContent(header);
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, h1Content);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        bodyTree.addContent(div);
        return bodyTree;
    }

    /**
     * Get the serialized form summaries header.
     *
     * @return the serialized form summary header tree
     */
    public Content getSerializedSummariesHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * Get the package serialized form header.
     *
     * @return the package serialized form header tree
     */
    public Content getPackageSerializedHeader() {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        return li;
    }

    /**
     * Get the given package header.
     *
     * @param packageName the package header to write
     * @return a content tree for the package header
     */
    public Content getPackageHeader(String packageName) {
        Content heading = HtmlTree.HEADING(HtmlConstants.PACKAGE_HEADING, true,
                packageLabel);
        heading.addContent(getSpace());
        heading.addContent(packageName);
        return heading;
    }

    /**
     * Get the serialized class header.
     *
     * @return a content tree for the serialized class header
     */
    public Content getClassSerializedHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * Get the serializable class heading.
     *
     * @param classDoc the class being processed
     * @return a content tree for the class header
     */
    public Content getClassHeader(ClassDoc classDoc) {
        Content classLink = (classDoc.isPublic() || classDoc.isProtected()) ?
            getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.DEFAULT, classDoc)
            .label(configuration.getClassName(classDoc))) :
            new StringContent(classDoc.qualifiedName());
        Content li = HtmlTree.LI(HtmlStyle.blockList, getMarkerAnchor(
                classDoc.qualifiedName()));
        Content superClassLink =
            classDoc.superclassType() != null ?
                getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.SERIALIZED_FORM,
                        classDoc.superclassType())) :
                null;

        //Print the heading.
        Content className = superClassLink == null ?
            configuration.getResource(
            "doclet.Class_0_implements_serializable", classLink) :
            configuration.getResource(
            "doclet.Class_0_extends_implements_serializable", classLink,
            superClassLink);
        li.addContent(HtmlTree.HEADING(HtmlConstants.SERIALIZED_MEMBER_HEADING,
                className));
        return li;
    }

    /**
     * Get the serial UID info header.
     *
     * @return a content tree for the serial uid info header
     */
    public Content getSerialUIDInfoHeader() {
        HtmlTree dl = new HtmlTree(HtmlTag.DL);
        dl.addStyle(HtmlStyle.nameValue);
        return dl;
    }

    /**
     * Adds the serial UID info.
     *
     * @param header the header that will show up before the UID.
     * @param serialUID the serial UID to print.
     * @param serialUidTree the serial UID content tree to which the serial UID
     *                      content will be added
     */
    public void addSerialUIDInfo(String header, String serialUID,
            Content serialUidTree) {
        Content headerContent = new StringContent(header);
        serialUidTree.addContent(HtmlTree.DT(headerContent));
        Content serialContent = new StringContent(serialUID);
        serialUidTree.addContent(HtmlTree.DD(serialContent));
    }

    /**
     * Get the class serialize content header.
     *
     * @return a content tree for the class serialize content header
     */
    public Content getClassContentHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * Get the serialized content tree section.
     *
     * @param serializedTreeContent the serialized content tree to be added
     * @return a div content tree
     */
    public Content getSerializedContent(Content serializedTreeContent) {
        Content divContent = HtmlTree.DIV(HtmlStyle.serializedFormContainer,
                serializedTreeContent);
        return divContent;
    }

    /**
     * Add the footer.
     *
     * @param serializedTree the serialized tree to be added
     */
    public void addFooter(Content serializedTree) {
        addNavLinks(false, serializedTree);
        addBottom(serializedTree);
    }

    /**
     * {@inheritDoc}
     */
    public void printDocument(Content serializedTree) throws IOException {
        printHtmlDocument(null, true, serializedTree);
    }

    /**
     * Return an instance of a SerialFieldWriter.
     *
     * @return an instance of a SerialFieldWriter.
     */
    public SerialFieldWriter getSerialFieldWriter(ClassDoc classDoc) {
        return new HtmlSerialFieldWriter(this, classDoc);
    }

    /**
     * Return an instance of a SerialMethodWriter.
     *
     * @return an instance of a SerialMethodWriter.
     */
    public SerialMethodWriter getSerialMethodWriter(ClassDoc classDoc) {
        return new HtmlSerialMethodWriter(this, classDoc);
    }
}
