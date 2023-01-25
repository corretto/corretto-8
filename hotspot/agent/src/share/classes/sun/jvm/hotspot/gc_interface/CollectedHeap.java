/*
 * Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc_interface;

import java.io.*;
import java.util.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;

public class CollectedHeap extends VMObject {
  private static long         reservedFieldOffset;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("CollectedHeap");

    reservedFieldOffset = type.getField("_reserved").getOffset();
  }

  public CollectedHeap(Address addr) {
    super(addr);
  }

  /** Returns the lowest address of the heap. */
  public Address start() {
    return reservedRegion().start();
  }

  public long capacity() { return 0; }
  public long used()     { return 0; }

  public MemRegion reservedRegion() {
    return new MemRegion(addr.addOffsetTo(reservedFieldOffset));
  }

  public boolean isIn(Address a) {
    return isInReserved(a);
  }

  public boolean isInReserved(Address a) {
    return reservedRegion().contains(a);
  }

  public CollectedHeapName kind() {
    return CollectedHeapName.ABSTRACT;
  }

  public void print() { printOn(System.out); }
  public void printOn(PrintStream tty) {
    MemRegion mr = reservedRegion();
    tty.println("unknown subtype of CollectedHeap @ " + getAddress() + " (" +
                mr.start() + "," + mr.end() + ")");
  }
}
