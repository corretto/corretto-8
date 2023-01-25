/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.compiler;

import sun.jvm.hotspot.code.*;

public class OopMapStream {
  private CompressedReadStream stream;
  private OopMap oopMap;
  private int mask;
  private int size;
  private int position;
  private OopMapValue omv;
  private boolean omvValid;

  public OopMapStream(OopMap oopMap) {
    this(oopMap, (OopMapValue.OopTypes[]) null);
  }

  public OopMapStream(OopMap oopMap, OopMapValue.OopTypes type) {
    this(oopMap, (OopMapValue.OopTypes[]) null);
    mask = type.getValue();
  }

  public OopMapStream(OopMap oopMap, OopMapValue.OopTypes[] types) {
    if (oopMap.getOMVData() == null) {
      stream = new CompressedReadStream(oopMap.getWriteStream().getBuffer());
    } else {
      stream = new CompressedReadStream(oopMap.getOMVData());
    }
    mask = computeMask(types);
    size = (int) oopMap.getOMVCount();
    position = 0;
    omv = new OopMapValue();
    omvValid = false;
  }

  public boolean isDone() {
    if (!omvValid) {
      findNext();
    }
    return !omvValid;
  }

  public void next() {
    findNext();
  }

  public OopMapValue getCurrent() {
    return omv;
  }

  //--------------------------------------------------------------------------------
  // Internals only below this point
  //

  private int computeMask(OopMapValue.OopTypes[] types) {
    mask = 0;
    if (types != null) {
      for (int i = 0; i < types.length; i++) {
        mask |= types[i].getValue();
      }
    }
    return mask;
  }

  private void findNext() {
    while (position++ < size) {
      omv.readFrom(stream);
      if ((omv.getType().getValue() & mask) > 0) {
        omvValid = true;
        return;
      }
    }
    omvValid = false;
  }
}
