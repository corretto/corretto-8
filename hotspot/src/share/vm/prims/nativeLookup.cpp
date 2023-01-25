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
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "prims/jvm_misc.hpp"
#include "prims/nativeLookup.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "os_aix.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "os_bsd.inline.hpp"
#endif


/*

The JNI specification defines the mapping from a Java native method name to
a C native library implementation function name as follows:

  The mapping produces a native method name by concatenating the following components
  derived from a `native` method declaration:

  1. the prefix Java_
  2. given the binary name, in internal form, of the class which declares the native method:
     the result of escaping the name.
  3. an underscore ("_")
  4. the escaped method name
  5. if the native method declaration is overloaded: two underscores ("__") followed by the
   escaped parameter descriptor (JVMS 4.3.3) of the method declaration.

  Escaping leaves every alphanumeric ASCII character (A-Za-z0-9) unchanged, and replaces each
  UTF-16 code unit n the table below with the corresponding escape sequence. If the name to be
  escaped contains a surrogate pair, then the high-surrogate code unit and the low-surrogate code
  unit are escaped separately. The result of escaping is a string consisting only of the ASCII
  characters A-Za-z0-9 and underscore.

  ------------------------------                  ------------------------------------
  UTF-16 code unit                                Escape sequence
  ------------------------------                  ------------------------------------
  Forward slash (/, U+002F)                       _
  Underscore (_, U+005F)                          _1
  Semicolon (;, U+003B)                           _2
  Left square bracket ([, U+005B)                 _3
  Any UTF-16 code unit \u_WXYZ_ that does not     _0wxyz where w, x, y, and z are the lower-case
  represent alphanumeric ASCII (A-Za-z0-9),       forms of the hexadecimal digits W, X, Y, and Z.
  forward slash, underscore, semicolon,           (For example, U+ABCD becomes _0abcd.)
  or left square bracket
  ------------------------------                  ------------------------------------

  Note that escape sequences can safely begin _0, _1, etc, because class and method
  names in Java source code never begin with a number. However, that is not the case in
  class files that were not generated from Java source code.

  To preserve the 1:1 mapping to a native method name, the VM checks the resulting name as
  follows. If the process of escaping any precursor string from the native  method declaration
  (class or method name, or argument type) causes a "0", "1", "2", or "3" character
  from the precursor string to appear unchanged in the result *either* immediately after an
  underscore *or* at the beginning of the escaped string (where it will follow an underscore
  in the fully assembled name), then the escaping process is said to have "failed".
  In such cases, no native library search is performed, and the attempt to link the native
  method invocation will throw UnsatisfiedLinkError.


For example:

  package/my_class/method

and

  package/my/1class/method

both map to

  Java_package_my_1class_method

To address this potential conflict we need only check if the character after
/ is a digit 0..3, or if the first character after an injected '_' seperator
is a digit 0..3. If we encounter an invalid identifier we reset the
stringStream and return false. Otherwise the stringStream contains the mapped
name and we return true.

To address legacy compatibility, the UseLegacyJNINameEscaping flag can be set
which skips the extra checks.

*/
static bool map_escaped_name_on(stringStream* st, Symbol* name, int begin, int end) {
  char* bytes = (char*)name->bytes() + begin;
  char* end_bytes = (char*)name->bytes() + end;
  bool check_escape_char = true; // initially true as first character here follows '_'
  while (bytes < end_bytes) {
    jchar c;
    bytes = UTF8::next(bytes, &c);
    if (c <= 0x7f && isalnum(c)) {
      if (check_escape_char && (c >= '0' && c <= '3') &&
          !UseLegacyJNINameEscaping) {
        // This is a non-Java identifier and we won't escape it to
        // ensure no name collisions with a Java identifier.
        if (PrintJNIResolving) {
          ResourceMark rm;
          tty->print_cr("[Lookup of native method with non-Java identifier rejected: %s]",
                                  name->as_C_string());
        }
        st->reset();  // restore to "" on error
        return false;
      }
      st->put((char) c);
      check_escape_char = false;
    } else {
      check_escape_char = false;
      if (c == '_') st->print("_1");
      else if (c == '/') {
        st->print("_");
        // Following a / we must have non-escape character
        check_escape_char = true;
      }
      else if (c == ';') st->print("_2");
      else if (c == '[') st->print("_3");
      else               st->print("_%.5x", c);
    }
  }
  return true;
}


static bool map_escaped_name_on(stringStream* st, Symbol* name) {
  return map_escaped_name_on(st, name, 0, name->utf8_length());
}


