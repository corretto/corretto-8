/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_FIELDDESCRIPTOR_HPP
#define SHARE_VM_RUNTIME_FIELDDESCRIPTOR_HPP

#include "oops/constantPool.hpp"
#include "oops/symbol.hpp"
#include "runtime/fieldType.hpp"
#include "utilities/accessFlags.hpp"
#include "utilities/constantTag.hpp"

// A fieldDescriptor describes the attributes of a single field (instance or class variable).
// It needs the class constant pool to work (because it only holds indices into the pool
// rather than the actual info).

class fieldDescriptor VALUE_OBJ_CLASS_SPEC {
 private:
  AccessFlags         _access_flags;
  int                 _index; // the field index
  constantPoolHandle  _cp;

  // update the access_flags for the field in the klass
  void update_klass_field_access_flag() {
    InstanceKlass* ik = field_holder();
    ik->field(index())->set_access_flags(_access_flags.as_short());
  }

  FieldInfo* field() const {
    InstanceKlass* ik = field_holder();
    return ik->field(_index);
  }

 public:
  fieldDescriptor() {
    DEBUG_ONLY(_index = badInt);
  }
  fieldDescriptor(InstanceKlass* ik, int index) {
    DEBUG_ONLY(_index = badInt);
    reinitialize(ik, index);
  }
  Symbol* name() const {
    return field()->name(_cp);
  }
  Symbol* signature() const {
    return field()->signature(_cp);
  }
  InstanceKlass* field_holder()   const    { return _cp->pool_holder(); }
  ConstantPool* constants()       const    { return _cp(); }
  AccessFlags access_flags()      const    { return _access_flags; }
  oop loader()                    const;
  // Offset (in words) of field from start of instanceOop / Klass*
  int offset()                    const    { return field()->offset(); }
  Symbol* generic_signature()     const;
  int index()                     const    { return _index; }
  AnnotationArray* annotations()  const;
  AnnotationArray* type_annotations()  const;

  // Initial field value
  bool has_initial_value()        const    { return field()->initval_index() != 0; }
  int initial_value_index()       const    { return field()->initval_index(); }
  constantTag initial_value_tag() const;  // The tag will return true on one of is_int(), is_long(), is_single(), is_double()
  jint int_initial_value()        const;
  jlong long_initial_value()      const;
  jfloat float_initial_value()    const;
  jdouble double_initial_value()  const;
  oop string_initial_value(TRAPS) const;

  // Field signature type
  BasicType field_type()          const    { return FieldType::basic_type(signature()); }

  // Access flags
  bool is_public()                const    { return access_flags().is_public(); }
  bool is_private()               const    { return access_flags().is_private(); }
  bool is_protected()             const    { return access_flags().is_protected(); }
  bool is_package_private()       const    { return !is_public() && !is_private() && !is_protected(); }

  bool is_static()                const    { return access_flags().is_static(); }
  bool is_final()                 const    { return access_flags().is_final(); }
  bool is_volatile()              const    { return access_flags().is_volatile(); }
  bool is_transient()             const    { return access_flags().is_transient(); }

  bool is_synthetic()             const    { return access_flags().is_synthetic(); }

  bool is_field_access_watched()  const    { return access_flags().is_field_access_watched(); }
  bool is_field_modification_watched() const
                                           { return access_flags().is_field_modification_watched(); }
  bool has_initialized_final_update() const { return access_flags().has_field_initialized_final_update(); }
  bool has_generic_signature()    const    { return access_flags().field_has_generic_signature(); }

  void set_is_field_access_watched(const bool value) {
    _access_flags.set_is_field_access_watched(value);
    update_klass_field_access_flag();
  }

  void set_is_field_modification_watched(const bool value) {
    _access_flags.set_is_field_modification_watched(value);
    update_klass_field_access_flag();
  }

  void set_has_initialized_final_update(const bool value) {
    _access_flags.set_has_field_initialized_final_update(value);
    update_klass_field_access_flag();
  }

  // Initialization
  void reinitialize(InstanceKlass* ik, int index);

  // Print
  void print() { print_on(tty); }
  void print_on(outputStream* st) const         PRODUCT_RETURN;
  void print_on_for(outputStream* st, oop obj)  PRODUCT_RETURN;
  void verify() const                           PRODUCT_RETURN;
};

#endif // SHARE_VM_RUNTIME_FIELDDESCRIPTOR_HPP
