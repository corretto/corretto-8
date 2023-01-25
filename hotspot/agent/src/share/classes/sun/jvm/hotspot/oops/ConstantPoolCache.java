/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.oops;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

//  ConstantPoolCache : A constant pool cache (ConstantPoolCache).
//  See cpCache.hpp for details about this class.
//
public class ConstantPoolCache extends Metadata {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type      = db.lookupType("ConstantPoolCache");
    constants      = new MetadataField(type.getAddressField("_constant_pool"), 0);
    baseOffset     = type.getSize();
    Type elType    = db.lookupType("ConstantPoolCacheEntry");
    elementSize    = elType.getSize();
    length         = new CIntField(type.getCIntegerField("_length"), 0);
    intSize        = VM.getVM().getObjectHeap().getIntSize();
  }

  public ConstantPoolCache(Address addr) {
    super(addr);
  }

  public boolean isConstantPoolCache() { return true; }

  private static MetadataField constants;

  private static long baseOffset;
  private static long elementSize;
  private static CIntField length;
  private static long intSize;


  public ConstantPool getConstants() { return (ConstantPool) constants.getValue(this); }

  public long getSize() {
    return Oop.alignObjectSize(baseOffset + getLength() * elementSize);
  }

  public ConstantPoolCacheEntry getEntryAt(int i) {
    if (i < 0 || i >= getLength()) throw new IndexOutOfBoundsException(i + " " + getLength());
    return new ConstantPoolCacheEntry(this, i);
  }

  public int getIntAt(int entry, int fld) {
    //alignObjectSize ?
    long offset = baseOffset + /*alignObjectSize*/entry * elementSize + fld * intSize;
    return (int) getAddress().getCIntegerAt(offset, intSize, true );
  }


  public void printValueOn(PrintStream tty) {
    tty.print("ConstantPoolCache for " + getConstants().getPoolHolder().getName().asString() + " address = " + getAddress() + " offset = " + baseOffset);
  }

  public int getLength() {
    return (int) length.getValue(getAddress());
  }

  public void iterateFields(MetadataVisitor visitor) {
    super.iterateFields(visitor);
    visitor.doMetadata(constants, true);
      for (int i = 0; i < getLength(); i++) {
        ConstantPoolCacheEntry entry = getEntryAt(i);
        entry.iterateFields(visitor);
      }
    }
};
