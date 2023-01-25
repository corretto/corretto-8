/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc_implementation/shared/markSweep.inline.hpp"
#include "memory/gcLocker.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klassVtable.hpp"
#include "oops/method.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/copy.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

inline InstanceKlass* klassVtable::ik() const {
  Klass* k = _klass();
  assert(k->oop_is_instance(), "not an InstanceKlass");
  return (InstanceKlass*)k;
}

bool klassVtable::is_preinitialized_vtable() {
  return _klass->is_shared() && !MetaspaceShared::remapped_readwrite();
}


// this function computes the vtable size (including the size needed for miranda
// methods) and the number of miranda methods in this class.
// Note on Miranda methods: Let's say there is a class C that implements
// interface I, and none of C's superclasses implements I.
// Let's say there is an abstract method m in I that neither C
// nor any of its super classes implement (i.e there is no method of any access,
// with the same name and signature as m), then m is a Miranda method which is
// entered as a public abstract method in C's vtable.  From then on it should
// treated as any other public method in C for method over-ride purposes.
void klassVtable::compute_vtable_size_and_num_mirandas(
    int* vtable_length_ret, int* num_new_mirandas,
    GrowableArray<Method*>* all_mirandas, Klass* super,
    Array<Method*>* methods, AccessFlags class_flags,
    Handle classloader, Symbol* classname, Array<Klass*>* local_interfaces,
    TRAPS) {
  No_Safepoint_Verifier nsv;

  // set up default result values
  int vtable_length = 0;

  // start off with super's vtable length
  InstanceKlass* sk = (InstanceKlass*)super;
  vtable_length = super == NULL ? 0 : sk->vtable_length();

  // go thru each method in the methods table to see if it needs a new entry
  int len = methods->length();
  for (int i = 0; i < len; i++) {
    assert(methods->at(i)->is_method(), "must be a Method*");
    methodHandle mh(THREAD, methods->at(i));

    if (needs_new_vtable_entry(mh, super, classloader, classname, class_flags, THREAD)) {
      vtable_length += vtableEntry::size(); // we need a new entry
    }
  }

  GrowableArray<Method*> new_mirandas(20);
  // compute the number of mirandas methods that must be added to the end
  get_mirandas(&new_mirandas, all_mirandas, super, methods, NULL, local_interfaces);
  *num_new_mirandas = new_mirandas.length();

  // Interfaces do not need interface methods in their vtables
  // This includes miranda methods and during later processing, default methods
  if (!class_flags.is_interface()) {
    vtable_length += *num_new_mirandas * vtableEntry::size();
  }

  if (Universe::is_bootstrapping() && vtable_length == 0) {
    // array classes don't have their superclass set correctly during
    // bootstrapping
    vtable_length = Universe::base_vtable_size();
  }

  if (super == NULL && !Universe::is_bootstrapping() &&
      vtable_length != Universe::base_vtable_size()) {
    // Someone is attempting to redefine java.lang.Object incorrectly.  The
    // only way this should happen is from
    // SystemDictionary::resolve_from_stream(), which will detect this later
    // and throw a security exception.  So don't assert here to let
    // the exception occur.
    vtable_length = Universe::base_vtable_size();
  }
  assert(super != NULL || vtable_length == Universe::base_vtable_size(),
         "bad vtable size for class Object");
  assert(vtable_length % vtableEntry::size() == 0, "bad vtable length");
  assert(vtable_length >= Universe::base_vtable_size(), "vtable too small");

  *vtable_length_ret = vtable_length;
}

int klassVtable::index_of(Method* m, int len) const {
  assert(m->has_vtable_index(), "do not ask this of non-vtable methods");
  return m->vtable_index();
}

// Copy super class's vtable to the first part (prefix) of this class's vtable,
// and return the number of entries copied.  Expects that 'super' is the Java
// super class (arrays can have "array" super classes that must be skipped).
int klassVtable::initialize_from_super(KlassHandle super) {
  if (super.is_null()) {
    return 0;
  } else if (is_preinitialized_vtable()) {
    // A shared class' vtable is preinitialized at dump time. No need to copy
    // methods from super class for shared class, as that was already done
    // during archiving time. However, if Jvmti has redefined a class,
    // copy super class's vtable in case the super class has changed.
    return super->vtable()->length();
  } else {
    // copy methods from superKlass
    // can't inherit from array class, so must be InstanceKlass
    assert(super->oop_is_instance(), "must be instance klass");
    InstanceKlass* sk = (InstanceKlass*)super();
    klassVtable* superVtable = sk->vtable();
    assert(superVtable->length() <= _length, "vtable too short");
#ifdef ASSERT
    superVtable->verify(tty, true);
#endif
    superVtable->copy_vtable_to(table());
#ifndef PRODUCT
    if (PrintVtables && Verbose) {
      ResourceMark rm;
      tty->print_cr("copy vtable from %s to %s size %d", sk->internal_name(), klass()->internal_name(), _length);
    }
#endif
    return superVtable->length();
  }
}

//
// Revised lookup semantics   introduced 1.3 (Kestrel beta)
void klassVtable::initialize_vtable(bool checkconstraints, TRAPS) {

  // Note:  Arrays can have intermediate array supers.  Use java_super to skip them.
  KlassHandle super (THREAD, klass()->java_super());
  int nofNewEntries = 0;

  bool is_shared = _klass->is_shared();

  if (PrintVtables && !klass()->oop_is_array()) {
    ResourceMark rm(THREAD);
    tty->print_cr("Initializing: %s", _klass->name()->as_C_string());
  }

#ifdef ASSERT
  oop* end_of_obj = (oop*)_klass() + _klass()->size();
  oop* end_of_vtable = (oop*)&table()[_length];
  assert(end_of_vtable <= end_of_obj, "vtable extends beyond end");
#endif

  if (Universe::is_bootstrapping()) {
    assert(!is_shared, "sanity");
    // just clear everything
    for (int i = 0; i < _length; i++) table()[i].clear();
    return;
  }

  int super_vtable_len = initialize_from_super(super);
  if (klass()->oop_is_array()) {
    assert(super_vtable_len == _length, "arrays shouldn't introduce new methods");
  } else {
    assert(_klass->oop_is_instance(), "must be InstanceKlass");

    Array<Method*>* methods = ik()->methods();
    int len = methods->length();
    int initialized = super_vtable_len;

    // Check each of this class's methods against super;
    // if override, replace in copy of super vtable, otherwise append to end
    for (int i = 0; i < len; i++) {
      // update_inherited_vtable can stop for gc - ensure using handles
      HandleMark hm(THREAD);
      assert(methods->at(i)->is_method(), "must be a Method*");
      methodHandle mh(THREAD, methods->at(i));

      bool needs_new_entry = update_inherited_vtable(ik(), mh, super_vtable_len, -1, checkconstraints, CHECK);

      if (needs_new_entry) {
        put_method_at(mh(), initialized);
        mh()->set_vtable_index(initialized); // set primary vtable index
        initialized++;
      }
    }

    // update vtable with default_methods
    Array<Method*>* default_methods = ik()->default_methods();
    if (default_methods != NULL) {
      len = default_methods->length();
      if (len > 0) {
        Array<int>* def_vtable_indices = NULL;
        if ((def_vtable_indices = ik()->default_vtable_indices()) == NULL) {
          assert(!is_shared, "shared class def_vtable_indices does not exist");
          def_vtable_indices = ik()->create_new_default_vtable_indices(len, CHECK);
        } else {
          assert(def_vtable_indices->length() == len, "reinit vtable len?");
        }
        for (int i = 0; i < len; i++) {
          HandleMark hm(THREAD);
          assert(default_methods->at(i)->is_method(), "must be a Method*");
          methodHandle mh(THREAD, default_methods->at(i));

          bool needs_new_entry = update_inherited_vtable(ik(), mh, super_vtable_len, i, checkconstraints, CHECK);

          // needs new entry
          if (needs_new_entry) {
            put_method_at(mh(), initialized);
            if (is_preinitialized_vtable()) {
              // At runtime initialize_vtable is rerun for a shared class
              // (loaded by the non-boot loader) as part of link_class_impl().
              // The dumptime vtable index should be the same as the runtime index.
              assert(def_vtable_indices->at(i) == initialized,
                     "dump time vtable index is different from runtime index");
            } else {
              def_vtable_indices->at_put(i, initialized); //set vtable index
            }
            initialized++;
          }
        }
      }
    }

    // add miranda methods; it will also return the updated initialized
    // Interfaces do not need interface methods in their vtables
    // This includes miranda methods and during later processing, default methods
    if (!ik()->is_interface()) {
      initialized = fill_in_mirandas(initialized);
    }

    // In class hierarchies where the accessibility is not increasing (i.e., going from private ->
    // package_private -> public/protected), the vtable might actually be smaller than our initial
    // calculation.
    assert(initialized <= _length, "vtable initialization failed");
    for(;initialized < _length; initialized++) {
      put_method_at(NULL, initialized);
    }
    NOT_PRODUCT(verify(tty, true));
  }
}

