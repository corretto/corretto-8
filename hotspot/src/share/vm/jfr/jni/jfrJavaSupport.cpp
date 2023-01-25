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
#include "jni.h"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "jfr/jni/jfrJavaCall.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/support/jfrThreadId.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/java.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
//#include "runtime/threadSMR.hpp"

#ifdef ASSERT
void JfrJavaSupport::check_java_thread_in_vm(Thread* t) {
  assert(t != NULL, "invariant");
  assert(t->is_Java_thread(), "invariant");
  assert(((JavaThread*)t)->thread_state() == _thread_in_vm, "invariant");
}

void JfrJavaSupport::check_java_thread_in_native(Thread* t) {
  assert(t != NULL, "invariant");
  assert(t->is_Java_thread(), "invariant");
  assert(((JavaThread*)t)->thread_state() == _thread_in_native, "invariant");
}
#endif

/*
 *  Handles and references
 */
jobject JfrJavaSupport::local_jni_handle(const oop obj, Thread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  return t->active_handles()->allocate_handle(obj);
}

jobject JfrJavaSupport::local_jni_handle(const jobject handle, Thread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  const oop obj = JNIHandles::resolve(handle);
  return obj == NULL ? NULL : local_jni_handle(obj, t);
}

void JfrJavaSupport::destroy_local_jni_handle(jobject handle) {
  JNIHandles::destroy_local(handle);
}

jobject JfrJavaSupport::global_jni_handle(const oop obj, Thread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  HandleMark hm(t);
  return JNIHandles::make_global(Handle(t, obj));
}

jobject JfrJavaSupport::global_jni_handle(const jobject handle, Thread* t) {
  const oop obj = JNIHandles::resolve(handle);
  return obj == NULL ? NULL : global_jni_handle(obj, t);
}

void JfrJavaSupport::destroy_global_jni_handle(const jobject handle) {
  JNIHandles::destroy_global(handle);
}

oop JfrJavaSupport::resolve_non_null(jobject obj) {
  return JNIHandles::resolve_non_null(obj);
}

/*
 *  Method invocation
 */
void JfrJavaSupport::call_static(JfrJavaArguments* args, TRAPS) {
  JfrJavaCall::call_static(args, THREAD);
}

void JfrJavaSupport::call_special(JfrJavaArguments* args, TRAPS) {
  JfrJavaCall::call_special(args, THREAD);
}

void JfrJavaSupport::call_virtual(JfrJavaArguments* args, TRAPS) {
  JfrJavaCall::call_virtual(args, THREAD);
}

