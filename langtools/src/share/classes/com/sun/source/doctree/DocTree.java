/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.source.doctree;

/**
 * Common interface for all nodes in a documentation syntax tree.
 *
 * @since 1.8
 */
@jdk.Exported
public interface DocTree {
    @jdk.Exported
    enum Kind {
        /**
         * Used for instances of {@link AttributeTree}
         * representing an HTML attribute.
         */
        ATTRIBUTE,

        /**
         * Used for instances of {@link AuthorTree}
         * representing an @author tag.
         */
        AUTHOR("author"),

        /**
         * Used for instances of {@link LiteralTree}
         * representing an @code tag.
         */
        CODE("code"),

        /**
         * Used for instances of {@link CommentTree}
         * representing an HTML comment.
         */
        COMMENT,

        /**
         * Used for instances of {@link DeprecatedTree}
         * representing an @deprecated tag.
         */
        DEPRECATED("deprecated"),

        /**
         * Used for instances of {@link DocCommentTree}
         * representing a complete doc comment.
         */
        DOC_COMMENT,

        /**
         * Used for instances of {@link DocRootTree}
         * representing an @docRoot tag.
         */
        DOC_ROOT("docRoot"),

        /**
         * Used for instances of {@link EndElementTree}
         * representing the end of an HTML element.
         */
        END_ELEMENT,

        /**
         * Used for instances of {@link EntityTree}
         * representing an HTML entity.
         */
        ENTITY,

        /**
         * Used for instances of {@link ErroneousTree}
         * representing some invalid text.
         */
        ERRONEOUS,

        /**
         * Used for instances of {@link ThrowsTree}
         * representing an @exception tag.
         */
        EXCEPTION("exception"),

        /**
         * Used for instances of {@link IdentifierTree}
         * representing an identifier.
         */
        IDENTIFIER,

        /**
         * Used for instances of {@link InheritDocTree}
         * representing an @inheritDoc tag.
         */
        INHERIT_DOC("inheritDoc"),

        /**
         * Used for instances of {@link LinkTree}
         * representing an @link tag.
         */
        LINK("link"),

        /**
         * Used for instances of {@link LinkTree}
         * representing an @linkplain tag.
         */
        LINK_PLAIN("linkplain"),

        /**
         * Used for instances of {@link LiteralTree}
         * representing an @literal tag.
         */
        LITERAL("literal"),

        /**
         * Used for instances of {@link ParamTree}
         * representing an @param tag.
         */
        PARAM("param"),

        /**
         * Used for instances of {@link ReferenceTree}
         * representing a reference to a element in the
         * Java programming language.
         */
        REFERENCE,

        /**
         * Used for instances of {@link ReturnTree}
         * representing an @return tag.
         */
        RETURN("return"),

        /**
         * Used for instances of {@link SeeTree}
         * representing an @see tag.
         */
        SEE("see"),

        /**
         * Used for instances of {@link SerialTree}
         * representing an @serial tag.
         */
        SERIAL("serial"),

        /**
         * Used for instances of {@link SerialDataTree}
         * representing an @serialData tag.
         */
        SERIAL_DATA("serialData"),

        /**
         * Used for instances of {@link SerialFieldTree}
         * representing an @serialField tag.
         */
        SERIAL_FIELD("serialField"),

        /**
         * Used for instances of {@link SinceTree}
         * representing an @since tag.
         */
        SINCE("since"),

        /**
         * Used for instances of {@link EndElementTree}
         * representing the start of an HTML element.
         */
        START_ELEMENT,

        /**
         * Used for instances of {@link TextTree}
         * representing some documentation text.
         */
        TEXT,

        /**
         * Used for instances of {@link ThrowsTree}
         * representing an @throws tag.
         */
        THROWS("throws"),

        /**
         * Used for instances of {@link UnknownBlockTagTree}
         * representing an unknown block tag.
         */
        UNKNOWN_BLOCK_TAG,

        /**
         * Used for instances of {@link UnknownInlineTagTree}
         * representing an unknown inline tag.
         */
        UNKNOWN_INLINE_TAG,

        /**
         * Used for instances of {@link ValueTree}
         * representing an @value tag.
         */
        VALUE("value"),

        /**
         * Used for instances of {@link VersionTree}
         * representing an @version tag.
         */
        VERSION("version"),

        /**
         * An implementation-reserved node. This is the not the node
         * you are looking for.
         */
        OTHER;

        public final String tagName;

        Kind() {
            tagName = null;
        }

        Kind(String tagName) {
            this.tagName = tagName;
        }
    };

    /**
     * Gets the kind of this tree.
     *
     * @return the kind of this tree.
     */
    Kind getKind();

    /**
     * Accept method used to implement the visitor pattern.  The
     * visitor pattern is used to implement operations on trees.
     *
     * @param <R> result type of this operation.
     * @param <D> type of additional data.
     */
    <R, D> R accept(DocTreeVisitor<R,D> visitor, D data);
}