// Called for cases where a method does not override its superclass' vtable entry
// For bytecodes not produced by javac together it is possible that a method does not override
// the superclass's method, but might indirectly override a super-super class's vtable entry
// If none found, return a null superk, else return the superk of the method this does override
// For public and protected methods: if they override a superclass, they will
// also be overridden themselves appropriately.
// Private methods do not override and are not overridden.
// Package Private methods are trickier:
// e.g. P1.A, pub m
// P2.B extends A, package private m
// P1.C extends B, public m
// P1.C.m needs to override P1.A.m and can not override P2.B.m
// Therefore: all package private methods need their own vtable entries for
// them to be the root of an inheritance overriding decision
// Package private methods may also override other vtable entries
InstanceKlass* klassVtable::find_transitive_override(InstanceKlass* initialsuper, methodHandle target_method,
                            int vtable_index, Handle target_loader, Symbol* target_classname, Thread * THREAD) {
  InstanceKlass* superk = initialsuper;
  while (superk != NULL && superk->super() != NULL) {
    klassVtable* ssVtable = (superk->super())->vtable();
    if (vtable_index < ssVtable->length()) {
      Method* super_method = ssVtable->method_at(vtable_index);
      // get the class holding the matching method
      // make sure you use that class for is_override
      InstanceKlass* supermethodholder = super_method->method_holder();
#ifndef PRODUCT
      Symbol* name= target_method()->name();
      Symbol* signature = target_method()->signature();
      assert(super_method->name() == name && super_method->signature() == signature, "vtable entry name/sig mismatch");
#endif

      if (supermethodholder->is_override(super_method, target_loader, target_classname, THREAD)) {
#ifndef PRODUCT
        if (PrintVtables && Verbose) {
          ResourceMark rm(THREAD);
          char* sig = target_method()->name_and_sig_as_C_string();
          tty->print("transitive overriding superclass %s with %s::%s index %d, original flags: ",
           supermethodholder->internal_name(),
           _klass->internal_name(), sig, vtable_index);
           super_method->access_flags().print_on(tty);
           if (super_method->is_default_method()) {
             tty->print("default ");
           }
           tty->print("overriders flags: ");
           target_method->access_flags().print_on(tty);
           if (target_method->is_default_method()) {
             tty->print("default ");
           }
        }
#endif /*PRODUCT*/
        break; // return found superk
      }
    } else  {
      // super class has no vtable entry here, stop transitive search
      superk = (InstanceKlass*)NULL;
      break;
    }
    // if no override found yet, continue to search up
    superk = InstanceKlass::cast(superk->super());
  }

  return superk;
}

