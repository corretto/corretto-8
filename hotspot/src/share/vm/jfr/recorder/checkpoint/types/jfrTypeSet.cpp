/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "jfr/jfr.hpp"
#include "jfr/jni/jfrGetAllEventClasses.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSet.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSetUtils.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSetWriter.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/utilities/jfrHashtable.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/accessFlags.hpp"

// incremented on each checkpoint
static u8 checkpoint_id = 0;
static JfrCheckpointWriter* _writer = NULL;
static int primitives_count = 9;

// creates a unique id by combining a checkpoint relative symbol id (2^24)
// with the current checkpoint id (2^40)
#define CREATE_SYMBOL_ID(sym_id) (((u8)((checkpoint_id << 24) | sym_id)))
#define CREATE_PACKAGE_ID(pkg_id) (((u8)((checkpoint_id << 24) | pkg_id)))

typedef const Klass* KlassPtr;
typedef const ClassLoaderData* CldPtr;
typedef const Method* MethodPtr;
typedef const Symbol* SymbolPtr;
typedef const JfrSymbolId::SymbolEntry* SymbolEntryPtr;
typedef const JfrSymbolId::CStringEntry* CStringEntryPtr;

static traceid create_symbol_id(traceid artifact_id) {
  return artifact_id != 0 ? CREATE_SYMBOL_ID(artifact_id) : 0;
}

static bool is_initial_typeset_for_chunk(bool class_unload) {
  return !class_unload;
}

static traceid mark_symbol(Symbol* symbol, JfrArtifactSet* artifacts) {
  return symbol != NULL ? create_symbol_id(artifacts->mark(symbol)) : 0;
}

static const char* primitive_name(KlassPtr type_array_klass) {
  switch (type_array_klass->name()->base()[1]) {
    case JVM_SIGNATURE_BOOLEAN: return "boolean";
    case JVM_SIGNATURE_BYTE: return "byte";
    case JVM_SIGNATURE_CHAR: return "char";
    case JVM_SIGNATURE_SHORT: return "short";
    case JVM_SIGNATURE_INT: return "int";
    case JVM_SIGNATURE_LONG: return "long";
    case JVM_SIGNATURE_FLOAT: return "float";
    case JVM_SIGNATURE_DOUBLE: return "double";
  }
  assert(false, "invalid type array klass");
  return NULL;
}

static Symbol* primitive_symbol(KlassPtr type_array_klass) {
  if (type_array_klass == NULL) {
    // void.class
    static Symbol* const void_class_name = SymbolTable::probe("void", 4);
    assert(void_class_name != NULL, "invariant");
    return void_class_name;
  }
  const char* const primitive_type_str = primitive_name(type_array_klass);
  assert(primitive_type_str != NULL, "invariant");
  Symbol* const primitive_type_sym = SymbolTable::probe(primitive_type_str, (int)strlen(primitive_type_str));
  assert(primitive_type_sym != NULL, "invariant");
  return primitive_type_sym;
}

inline uintptr_t package_name_hash(const char *s) {
  uintptr_t val = 0;
  while (*s != 0) {
    val = *s++ + 31 * val;
  }
  return val;
}

static traceid package_id(KlassPtr klass, JfrArtifactSet* artifacts) {
  assert(klass != NULL, "invariant");
  char* klass_name = klass->name()->as_C_string(); // uses ResourceMark declared in JfrTypeSet::serialize()
  const char* pkg_name = ClassLoader::package_from_name(klass_name, NULL);
  if (pkg_name == NULL) {
    return 0;
  }
  return CREATE_PACKAGE_ID(artifacts->markPackage(pkg_name, package_name_hash(pkg_name)));
}

static traceid cld_id(CldPtr cld) {
  assert(cld != NULL, "invariant");
  return cld->is_anonymous() ? 0 : TRACE_ID(cld);
}

static u4 get_primitive_flags() {
  return JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC;
}

static void tag_leakp_klass_artifacts(KlassPtr k, bool class_unload) {
  assert(k != NULL, "invariant");
  CldPtr cld = k->class_loader_data();
  assert(cld != NULL, "invariant");
  if (!cld->is_anonymous()) {
    tag_leakp_artifact(cld, class_unload);
  }
}

