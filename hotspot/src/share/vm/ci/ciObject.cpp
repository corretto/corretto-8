/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "ci/ciObject.hpp"
#include "ci/ciUtilities.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "oops/oop.inline2.hpp"

// ciObject
//
// This class represents an oop in the HotSpot virtual machine.
// Its subclasses are structured in a hierarchy which mirrors
// an aggregate of the VM's oop and klass hierarchies (see
// oopHierarchy.hpp).  Each instance of ciObject holds a handle
// to a corresponding oop on the VM side and provides routines
// for accessing the information in its oop.  By using the ciObject
// hierarchy for accessing oops in the VM, the compiler ensures
// that it is safe with respect to garbage collection; that is,
// GC and compilation can proceed independently without
// interference.
//
// Within the VM, the oop and klass hierarchies are separate.
// The compiler interface does not preserve this separation --
// the distinction between `Klass*' and `Klass' are not
// reflected in the interface and instead the Klass hierarchy
// is directly modeled as the subclasses of ciKlass.

// ------------------------------------------------------------------
// ciObject::ciObject
ciObject::ciObject(oop o) {
  ASSERT_IN_VM;
  if (ciObjectFactory::is_initialized()) {
    _handle = JNIHandles::make_local(o);
  } else {
    _handle = JNIHandles::make_global(o);
  }
  _klass = NULL;
  init_flags_from(o);
}

// ------------------------------------------------------------------
// ciObject::ciObject
//
ciObject::ciObject(Handle h) {
  ASSERT_IN_VM;
  if (ciObjectFactory::is_initialized()) {
    _handle = JNIHandles::make_local(h());
  } else {
    _handle = JNIHandles::make_global(h);
  }
  _klass = NULL;
  init_flags_from(h());
}

// ------------------------------------------------------------------
// ciObject::ciObject
//
// Unloaded klass/method variant.  `klass' is the klass of the unloaded
// klass/method, if that makes sense.
ciObject::ciObject(ciKlass* klass) {
  ASSERT_IN_VM;
  assert(klass != NULL, "must supply klass");
  _handle = NULL;
  _klass = klass;
}

// ------------------------------------------------------------------
// ciObject::ciObject
//
// NULL variant.  Used only by ciNullObject.
ciObject::ciObject() {
  ASSERT_IN_VM;
  _handle = NULL;
  _klass = NULL;
}

// ------------------------------------------------------------------
// ciObject::klass
//
// Get the ciKlass of this ciObject.
ciKlass* ciObject::klass() {
  if (_klass == NULL) {
    if (_handle == NULL) {
      // When both _klass and _handle are NULL, we are dealing
      // with the distinguished instance of ciNullObject.
      // No one should ask it for its klass.
      assert(is_null_object(), "must be null object");
      ShouldNotReachHere();
      return NULL;
    }

    GUARDED_VM_ENTRY(
      oop o = get_oop();
      _klass = CURRENT_ENV->get_klass(o->klass());
    );
  }
  return _klass;
}

// ------------------------------------------------------------------
// ciObject::equals
//
// Are two ciObjects equal?
bool ciObject::equals(ciObject* obj) {
  return (this == obj);
}

// ------------------------------------------------------------------
// ciObject::hash
//
// A hash value for the convenience of compilers.
//
// Implementation note: we use the address of the ciObject as the
// basis for the hash.  Use the _ident field, which is well-behaved.
int ciObject::hash() {
  return ident() * 31;
}

// ------------------------------------------------------------------
// ciObject::constant_encoding
//
// The address which the compiler should embed into the
// generated code to represent this oop.  This address
// is not the true address of the oop -- it will get patched
// during nmethod creation.
//
//
//
// Implementation note: we use the handle as the encoding.  The
// nmethod constructor resolves the handle and patches in the oop.
//
// This method should be changed to return an generified address
// to discourage use of the JNI handle.
jobject ciObject::constant_encoding() {
  assert(is_null_object() || handle() != NULL, "cannot embed null pointer");
  assert(can_be_constant(), "oop must be NULL or perm");
  return handle();
}

// ------------------------------------------------------------------
// ciObject::can_be_constant
bool ciObject::can_be_constant() {
  if (ScavengeRootsInCode >= 1)  return true;  // now everybody can encode as a constant
  return handle() == NULL;
}

// ------------------------------------------------------------------
// ciObject::should_be_constant()
bool ciObject::should_be_constant() {
  if (ScavengeRootsInCode >= 2)  return true;  // force everybody to be a constant
  if (is_null_object()) return true;

  ciEnv* env = CURRENT_ENV;

    // We want Strings and Classes to be embeddable by default since
    // they used to be in the perm world.  Not all Strings used to be
    // embeddable but there's no easy way to distinguish the interned
    // from the regulars ones so just treat them all that way.
    if (klass() == env->String_klass() || klass() == env->Class_klass()) {
      return true;
    }
  if (EnableInvokeDynamic &&
      (klass()->is_subclass_of(env->MethodHandle_klass()) ||
       klass()->is_subclass_of(env->CallSite_klass()))) {
    assert(ScavengeRootsInCode >= 1, "must be");
    // We want to treat these aggressively.
    return true;
  }

  return handle() == NULL;
}

// ------------------------------------------------------------------
// ciObject::should_be_constant()
void ciObject::init_flags_from(oop x) {
  int flags = 0;
  if (x != NULL) {
    assert(Universe::heap()->is_in_reserved(x), "must be");
    if (x->is_scavengable())
      flags |= SCAVENGABLE_FLAG;
  }
  _ident |= flags;
}

// ------------------------------------------------------------------
// ciObject::print
//
// Print debugging output about this ciObject.
//
// Implementation note: dispatch to the virtual print_impl behavior
// for this ciObject.
void ciObject::print(outputStream* st) {
  st->print("<%s", type_string());
  GUARDED_VM_ENTRY(print_impl(st);)
  st->print(" ident=%d %s address=" INTPTR_FORMAT ">", ident(),
        is_scavengable() ? "SCAVENGABLE" : "",
        p2i((address)this));
}

// ------------------------------------------------------------------
// ciObject::print_oop
//
// Print debugging output about the oop this ciObject represents.
void ciObject::print_oop(outputStream* st) {
  if (is_null_object()) {
    st->print_cr("NULL");
  } else if (!is_loaded()) {
    st->print_cr("UNLOADED");
  } else {
    GUARDED_VM_ENTRY(get_oop()->print_on(st);)
  }
}