// Update child's copy of super vtable for overrides
// OR return true if a new vtable entry is required.
// Only called for InstanceKlass's, i.e. not for arrays
// If that changed, could not use _klass as handle for klass
bool klassVtable::update_inherited_vtable(InstanceKlass* klass, methodHandle target_method,
                                          int super_vtable_len, int default_index,
                                          bool checkconstraints, TRAPS) {
  ResourceMark rm;
  bool allocate_new = true;
  assert(klass->oop_is_instance(), "must be InstanceKlass");

  Array<int>* def_vtable_indices = NULL;
  bool is_default = false;
  // default methods are concrete methods in superinterfaces which are added to the vtable
  // with their real method_holder
  // Since vtable and itable indices share the same storage, don't touch
  // the default method's real vtable/itable index
  // default_vtable_indices stores the vtable value relative to this inheritor
  if (default_index >= 0 ) {
    is_default = true;
    def_vtable_indices = klass->default_vtable_indices();
    assert(def_vtable_indices != NULL, "def vtable alloc?");
    assert(default_index <= def_vtable_indices->length(), "def vtable len?");
  } else {
    assert(klass == target_method()->method_holder(), "caller resp.");
    // Initialize the method's vtable index to "nonvirtual".
    // If we allocate a vtable entry, we will update it to a non-negative number.
    target_method()->set_vtable_index(Method::nonvirtual_vtable_index);
  }

  // Static and <init> methods are never in
  if (target_method()->is_static() || target_method()->name() ==  vmSymbols::object_initializer_name()) {
    return false;
  }

  if (target_method->is_final_method(klass->access_flags())) {
    // a final method never needs a new entry; final methods can be statically
    // resolved and they have to be present in the vtable only if they override
    // a super's method, in which case they re-use its entry
    allocate_new = false;
  } else if (klass->is_interface()) {
    allocate_new = false;  // see note below in needs_new_vtable_entry
    // An interface never allocates new vtable slots, only inherits old ones.
    // This method will either be assigned its own itable index later,
    // or be assigned an inherited vtable index in the loop below.
    // default methods inherited by classes store their vtable indices
    // in the inheritor's default_vtable_indices
    // default methods inherited by interfaces may already have a
    // valid itable index, if so, don't change it
    // overpass methods in an interface will be assigned an itable index later
    // by an inheriting class
    if (!is_default || !target_method()->has_itable_index()) {
      target_method()->set_vtable_index(Method::pending_itable_index);
    }
  }

  // we need a new entry if there is no superclass
  Klass* super = klass->super();
  if (super == NULL) {
    return allocate_new;
  }

  // private methods in classes always have a new entry in the vtable
  // specification interpretation since classic has
  // private methods not overriding
  // JDK8 adds private methods in interfaces which require invokespecial
  if (target_method()->is_private()) {
    return allocate_new;
  }

  // search through the vtable and update overridden entries
  // Since check_signature_loaders acquires SystemDictionary_lock
  // which can block for gc, once we are in this loop, use handles
  // For classfiles built with >= jdk7, we now look for transitive overrides

  Symbol* name = target_method()->name();
  Symbol* signature = target_method()->signature();

  KlassHandle target_klass(THREAD, target_method()->method_holder());
  if (target_klass == NULL) {
    target_klass = _klass;
  }

  Handle target_loader(THREAD, target_klass->class_loader());

  Symbol* target_classname = target_klass->name();
  for(int i = 0; i < super_vtable_len; i++) {
    Method* super_method;
    if (is_preinitialized_vtable()) {
      // If this is a shared class, the vtable is already in the final state (fully
      // initialized). Need to look at the super's vtable.
      klassVtable* superVtable = super->vtable();
      super_method = superVtable->method_at(i);
    } else {
      super_method = method_at(i);
    }
    // Check if method name matches
    if (super_method->name() == name && super_method->signature() == signature) {

      // get super_klass for method_holder for the found method
      InstanceKlass* super_klass =  super_method->method_holder();

      // private methods are also never overridden
      if (!super_method->is_private() &&
          (is_default
          || ((super_klass->is_override(super_method, target_loader, target_classname, THREAD))
          || ((klass->major_version() >= VTABLE_TRANSITIVE_OVERRIDE_VERSION)
          && ((super_klass = find_transitive_override(super_klass,
                             target_method, i, target_loader,
                             target_classname, THREAD))
                             != (InstanceKlass*)NULL)))))
        {
        // Package private methods always need a new entry to root their own
        // overriding. They may also override other methods.
        if (!target_method()->is_package_private()) {
          allocate_new = false;
        }

        if (checkconstraints) {
        // Override vtable entry if passes loader constraint check
        // if loader constraint checking requested
        // No need to visit his super, since he and his super
        // have already made any needed loader constraints.
        // Since loader constraints are transitive, it is enough
        // to link to the first super, and we get all the others.
          Handle super_loader(THREAD, super_klass->class_loader());

          if (target_loader() != super_loader()) {
            ResourceMark rm(THREAD);
            Symbol* failed_type_symbol =
              SystemDictionary::check_signature_loaders(signature, target_loader,
                                                        super_loader, true,
                                                        CHECK_(false));
            if (failed_type_symbol != NULL) {
              const char* msg = "loader constraint violation: when resolving "
                "overridden method \"%s\" the class loader (instance"
                " of %s) of the current class, %s, and its superclass loader "
                "(instance of %s), have different Class objects for the type "
                "%s used in the signature";
              char* sig = target_method()->name_and_sig_as_C_string();
              const char* loader1 = SystemDictionary::loader_name(target_loader());
              char* current = target_klass->name()->as_C_string();
              const char* loader2 = SystemDictionary::loader_name(super_loader());
              char* failed_type_name = failed_type_symbol->as_C_string();
              size_t buflen = strlen(msg) + strlen(sig) + strlen(loader1) +
                strlen(current) + strlen(loader2) + strlen(failed_type_name);
              char* buf = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, buflen);
              jio_snprintf(buf, buflen, msg, sig, loader1, current, loader2,
                           failed_type_name);
              THROW_MSG_(vmSymbols::java_lang_LinkageError(), buf, false);
            }
          }
       }

       put_method_at(target_method(), i);
       if (!is_default) {
         target_method()->set_vtable_index(i);
       } else {
         if (def_vtable_indices != NULL) {
           if (is_preinitialized_vtable()) {
             // At runtime initialize_vtable is rerun as part of link_class_impl()
             // for a shared class loaded by the non-boot loader.
             // The dumptime vtable index should be the same as the runtime index.
             assert(def_vtable_indices->at(default_index) == i,
                    "dump time vtable index is different from runtime index");
           } else {
             def_vtable_indices->at_put(default_index, i);
           }
         }
         assert(super_method->is_default_method() || super_method->is_overpass()
                || super_method->is_abstract(), "default override error");
       }


#ifndef PRODUCT
        if (PrintVtables && Verbose) {
          ResourceMark rm(THREAD);
          char* sig = target_method()->name_and_sig_as_C_string();
          tty->print("overriding with %s::%s index %d, original flags: ",
           target_klass->internal_name(), sig, i);
           super_method->access_flags().print_on(tty);
           if (super_method->is_default_method()) {
             tty->print("default ");
           }
           if (super_method->is_overpass()) {
             tty->print("overpass");
           }
           tty->print("overriders flags: ");
           target_method->access_flags().print_on(tty);
           if (target_method->is_default_method()) {
             tty->print("default ");
           }
           if (target_method->is_overpass()) {
             tty->print("overpass");
           }
           tty->cr();
        }
#endif /*PRODUCT*/
      } else {
        // allocate_new = true; default. We might override one entry,
        // but not override another. Once we override one, not need new
#ifndef PRODUCT
        if (PrintVtables && Verbose) {
          ResourceMark rm(THREAD);
          char* sig = target_method()->name_and_sig_as_C_string();
          tty->print("NOT overriding with %s::%s index %d, original flags: ",
           target_klass->internal_name(), sig,i);
           super_method->access_flags().print_on(tty);
           if (super_method->is_default_method()) {
             tty->print("default ");
           }
           if (super_method->is_overpass()) {
             tty->print("overpass");
           }
           tty->print("overriders flags: ");
           target_method->access_flags().print_on(tty);
           if (target_method->is_default_method()) {
             tty->print("default ");
           }
           if (target_method->is_overpass()) {
             tty->print("overpass");
           }
           tty->cr();
        }
#endif /*PRODUCT*/
      }
    }
  }
  return allocate_new;
}

void klassVtable::put_method_at(Method* m, int index) {
  if (is_preinitialized_vtable()) {
    // At runtime initialize_vtable is rerun as part of link_class_impl()
    // for shared class loaded by the non-boot loader to obtain the loader
    // constraints based on the runtime classloaders' context. The dumptime
    // method at the vtable index should be the same as the runtime method.
    assert(table()[index].method() == m,
           "archived method is different from the runtime method");
  } else {
#ifndef PRODUCT
    if (PrintVtables && Verbose) {
      ResourceMark rm;
      const char* sig = (m != NULL) ? m->name_and_sig_as_C_string() : "<NULL>";
      tty->print("adding %s at index %d, flags: ", sig, index);
      if (m != NULL) {
        m->access_flags().print_on(tty);
        if (m->is_default_method()) {
          tty->print("default ");
        }
        if (m->is_overpass()) {
          tty->print("overpass");
        }
      }
      tty->cr();
    }
#endif
    table()[index].set(m);
  }
}