class TagLeakpKlassArtifact {
  bool _class_unload;
 public:
  TagLeakpKlassArtifact(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(KlassPtr klass) {
    if (_class_unload) {
      if (LEAKP_USED_THIS_EPOCH(klass)) {
        tag_leakp_klass_artifacts(klass, _class_unload);
      }
    } else {
      if (LEAKP_USED_PREV_EPOCH(klass)) {
        tag_leakp_klass_artifacts(klass, _class_unload);
      }
    }
    return true;
  }
};

/*
 * In C++03, functions used as template parameters must have external linkage;
 * this restriction was removed in C++11. Change back to "static" and
 * rename functions when C++11 becomes available.
 *
 * The weird naming is an effort to decrease the risk of name clashes.
 */

int write__artifact__klass(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* k) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  assert(k != NULL, "invariant");
  KlassPtr klass = (KlassPtr)k;
  traceid pkg_id = 0;
  KlassPtr theklass = klass;
  if (theklass->oop_is_objArray()) {
    const ObjArrayKlass* obj_arr_klass = ObjArrayKlass::cast((Klass*)klass);
    theklass = obj_arr_klass->bottom_klass();
  }
  if (theklass->oop_is_instance()) {
    pkg_id = package_id(theklass, artifacts);
  } else {
    assert(theklass->oop_is_typeArray(), "invariant");
  }
  const traceid symbol_id = artifacts->mark(klass);
  assert(symbol_id > 0, "need to have an address for symbol!");
  writer->write(TRACE_ID(klass));
  writer->write(cld_id(klass->class_loader_data()));
  writer->write((traceid)CREATE_SYMBOL_ID(symbol_id));
  writer->write(pkg_id);
  writer->write((s4)klass->access_flags().get_flags());
  return 1;
}

typedef LeakPredicate<KlassPtr> LeakKlassPredicate;
typedef JfrPredicatedArtifactWriterImplHost<KlassPtr, LeakKlassPredicate, write__artifact__klass> LeakKlassWriterImpl;
typedef JfrArtifactWriterHost<LeakKlassWriterImpl, TYPE_CLASS> LeakKlassWriter;
typedef JfrArtifactWriterImplHost<KlassPtr, write__artifact__klass> KlassWriterImpl;
typedef JfrArtifactWriterHost<KlassWriterImpl, TYPE_CLASS> KlassWriter;

int write__artifact__method(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* m) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  assert(m != NULL, "invariant");
  MethodPtr method = (MethodPtr)m;
  const traceid method_name_symbol_id = artifacts->mark(method->name());
  assert(method_name_symbol_id > 0, "invariant");
  const traceid method_sig_symbol_id = artifacts->mark(method->signature());
  assert(method_sig_symbol_id > 0, "invariant");
  KlassPtr klass = method->method_holder();
  assert(klass != NULL, "invariant");
  assert(METHOD_USED_ANY_EPOCH(klass), "invariant");
  writer->write((u8)METHOD_ID(klass, method));
  writer->write((u8)TRACE_ID(klass));
  writer->write((u8)CREATE_SYMBOL_ID(method_name_symbol_id));
  writer->write((u8)CREATE_SYMBOL_ID(method_sig_symbol_id));
  writer->write((u2)method->access_flags().get_flags());
  writer->write(const_cast<Method*>(method)->is_hidden() ? (u1)1 : (u1)0);
  return 1;
}

typedef JfrArtifactWriterImplHost<MethodPtr, write__artifact__method> MethodWriterImplTarget;
typedef JfrArtifactWriterHost<MethodWriterImplTarget, TYPE_METHOD> MethodWriterImpl;

int write__artifact__package(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* p) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  assert(p != NULL, "invariant");

  CStringEntryPtr entry = (CStringEntryPtr)p;
  const traceid package_name_symbol_id = artifacts->mark(entry->value(), package_name_hash(entry->value()));
  assert(package_name_symbol_id > 0, "invariant");
  writer->write((traceid)CREATE_PACKAGE_ID(entry->id()));
  writer->write((traceid)CREATE_SYMBOL_ID(package_name_symbol_id));
  writer->write((bool)true); // exported
  return 1;
}