char* NativeLookup::pure_jni_name(methodHandle method) {
  stringStream st;
  // Prefix
  st.print("Java_");
  // Klass name
  if (!map_escaped_name_on(&st, method->klass_name())) {
    return NULL;
  }
  st.print("_");
  // Method name
  if (!map_escaped_name_on(&st, method->name())) {
    return NULL;
  }
  return st.as_string();
}


char* NativeLookup::critical_jni_name(methodHandle method) {
  stringStream st;
  // Prefix
  st.print("JavaCritical_");
  // Klass name
  if (!map_escaped_name_on(&st, method->klass_name())) {
    return NULL;
  }
  st.print("_");
  // Method name
  if (!map_escaped_name_on(&st, method->name())) {
    return NULL;
  }
  return st.as_string();
}


char* NativeLookup::long_jni_name(methodHandle method) {
  // Signatures ignore the wrapping parentheses and the trailing return type
  stringStream st;
  Symbol* signature = method->signature();
  st.print("__");
  // find ')'
  int end;
  for (end = 0; end < signature->utf8_length() && signature->byte_at(end) != ')'; end++);
  // skip first '('
  if (!map_escaped_name_on(&st, signature, 1, end)) {
    return NULL;
  }

  return st.as_string();
}

extern "C" {
  void JNICALL JVM_RegisterUnsafeMethods(JNIEnv *env, jclass unsafecls);
  void JNICALL JVM_RegisterMethodHandleMethods(JNIEnv *env, jclass unsafecls);
  void JNICALL JVM_RegisterPerfMethods(JNIEnv *env, jclass perfclass);
  void JNICALL JVM_RegisterWhiteBoxMethods(JNIEnv *env, jclass wbclass);
}

#define CC (char*)  /* cast a literal from (const char*) */
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod lookup_special_native_methods[] = {
  { CC"Java_sun_misc_Unsafe_registerNatives",                      NULL, FN_PTR(JVM_RegisterUnsafeMethods)       },
  { CC"Java_java_lang_invoke_MethodHandleNatives_registerNatives", NULL, FN_PTR(JVM_RegisterMethodHandleMethods) },
  { CC"Java_sun_misc_Perf_registerNatives",                        NULL, FN_PTR(JVM_RegisterPerfMethods)         },
  { CC"Java_sun_hotspot_WhiteBox_registerNatives",                 NULL, FN_PTR(JVM_RegisterWhiteBoxMethods)     },
#if INCLUDE_JFR
  { CC"Java_jdk_jfr_internal_JVM_registerNatives",                 NULL, FN_PTR(jfr_register_natives)            },
#endif
};

static address lookup_special_native(char* jni_name) {
  int count = sizeof(lookup_special_native_methods) / sizeof(JNINativeMethod);
  for (int i = 0; i < count; i++) {
    // NB: To ignore the jni prefix and jni postfix strstr is used matching.
    if (strstr(jni_name, lookup_special_native_methods[i].name) != NULL) {
      return CAST_FROM_FN_PTR(address, lookup_special_native_methods[i].fnPtr);
    }
  }
  return NULL;
}

address NativeLookup::lookup_style(methodHandle method, char* pure_name, const char* long_name, int args_size, bool os_style, bool& in_base_library, TRAPS) {
  address entry;
  // Compute complete JNI name for style
  stringStream st;
  if (os_style) os::print_jni_name_prefix_on(&st, args_size);
  st.print_raw(pure_name);
  st.print_raw(long_name);
  if (os_style) os::print_jni_name_suffix_on(&st, args_size);
  char* jni_name = st.as_string();

  // If the loader is null we have a system class, so we attempt a lookup in
  // the native Java library. This takes care of any bootstrapping problems.
  // Note: It is critical for bootstrapping that Java_java_lang_ClassLoader_00024NativeLibrary_find
  // gets found the first time around - otherwise an infinite loop can occure. This is
  // another VM/library dependency
  Handle loader(THREAD, method->method_holder()->class_loader());
  if (loader.is_null()) {
    entry = lookup_special_native(jni_name);
    if (entry == NULL) {
       entry = (address) os::dll_lookup(os::native_java_library(), jni_name);
    }
    if (entry != NULL) {
      in_base_library = true;
      return entry;
    }
  }

  // Otherwise call static method findNative in ClassLoader
  KlassHandle   klass (THREAD, SystemDictionary::ClassLoader_klass());
  Handle name_arg = java_lang_String::create_from_str(jni_name, CHECK_NULL);

  JavaValue result(T_LONG);
  JavaCalls::call_static(&result,
                         klass,
                         vmSymbols::findNative_name(),
                         vmSymbols::classloader_string_long_signature(),
                         // Arguments
                         loader,
                         name_arg,
                         CHECK_NULL);
  entry = (address) (intptr_t) result.get_jlong();

  if (entry == NULL) {
    // findNative didn't find it, if there are any agent libraries look in them
    AgentLibrary* agent;
    for (agent = Arguments::agents(); agent != NULL; agent = agent->next()) {
      entry = (address) os::dll_lookup(agent->os_lib(), jni_name);
      if (entry != NULL) {
        return entry;
      }
    }
  }

  return entry;
}