// Find out if a method "m" with superclass "super", loader "classloader" and
// name "classname" needs a new vtable entry.  Let P be a class package defined
// by "classloader" and "classname".
// NOTE: The logic used here is very similar to the one used for computing
// the vtables indices for a method. We cannot directly use that function because,
// we allocate the InstanceKlass at load time, and that requires that the
// superclass has been loaded.
// However, the vtable entries are filled in at link time, and therefore
// the superclass' vtable may not yet have been filled in.
bool klassVtable::needs_new_vtable_entry(methodHandle target_method,
                                         Klass* super,
                                         Handle classloader,
                                         Symbol* classname,
                                         AccessFlags class_flags,
                                         TRAPS) {
  if (class_flags.is_interface()) {
    // Interfaces do not use vtables, except for java.lang.Object methods,
    // so there is no point to assigning
    // a vtable index to any of their local methods.  If we refrain from doing this,
    // we can use Method::_vtable_index to hold the itable index
    return false;
  }

  if (target_method->is_final_method(class_flags) ||
      // a final method never needs a new entry; final methods can be statically
      // resolved and they have to be present in the vtable only if they override
      // a super's method, in which case they re-use its entry
      (target_method()->is_static()) ||
      // static methods don't need to be in vtable
      (target_method()->name() ==  vmSymbols::object_initializer_name())
      // <init> is never called dynamically-bound
      ) {
    return false;
  }

  // Concrete interface methods do not need new entries, they override
  // abstract method entries using default inheritance rules
  if (target_method()->method_holder() != NULL &&
      target_method()->method_holder()->is_interface()  &&
      !target_method()->is_abstract() ) {
    return false;
  }

  // we need a new entry if there is no superclass
  if (super == NULL) {
    return true;
  }

  // private methods in classes always have a new entry in the vtable
  // specification interpretation since classic has
  // private methods not overriding
  // JDK8 adds private  methods in interfaces which require invokespecial
  if (target_method()->is_private()) {
    return true;
  }

  // Package private methods always need a new entry to root their own
  // overriding. This allows transitive overriding to work.
  if (target_method()->is_package_private()) {
    return true;
  }

  // search through the super class hierarchy to see if we need
  // a new entry
  ResourceMark rm(THREAD);
  Symbol* name = target_method()->name();
  Symbol* signature = target_method()->signature();
  Klass* k = super;
  Method* super_method = NULL;
  InstanceKlass *holder = NULL;
  Method* recheck_method =  NULL;
  bool found_pkg_prvt_method = false;
  while (k != NULL) {
    // lookup through the hierarchy for a method with matching name and sign.
    super_method = InstanceKlass::cast(k)->lookup_method(name, signature);
    if (super_method == NULL) {
      break; // we still have to search for a matching miranda method
    }
    // get the class holding the matching method
    // make sure you use that class for is_override
    InstanceKlass* superk = super_method->method_holder();
    // we want only instance method matches
    // pretend private methods are not in the super vtable
    // since we do override around them: e.g. a.m pub/b.m private/c.m pub,
    // ignore private, c.m pub does override a.m pub
    // For classes that were not javac'd together, we also do transitive overriding around
    // methods that have less accessibility
    if ((!super_method->is_static()) &&
       (!super_method->is_private())) {
      if (superk->is_override(super_method, classloader, classname, THREAD)) {
        return false;
      // else keep looking for transitive overrides
      }
      // If we get here then one of the super classes has a package private method
      // that will not get overridden because it is in a different package.  But,
      // that package private method does "override" any matching methods in super
      // interfaces, so there will be no miranda vtable entry created.  So, set flag
      // to TRUE for use below, in case there are no methods in super classes that
      // this target method overrides.
      assert(super_method->is_package_private(), "super_method must be package private");
      assert(!superk->is_same_class_package(classloader(), classname),
             "Must be different packages");
      found_pkg_prvt_method = true;
    }

    // Start with lookup result and continue to search up
    k = superk->super(); // haven't found an override match yet; continue to look
  }

  // If found_pkg_prvt_method is set, then the ONLY matching method in the
  // superclasses is package private in another package. That matching method will
  // prevent a miranda vtable entry from being created. Because the target method can not
  // override the package private method in another package, then it needs to be the root
  // for its own vtable entry.
  if (found_pkg_prvt_method) {
     return true;
  }

  // if the target method is public or protected it may have a matching
  // miranda method in the super, whose entry it should re-use.
  // Actually, to handle cases that javac would not generate, we need
  // this check for all access permissions.
  InstanceKlass *sk = InstanceKlass::cast(super);
  if (sk->has_miranda_methods()) {
    if (sk->lookup_method_in_all_interfaces(name, signature, Klass::find_defaults) != NULL) {
      return false; // found a matching miranda; we do not need a new entry
    }
  }
  return true; // found no match; we need a new entry
}

// Support for miranda methods

// get the vtable index of a miranda method with matching "name" and "signature"
int klassVtable::index_of_miranda(Symbol* name, Symbol* signature) {
  // search from the bottom, might be faster
  for (int i = (length() - 1); i >= 0; i--) {
    Method* m = table()[i].method();
    if (is_miranda_entry_at(i) &&
        m->name() == name && m->signature() == signature) {
      return i;
    }
  }
  return Method::invalid_vtable_index;
}

// check if an entry at an index is miranda
// requires that method m at entry be declared ("held") by an interface.
bool klassVtable::is_miranda_entry_at(int i) {
  Method* m = method_at(i);
  Klass* method_holder = m->method_holder();
  InstanceKlass *mhk = InstanceKlass::cast(method_holder);

  // miranda methods are public abstract instance interface methods in a class's vtable
  if (mhk->is_interface()) {
    assert(m->is_public(), "should be public");
    assert(ik()->implements_interface(method_holder) , "this class should implement the interface");
    if (is_miranda(m, ik()->methods(), ik()->default_methods(), ik()->super())) {
      return true;
    }
  }
  return false;
}

// Check if a method is a miranda method, given a class's methods array,
// its default_method table and its super class.
// "Miranda" means an abstract non-private method that would not be
// overridden for the local class.
// A "miranda" method should only include non-private interface
// instance methods, i.e. not private methods, not static methods,
// not default methods (concrete interface methods), not overpass methods.
// If a given class already has a local (including overpass) method, a
// default method, or any of its superclasses has the same which would have
// overridden an abstract method, then this is not a miranda method.
//
// Miranda methods are checked multiple times.
// Pass 1: during class load/class file parsing: before vtable size calculation:
// include superinterface abstract and default methods (non-private instance).
// We include potential default methods to give them space in the vtable.
// During the first run, the current instanceKlass has not yet been
// created, the superclasses and superinterfaces do have instanceKlasses
// but may not have vtables, the default_methods list is empty, no overpasses.
// This is seen by default method creation.
//
// Pass 2: recalculated during vtable initialization: only include abstract methods.
// The goal of pass 2 is to walk through the superinterfaces to see if any of
// the superinterface methods (which were all abstract pre-default methods)
// need to be added to the vtable.
// With the addition of default methods, we have three new challenges:
// overpasses, static interface methods and private interface methods.
// Static and private interface methods do not get added to the vtable and
// are not seen by the method resolution process, so we skip those.
// Overpass methods are already in the vtable, so vtable lookup will
// find them and we don't need to add a miranda method to the end of
// the vtable. So we look for overpass methods and if they are found we
// return false. Note that we inherit our superclasses vtable, so
// the superclass' search also needs to use find_overpass so that if
// one is found we return false.
// False means - we don't need a miranda method added to the vtable.
//
// During the second run, default_methods is set up, so concrete methods from
// superinterfaces with matching names/signatures to default_methods are already
// in the default_methods list and do not need to be appended to the vtable
// as mirandas. Abstract methods may already have been handled via
// overpasses - either local or superclass overpasses, which may be
// in the vtable already.
//
// Pass 3: They are also checked by link resolution and selection,
// for invocation on a method (not interface method) reference that
// resolves to a method with an interface as its method_holder.
// Used as part of walking from the bottom of the vtable to find
// the vtable index for the miranda method.
//
// Part of the Miranda Rights in the US mean that if you do not have
// an attorney one will be appointed for you.
bool klassVtable::is_miranda(Method* m, Array<Method*>* class_methods,
                             Array<Method*>* default_methods, Klass* super) {
  if (m->is_static() || m->is_private() || m->is_overpass()) {
    return false;
  }
  Symbol* name = m->name();
  Symbol* signature = m->signature();

  // First look in local methods to see if already covered
  if (InstanceKlass::find_local_method(class_methods, name, signature,
              Klass::find_overpass, Klass::skip_static, Klass::skip_private) != NULL)
  {
    return false;
  }

  // Check local default methods
  if ((default_methods != NULL) &&
    (InstanceKlass::find_method(default_methods, name, signature) != NULL))
   {
     return false;
   }

  InstanceKlass* cursuper;
  // Iterate on all superclasses, which should have instanceKlasses
  // Note that we explicitly look for overpasses at each level.
  // Overpasses may or may not exist for supers for pass 1,
  // they should have been created for pass 2 and later.

  for (cursuper = InstanceKlass::cast(super); cursuper != NULL;  cursuper = (InstanceKlass*)cursuper->super())
  {
     if (cursuper->find_local_method(name, signature,
           Klass::find_overpass, Klass::skip_static, Klass::skip_private) != NULL) {
       return false;
     }
  }

  return true;
}

