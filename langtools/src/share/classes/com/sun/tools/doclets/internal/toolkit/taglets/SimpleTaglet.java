/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocFinder;
import com.sun.tools.javac.util.StringUtils;

/**
 * A simple single argument custom tag.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */

public class SimpleTaglet extends BaseTaglet implements InheritableTaglet {

    /**
     * The marker in the location string for excluded tags.
     */
    public static final String EXCLUDED = "x";

    /**
     * The marker in the location string for packages.
     */
    public static final String PACKAGE = "p";

    /**
     * The marker in the location string for types.
     */
    public static final String TYPE = "t";

    /**
     * The marker in the location string for constructors.
     */
    public static final String CONSTRUCTOR = "c";

    /**
     * The marker in the location string for fields.
     */
    public static final String FIELD = "f";

    /**
     * The marker in the location string for methods.
     */
    public static final String METHOD = "m";

    /**
     * The marker in the location string for overview.
     */
    public static final String OVERVIEW = "o";

    /**
     * Use in location string when the tag is to
     * appear in all locations.
     */
    public static final String ALL = "a";

    /**
     * The name of this tag.
     */
    protected String tagName;

    /**
     * The header to output.
     */
    protected String header;

    /**
     * The possible locations that this tag can appear in.
     */
    protected String locations;

    /**
     * Construct a <code>SimpleTaglet</code>.
     * @param tagName the name of this tag
     * @param header the header to output.
     * @param locations the possible locations that this tag
     * can appear in.  The <code>String</code> can contain 'p'
     * for package, 't' for type, 'm' for method, 'c' for constructor
     * and 'f' for field.
     */
    public SimpleTaglet(String tagName, String header, String locations) {
        this.tagName = tagName;
        this.header = header;
        locations = StringUtils.toLowerCase(locations);
        if (locations.indexOf(ALL) != -1 && locations.indexOf(EXCLUDED) == -1) {
            this.locations = PACKAGE + TYPE + FIELD + METHOD + CONSTRUCTOR + OVERVIEW;
        } else {
            this.locations = locations;
        }
    }

    /**
     * Return the name of this <code>Taglet</code>.
     */
    public String getName() {
        return tagName;
    }

    /**
     * Return true if this <code>SimpleTaglet</code>
     * is used in constructor documentation.
     * @return true if this <code>SimpleTaglet</code>
     * is used in constructor documentation and false
     * otherwise.
     */
    public boolean inConstructor() {
        return locations.indexOf(CONSTRUCTOR) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    /**
     * Return true if this <code>SimpleTaglet</code>
     * is used in field documentation.
     * @return true if this <code>SimpleTaglet</code>
     * is used in field documentation and false
     * otherwise.
     */
    public boolean inField() {
        return locations.indexOf(FIELD) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    /**
     * Return true if this <code>SimpleTaglet</code>
     * is used in method documentation.
     * @return true if this <code>SimpleTaglet</code>
     * is used in method documentation and false
     * otherwise.
     */
    public boolean inMethod() {
        return locations.indexOf(METHOD) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    /**
     * Return true if this <code>SimpleTaglet</code>
     * is used in overview documentation.
     * @return true if this <code>SimpleTaglet</code>
     * is used in overview documentation and false
     * otherwise.
     */
    public boolean inOverview() {
        return locations.indexOf(OVERVIEW) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    /**
     * Return true if this <code>SimpleTaglet</code>
     * is used in package documentation.
     * @return true if this <code>SimpleTaglet</code>
     * is used in package documentation and false
     * otherwise.
     */
    public boolean inPackage() {
        return locations.indexOf(PACKAGE) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    /**
     * Return true if this <code>SimpleTaglet</code>
     * is used in type documentation (classes or interfaces).
     * @return true if this <code>SimpleTaglet</code>
     * is used in type documentation and false
     * otherwise.
     */
    public boolean inType() {
        return locations.indexOf(TYPE) != -1&& locations.indexOf(EXCLUDED) == -1;
    }

    /**
     * Return true if this <code>Taglet</code>
     * is an inline tag.
     * @return true if this <code>Taglet</code>
     * is an inline tag and false otherwise.
     */
    public boolean isInlineTag() {
        return false;
    }

    @Override
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        Tag[] tags = input.element.tags(tagName);
        if (tags.length > 0) {
            output.holder = input.element;
            output.holderTag = tags[0];
            output.inlineTags = input.isFirstSentence
                    ? tags[0].firstSentenceTags() : tags[0].inlineTags();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Content getTagletOutput(Tag tag, TagletWriter writer) {
        return header == null || tag == null ? null : writer.simpleTagOutput(tag, header);
    }

    /**
     * {@inheritDoc}
     */
    public Content getTagletOutput(Doc holder, TagletWriter writer) {
        if (header == null || holder.tags(getName()).length == 0) {
            return null;
        }
        return writer.simpleTagOutput(holder.tags(getName()), header);
    }
}