int write__artifact__classloader(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* c) {
  assert(c != NULL, "invariant");
  CldPtr cld = (CldPtr)c;
  assert(!cld->is_anonymous(), "invariant");
  const traceid cld_id = TRACE_ID(cld);
  // class loader type
  const Klass* class_loader_klass = cld->class_loader() != NULL ? cld->class_loader()->klass() : NULL;
  if (class_loader_klass == NULL) {
    // (primordial) boot class loader
    writer->write(cld_id); // class loader instance id
    writer->write((traceid)0);  // class loader type id (absence of)
    writer->write((traceid)CREATE_SYMBOL_ID(1)); // 1 maps to synthetic name -> "bootstrap"
  } else {
    Symbol* symbol_name = class_loader_klass->name();
    const traceid symbol_name_id = symbol_name != NULL ? artifacts->mark(symbol_name) : 0;
    writer->write(cld_id); // class loader instance id
    writer->write(TRACE_ID(class_loader_klass)); // class loader type id
    writer->write(symbol_name_id == 0 ? (traceid)0 :
      (traceid)CREATE_SYMBOL_ID(symbol_name_id)); // class loader instance name
  }
  return 1;
}

typedef LeakPredicate<CldPtr> LeakCldPredicate;
int _compare_cld_ptr_(CldPtr const& lhs, CldPtr const& rhs) { return lhs > rhs ? 1 : (lhs < rhs) ? -1 : 0; }
typedef UniquePredicate<CldPtr, _compare_cld_ptr_> CldPredicate;
typedef JfrPredicatedArtifactWriterImplHost<CldPtr, LeakCldPredicate, write__artifact__classloader> LeakCldWriterImpl;
typedef JfrPredicatedArtifactWriterImplHost<CldPtr, CldPredicate, write__artifact__classloader> CldWriterImpl;
typedef JfrArtifactWriterHost<LeakCldWriterImpl, TYPE_CLASSLOADER> LeakCldWriter;
typedef JfrArtifactWriterHost<CldWriterImpl, TYPE_CLASSLOADER> CldWriter;

typedef const JfrSymbolId::SymbolEntry* SymbolEntryPtr;

static int write__artifact__symbol__entry__(JfrCheckpointWriter* writer,
                                            SymbolEntryPtr entry) {
  assert(writer != NULL, "invariant");
  assert(entry != NULL, "invariant");
  ResourceMark rm;
  writer->write(CREATE_SYMBOL_ID(entry->id()));
  writer->write(entry->value()->as_C_string());
  return 1;
}

int write__artifact__symbol__entry(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* e) {
  assert(e != NULL, "invariant");
  return write__artifact__symbol__entry__(writer, (SymbolEntryPtr)e);
}

typedef JfrArtifactWriterImplHost<SymbolEntryPtr, write__artifact__symbol__entry> SymbolEntryWriterImpl;
typedef JfrArtifactWriterHost<SymbolEntryWriterImpl, TYPE_SYMBOL> SymbolEntryWriter;

typedef const JfrSymbolId::CStringEntry* CStringEntryPtr;

static int write__artifact__cstring__entry__(JfrCheckpointWriter* writer, CStringEntryPtr entry) {
  assert(writer != NULL, "invariant");
  assert(entry != NULL, "invariant");
  writer->write(CREATE_SYMBOL_ID(entry->id()));
  writer->write(entry->value());
  return 1;
}

int write__artifact__cstring__entry(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* e) {
  assert(e != NULL, "invariant");
  return write__artifact__cstring__entry__(writer, (CStringEntryPtr)e);
}

typedef JfrArtifactWriterImplHost<CStringEntryPtr, write__artifact__cstring__entry> CStringEntryWriterImpl;
typedef JfrArtifactWriterHost<CStringEntryWriterImpl, TYPE_SYMBOL> CStringEntryWriter;

int write__artifact__klass__symbol(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* k) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invaiant");
  assert(k != NULL, "invariant");
  const InstanceKlass* const ik = (const InstanceKlass*)k;
  if (ik->is_anonymous()) {
    CStringEntryPtr entry = NULL;
    {
      ResourceMark rm;
      uintptr_t hashcode = JfrSymbolId::anonymous_klass_name_hash_code(ik);
      entry = artifacts->map_cstring(JfrSymbolId::get_anonymous_klass_chars(ik, hashcode), hashcode);
    }
    assert(entry != NULL, "invariant");
    return write__artifact__cstring__entry__(writer, entry);
  }

  SymbolEntryPtr entry = artifacts->map_symbol(ik->name());
  return write__artifact__symbol__entry__(writer, entry);
}