// Scans current_interface_methods for miranda methods that do not
// already appear in new_mirandas, or default methods,  and are also not defined-and-non-private
// in super (superclass).  These mirandas are added to all_mirandas if it is
// not null; in addition, those that are not duplicates of miranda methods
// inherited by super from its interfaces are added to new_mirandas.
// Thus, new_mirandas will be the set of mirandas that this class introduces,
// all_mirandas will be the set of all mirandas applicable to this class
// including all defined in superclasses.
void klassVtable::add_new_mirandas_to_lists(
    GrowableArray<Method*>* new_mirandas, GrowableArray<Method*>* all_mirandas,
    Array<Method*>* current_interface_methods, Array<Method*>* class_methods,
    Array<Method*>* default_methods, Klass* super) {

  // iterate thru the current interface's method to see if it a miranda
  int num_methods = current_interface_methods->length();
  for (int i = 0; i < num_methods; i++) {
    Method* im = current_interface_methods->at(i);
    bool is_duplicate = false;
    int num_of_current_mirandas = new_mirandas->length();
    // check for duplicate mirandas in different interfaces we implement
    for (int j = 0; j < num_of_current_mirandas; j++) {
      Method* miranda = new_mirandas->at(j);
      if ((im->name() == miranda->name()) &&
          (im->signature() == miranda->signature())) {
        is_duplicate = true;
        break;
      }
    }

    if (!is_duplicate) { // we don't want duplicate miranda entries in the vtable
      if (is_miranda(im, class_methods, default_methods, super)) { // is it a miranda at all?
        InstanceKlass *sk = InstanceKlass::cast(super);
        // check if it is a duplicate of a super's miranda
        if (sk->lookup_method_in_all_interfaces(im->name(), im->signature(), Klass::find_defaults) == NULL) {
          new_mirandas->append(im);
        }
        if (all_mirandas != NULL) {
          all_mirandas->append(im);
        }
      }
    }
  }
}

void klassVtable::get_mirandas(GrowableArray<Method*>* new_mirandas,
                               GrowableArray<Method*>* all_mirandas,
                               Klass* super, Array<Method*>* class_methods,
                               Array<Method*>* default_methods,
                               Array<Klass*>* local_interfaces) {
  assert((new_mirandas->length() == 0) , "current mirandas must be 0");

  // iterate thru the local interfaces looking for a miranda
  int num_local_ifs = local_interfaces->length();
  for (int i = 0; i < num_local_ifs; i++) {
    InstanceKlass *ik = InstanceKlass::cast(local_interfaces->at(i));
    add_new_mirandas_to_lists(new_mirandas, all_mirandas,
                              ik->methods(), class_methods,
                              default_methods, super);
    // iterate thru each local's super interfaces
    Array<Klass*>* super_ifs = ik->transitive_interfaces();
    int num_super_ifs = super_ifs->length();
    for (int j = 0; j < num_super_ifs; j++) {
      InstanceKlass *sik = InstanceKlass::cast(super_ifs->at(j));
      add_new_mirandas_to_lists(new_mirandas, all_mirandas,
                                sik->methods(), class_methods,
                                default_methods, super);
    }
  }
}

// Discover miranda methods ("miranda" = "interface abstract, no binding"),
// and append them into the vtable starting at index initialized,
// return the new value of initialized.
// Miranda methods use vtable entries, but do not get assigned a vtable_index
// The vtable_index is discovered by searching from the end of the vtable
int klassVtable::fill_in_mirandas(int initialized) {
  GrowableArray<Method*> mirandas(20);
  get_mirandas(&mirandas, NULL, ik()->super(), ik()->methods(),
               ik()->default_methods(), ik()->local_interfaces());
  for (int i = 0; i < mirandas.length(); i++) {
    if (PrintVtables && Verbose) {
      Method* meth = mirandas.at(i);
      ResourceMark rm(Thread::current());
      if (meth != NULL) {
        char* sig = meth->name_and_sig_as_C_string();
        tty->print("fill in mirandas with %s index %d, flags: ",
          sig, initialized);
        meth->access_flags().print_on(tty);
        if (meth->is_default_method()) {
          tty->print("default ");
        }
        tty->cr();
      }
    }
    put_method_at(mirandas.at(i), initialized);
    ++initialized;
  }
  return initialized;
}

// Copy this class's vtable to the vtable beginning at start.
// Used to copy superclass vtable to prefix of subclass's vtable.
void klassVtable::copy_vtable_to(vtableEntry* start) {
  Copy::disjoint_words((HeapWord*)table(), (HeapWord*)start, _length * vtableEntry::size());
}

#if INCLUDE_JVMTI
bool klassVtable::adjust_default_method(int vtable_index, Method* old_method, Method* new_method) {
  // If old_method is default, find this vtable index in default_vtable_indices
  // and replace that method in the _default_methods list
  bool updated = false;

  Array<Method*>* default_methods = ik()->default_methods();
  if (default_methods != NULL) {
    int len = default_methods->length();
    for (int idx = 0; idx < len; idx++) {
      if (vtable_index == ik()->default_vtable_indices()->at(idx)) {
        if (default_methods->at(idx) == old_method) {
          default_methods->at_put(idx, new_method);
          updated = true;
        }
        break;
      }
    }
  }
  return updated;
}

// search the vtable for uses of either obsolete or EMCP methods
void klassVtable::adjust_method_entries(InstanceKlass* holder, bool * trace_name_printed) {
  int prn_enabled = 0;
  for (int index = 0; index < length(); index++) {
    Method* old_method = unchecked_method_at(index);
    if (old_method == NULL || old_method->method_holder() != holder || !old_method->is_old()) {
      continue; // skip uninteresting entries
    }
    assert(!old_method->is_deleted(), "vtable methods may not be deleted");

    Method* new_method = holder->method_with_idnum(old_method->orig_method_idnum());

    assert(new_method != NULL, "method_with_idnum() should not be NULL");
    assert(old_method != new_method, "sanity check");

    put_method_at(new_method, index);
    // For default methods, need to update the _default_methods array
    // which can only have one method entry for a given signature
    bool updated_default = false;
    if (old_method->is_default_method()) {
      updated_default = adjust_default_method(index, old_method, new_method);
    }

    if (RC_TRACE_IN_RANGE(0x00100000, 0x00400000)) {
      if (!(*trace_name_printed)) {
        // RC_TRACE_MESG macro has an embedded ResourceMark
        RC_TRACE_MESG(("adjust: klassname=%s for methods from name=%s",
                       klass()->external_name(),
                       old_method->method_holder()->external_name()));
        *trace_name_printed = true;
      }
      // RC_TRACE macro has an embedded ResourceMark
      RC_TRACE(0x00100000, ("vtable method update: %s(%s), updated default = %s",
                            new_method->name()->as_C_string(),
                            new_method->signature()->as_C_string(),
                            updated_default ? "true" : "false"));
    }
  }
}

// a vtable should never contain old or obsolete methods
bool klassVtable::check_no_old_or_obsolete_entries() {
  for (int i = 0; i < length(); i++) {
    Method* m = unchecked_method_at(i);
    if (m != NULL &&
        (NOT_PRODUCT(!m->is_valid() ||) m->is_old() || m->is_obsolete())) {
      return false;
    }
  }
  return true;
}

void klassVtable::dump_vtable() {
  tty->print_cr("vtable dump --");
  for (int i = 0; i < length(); i++) {
    Method* m = unchecked_method_at(i);
    if (m != NULL) {
      tty->print("      (%5d)  ", i);
      m->access_flags().print_on(tty);
      if (m->is_default_method()) {
        tty->print("default ");
      }
      if (m->is_overpass()) {
        tty->print("overpass");
      }
      tty->print(" --  ");
      m->print_name(tty);
      tty->cr();
    }
  }
}
#endif // INCLUDE_JVMTI

// CDS/RedefineClasses support - clear vtables so they can be reinitialized
void klassVtable::clear_vtable() {
  for (int i = 0; i < _length; i++) table()[i].clear();
}

bool klassVtable::is_initialized() {
  return _length == 0 || table()[0].method() != NULL;
}

//-----------------------------------------------------------------------------------------
// Itable code

