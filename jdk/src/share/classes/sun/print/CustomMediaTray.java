/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.print;

import javax.print.attribute.EnumSyntax;
import javax.print.attribute.standard.MediaTray;
import javax.print.attribute.standard.Media;
import java.util.ArrayList;

class CustomMediaTray extends MediaTray {
    private static ArrayList customStringTable = new ArrayList();
    private static ArrayList customEnumTable = new ArrayList();
    private String choiceName;

    private CustomMediaTray(int x) {
        super(x);

    }

    private synchronized static int nextValue(String name) {
      customStringTable.add(name);
      return (customStringTable.size()-1);
    }


    public CustomMediaTray(String name, String choice) {
        super(nextValue(name));
        choiceName = choice;
        customEnumTable.add(this);
    }

    /**
     * Version ID for serialized form.
     */
    private static final long serialVersionUID = 1019451298193987013L;


    /**
     * Returns the command string for this media tray.
     */
    public String getChoiceName() {
        return choiceName;
    }


    /**
     * Returns the string table for super class MediaTray.
     */
    public Media[] getSuperEnumTable() {
      return (Media[])super.getEnumValueTable();
    }


    /**
     * Returns the string table for class CustomMediaTray.
     */
    protected String[] getStringTable() {
      String[] nameTable = new String[customStringTable.size()];
      return (String[])customStringTable.toArray(nameTable);
    }

    /**
     * Returns the enumeration value table for class CustomMediaTray.
     */
    protected EnumSyntax[] getEnumValueTable() {
      MediaTray[] enumTable = new MediaTray[customEnumTable.size()];
      return (MediaTray[])customEnumTable.toArray(enumTable);
    }

}