int _compare_traceid_(const traceid& lhs, const traceid& rhs) {
  return lhs > rhs ? 1 : (lhs < rhs) ? -1 : 0;
}

template <template <typename> class Predicate>
class KlassSymbolWriterImpl {
 private:
  JfrCheckpointWriter* _writer;
  JfrArtifactSet* _artifacts;
  Predicate<KlassPtr> _predicate;
  MethodUsedPredicate<true> _method_used_predicate;
  MethodFlagPredicate _method_flag_predicate;
  UniquePredicate<traceid, _compare_traceid_> _unique_predicate;

  int klass_symbols(KlassPtr klass);
  int class_loader_symbols(CldPtr cld);
  int method_symbols(KlassPtr klass);

 public:
  typedef KlassPtr Type;
  KlassSymbolWriterImpl(JfrCheckpointWriter* writer,
                        JfrArtifactSet* artifacts,
                        bool class_unload) : _writer(writer),
                                             _artifacts(artifacts),
                                             _predicate(class_unload),
                                             _method_used_predicate(class_unload),
                                             _method_flag_predicate(class_unload),
                                             _unique_predicate(class_unload) {}

  int operator()(KlassPtr klass) {
    assert(klass != NULL, "invariant");
    int count = 0;
    if (_predicate(klass)) {
      count += klass_symbols(klass);
      CldPtr cld = klass->class_loader_data();
      assert(cld != NULL, "invariant");
      if (!cld->is_anonymous()) {
        count += class_loader_symbols(cld);
      }
      if (_method_used_predicate(klass)) {
        count += method_symbols(klass);
      }
    }
    return count;
  }
};

template <template <typename> class Predicate>
int KlassSymbolWriterImpl<Predicate>::klass_symbols(KlassPtr klass) {
  assert(klass != NULL, "invariant");
  assert(_predicate(klass), "invariant");
  const InstanceKlass* const ik = (const InstanceKlass*)klass;
  if (ik->is_anonymous()) {
    CStringEntryPtr entry = NULL;
    {
      ResourceMark rm;
      uintptr_t hashcode = JfrSymbolId::anonymous_klass_name_hash_code(ik);
      entry = _artifacts->map_cstring(JfrSymbolId::get_anonymous_klass_chars(ik, hashcode), hashcode);
    }
    assert(entry != NULL, "invariant");
    return _unique_predicate(entry->id()) ? write__artifact__cstring__entry__(this->_writer, entry) : 0;
  }
  SymbolEntryPtr entry = this->_artifacts->map_symbol(ik->name());
  assert(entry != NULL, "invariant");
  return _unique_predicate(entry->id()) ? write__artifact__symbol__entry__(this->_writer, entry) : 0;
}

template <template <typename> class Predicate>
int KlassSymbolWriterImpl<Predicate>::class_loader_symbols(CldPtr cld) {
  assert(cld != NULL, "invariant");
  assert(!cld->is_anonymous(), "invariant");
  int count = 0;
  // class loader type
  const Klass* class_loader_klass = cld->class_loader() != NULL ? cld->class_loader()->klass() : NULL;
  if (class_loader_klass == NULL) {
    // (primordial) boot class loader
    CStringEntryPtr entry = this->_artifacts->map_cstring(BOOTSTRAP_LOADER_NAME, 0);
    assert(entry != NULL, "invariant");
    assert(strncmp(entry->literal(),
      BOOTSTRAP_LOADER_NAME,
      BOOTSTRAP_LOADER_NAME_LEN) == 0, "invariant");
    if (_unique_predicate(entry->id())) {
      count += write__artifact__cstring__entry__(this->_writer, entry);
    }
  } else {
    const Symbol* class_loader_name = class_loader_klass->name()/* XXX TODO cld->name()*/;
    if (class_loader_name != NULL) {
      SymbolEntryPtr entry = this->_artifacts->map_symbol(class_loader_name);
      assert(entry != NULL, "invariant");
      if (_unique_predicate(entry->id())) {
        count += write__artifact__symbol__entry__(this->_writer, entry);
      }
    }
  }
  return count;
}