// Initialize a itableMethodEntry
void itableMethodEntry::initialize(Method* m) {
  if (m == NULL) return;

  if (MetaspaceShared::is_in_shared_space((void*)&_method) &&
     !MetaspaceShared::remapped_readwrite()) {
    // At runtime initialize_itable is rerun as part of link_class_impl()
    // for a shared class loaded by the non-boot loader.
    // The dumptime itable method entry should be the same as the runtime entry.
    assert(_method == m, "sanity");
  } else {
    _method = m;
  }
}

klassItable::klassItable(instanceKlassHandle klass) {
  _klass = klass;

  if (klass->itable_length() > 0) {
    itableOffsetEntry* offset_entry = (itableOffsetEntry*)klass->start_of_itable();
    if (offset_entry  != NULL && offset_entry->interface_klass() != NULL) { // Check that itable is initialized
      // First offset entry points to the first method_entry
      intptr_t* method_entry  = (intptr_t *)(((address)klass()) + offset_entry->offset());
      intptr_t* end         = klass->end_of_itable();

      _table_offset      = (intptr_t*)offset_entry - (intptr_t*)klass();
      _size_offset_table = (method_entry - ((intptr_t*)offset_entry)) / itableOffsetEntry::size();
      _size_method_table = (end - method_entry)                  / itableMethodEntry::size();
      assert(_table_offset >= 0 && _size_offset_table >= 0 && _size_method_table >= 0, "wrong computation");
      return;
    }
  }

  // The length of the itable was either zero, or it has not yet been initialized.
  _table_offset      = 0;
  _size_offset_table = 0;
  _size_method_table = 0;
}

static int initialize_count = 0;

// Initialization
void klassItable::initialize_itable(bool checkconstraints, TRAPS) {
  if (_klass->is_interface()) {
    // This needs to go after vtable indices are assigned but
    // before implementors need to know the number of itable indices.
    assign_itable_indices_for_interface(_klass());
  }

  // Cannot be setup doing bootstrapping, interfaces don't have
  // itables, and klass with only ones entry have empty itables
  if (Universe::is_bootstrapping() ||
      _klass->is_interface() ||
      _klass->itable_length() == itableOffsetEntry::size()) return;

  // There's alway an extra itable entry so we can null-terminate it.
  guarantee(size_offset_table() >= 1, "too small");
  int num_interfaces = size_offset_table() - 1;
  if (num_interfaces > 0) {
    if (TraceItables) tty->print_cr("%3d: Initializing itables for %s", ++initialize_count,
                                    _klass->name()->as_C_string());


    // Iterate through all interfaces
    int i;
    for(i = 0; i < num_interfaces; i++) {
      itableOffsetEntry* ioe = offset_entry(i);
      HandleMark hm(THREAD);
      KlassHandle interf_h (THREAD, ioe->interface_klass());
      assert(interf_h() != NULL && ioe->offset() != 0, "bad offset entry in itable");
      initialize_itable_for_interface(ioe->offset(), interf_h, checkconstraints, CHECK);
    }

  }
  // Check that the last entry is empty
  itableOffsetEntry* ioe = offset_entry(size_offset_table() - 1);
  guarantee(ioe->interface_klass() == NULL && ioe->offset() == 0, "terminator entry missing");
}


inline bool interface_method_needs_itable_index(Method* m) {
  if (m->is_static())           return false;   // e.g., Stream.empty
  if (m->is_initializer())      return false;   // <init> or <clinit>
  // If an interface redeclares a method from java.lang.Object,
  // it should already have a vtable index, don't touch it.
  // e.g., CharSequence.toString (from initialize_vtable)
  // if (m->has_vtable_index())  return false; // NO!
  return true;
}

int klassItable::assign_itable_indices_for_interface(Klass* klass) {
  // an interface does not have an itable, but its methods need to be numbered
  if (TraceItables) tty->print_cr("%3d: Initializing itable indices for interface %s", ++initialize_count,
                                  klass->name()->as_C_string());
  Array<Method*>* methods = InstanceKlass::cast(klass)->methods();
  int nof_methods = methods->length();
  int ime_num = 0;
  for (int i = 0; i < nof_methods; i++) {
    Method* m = methods->at(i);
    if (interface_method_needs_itable_index(m)) {
      assert(!m->is_final_method(), "no final interface methods");
      // If m is already assigned a vtable index, do not disturb it.
      if (TraceItables && Verbose) {
        ResourceMark rm;
        const char* sig = (m != NULL) ? m->name_and_sig_as_C_string() : "<NULL>";
        if (m->has_vtable_index()) {
          tty->print("vtable index %d for method: %s, flags: ", m->vtable_index(), sig);
        } else {
          tty->print("itable index %d for method: %s, flags: ", ime_num, sig);
        }
        if (m != NULL) {
          m->access_flags().print_on(tty);
          if (m->is_default_method()) {
            tty->print("default ");
          }
          if (m->is_overpass()) {
            tty->print("overpass");
          }
        }
        tty->cr();
      }
      if (!m->has_vtable_index()) {
        // A shared method could have an initialized itable_index that
        // is < 0.
        assert(m->vtable_index() == Method::pending_itable_index ||
               m->is_shared(),
               "set by initialize_vtable");
        m->set_itable_index(ime_num);
        // Progress to next itable entry
        ime_num++;
      }
    }
  }
  assert(ime_num == method_count_for_interface(klass), "proper sizing");
  return ime_num;
}

int klassItable::method_count_for_interface(Klass* interf) {
  assert(interf->oop_is_instance(), "must be");
  assert(interf->is_interface(), "must be");
  Array<Method*>* methods = InstanceKlass::cast(interf)->methods();
  int nof_methods = methods->length();
  int length = 0;
  while (nof_methods > 0) {
    Method* m = methods->at(nof_methods-1);
    if (m->has_itable_index()) {
      length = m->itable_index() + 1;
      break;
    }
    nof_methods -= 1;
  }
#ifdef ASSERT
  int nof_methods_copy = nof_methods;
  while (nof_methods_copy > 0) {
    Method* mm = methods->at(--nof_methods_copy);
    assert(!mm->has_itable_index() || mm->itable_index() < length, "");
  }
#endif //ASSERT
  // return the rightmost itable index, plus one; or 0 if no methods have
  // itable indices
  return length;
}