void JfrJavaSupport::notify_all(jobject object, TRAPS) {
  assert(object != NULL, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  HandleMark hm(THREAD);
  Handle h_obj(THREAD, resolve_non_null(object));
  assert(h_obj.not_null(), "invariant");
  ObjectSynchronizer::jni_enter(h_obj, THREAD);
  ObjectSynchronizer::notifyall(h_obj, THREAD);
  ObjectSynchronizer::jni_exit(h_obj(), THREAD);
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
}

/*
 *  Object construction
 */
static void object_construction(JfrJavaArguments* args, JavaValue* result, InstanceKlass* klass, TRAPS) {
  assert(args != NULL, "invariant");
  assert(result != NULL, "invariant");
  assert(klass != NULL, "invariant");
  assert(klass->is_initialized(), "invariant");

  HandleMark hm(THREAD);
  instanceOop obj = klass->allocate_instance(CHECK);
  instanceHandle h_obj(THREAD, obj);
  assert(h_obj.not_null(), "invariant");
  args->set_receiver(h_obj);
  result->set_type(T_VOID); // constructor result type
  JfrJavaSupport::call_special(args, CHECK);
  result->set_type(T_OBJECT); // set back to original result type
  result->set_jobject((jobject)h_obj());
}

static void array_construction(JfrJavaArguments* args, JavaValue* result, InstanceKlass* klass, int array_length, TRAPS) {
  assert(args != NULL, "invariant");
  assert(result != NULL, "invariant");
  assert(klass != NULL, "invariant");
  assert(klass->is_initialized(), "invariant");

  Klass* const ak = klass->array_klass(THREAD);
  ObjArrayKlass::cast(ak)->initialize(THREAD);
  HandleMark hm(THREAD);
  objArrayOop arr = ObjArrayKlass::cast(ak)->allocate(array_length, CHECK);
  result->set_jobject((jobject)arr);
}

static void create_object(JfrJavaArguments* args, JavaValue* result, TRAPS) {
  assert(args != NULL, "invariant");
  assert(result != NULL, "invariant");
  assert(result->get_type() == T_OBJECT, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  InstanceKlass* const klass = static_cast<InstanceKlass*>(args->klass());
  klass->initialize(CHECK);

  const int array_length = args->array_length();

  if (array_length >= 0) {
    array_construction(args, result, klass, array_length, CHECK);
  } else {
    object_construction(args, result, klass, THREAD);
  }
}

static void handle_result(JavaValue* result, bool global_ref, Thread* t) {
  assert(result != NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(t));
  const oop result_oop = (const oop)result->get_jobject();
  if (result_oop == NULL) {
    return;
  }
  result->set_jobject(global_ref ?
                      JfrJavaSupport::global_jni_handle(result_oop, t) :
                      JfrJavaSupport::local_jni_handle(result_oop, t));
}

void JfrJavaSupport::new_object(JfrJavaArguments* args, TRAPS) {
  assert(args != NULL, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  create_object(args, args->result(), THREAD);
}

void JfrJavaSupport::new_object_local_ref(JfrJavaArguments* args, TRAPS) {
  assert(args != NULL, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue* const result = args->result();
  assert(result != NULL, "invariant");
  create_object(args, result, CHECK);
  handle_result(result, false, THREAD);
}

void JfrJavaSupport::new_object_global_ref(JfrJavaArguments* args, TRAPS) {
  assert(args != NULL, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue* const result = args->result();
  assert(result != NULL, "invariant");
  create_object(args, result, CHECK);
  handle_result(result, true, THREAD);
}

jstring JfrJavaSupport::new_string(const char* c_str, TRAPS) {
  assert(c_str != NULL, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  const oop result = java_lang_String::create_oop_from_str(c_str, THREAD);
  return (jstring)local_jni_handle(result, THREAD);
}

jobjectArray JfrJavaSupport::new_string_array(int length, TRAPS) {
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/lang/String", "<init>", "()V", CHECK_NULL);
  args.set_array_length(length);
  new_object_local_ref(&args, THREAD);
  return (jobjectArray)args.result()->get_jobject();
}

jobject JfrJavaSupport::new_java_lang_Boolean(bool value, TRAPS) {
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/lang/Boolean", "<init>", "(Z)V", CHECK_NULL);
  args.push_int(value ? (jint)JNI_TRUE : (jint)JNI_FALSE);
  new_object_local_ref(&args, THREAD);
  return args.result()->get_jobject();
}

jobject JfrJavaSupport::new_java_lang_Integer(jint value, TRAPS) {
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/lang/Integer", "<init>", "(I)V", CHECK_NULL);
  args.push_int(value);
  new_object_local_ref(&args, THREAD);
  return args.result()->get_jobject();
}

jobject JfrJavaSupport::new_java_lang_Long(jlong value, TRAPS) {
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/lang/Long", "<init>", "(J)V", CHECK_NULL);
  args.push_long(value);
  new_object_local_ref(&args, THREAD);
  return args.result()->get_jobject();
}

void JfrJavaSupport::set_array_element(jobjectArray arr, jobject element, int index, Thread* t) {
  assert(arr != NULL, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(t));
  HandleMark hm(t);
  objArrayHandle a(t, (objArrayOop)resolve_non_null(arr));
  a->obj_at_put(index, resolve_non_null(element));
}

/*
 *  Field access
 */
static void write_int_field(const Handle& h_oop, fieldDescriptor* fd, jint value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != NULL, "invariant");
  h_oop->int_field_put(fd->offset(), value);
}

static void write_float_field(const Handle& h_oop, fieldDescriptor* fd, jfloat value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != NULL, "invariant");
  h_oop->float_field_put(fd->offset(), value);
}

static void write_double_field(const Handle& h_oop, fieldDescriptor* fd, jdouble value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != NULL, "invariant");
  h_oop->double_field_put(fd->offset(), value);
}

static void write_long_field(const Handle& h_oop, fieldDescriptor* fd, jlong value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != NULL, "invariant");
  h_oop->long_field_put(fd->offset(), value);
}

static void write_oop_field(const Handle& h_oop, fieldDescriptor* fd, const oop value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != NULL, "invariant");
  h_oop->obj_field_put(fd->offset(), value);
}

static void write_specialized_field(JfrJavaArguments* args, const Handle& h_oop, fieldDescriptor* fd, bool static_field) {
  assert(args != NULL, "invariant");
  assert(h_oop.not_null(), "invariant");
  assert(fd != NULL, "invariant");
  assert(fd->offset() > 0, "invariant");
  assert(args->length() >= 1, "invariant");

  // attempt must set a real value
  assert(args->param(1).get_type() != T_VOID, "invariant");

  switch(fd->field_type()) {
    case T_BOOLEAN:
    case T_CHAR:
    case T_SHORT:
    case T_INT:
      write_int_field(h_oop, fd, args->param(1).get_jint());
      break;
    case T_FLOAT:
      write_float_field(h_oop, fd, args->param(1).get_jfloat());
      break;
    case T_DOUBLE:
      write_double_field(h_oop, fd, args->param(1).get_jdouble());
      break;
    case T_LONG:
      write_long_field(h_oop, fd, args->param(1).get_jlong());
      break;
    case T_OBJECT:
      write_oop_field(h_oop, fd, (oop)args->param(1).get_jobject());
      break;
    case T_ADDRESS:
      write_oop_field(h_oop, fd, JfrJavaSupport::resolve_non_null(args->param(1).get_jobject()));
      break;
    default:
      ShouldNotReachHere();
  }
}

static void read_specialized_field(JavaValue* result, const Handle& h_oop, fieldDescriptor* fd) {
  assert(result != NULL, "invariant");
  assert(h_oop.not_null(), "invariant");
  assert(fd != NULL, "invariant");
  assert(fd->offset() > 0, "invariant");

  switch(fd->field_type()) {
    case T_BOOLEAN:
    case T_CHAR:
    case T_SHORT:
    case T_INT:
      result->set_jint(h_oop->int_field(fd->offset()));
      break;
    case T_FLOAT:
      result->set_jfloat(h_oop->float_field(fd->offset()));
      break;
    case T_DOUBLE:
      result->set_jdouble(h_oop->double_field(fd->offset()));
      break;
    case T_LONG:
      result->set_jlong(h_oop->long_field(fd->offset()));
      break;
    case T_OBJECT:
      result->set_jobject((jobject)h_oop->obj_field(fd->offset()));
      break;
    default:
      ShouldNotReachHere();
  }
}

static bool find_field(InstanceKlass* ik,
                       Symbol* name_symbol,
                       Symbol* signature_symbol,
                       fieldDescriptor* fd,
                       bool is_static = false,
                       bool allow_super = false) {
  if (allow_super || is_static) {
    return ik->find_field(name_symbol, signature_symbol, is_static, fd) != NULL;
  }
  return ik->find_local_field(name_symbol, signature_symbol, fd);
}

static void lookup_field(JfrJavaArguments* args, InstanceKlass* klass, fieldDescriptor* fd, bool static_field) {
  assert(args != NULL, "invariant");
  assert(klass != NULL, "invariant");
  assert(klass->is_initialized(), "invariant");
  assert(fd != NULL, "invariant");
  find_field(klass, args->name(), args->signature(), fd, static_field, true);
}

static void read_field(JfrJavaArguments* args, JavaValue* result, TRAPS) {
  assert(args != NULL, "invariant");
  assert(result != NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  InstanceKlass* const klass = static_cast<InstanceKlass*>(args->klass());
  klass->initialize(CHECK);
  const bool static_field = !args->has_receiver();
  fieldDescriptor fd;
  lookup_field(args, klass, &fd, static_field);
  assert(fd.offset() > 0, "invariant");

  HandleMark hm(THREAD);
  Handle h_oop(static_field ? Handle(THREAD, klass->java_mirror()) : Handle(THREAD, args->receiver()));
  read_specialized_field(result, h_oop, &fd);
}

static void write_field(JfrJavaArguments* args, JavaValue* result, TRAPS) {
  assert(args != NULL, "invariant");
  assert(result != NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  InstanceKlass* const klass = static_cast<InstanceKlass*>(args->klass());
  klass->initialize(CHECK);

  const bool static_field = !args->has_receiver();
  fieldDescriptor fd;
  lookup_field(args, klass, &fd, static_field);
  assert(fd.offset() > 0, "invariant");

  HandleMark hm(THREAD);
  Handle h_oop(static_field ? Handle(THREAD, klass->java_mirror()) : Handle(THREAD, args->receiver()));
  write_specialized_field(args, h_oop, &fd, static_field);
}

void JfrJavaSupport::set_field(JfrJavaArguments* args, TRAPS) {
  assert(args != NULL, "invariant");
  write_field(args, args->result(), THREAD);
}

void JfrJavaSupport::get_field(JfrJavaArguments* args, TRAPS) {
  assert(args != NULL, "invariant");
  read_field(args, args->result(), THREAD);
}

void JfrJavaSupport::get_field_local_ref(JfrJavaArguments* args, TRAPS) {
  assert(args != NULL, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));

  JavaValue* const result = args->result();
  assert(result != NULL, "invariant");
  assert(result->get_type() == T_OBJECT, "invariant");

  read_field(args, result, CHECK);
  const oop obj = (const oop)result->get_jobject();

  if (obj != NULL) {
    result->set_jobject(local_jni_handle(obj, THREAD));
  }
}

void JfrJavaSupport::get_field_global_ref(JfrJavaArguments* args, TRAPS) {
  assert(args != NULL, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));

  JavaValue* const result = args->result();
  assert(result != NULL, "invariant");
  assert(result->get_type() == T_OBJECT, "invariant");
  read_field(args, result, CHECK);
  const oop obj = (const oop)result->get_jobject();
  if (obj != NULL) {
    result->set_jobject(global_jni_handle(obj, THREAD));
  }
}

/*
 *  Misc
 */
Klass* JfrJavaSupport::klass(const jobject handle) {
  const oop obj = resolve_non_null(handle);
  assert(obj != NULL, "invariant");
  return obj->klass();
}

// caller needs ResourceMark
const char* JfrJavaSupport::c_str(jstring string, Thread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  if (string == NULL) {
    return NULL;
  }
  const char* temp = NULL;
  const oop java_string = resolve_non_null(string);
  if (java_lang_String::value(java_string) != NULL) {
    const size_t length = java_lang_String::utf8_length(java_string);
    temp = NEW_RESOURCE_ARRAY_IN_THREAD(t, const char, (length + 1));
    if (temp == NULL) {
       JfrJavaSupport::throw_out_of_memory_error("Unable to allocate thread local native memory", t);
       return NULL;
    }
    assert(temp != NULL, "invariant");
    java_lang_String::as_utf8_string(java_string, const_cast<char*>(temp), (int) length + 1);
  }
  return temp;
}

/*
 *  Exceptions and errors
 */
static void create_and_throw(Symbol* name, const char* message, TRAPS) {
  assert(name != NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(!HAS_PENDING_EXCEPTION, "invariant");
  THROW_MSG(name, message);
}

void JfrJavaSupport::throw_illegal_state_exception(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_IllegalStateException(), message, THREAD);
}

void JfrJavaSupport::throw_internal_error(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_InternalError(), message, THREAD);
}

