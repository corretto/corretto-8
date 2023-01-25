/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 7190310
 * @summary Inlining WeakReference.get(), and hoisting $referent may lead to non-terminating loops
 * @run main/othervm -Xbatch Test7190310_unsafe
 */

import java.lang.ref.*;
import java.lang.reflect.*;
import sun.misc.Unsafe;

public class Test7190310_unsafe {

  static class TestObject {
    public String toString() {
      return "TestObject";
    }
  };

  private static TestObject str = new TestObject();
  private static final WeakReference ref = new WeakReference(str);

  private TestObject obj;

  public static void main(String[] args) throws Exception {
    Class c = Test7190310_unsafe.class.getClassLoader().loadClass("sun.misc.Unsafe");
    Field f = c.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    Unsafe unsafe = (Unsafe)f.get(c);

    f = Reference.class.getDeclaredField("referent");
    f.setAccessible(true);
    long referent_offset = unsafe.objectFieldOffset(f);

    Test7190310_unsafe t = new Test7190310_unsafe();
    TestObject o = new TestObject();
    t.obj = o;

    // Warmup (compile methods)
    System.err.println("Warmup");
    Object obj = null;
    for (int i = 0; i < 11000; i++) {
      obj = getRef0(ref);
    }
    for (int i = 0; i < 11000; i++) {
      obj = getRef1(unsafe, ref, referent_offset);
    }
    for (int i = 0; i < 11000; i++) {
      obj = getRef2(unsafe, ref, referent_offset);
    }
    for (int i = 0; i < 11000; i++) {
      obj = getRef3(unsafe, ref, referent_offset);
    }
    for (int i = 0; i < 11000; i++) {
      obj = getRef4(unsafe, t, referent_offset);
    }

    // Access verification
    System.err.println("Verification");
    if (!verifyGet(referent_offset, unsafe)) {
      System.exit(97);
    }

    obj = getRef3(unsafe, t, referent_offset);
    if (obj != o) {
      System.out.println("FAILED: unsafe.getObject(Object, " + referent_offset + ") " + obj + " != " + o);
      System.exit(97);
    }
    obj = getRef4(unsafe, t, referent_offset);
    if (obj != o) {
      System.out.println("FAILED: unsafe.getObject(Test7190310, " + referent_offset + ") " + obj + " != " + o);
      System.exit(97);
    }
  }

  static boolean verifyGet(long referent_offset, Unsafe unsafe) throws Exception {
    // Access verification
    System.out.println("referent: " + str);
    Object obj = getRef0(ref);
    if (obj != str) {
      System.out.println("FAILED: weakRef.get() " + obj + " != " + str);
      return false;
    }
    obj = getRef1(unsafe, ref, referent_offset);
    if (obj != str) {
      System.out.println("FAILED: unsafe.getObject(weakRef, " + referent_offset + ") " + obj + " != " + str);
      return false;
    }
    obj = getRef2(unsafe, ref, referent_offset);
    if (obj != str) {
      System.out.println("FAILED: unsafe.getObject(abstRef, " + referent_offset + ") " + obj + " != " + str);
      return false;
    }
    obj = getRef3(unsafe, ref, referent_offset);
    if (obj != str) {
      System.out.println("FAILED: unsafe.getObject(Object, " + referent_offset + ") " + obj + " != " + str);
      return false;
    }
    return true;
  }

  static Object getRef0(WeakReference ref) throws Exception {
    return ref.get();
  }
  static Object getRef1(Unsafe unsafe, WeakReference ref, long referent_offset) throws Exception {
    return unsafe.getObject(ref, referent_offset);
  }
  static Object getRef2(Unsafe unsafe, Reference ref, long referent_offset) throws Exception {
    return unsafe.getObject(ref, referent_offset);
  }
  static Object getRef3(Unsafe unsafe, Object ref, long referent_offset) throws Exception {
    return unsafe.getObject(ref, referent_offset);
  }
  static Object getRef4(Unsafe unsafe, Test7190310_unsafe ref, long referent_offset) throws Exception {
    return unsafe.getObject(ref, referent_offset);
  }
}