void klassItable::initialize_itable_for_interface(int method_table_offset, KlassHandle interf_h, bool checkconstraints, TRAPS) {
  Array<Method*>* methods = InstanceKlass::cast(interf_h())->methods();
  int nof_methods = methods->length();
  HandleMark hm;
  Handle interface_loader (THREAD, InstanceKlass::cast(interf_h())->class_loader());

  int ime_count = method_count_for_interface(interf_h());
  for (int i = 0; i < nof_methods; i++) {
    Method* m = methods->at(i);
    methodHandle target;
    if (m->has_itable_index()) {
      // This search must match the runtime resolution, i.e. selection search for invokeinterface
      // to correctly enforce loader constraints for interface method inheritance
      LinkResolver::lookup_instance_method_in_klasses(target, _klass, m->name(), m->signature(), CHECK);
    }
    if (target == NULL || !target->is_public() || target->is_abstract()) {
      // Entry does not resolve. Leave it empty for AbstractMethodError.
        if (!(target == NULL) && !target->is_public()) {
          // Stuff an IllegalAccessError throwing method in there instead.
          itableOffsetEntry::method_entry(_klass(), method_table_offset)[m->itable_index()].
              initialize(Universe::throw_illegal_access_error());
        }
    } else {
      // Entry did resolve, check loader constraints before initializing
      // if checkconstraints requested
      if (checkconstraints) {
        Handle method_holder_loader (THREAD, target->method_holder()->class_loader());
        if (method_holder_loader() != interface_loader()) {
          ResourceMark rm(THREAD);
          Symbol* failed_type_symbol =
            SystemDictionary::check_signature_loaders(m->signature(),
                                                      method_holder_loader,
                                                      interface_loader,
                                                      true, CHECK);
          if (failed_type_symbol != NULL) {
            const char* msg = "loader constraint violation in interface "
              "itable initialization: when resolving method \"%s\" the class"
              " loader (instance of %s) of the current class, %s, "
              "and the class loader (instance of %s) for interface "
              "%s have different Class objects for the type %s "
              "used in the signature";
            char* sig = target()->name_and_sig_as_C_string();
            const char* loader1 = SystemDictionary::loader_name(method_holder_loader());
            char* current = _klass->name()->as_C_string();
            const char* loader2 = SystemDictionary::loader_name(interface_loader());
            char* iface = InstanceKlass::cast(interf_h())->name()->as_C_string();
            char* failed_type_name = failed_type_symbol->as_C_string();
            size_t buflen = strlen(msg) + strlen(sig) + strlen(loader1) +
              strlen(current) + strlen(loader2) + strlen(iface) +
              strlen(failed_type_name);
            char* buf = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, buflen);
            jio_snprintf(buf, buflen, msg, sig, loader1, current, loader2,
                         iface, failed_type_name);
            THROW_MSG(vmSymbols::java_lang_LinkageError(), buf);
          }
        }
      }

      // ime may have moved during GC so recalculate address
      int ime_num = m->itable_index();
      assert(ime_num < ime_count, "oob");
      itableOffsetEntry::method_entry(_klass(), method_table_offset)[ime_num].initialize(target());
      if (TraceItables && Verbose) {
        ResourceMark rm(THREAD);
        if (target() != NULL) {
          char* sig = target()->name_and_sig_as_C_string();
          tty->print("interface: %s, ime_num: %d, target: %s, method_holder: %s ",
                    interf_h()->internal_name(), ime_num, sig,
                    target()->method_holder()->internal_name());
          tty->print("target_method flags: ");
          target()->access_flags().print_on(tty);
          if (target()->is_default_method()) {
            tty->print("default ");
          }
          tty->cr();
        }
      }
    }
  }
}

// Update entry for specific Method*
void klassItable::initialize_with_method(Method* m) {
  itableMethodEntry* ime = method_entry(0);
  for(int i = 0; i < _size_method_table; i++) {
    if (ime->method() == m) {
      ime->initialize(m);
    }
    ime++;
  }
}

#if INCLUDE_JVMTI
// search the itable for uses of either obsolete or EMCP methods
void klassItable::adjust_method_entries(InstanceKlass* holder, bool * trace_name_printed) {

  itableMethodEntry* ime = method_entry(0);
  for (int i = 0; i < _size_method_table; i++, ime++) {
    Method* old_method = ime->method();
    if (old_method == NULL || old_method->method_holder() != holder || !old_method->is_old()) {
      continue; // skip uninteresting entries
    }
    assert(!old_method->is_deleted(), "itable methods may not be deleted");

    Method* new_method = holder->method_with_idnum(old_method->orig_method_idnum());

    assert(new_method != NULL, "method_with_idnum() should not be NULL");
    assert(old_method != new_method, "sanity check");

    ime->initialize(new_method);

    if (RC_TRACE_IN_RANGE(0x00100000, 0x00400000)) {
      if (!(*trace_name_printed)) {
        // RC_TRACE_MESG macro has an embedded ResourceMark
        RC_TRACE_MESG(("adjust: name=%s",
          old_method->method_holder()->external_name()));
        *trace_name_printed = true;
      }
      // RC_TRACE macro has an embedded ResourceMark
      RC_TRACE(0x00200000, ("itable method update: %s(%s)",
        new_method->name()->as_C_string(),
        new_method->signature()->as_C_string()));
    }
  }
}

// an itable should never contain old or obsolete methods
bool klassItable::check_no_old_or_obsolete_entries() {
  itableMethodEntry* ime = method_entry(0);
  for (int i = 0; i < _size_method_table; i++) {
    Method* m = ime->method();
    if (m != NULL &&
        (NOT_PRODUCT(!m->is_valid() ||) m->is_old() || m->is_obsolete())) {
      return false;
    }
    ime++;
  }
  return true;
}

void klassItable::dump_itable() {
  itableMethodEntry* ime = method_entry(0);
  tty->print_cr("itable dump --");
  for (int i = 0; i < _size_method_table; i++) {
    Method* m = ime->method();
    if (m != NULL) {
      tty->print("      (%5d)  ", i);
      m->access_flags().print_on(tty);
      if (m->is_default_method()) {
        tty->print("default ");
      }
      tty->print(" --  ");
      m->print_name(tty);
      tty->cr();
    }
    ime++;
  }
}
#endif // INCLUDE_JVMTI

// Setup
class InterfaceVisiterClosure : public StackObj {
 public:
  virtual void doit(Klass* intf, int method_count) = 0;
};

// Visit all interfaces with at least one itable method
void visit_all_interfaces(Array<Klass*>* transitive_intf, InterfaceVisiterClosure *blk) {
  // Handle array argument
  for(int i = 0; i < transitive_intf->length(); i++) {
    Klass* intf = transitive_intf->at(i);
    assert(intf->is_interface(), "sanity check");

    // Find no. of itable methods
    int method_count = 0;
    // method_count = klassItable::method_count_for_interface(intf);
    Array<Method*>* methods = InstanceKlass::cast(intf)->methods();
    if (methods->length() > 0) {
      for (int i = methods->length(); --i >= 0; ) {
        if (interface_method_needs_itable_index(methods->at(i))) {
          method_count++;
        }
      }
    }

    // Visit all interfaces which either have any methods or can participate in receiver type check.
    // We do not bother to count methods in transitive interfaces, although that would allow us to skip
    // this step in the rare case of a zero-method interface extending another zero-method interface.
    if (method_count > 0 || InstanceKlass::cast(intf)->transitive_interfaces()->length() > 0) {
      blk->doit(intf, method_count);
    }
  }
}

class CountInterfacesClosure : public InterfaceVisiterClosure {
 private:
  int _nof_methods;
  int _nof_interfaces;
 public:
   CountInterfacesClosure() { _nof_methods = 0; _nof_interfaces = 0; }

   int nof_methods() const    { return _nof_methods; }
   int nof_interfaces() const { return _nof_interfaces; }

   void doit(Klass* intf, int method_count) { _nof_methods += method_count; _nof_interfaces++; }
};

class SetupItableClosure : public InterfaceVisiterClosure  {
 private:
  itableOffsetEntry* _offset_entry;
  itableMethodEntry* _method_entry;
  address            _klass_begin;
 public:
  SetupItableClosure(address klass_begin, itableOffsetEntry* offset_entry, itableMethodEntry* method_entry) {
    _klass_begin  = klass_begin;
    _offset_entry = offset_entry;
    _method_entry = method_entry;
  }

  itableMethodEntry* method_entry() const { return _method_entry; }

  void doit(Klass* intf, int method_count) {
    int offset = ((address)_method_entry) - _klass_begin;
    _offset_entry->initialize(intf, offset);
    _offset_entry++;
    _method_entry += method_count;
  }
};

int klassItable::compute_itable_size(Array<Klass*>* transitive_interfaces) {
  // Count no of interfaces and total number of interface methods
  CountInterfacesClosure cic;
  visit_all_interfaces(transitive_interfaces, &cic);

  // There's alway an extra itable entry so we can null-terminate it.
  int itable_size = calc_itable_size(cic.nof_interfaces() + 1, cic.nof_methods());

  // Statistics
  update_stats(itable_size * HeapWordSize);

  return itable_size;
}


