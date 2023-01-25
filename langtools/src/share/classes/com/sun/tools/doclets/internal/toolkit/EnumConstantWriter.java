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

package com.sun.tools.doclets.internal.toolkit;

import java.io.*;
import com.sun.javadoc.*;

/**
 * The interface for writing enum constant output.
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

public interface EnumConstantWriter {

    /**
     * Get the enum constants details tree header.
     *
     * @param classDoc the class being documented
     * @param memberDetailsTree the content tree representing member details
     * @return content tree for the enum constants details header
     */
    public Content getEnumConstantsDetailsTreeHeader(ClassDoc classDoc,
            Content memberDetailsTree);

    /**
     * Get the enum constants documentation tree header.
     *
     * @param enumConstant the enum constant being documented
     * @param enumConstantsDetailsTree the content tree representing enum constant details
     * @return content tree for the enum constant documentation header
     */
    public Content getEnumConstantsTreeHeader(FieldDoc enumConstant,
            Content enumConstantsDetailsTree);

    /**
     * Get the signature for the given enum constant.
     *
     * @param enumConstant the enum constant being documented
     * @return content tree for the enum constant signature
     */
    public Content getSignature(FieldDoc enumConstant);

    /**
     * Add the deprecated output for the given enum constant.
     *
     * @param enumConstant the enum constant being documented
     * @param enumConstantsTree content tree to which the deprecated information will be added
     */
    public void addDeprecated(FieldDoc enumConstant, Content enumConstantsTree);

    /**
     * Add the comments for the given enum constant.
     *
     * @param enumConstant the enum constant being documented
     * @param enumConstantsTree the content tree to which the comments will be added
     */
    public void addComments(FieldDoc enumConstant, Content enumConstantsTree);

    /**
     * Add the tags for the given enum constant.
     *
     * @param enumConstant the enum constant being documented
     * @param enumConstantsTree the content tree to which the tags will be added
     */
    public void addTags(FieldDoc enumConstant, Content enumConstantsTree);

    /**
     * Get the enum constants details tree.
     *
     * @param memberDetailsTree the content tree representing member details
     * @return content tree for the enum constant details
     */
    public Content getEnumConstantsDetails(Content memberDetailsTree);

    /**
     * Get the enum constants documentation.
     *
     * @param enumConstantsTree the content tree representing enum constants documentation
     * @param isLastContent true if the content to be added is the last content
     * @return content tree for the enum constants documentation
     */
    public Content getEnumConstants(Content enumConstantsTree, boolean isLastContent);

    /**
     * Close the writer.
     */
    public void close() throws IOException;
}