template <template <typename> class Predicate>
int KlassSymbolWriterImpl<Predicate>::method_symbols(KlassPtr klass) {
  assert(_predicate(klass), "invariant");
  assert(_method_used_predicate(klass), "invariant");
  assert(METHOD_AND_CLASS_USED_ANY_EPOCH(klass), "invariant");
  int count = 0;
  const InstanceKlass* const ik = InstanceKlass::cast((Klass*)klass);
  const int len = ik->methods()->length();
  for (int i = 0; i < len; ++i) {
    MethodPtr method = ik->methods()->at(i);
    if (_method_flag_predicate(method)) {
      SymbolEntryPtr entry = this->_artifacts->map_symbol(method->name());
      assert(entry != NULL, "invariant");
      if (_unique_predicate(entry->id())) {
        count += write__artifact__symbol__entry__(this->_writer, entry);
      }
      entry = this->_artifacts->map_symbol(method->signature());
      assert(entry != NULL, "invariant");
      if (_unique_predicate(entry->id())) {
        count += write__artifact__symbol__entry__(this->_writer, entry);
      }
    }
  }
  return count;
}

typedef KlassSymbolWriterImpl<LeakPredicate> LeakKlassSymbolWriterImpl;
typedef JfrArtifactWriterHost<LeakKlassSymbolWriterImpl, TYPE_SYMBOL> LeakKlassSymbolWriter;

class ClearKlassAndMethods {
 private:
  ClearArtifact<KlassPtr> _clear_klass_tag_bits;
  ClearArtifact<MethodPtr> _clear_method_flag;
  MethodUsedPredicate<false> _method_used_predicate;

 public:
  ClearKlassAndMethods(bool class_unload) : _clear_klass_tag_bits(class_unload),
                                            _clear_method_flag(class_unload),
                                            _method_used_predicate(class_unload) {}
  bool operator()(KlassPtr klass) {
    if (_method_used_predicate(klass)) {
      const InstanceKlass* ik = InstanceKlass::cast((Klass*)klass);
      const int len = ik->methods()->length();
      for (int i = 0; i < len; ++i) {
        MethodPtr method = ik->methods()->at(i);
        _clear_method_flag(method);
      }
    }
    _clear_klass_tag_bits(klass);
    return true;
  }
};

typedef CompositeFunctor<KlassPtr,
                         TagLeakpKlassArtifact,
                         LeakKlassWriter> LeakpKlassArtifactTagging;

typedef CompositeFunctor<KlassPtr,
                         LeakpKlassArtifactTagging,
                         KlassWriter> CompositeKlassWriter;

typedef CompositeFunctor<KlassPtr,
                         CompositeKlassWriter,
                         KlassArtifactRegistrator> CompositeKlassWriterRegistration;

typedef CompositeFunctor<KlassPtr,
                         KlassWriter,
                         KlassArtifactRegistrator> KlassWriterRegistration;

typedef JfrArtifactCallbackHost<KlassPtr, KlassWriterRegistration> KlassCallback;
typedef JfrArtifactCallbackHost<KlassPtr, CompositeKlassWriterRegistration> CompositeKlassCallback;

/*
 * Composite operation
 *
 * TagLeakpKlassArtifact ->
 *   LeakpPredicate ->
 *     LeakpKlassWriter ->
 *       KlassPredicate ->
 *         KlassWriter ->
 *           KlassWriterRegistration
 */
void JfrTypeSet::write_klass_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(!_artifacts->has_klass_entries(), "invariant");
  KlassArtifactRegistrator reg(_artifacts);
  KlassWriter kw(writer, _artifacts, _class_unload);
  KlassWriterRegistration kwr(&kw, &reg);
  if (leakp_writer == NULL) {
    KlassCallback callback(&kwr);
    _subsystem_callback = &callback;
    do_klasses();
  } else {
    TagLeakpKlassArtifact tagging(_class_unload);
    LeakKlassWriter lkw(leakp_writer, _artifacts, _class_unload);
    LeakpKlassArtifactTagging lpkat(&tagging, &lkw);
    CompositeKlassWriter ckw(&lpkat, &kw);
    CompositeKlassWriterRegistration ckwr(&ckw, &reg);
    CompositeKlassCallback callback(&ckwr);
    _subsystem_callback = &callback;
    do_klasses();
  }

  if (is_initial_typeset_for_chunk(_class_unload)) {
    // Because the set of primitives is written outside the callback,
    // their count is not automatically incremented.
    kw.add(primitives_count);
  }
}

