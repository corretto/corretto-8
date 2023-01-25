/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Builds documentation for optional annotation type members.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 * @since 1.5
 */
public class AnnotationTypeOptionalMemberBuilder extends
        AnnotationTypeRequiredMemberBuilder {


    /**
     * Construct a new AnnotationTypeMemberBuilder.
     *
     * @param context  the build context.
     * @param classDoc the class whose members are being documented.
     * @param writer the doclet specific writer.
     */
    private AnnotationTypeOptionalMemberBuilder(Context context,
            ClassDoc classDoc,
            AnnotationTypeOptionalMemberWriter writer) {
        super(context, classDoc, writer,
                VisibleMemberMap.ANNOTATION_TYPE_MEMBER_OPTIONAL);
    }


    /**
     * Construct a new AnnotationTypeMemberBuilder.
     *
     * @param context  the build context.
     * @param classDoc the class whose members are being documented.
     * @param writer the doclet specific writer.
     */
    public static AnnotationTypeOptionalMemberBuilder getInstance(
            Context context, ClassDoc classDoc,
            AnnotationTypeOptionalMemberWriter writer) {
        return new AnnotationTypeOptionalMemberBuilder(context,
                classDoc, writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "AnnotationTypeOptionalMemberDetails";
    }

    /**
     * Build the annotation type optional member documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildAnnotationTypeOptionalMember(XMLNode node, Content memberDetailsTree) {
        buildAnnotationTypeMember(node, memberDetailsTree);
    }

    /**
     * Build the default value for this optional member.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    public void buildDefaultValueInfo(XMLNode node, Content annotationDocTree) {
        ((AnnotationTypeOptionalMemberWriter) writer).addDefaultValueInfo(
                (MemberDoc) members.get(currentMemberIndex),
                annotationDocTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AnnotationTypeRequiredMemberWriter getWriter() {
        return writer;
    }
}