void JfrJavaSupport::throw_illegal_argument_exception(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_IllegalArgumentException(), message, THREAD);
}

void JfrJavaSupport::throw_out_of_memory_error(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_OutOfMemoryError(), message, THREAD);
}

void JfrJavaSupport::throw_class_format_error(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_ClassFormatError(), message, THREAD);
}

void JfrJavaSupport::abort(jstring errorMsg, Thread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));

  ResourceMark rm(t);
  const char* const error_msg = c_str(errorMsg, t);
  if (error_msg != NULL) {
    if (true) tty->print_cr("%s",error_msg);
  }
  if (true) tty->print_cr("%s", "An irrecoverable error in Jfr. Shutting down VM...");
  vm_abort();
}

JfrJavaSupport::CAUSE JfrJavaSupport::_cause = JfrJavaSupport::VM_ERROR;
void JfrJavaSupport::set_cause(jthrowable throwable, Thread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));

  HandleMark hm(t);
  Handle ex(t, JNIHandles::resolve_external_guard(throwable));

  if (ex.is_null()) {
    return;
  }

  if (ex->is_a(SystemDictionary::OutOfMemoryError_klass())) {
    _cause = OUT_OF_MEMORY;
    return;
  }
  if (ex->is_a(SystemDictionary::StackOverflowError_klass())) {
    _cause = STACK_OVERFLOW;
    return;
  }
  if (ex->is_a(SystemDictionary::Error_klass())) {
    _cause = VM_ERROR;
    return;
  }
  if (ex->is_a(SystemDictionary::RuntimeException_klass())) {
    _cause = RUNTIME_EXCEPTION;
    return;
  }
  if (ex->is_a(SystemDictionary::Exception_klass())) {
    _cause = UNKNOWN;
    return;
  }
}

void JfrJavaSupport::uncaught_exception(jthrowable throwable, Thread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  assert(throwable != NULL, "invariant");
  set_cause(throwable, t);
}

JfrJavaSupport::CAUSE JfrJavaSupport::cause() {
  return _cause;
}

jlong JfrJavaSupport::jfr_thread_id(jobject target_thread) {
//  ThreadsListHandle tlh;
  // XXX is it correct and safe?
  JavaThread* native_thread = java_lang_Thread::thread(JNIHandles::resolve_non_null(target_thread));
//  (void)tlh.cv_internal_thread_to_JavaThread(target_thread, &native_thread, NULL);
  return native_thread != NULL ? JFR_THREAD_ID(native_thread) : 0;
}