typedef JfrArtifactWriterImplHost<CStringEntryPtr, write__artifact__package> PackageEntryWriterImpl;
typedef JfrArtifactWriterHost<PackageEntryWriterImpl, TYPE_PACKAGE> PackageEntryWriter;

void JfrTypeSet::write_package_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(_artifacts->has_klass_entries(), "invariant");
  assert(writer != NULL, "invariant");
  // below jdk9 there is no oop for packages, so nothing to do with leakp_writer
  // just write packages
  PackageEntryWriter pw(writer, _artifacts, _class_unload);
  _artifacts->iterate_packages(pw);
}

typedef CompositeFunctor<CldPtr, CldWriter, ClearArtifact<CldPtr> > CldWriterWithClear;
typedef CompositeFunctor<CldPtr, LeakCldWriter, CldWriter> CompositeCldWriter;
typedef CompositeFunctor<CldPtr, CompositeCldWriter, ClearArtifact<CldPtr> > CompositeCldWriterWithClear;
typedef JfrArtifactCallbackHost<CldPtr, CldWriterWithClear> CldCallback;
typedef JfrArtifactCallbackHost<CldPtr, CompositeCldWriterWithClear> CompositeCldCallback;

class CldFieldSelector {
 public:
  typedef CldPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != NULL, "invariant");
    CldPtr cld = klass->class_loader_data();
    return cld->is_anonymous() ? NULL : cld;
  }
};

typedef KlassToFieldEnvelope<CldFieldSelector, CldWriterWithClear> KlassCldWriterWithClear;
typedef KlassToFieldEnvelope<CldFieldSelector, CompositeCldWriterWithClear> KlassCompositeCldWriterWithClear;

/*
 * Composite operation
 *
 * LeakpClassLoaderWriter ->
 *   ClassLoaderWriter ->
 *     ClearArtifact<ClassLoaderData>
 */
void JfrTypeSet::write_class_loader_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(_artifacts->has_klass_entries(), "invariant");
  ClearArtifact<CldPtr> clear(_class_unload);
  CldWriter cldw(writer, _artifacts, _class_unload);
  if (leakp_writer == NULL) {
    CldWriterWithClear cldwwc(&cldw, &clear);
    KlassCldWriterWithClear kcldwwc(&cldwwc);
    _artifacts->iterate_klasses(kcldwwc);
    CldCallback callback(&cldwwc);
    _subsystem_callback = &callback;
    do_class_loaders();
    return;
  }
  LeakCldWriter lcldw(leakp_writer, _artifacts, _class_unload);
  CompositeCldWriter ccldw(&lcldw, &cldw);
  CompositeCldWriterWithClear ccldwwc(&ccldw, &clear);
  KlassCompositeCldWriterWithClear kcclwwc(&ccldwwc);
  _artifacts->iterate_klasses(kcclwwc);
  CompositeCldCallback callback(&ccldwwc);
  _subsystem_callback = &callback;
  do_class_loaders();
}

template <bool predicate_bool, typename MethodFunctor>
class MethodIteratorHost {
 private:
  MethodFunctor _method_functor;
  MethodUsedPredicate<predicate_bool> _method_used_predicate;
  MethodFlagPredicate _method_flag_predicate;

 public:
  MethodIteratorHost(JfrCheckpointWriter* writer,
                     JfrArtifactSet* artifacts,
                     bool class_unload,
                     bool skip_header = false) :
    _method_functor(writer, artifacts, class_unload, skip_header),
    _method_used_predicate(class_unload),
    _method_flag_predicate(class_unload) {}

  bool operator()(KlassPtr klass) {
    if (_method_used_predicate(klass)) {
      assert(METHOD_AND_CLASS_USED_ANY_EPOCH(klass), "invariant");
      const InstanceKlass* ik = InstanceKlass::cast((Klass*)klass);
      const int len = ik->methods()->length();
      for (int i = 0; i < len; ++i) {
        MethodPtr method = ik->methods()->at(i);
        if (_method_flag_predicate(method)) {
          _method_functor(method);
        }
      }
    }
    return true;
  }

  int count() const { return _method_functor.count(); }
  void add(int count) { _method_functor.add(count); }
};

typedef MethodIteratorHost<true /*leakp */,  MethodWriterImpl> LeakMethodWriter;
typedef MethodIteratorHost<false, MethodWriterImpl> MethodWriter;
typedef CompositeFunctor<KlassPtr, LeakMethodWriter, MethodWriter> CompositeMethodWriter;