address NativeLookup::lookup_critical_style(methodHandle method, char* pure_name, const char* long_name, int args_size, bool os_style) {
  if (!method->has_native_function()) {
    return NULL;
  }

  address current_entry = method->native_function();

  char dll_name[JVM_MAXPATHLEN];
  int offset;
  if (os::dll_address_to_library_name(current_entry, dll_name, sizeof(dll_name), &offset)) {
    char ebuf[32];
    void* dll = os::dll_load(dll_name, ebuf, sizeof(ebuf));
    if (dll != NULL) {
      // Compute complete JNI name for style
      stringStream st;
      if (os_style) os::print_jni_name_prefix_on(&st, args_size);
      st.print_raw(pure_name);
      st.print_raw(long_name);
      if (os_style) os::print_jni_name_suffix_on(&st, args_size);
      char* jni_name = st.as_string();
      return (address)os::dll_lookup(dll, jni_name);
    }
  }

  return NULL;
}


// Check all the formats of native implementation name to see if there is one
// for the specified method.
address NativeLookup::lookup_entry(methodHandle method, bool& in_base_library, TRAPS) {
  address entry = NULL;
  in_base_library = false;
  // Compute pure name
  char* pure_name = pure_jni_name(method);
  if (pure_name == NULL) {
    // JNI name mapping rejected this method so return
    // NULL to indicate UnsatisfiedLinkError should be thrown.
    return NULL;
  }

  // Compute argument size
  int args_size = 1                             // JNIEnv
                + (method->is_static() ? 1 : 0) // class for static methods
                + method->size_of_parameters(); // actual parameters


  // 1) Try JNI short style
  entry = lookup_style(method, pure_name, "",        args_size, true,  in_base_library, CHECK_NULL);
  if (entry != NULL) return entry;

  // Compute long name
  char* long_name = long_jni_name(method);
  if (long_name == NULL) {
    // JNI name mapping rejected this method so return
    // NULL to indicate UnsatisfiedLinkError should be thrown.
    return NULL;
  }

  // 2) Try JNI long style
  entry = lookup_style(method, pure_name, long_name, args_size, true,  in_base_library, CHECK_NULL);
  if (entry != NULL) return entry;

  // 3) Try JNI short style without os prefix/suffix
  entry = lookup_style(method, pure_name, "",        args_size, false, in_base_library, CHECK_NULL);
  if (entry != NULL) return entry;

  // 4) Try JNI long style without os prefix/suffix
  entry = lookup_style(method, pure_name, long_name, args_size, false, in_base_library, CHECK_NULL);

  return entry; // NULL indicates not found
}

// Check all the formats of native implementation name to see if there is one
// for the specified method.
address NativeLookup::lookup_critical_entry(methodHandle method) {
  if (!CriticalJNINatives) return NULL;

  if (method->is_synchronized() ||
      !method->is_static()) {
    // Only static non-synchronized methods are allowed
    return NULL;
  }

  ResourceMark rm;
  address entry = NULL;

  Symbol* signature = method->signature();
  for (int end = 0; end < signature->utf8_length(); end++) {
    if (signature->byte_at(end) == 'L') {
      // Don't allow object types
      return NULL;
    }
  }

  // Compute critical name
  char* critical_name = critical_jni_name(method);
  if (critical_name == NULL) {
    // JNI name mapping rejected this method so return
    // NULL to indicate UnsatisfiedLinkError should be thrown.
    return NULL;
  }

  // Compute argument size
  int args_size = 1                             // JNIEnv
                + (method->is_static() ? 1 : 0) // class for static methods
                + method->size_of_parameters(); // actual parameters


  // 1) Try JNI short style
  entry = lookup_critical_style(method, critical_name, "",        args_size, true);
  if (entry != NULL) return entry;

  // Compute long name
  char* long_name = long_jni_name(method);
  if (long_name == NULL) {
    // JNI name mapping rejected this method so return
    // NULL to indicate UnsatisfiedLinkError should be thrown.
    return NULL;
  }

  // 2) Try JNI long style
  entry = lookup_critical_style(method, critical_name, long_name, args_size, true);
  if (entry != NULL) return entry;

  // 3) Try JNI short style without os prefix/suffix
  entry = lookup_critical_style(method, critical_name, "",        args_size, false);
  if (entry != NULL) return entry;

  // 4) Try JNI long style without os prefix/suffix
  entry = lookup_critical_style(method, critical_name, long_name, args_size, false);

  return entry; // NULL indicates not found
}