// Fill out offset table and interface klasses into the itable space
void klassItable::setup_itable_offset_table(instanceKlassHandle klass) {
  if (klass->itable_length() == 0) return;
  assert(!klass->is_interface(), "Should have zero length itable");

  // Count no of interfaces and total number of interface methods
  CountInterfacesClosure cic;
  visit_all_interfaces(klass->transitive_interfaces(), &cic);
  int nof_methods    = cic.nof_methods();
  int nof_interfaces = cic.nof_interfaces();

  // Add one extra entry so we can null-terminate the table
  nof_interfaces++;

  assert(compute_itable_size(klass->transitive_interfaces()) ==
         calc_itable_size(nof_interfaces, nof_methods),
         "mismatch calculation of itable size");

  // Fill-out offset table
  itableOffsetEntry* ioe = (itableOffsetEntry*)klass->start_of_itable();
  itableMethodEntry* ime = (itableMethodEntry*)(ioe + nof_interfaces);
  intptr_t* end               = klass->end_of_itable();
  assert((oop*)(ime + nof_methods) <= (oop*)klass->start_of_nonstatic_oop_maps(), "wrong offset calculation (1)");
  assert((oop*)(end) == (oop*)(ime + nof_methods),                      "wrong offset calculation (2)");

  // Visit all interfaces and initialize itable offset table
  SetupItableClosure sic((address)klass(), ioe, ime);
  visit_all_interfaces(klass->transitive_interfaces(), &sic);

#ifdef ASSERT
  ime  = sic.method_entry();
  oop* v = (oop*) klass->end_of_itable();
  assert( (oop*)(ime) == v, "wrong offset calculation (2)");
#endif
}


// inverse to itable_index
Method* klassItable::method_for_itable_index(Klass* intf, int itable_index) {
  assert(InstanceKlass::cast(intf)->is_interface(), "sanity check");
  assert(intf->verify_itable_index(itable_index), "");
  Array<Method*>* methods = InstanceKlass::cast(intf)->methods();

  if (itable_index < 0 || itable_index >= method_count_for_interface(intf))
    return NULL;                // help caller defend against bad indices

  int index = itable_index;
  Method* m = methods->at(index);
  int index2 = -1;
  while (!m->has_itable_index() ||
         (index2 = m->itable_index()) != itable_index) {
    assert(index2 < itable_index, "monotonic");
    if (++index == methods->length())
      return NULL;
    m = methods->at(index);
  }
  assert(m->itable_index() == itable_index, "correct inverse");

  return m;
}

void klassVtable::verify(outputStream* st, bool forced) {
  // make sure table is initialized
  if (!Universe::is_fully_initialized()) return;
#ifndef PRODUCT
  // avoid redundant verifies
  if (!forced && _verify_count == Universe::verify_count()) return;
  _verify_count = Universe::verify_count();
#endif
  oop* end_of_obj = (oop*)_klass() + _klass()->size();
  oop* end_of_vtable = (oop *)&table()[_length];
  if (end_of_vtable > end_of_obj) {
    fatal(err_msg("klass %s: klass object too short (vtable extends beyond "
                  "end)", _klass->internal_name()));
  }

  for (int i = 0; i < _length; i++) table()[i].verify(this, st);
  // verify consistency with superKlass vtable
  Klass* super = _klass->super();
  if (super != NULL) {
    InstanceKlass* sk = InstanceKlass::cast(super);
    klassVtable* vt = sk->vtable();
    for (int i = 0; i < vt->length(); i++) {
      verify_against(st, vt, i);
    }
  }
}

void klassVtable::verify_against(outputStream* st, klassVtable* vt, int index) {
  vtableEntry* vte = &vt->table()[index];
  if (vte->method()->name()      != table()[index].method()->name() ||
      vte->method()->signature() != table()[index].method()->signature()) {
    fatal("mismatched name/signature of vtable entries");
  }
}

#ifndef PRODUCT
void klassVtable::print() {
  ResourceMark rm;
  tty->print("klassVtable for klass %s (length %d):\n", _klass->internal_name(), length());
  for (int i = 0; i < length(); i++) {
    table()[i].print();
    tty->cr();
  }
}
#endif

void vtableEntry::verify(klassVtable* vt, outputStream* st) {
  NOT_PRODUCT(FlagSetting fs(IgnoreLockingAssertions, true));
  assert(method() != NULL, "must have set method");
  method()->verify();
  // we sub_type, because it could be a miranda method
  if (!vt->klass()->is_subtype_of(method()->method_holder())) {
#ifndef PRODUCT
    print();
#endif
    fatal(err_msg("vtableEntry " PTR_FORMAT ": method is from subclass", this));
  }
}

#ifndef PRODUCT

void vtableEntry::print() {
  ResourceMark rm;
  tty->print("vtableEntry %s: ", method()->name()->as_C_string());
  if (Verbose) {
    tty->print("m %#lx ", (address)method());
  }
}

class VtableStats : AllStatic {
 public:
  static int no_klasses;                // # classes with vtables
  static int no_array_klasses;          // # array classes
  static int no_instance_klasses;       // # instanceKlasses
  static int sum_of_vtable_len;         // total # of vtable entries
  static int sum_of_array_vtable_len;   // total # of vtable entries in array klasses only
  static int fixed;                     // total fixed overhead in bytes
  static int filler;                    // overhead caused by filler bytes
  static int entries;                   // total bytes consumed by vtable entries
  static int array_entries;             // total bytes consumed by array vtable entries

  static void do_class(Klass* k) {
    Klass* kl = k;
    klassVtable* vt = kl->vtable();
    if (vt == NULL) return;
    no_klasses++;
    if (kl->oop_is_instance()) {
      no_instance_klasses++;
      kl->array_klasses_do(do_class);
    }
    if (kl->oop_is_array()) {
      no_array_klasses++;
      sum_of_array_vtable_len += vt->length();
    }
    sum_of_vtable_len += vt->length();
  }

  static void compute() {
    SystemDictionary::classes_do(do_class);
    fixed  = no_klasses * oopSize;      // vtable length
    // filler size is a conservative approximation
    filler = oopSize * (no_klasses - no_instance_klasses) * (sizeof(InstanceKlass) - sizeof(ArrayKlass) - 1);
    entries = sizeof(vtableEntry) * sum_of_vtable_len;
    array_entries = sizeof(vtableEntry) * sum_of_array_vtable_len;
  }
};

int VtableStats::no_klasses = 0;
int VtableStats::no_array_klasses = 0;
int VtableStats::no_instance_klasses = 0;
int VtableStats::sum_of_vtable_len = 0;
int VtableStats::sum_of_array_vtable_len = 0;
int VtableStats::fixed = 0;
int VtableStats::filler = 0;
int VtableStats::entries = 0;
int VtableStats::array_entries = 0;

void klassVtable::print_statistics() {
  ResourceMark rm;
  HandleMark hm;
  VtableStats::compute();
  tty->print_cr("vtable statistics:");
  tty->print_cr("%6d classes (%d instance, %d array)", VtableStats::no_klasses, VtableStats::no_instance_klasses, VtableStats::no_array_klasses);
  int total = VtableStats::fixed + VtableStats::filler + VtableStats::entries;
  tty->print_cr("%6d bytes fixed overhead (refs + vtable object header)", VtableStats::fixed);
  tty->print_cr("%6d bytes filler overhead", VtableStats::filler);
  tty->print_cr("%6d bytes for vtable entries (%d for arrays)", VtableStats::entries, VtableStats::array_entries);
  tty->print_cr("%6d bytes total", total);
}

int  klassItable::_total_classes;   // Total no. of classes with itables
long klassItable::_total_size;      // Total no. of bytes used for itables

void klassItable::print_statistics() {
 tty->print_cr("itable statistics:");
 tty->print_cr("%6d classes with itables", _total_classes);
 tty->print_cr("%6d K uses for itables (average by class: %d bytes)", _total_size / K, _total_size / _total_classes);
}

#endif // PRODUCT