/*
 * Composite operation
 *
 * LeakpMethodWriter ->
 *   MethodWriter
 */
void JfrTypeSet::write_method_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(_artifacts->has_klass_entries(), "invariant");
  MethodWriter mw(writer, _artifacts, _class_unload);
  if (leakp_writer == NULL) {
    _artifacts->iterate_klasses(mw);
    return;
  }
  LeakMethodWriter lpmw(leakp_writer, _artifacts, _class_unload);
  CompositeMethodWriter cmw(&lpmw, &mw);
  _artifacts->iterate_klasses(cmw);
}
static void write_symbols_leakp(JfrCheckpointWriter* leakp_writer, JfrArtifactSet* artifacts, bool class_unload) {
  assert(leakp_writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  LeakKlassSymbolWriter lpksw(leakp_writer, artifacts, class_unload);
  artifacts->iterate_klasses(lpksw);
}
static void write_symbols(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer, JfrArtifactSet* artifacts, bool class_unload) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  if (leakp_writer != NULL) {
    write_symbols_leakp(leakp_writer, artifacts, class_unload);
  }
  // iterate all registered symbols
  SymbolEntryWriter symbol_writer(writer, artifacts, class_unload);
  artifacts->iterate_symbols(symbol_writer);
  CStringEntryWriter cstring_writer(writer, artifacts, class_unload, true); // skip header
  artifacts->iterate_cstrings(cstring_writer);
  symbol_writer.add(cstring_writer.count());
}

bool JfrTypeSet::_class_unload = false;
JfrArtifactSet* JfrTypeSet::_artifacts = NULL;
JfrArtifactClosure* JfrTypeSet::_subsystem_callback = NULL;

void JfrTypeSet::write_symbol_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(writer != NULL, "invariant");
  assert(_artifacts->has_klass_entries(), "invariant");
  write_symbols(writer, leakp_writer, _artifacts, _class_unload);
}

void JfrTypeSet::do_unloaded_klass(Klass* klass) {
  assert(klass != NULL, "invariant");
  assert(_subsystem_callback != NULL, "invariant");
  if (IS_JDK_JFR_EVENT_SUBKLASS(klass)) {
    JfrEventClasses::increment_unloaded_event_class();
  }
  if (USED_THIS_EPOCH(klass)) { // includes leakp subset
    _subsystem_callback->do_artifact(klass);
    return;
  }
  if (klass->is_subclass_of(SystemDictionary::ClassLoader_klass()) || klass == SystemDictionary::Object_klass()) {
    SET_LEAKP_USED_THIS_EPOCH(klass); // tag leakp "safe byte" for subset inclusion
    _subsystem_callback->do_artifact(klass);
  }
}

void JfrTypeSet::do_klass(Klass* klass) {
  assert(klass != NULL, "invariant");
  assert(_subsystem_callback != NULL, "invariant");
  if (USED_PREV_EPOCH(klass)) { // includes leakp subset
    _subsystem_callback->do_artifact(klass);
    return;
  }
  if (klass->is_subclass_of(SystemDictionary::ClassLoader_klass()) || klass == SystemDictionary::Object_klass()) {
    SET_LEAKP_USED_PREV_EPOCH(klass); // tag leakp "safe byte" for subset inclusion
    _subsystem_callback->do_artifact(klass);
  }
}

static traceid primitive_id(KlassPtr array_klass) {
  if (array_klass == NULL) {
    // The first klass id is reserved for the void.class.
    return MaxJfrEventId + 101;
  }
  // Derive the traceid for a primitive mirror from its associated array klass (+1).
  return JfrTraceId::get(array_klass) + 1;
}

static void write_primitive(JfrCheckpointWriter* writer, Klass* type_array_klass, JfrArtifactSet* artifacts) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  writer->write(primitive_id(type_array_klass));
  writer->write(cld_id(Universe::boolArrayKlassObj()->class_loader_data()));
  writer->write(mark_symbol(primitive_symbol(type_array_klass), artifacts));
  writer->write(package_id(Universe::boolArrayKlassObj(), artifacts));
  writer->write(get_primitive_flags());
}