// Check if there are any JVM TI prefixes which have been applied to the native method name.
// If any are found, remove them before attemping the look up of the
// native implementation again.
// See SetNativeMethodPrefix in the JVM TI Spec for more details.
address NativeLookup::lookup_entry_prefixed(methodHandle method, bool& in_base_library, TRAPS) {
#if INCLUDE_JVMTI
  ResourceMark rm(THREAD);

  int prefix_count;
  char** prefixes = JvmtiExport::get_all_native_method_prefixes(&prefix_count);
  char* in_name = method->name()->as_C_string();
  char* wrapper_name = in_name;
  // last applied prefix will be first -- go backwards
  for (int i = prefix_count-1; i >= 0; i--) {
    char* prefix = prefixes[i];
    size_t prefix_len = strlen(prefix);
    if (strncmp(prefix, wrapper_name, prefix_len) == 0) {
      // has this prefix remove it
      wrapper_name += prefix_len;
    }
  }
  if (wrapper_name != in_name) {
    // we have a name for a wrapping method
    int wrapper_name_len = (int)strlen(wrapper_name);
    TempNewSymbol wrapper_symbol = SymbolTable::probe(wrapper_name, wrapper_name_len);
    if (wrapper_symbol != NULL) {
      KlassHandle kh(method->method_holder());
      Method* wrapper_method = kh()->lookup_method(wrapper_symbol,
                                                                  method->signature());
      if (wrapper_method != NULL && !wrapper_method->is_native()) {
        // we found a wrapper method, use its native entry
        method->set_is_prefixed_native();
        return lookup_entry(wrapper_method, in_base_library, THREAD);
      }
    }
  }
#endif // INCLUDE_JVMTI
  return NULL;
}

address NativeLookup::lookup_base(methodHandle method, bool& in_base_library, TRAPS) {
  address entry = NULL;
  ResourceMark rm(THREAD);

  entry = lookup_entry(method, in_base_library, THREAD);
  if (entry != NULL) return entry;

  // standard native method resolution has failed.  Check if there are any
  // JVM TI prefixes which have been applied to the native method name.
  entry = lookup_entry_prefixed(method, in_base_library, THREAD);
  if (entry != NULL) return entry;

  // Native function not found, throw UnsatisfiedLinkError
  THROW_MSG_0(vmSymbols::java_lang_UnsatisfiedLinkError(),
              method->name_and_sig_as_C_string());
}


address NativeLookup::lookup(methodHandle method, bool& in_base_library, TRAPS) {
  if (!method->has_native_function()) {
    address entry = lookup_base(method, in_base_library, CHECK_NULL);
    method->set_native_function(entry,
      Method::native_bind_event_is_interesting);
    // -verbose:jni printing
    if (PrintJNIResolving) {
      ResourceMark rm(THREAD);
      tty->print_cr("[Dynamic-linking native method %s.%s ... JNI]",
        method->method_holder()->external_name(),
        method->name()->as_C_string());
    }
  }
  return method->native_function();
}

address NativeLookup::base_library_lookup(const char* class_name, const char* method_name, const char* signature) {
  EXCEPTION_MARK;
  bool in_base_library = true;  // SharedRuntime inits some math methods.
  TempNewSymbol c_name = SymbolTable::new_symbol(class_name,  CATCH);
  TempNewSymbol m_name = SymbolTable::new_symbol(method_name, CATCH);
  TempNewSymbol s_name = SymbolTable::new_symbol(signature,   CATCH);

  // Find the class
  Klass* k = SystemDictionary::resolve_or_fail(c_name, true, CATCH);
  instanceKlassHandle klass (THREAD, k);

  // Find method and invoke standard lookup
  methodHandle method (THREAD,
                       klass->uncached_lookup_method(m_name, s_name, Klass::find_overpass));
  address result = lookup(method, in_base_library, CATCH);
  assert(in_base_library, "must be in basic library");
  guarantee(result != NULL, "must be non NULL");
  return result;
}
