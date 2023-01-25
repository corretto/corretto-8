/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc_implementation.shared;

import java.io.*;
import java.util.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;

public class MutableSpace extends ImmutableSpace {
   static {
      VM.registerVMInitializedObserver(new Observer() {
         public void update(Observable o, Object data) {
            initialize(VM.getVM().getTypeDataBase());
         }
      });
   }

   private static synchronized void initialize(TypeDataBase db) {
      Type type = db.lookupType("MutableSpace");
      topField    = type.getAddressField("_top");
   }

   public MutableSpace(Address addr) {
      super(addr);
   }

   // Fields
   private static AddressField topField;

   // Accessors
   public Address   top()          { return topField.getValue(addr);    }

   /** In bytes */
   public long used() {
      return top().minus(bottom());
   }

   /** returns all MemRegions where live objects are */
   public List/*<MemRegion>*/ getLiveRegions() {
      List res = new ArrayList();
      res.add(new MemRegion(bottom(), top()));
      return res;
   }

   public void printOn(PrintStream tty) {
      tty.print(" [" + bottom() + "," +
                top() + "," + end() + "] ");
   }
}
