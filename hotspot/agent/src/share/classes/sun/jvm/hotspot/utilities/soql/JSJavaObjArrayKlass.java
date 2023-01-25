/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.utilities.soql;

import java.util.*;
import sun.jvm.hotspot.oops.*;

/**
   This is JavaScript wrapper for ObjArrayKlass.
*/

public class JSJavaObjArrayKlass extends JSJavaArrayKlass {
   public JSJavaObjArrayKlass(ObjArrayKlass kls, JSJavaFactory fac) {
      super(kls, fac);
   }

   public ObjArrayKlass getObjArrayKlass() {
      return (ObjArrayKlass) getArrayKlass();
   }

   public String getName() {
      Klass botKls = getObjArrayKlass().getBottomKlass();
      int dimension = (int) getObjArrayKlass().getDimension();
      StringBuffer buf = new StringBuffer();
      if (botKls instanceof TypeArrayKlass) {
          dimension--;
      }
      buf.append(factory.newJSJavaKlass(botKls).getName());
      for (int i = 0; i < dimension; i++) {
         buf.append("[]");
      }
      return buf.toString();
   }

   public Object getFieldValue(int index, Array array) {
      Oop obj = ((ObjArray)array).getObjAt(index);
      return factory.newJSJavaObject(obj);
   }
}
