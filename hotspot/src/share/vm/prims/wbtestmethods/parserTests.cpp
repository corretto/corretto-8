/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/symbolTable.hpp"

#include "prims/jni.h"
#include "prims/whitebox.hpp"
#include "prims/wbtestmethods/parserTests.hpp"
#include "runtime/interfaceSupport.hpp"

#include "memory/oopFactory.hpp"

#include "services/diagnosticArgument.hpp"
#include "services/diagnosticFramework.hpp"

//There's no way of beforeahnd knowing an upper size
//Of the length of a string representation of
//the value of an argument.
#define VALUE_MAXLEN 256

// DiagnosticFramework test utility methods

/*
 * The DiagnosticArgumentType class contains an enum that says which type
 * this argument represents. (JLONG, BOOLEAN etc).
 * This method Returns a char* representation of that enum value.
 */
static const char* lookup_diagnosticArgumentEnum(const char* field_name, oop object) {
  Thread* THREAD = Thread::current();
  const char* enum_sig = "Lsun/hotspot/parser/DiagnosticCommand$DiagnosticArgumentType;";
  TempNewSymbol enumSigSymbol = SymbolTable::lookup(enum_sig, (int) strlen(enum_sig), THREAD);
  int offset = WhiteBox::offset_for_field(field_name, object, enumSigSymbol);
  oop enumOop = object->obj_field(offset);

  const char* ret = WhiteBox::lookup_jstring("name", enumOop);
  return ret;
}

/*
 * Takes an oop to a DiagnosticArgumentType-instance and
 * reads the fields from it. Fills an native DCmdParser with
 * this info.
 */
static void fill_in_parser(DCmdParser* parser, oop argument)
{
  const char* name = WhiteBox::lookup_jstring("name", argument);
  const char* desc = WhiteBox::lookup_jstring("desc", argument);
  const char* default_value = WhiteBox::lookup_jstring("defaultValue", argument);
  bool mandatory = WhiteBox::lookup_bool("mandatory", argument);
  const char*  type = lookup_diagnosticArgumentEnum("type", argument);

   if (strcmp(type, "STRING") == 0) {
     DCmdArgument<char*>* argument = new DCmdArgument<char*>(
     name, desc,
     "STRING", mandatory, default_value);
     parser->add_dcmd_option(argument);
   } else if (strcmp(type, "NANOTIME") == 0) {
     DCmdArgument<NanoTimeArgument>* argument = new DCmdArgument<NanoTimeArgument>(
     name, desc,
     "NANOTIME", mandatory, default_value);
     parser->add_dcmd_option(argument);
   } else if (strcmp(type, "JLONG") == 0) {
     DCmdArgument<jlong>* argument = new DCmdArgument<jlong>(
     name, desc,
     "JLONG", mandatory, default_value);
     parser->add_dcmd_option(argument);
   } else if (strcmp(type, "BOOLEAN") == 0) {
     DCmdArgument<bool>* argument = new DCmdArgument<bool>(
     name, desc,
     "BOOLEAN", mandatory, default_value);
     parser->add_dcmd_option(argument);
   } else if (strcmp(type, "MEMORYSIZE") == 0) {
     DCmdArgument<MemorySizeArgument>* argument = new DCmdArgument<MemorySizeArgument>(
     name, desc,
     "MEMORY SIZE", mandatory, default_value);
     parser->add_dcmd_option(argument);
   } else if (strcmp(type, "STRINGARRAY") == 0) {
     DCmdArgument<StringArrayArgument*>* argument = new DCmdArgument<StringArrayArgument*>(
     name, desc,
     "STRING SET", mandatory);
     parser->add_dcmd_option(argument);
   }
}

/*
 * Will Fill in a java object array with alternating names of parsed command line options and
 * the value that has been parsed for it:
 * { name, value, name, value ... }
 * This can then be checked from java.
 */
WB_ENTRY(jobjectArray, WB_ParseCommandLine(JNIEnv* env, jobject o, jstring j_cmdline, jobjectArray arguments))
  ResourceMark rm;
  DCmdParser parser;

  const char* c_cmdline = java_lang_String::as_utf8_string(JNIHandles::resolve(j_cmdline));
  objArrayOop argumentArray = objArrayOop(JNIHandles::resolve_non_null(arguments));
  objArrayHandle argumentArray_ah(THREAD, argumentArray);

  int length = argumentArray_ah->length();

  for (int i = 0; i < length; i++) {
    oop argument_oop = argumentArray_ah->obj_at(i);
    fill_in_parser(&parser, argument_oop);
  }

  CmdLine cmdline(c_cmdline, strlen(c_cmdline), true);
  parser.parse(&cmdline,',',CHECK_NULL);

  Klass* k = SystemDictionary::Object_klass();
  objArrayOop returnvalue_array = oopFactory::new_objArray(k, parser.num_arguments() * 2, CHECK_NULL);
  objArrayHandle returnvalue_array_ah(THREAD, returnvalue_array);

  GrowableArray<const char *>*parsedArgNames = parser.argument_name_array();

  for (int i = 0; i < parser.num_arguments(); i++) {
    oop parsedName = java_lang_String::create_oop_from_str(parsedArgNames->at(i), CHECK_NULL);
    returnvalue_array_ah->obj_at_put(i*2, parsedName);
    GenDCmdArgument* arg = parser.lookup_dcmd_option(parsedArgNames->at(i), strlen(parsedArgNames->at(i)));
    char buf[VALUE_MAXLEN];
    arg->value_as_str(buf, sizeof(buf));
    oop parsedValue = java_lang_String::create_oop_from_str(buf, CHECK_NULL);
    returnvalue_array_ah->obj_at_put(i*2+1, parsedValue);
  }

  return (jobjectArray) JNIHandles::make_local(returnvalue_array_ah());

WB_END
