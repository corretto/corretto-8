/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.interpreter;

import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class BytecodeLoadConstant extends Bytecode {
  BytecodeLoadConstant(Method method, int bci) {
    super(method, bci);
  }

  public boolean hasCacheIndex() {
    // normal ldc uses CP index, but fast_aldc uses swapped CP cache index
    return code() >= Bytecodes.number_of_java_codes;
  }

  int rawIndex() {
    if (javaCode() == Bytecodes._ldc)
      return getIndexU1();
    else
      return getIndexU2(code(), false);
  }

  public int poolIndex() {
    int index = rawIndex();
    if (hasCacheIndex()) {
      return method().getConstants().objectToCPIndex(index);
    } else {
      return index;
    }
  }

  public int cacheIndex() {
    if (hasCacheIndex()) {
      return rawIndex();
    } else {
      return -1;  // no cache index
    }
  }

  public BasicType resultType() {
    int index = poolIndex();
    ConstantTag tag = method().getConstants().getTagAt(index);
    return tag.basicType();
  }

  private Oop getCachedConstant() {
    int i = cacheIndex();
    if (i >= 0) {
      throw new InternalError("invokedynamic not implemented yet");
    }
    return null;
  }

  public void verify() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isValid(), "check load constant");
    }
  }

  public boolean isValid() {
    int jcode = javaCode();
    boolean codeOk = jcode == Bytecodes._ldc || jcode == Bytecodes._ldc_w ||
           jcode == Bytecodes._ldc2_w;
    if (! codeOk) return false;

    ConstantTag ctag = method().getConstants().getTagAt(poolIndex());
    if (jcode == Bytecodes._ldc2_w) {
       // has to be double or long
       return (ctag.isDouble() || ctag.isLong()) ? true: false;
    } else {
       // has to be int or float or String or Klass
       return (ctag.isString()
               || ctag.isUnresolvedKlass() || ctag.isKlass()
               || ctag.isMethodHandle() || ctag.isMethodType()
               || ctag.isInt() || ctag.isFloat())? true: false;
    }
  }

  public boolean isKlassConstant() {
    int jcode = javaCode();
    if (jcode == Bytecodes._ldc2_w) {
       return false;
    }

    ConstantTag ctag = method().getConstants().getTagAt(poolIndex());
    return ctag.isKlass() || ctag.isUnresolvedKlass();
  }

  // return Symbol (if unresolved) or Klass (if resolved)
  public Object getKlass() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isKlassConstant(), "not a klass literal");
    }
    // tag change from 'unresolved' to 'klass' does not happen atomically.
    // We just look at the object at the corresponding index and
    // decide based on the oop type.
    ConstantPool cpool = method().getConstants();
    int cpIndex = poolIndex();
    ConstantPool.CPSlot oop = cpool.getSlotAt(cpIndex);
    if (oop.isResolved()) {
      return oop.getKlass();
    } else if (oop.isUnresolved()) {
      return oop.getSymbol();
    } else {
       throw new RuntimeException("should not reach here");
    }
  }

  public static BytecodeLoadConstant at(Method method, int bci) {
    BytecodeLoadConstant b = new BytecodeLoadConstant(method, bci);
    if (Assert.ASSERTS_ENABLED) {
      b.verify();
    }
    return b;
  }

  /** Like at, but returns null if the BCI is not at ldc or ldc_w or ldc2_w  */
  public static BytecodeLoadConstant atCheck(Method method, int bci) {
    BytecodeLoadConstant b = new BytecodeLoadConstant(method, bci);
    return (b.isValid() ? b : null);
  }

  public static BytecodeLoadConstant at(BytecodeStream bcs) {
    return new BytecodeLoadConstant(bcs.method(), bcs.bci());
  }

  public String getConstantValue() {
    ConstantPool cpool = method().getConstants();
    int cpIndex = poolIndex();
    ConstantTag ctag = cpool.getTagAt(cpIndex);
    if (ctag.isInt()) {
       return "<int " + Integer.toString(cpool.getIntAt(cpIndex)) +">";
    } else if (ctag.isLong()) {
       return "<long " + Long.toString(cpool.getLongAt(cpIndex)) + "L>";
    } else if (ctag.isFloat()) {
       return "<float " + Float.toString(cpool.getFloatAt(cpIndex)) + "F>";
    } else if (ctag.isDouble()) {
       return "<double " + Double.toString(cpool.getDoubleAt(cpIndex)) + "D>";
    } else if (ctag.isString()) {
       // tag change from 'unresolved' to 'string' does not happen atomically.
       // We just look at the object at the corresponding index and
       // decide based on the oop type.
       Symbol sym = cpool.getUnresolvedStringAt(cpIndex);
         return "<String \"" + sym.asString() + "\">";
    } else if (ctag.isKlass() || ctag.isUnresolvedKlass()) {
       // tag change from 'unresolved' to 'klass' does not happen atomically.
       // We just look at the object at the corresponding index and
       // decide based on the oop type.
       ConstantPool.CPSlot obj = cpool.getSlotAt(cpIndex);
       if (obj.isResolved()) {
         Klass k = obj.getKlass();
         return "<Class " + k.getName().asString() + "@" + k.getAddress() + ">";
       } else if (obj.isUnresolved()) {
         Symbol sym = obj.getSymbol();
         return "<Class " + sym.asString() + ">";
       } else {
          throw new RuntimeException("should not reach here");
       }
    } else if (ctag.isMethodHandle()) {
       Oop x = getCachedConstant();
       int refidx = cpool.getMethodHandleIndexAt(cpIndex);
       int refkind = cpool.getMethodHandleRefKindAt(cpIndex);
       return "<MethodHandle kind=" + Integer.toString(refkind) +
           " ref=" + Integer.toString(refidx)
           + (x == null ? "" : " @" + x.getHandle()) + ">";
    } else if (ctag.isMethodType()) {
       Oop x = getCachedConstant();
       int refidx = cpool.getMethodTypeIndexAt(cpIndex);
       return "<MethodType " + cpool.getSymbolAt(refidx).asString()
           + (x == null ? "" : " @" + x.getHandle()) + ">";
    } else {
       if (Assert.ASSERTS_ENABLED) {
         Assert.that(false, "invalid load constant type");
       }
       return null;
    }
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append(getJavaBytecodeName());
    buf.append(spaces);
    buf.append('#');
    buf.append(Integer.toString(poolIndex()));
    if (hasCacheIndex()) {
       buf.append('(');
       buf.append(Integer.toString(cacheIndex()));
       buf.append(')');
    }
    buf.append(spaces);
    buf.append(getConstantValue());
    if (code() != javaCode()) {
       buf.append(spaces);
       buf.append('[');
       buf.append(getBytecodeName());
       buf.append(']');
    }
    return buf.toString();
  }
}
