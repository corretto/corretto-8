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

package build.tools.jdwpgen;

import java.io.*;

class ErrorSetNode extends AbstractSimpleNode {

    void constrainComponent(Context ctx, Node node) {
        if (node instanceof ErrorNode) {
            node.constrain(ctx);
        } else {
            error("Expected 'Error' item, got: " + node);
        }
    }

    void document(PrintWriter writer) {

        writer.println("<dt>" + "Error Data");
        if (components.isEmpty()) {
            writer.println("<dd>(None)");
        } else {
            writer.println("<dd><table border=1 cellpadding=3 cellspacing=0 width=\"90%\" summary=\"\">");
        for (Node node : components) {
            node.document(writer);
        }
        writer.println("</table>");
        }
    }

    void genJavaComment(PrintWriter writer, int depth) {}

    void genJava(PrintWriter writer, int depth) {}

    void genCInclude(PrintWriter writer) {}

    void genJavaDebugWrite(PrintWriter writer, int depth,
                           String writeLabel) {}

    void genJavaDebugWrite(PrintWriter writer, int depth,
                           String writeLabel, String displayValue) {}

    public void genJavaRead(PrintWriter writer, int depth,
                            String readLabel) {}

    void genJavaDebugRead(PrintWriter writer, int depth,
                          String readLabel, String displayValue) {}

    void genJavaPreDef(PrintWriter writer, int depth) {}

}
