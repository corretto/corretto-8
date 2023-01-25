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

import javax.print.attribute.PrintRequestAttribute;

/*
 * A class used to determine minimum and maximum pages.
 */
public final class SunMinMaxPage implements PrintRequestAttribute {
    private int page_max, page_min;

    public SunMinMaxPage(int min, int max) {
       page_min = min;
       page_max = max;
    }


    public final Class getCategory() {
        return SunMinMaxPage.class;
    }


    public final int getMin() {
        return page_min;
    }

    public final int getMax() {
        return page_max;
    }


    public final String getName() {
        return "sun-page-minmax";
    }

}