// A mirror representing a primitive class (e.g. int.class) has no reified Klass*,
// instead it has an associated TypeArrayKlass* (e.g. int[].class).
// We can use the TypeArrayKlass* as a proxy for deriving the id of the primitive class.
// The exception is the void.class, which has neither a Klass* nor a TypeArrayKlass*.
// It will use a reserved constant.
static void do_primitives(JfrArtifactSet* artifacts, bool class_unload) {
  // Only write the primitive classes once per chunk.
  if (is_initial_typeset_for_chunk(class_unload)) {
    write_primitive(_writer, Universe::boolArrayKlassObj(), artifacts);
    write_primitive(_writer, Universe::byteArrayKlassObj(), artifacts);
    write_primitive(_writer, Universe::charArrayKlassObj(), artifacts);
    write_primitive(_writer, Universe::shortArrayKlassObj(), artifacts);
    write_primitive(_writer, Universe::intArrayKlassObj(), artifacts);
    write_primitive(_writer, Universe::longArrayKlassObj(), artifacts);
    write_primitive(_writer, Universe::singleArrayKlassObj(), artifacts);
    write_primitive(_writer, Universe::doubleArrayKlassObj(), artifacts);
    write_primitive(_writer, NULL, artifacts); // void.class
  }
}

void JfrTypeSet::do_klasses() {
  if (_class_unload) {
    ClassLoaderDataGraph::classes_unloading_do(&do_unloaded_klass);
    return;
  }
  ClassLoaderDataGraph::classes_do(&do_klass);
  do_primitives(_artifacts, _class_unload);
}

void JfrTypeSet::do_unloaded_class_loader_data(ClassLoaderData* cld) {
  assert(_subsystem_callback != NULL, "invariant");
  if (ANY_USED_THIS_EPOCH(cld)) { // includes leakp subset
    _subsystem_callback->do_artifact(cld);
  }
}

void JfrTypeSet::do_class_loader_data(ClassLoaderData* cld) {
  assert(_subsystem_callback != NULL, "invariant");
  if (ANY_USED_PREV_EPOCH(cld)) { // includes leakp subset
    _subsystem_callback->do_artifact(cld);
  }
}

class CLDCallback : public CLDClosure {
 private:
  bool _class_unload;
 public:
  CLDCallback(bool class_unload) : _class_unload(class_unload) {}
  void do_cld(ClassLoaderData* cld) {
     assert(cld != NULL, "invariant");
    if (cld->is_anonymous()) {
      return;
    }
    if (_class_unload) {
      JfrTypeSet::do_unloaded_class_loader_data(cld);
      return;
    }
    JfrTypeSet::do_class_loader_data(cld);
  }
};

void JfrTypeSet::do_class_loaders() {
  CLDCallback cld_cb(_class_unload);
  if (_class_unload) {
    ClassLoaderDataGraph::cld_unloading_do(&cld_cb);
    return;
  }
  ClassLoaderDataGraph::cld_do(&cld_cb);
}

static void clear_artifacts(JfrArtifactSet* artifacts,
                            bool class_unload) {
  assert(artifacts != NULL, "invariant");
  assert(artifacts->has_klass_entries(), "invariant");

  // untag
  ClearKlassAndMethods clear(class_unload);
  artifacts->iterate_klasses(clear);
  artifacts->clear();
}

/**
 * Write all "tagged" (in-use) constant artifacts and their dependencies.
 */
void JfrTypeSet::serialize(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer, bool class_unload) {
  assert(writer != NULL, "invariant");
  ResourceMark rm;
  // initialization begin
  _writer = writer;
  _class_unload = class_unload;
  ++checkpoint_id;
  if (_artifacts == NULL) {
    _artifacts = new JfrArtifactSet(class_unload);
    _subsystem_callback = NULL;
  } else {
    _artifacts->initialize(class_unload);
    _subsystem_callback = NULL;
  }
  assert(_artifacts != NULL, "invariant");
  assert(!_artifacts->has_klass_entries(), "invariant");
  assert(_subsystem_callback == NULL, "invariant");
  // initialization complete

  // write order is important because an individual write step
  // might tag an artifact to be written in a subsequent step
  write_klass_constants(writer, leakp_writer);
  if (_artifacts->has_klass_entries()) {
    write_package_constants(writer, leakp_writer);
    write_class_loader_constants(writer, leakp_writer);
    write_method_constants(writer, leakp_writer);
    write_symbol_constants(writer, leakp_writer);
    clear_artifacts(_artifacts, class_unload);
  }
}

