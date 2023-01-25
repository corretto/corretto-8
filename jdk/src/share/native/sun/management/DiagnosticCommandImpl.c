/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

#include <stdlib.h>
#include <jni.h>
#include "management.h"
#include "sun_management_DiagnosticCommandImpl.h"

JNIEXPORT void JNICALL Java_sun_management_DiagnosticCommandImpl_setNotificationEnabled
(JNIEnv *env, jobject dummy, jboolean enabled) {
    if (jmm_version <= JMM_VERSION_1_2_2) {
        JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                        "JMX interface to diagnostic framework notifications is not supported by this VM");
        return;
    }
    jmm_interface->SetDiagnosticFrameworkNotificationEnabled(env, enabled);
}

JNIEXPORT jobjectArray JNICALL
Java_sun_management_DiagnosticCommandImpl_getDiagnosticCommands
  (JNIEnv *env, jobject dummy)
{
  return jmm_interface->GetDiagnosticCommands(env);
}

jobject getDiagnosticCommandArgumentInfoArray(JNIEnv *env, jstring command,
                                              int num_arg) {
  int i;
  jobject obj;
  jobjectArray result;
  dcmdArgInfo* dcmd_arg_info_array;
  jclass dcmdArgInfoCls;
  jclass arraysCls;
  jmethodID mid;
  jobject resultList;

  dcmd_arg_info_array = (dcmdArgInfo*) malloc(num_arg * sizeof(dcmdArgInfo));
  /* According to ISO C it is perfectly legal for malloc to return zero if called with a zero argument */
  if (dcmd_arg_info_array == NULL && num_arg != 0) {
    return NULL;
  }
  jmm_interface->GetDiagnosticCommandArgumentsInfo(env, command,
                                                   dcmd_arg_info_array);
  dcmdArgInfoCls = (*env)->FindClass(env,
                                     "sun/management/DiagnosticCommandArgumentInfo");
  result = (*env)->NewObjectArray(env, num_arg, dcmdArgInfoCls, NULL);
  if (result == NULL) {
    free(dcmd_arg_info_array);
    return NULL;
  }
  for (i=0; i<num_arg; i++) {
    obj = JNU_NewObjectByName(env,
                              "sun/management/DiagnosticCommandArgumentInfo",
                              "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZI)V",
                              (*env)->NewStringUTF(env,dcmd_arg_info_array[i].name),
                              (*env)->NewStringUTF(env,dcmd_arg_info_array[i].description),
                              (*env)->NewStringUTF(env,dcmd_arg_info_array[i].type),
                              dcmd_arg_info_array[i].default_string == NULL ? NULL:
                              (*env)->NewStringUTF(env, dcmd_arg_info_array[i].default_string),
                              dcmd_arg_info_array[i].mandatory,
                              dcmd_arg_info_array[i].option,
                              dcmd_arg_info_array[i].multiple,
                              dcmd_arg_info_array[i].position);
    if (obj == NULL) {
      free(dcmd_arg_info_array);
      return NULL;
    }
    (*env)->SetObjectArrayElement(env, result, i, obj);
  }
  free(dcmd_arg_info_array);
  arraysCls = (*env)->FindClass(env, "java/util/Arrays");
  mid = (*env)->GetStaticMethodID(env, arraysCls,
                                  "asList", "([Ljava/lang/Object;)Ljava/util/List;");
  resultList = (*env)->CallStaticObjectMethod(env, arraysCls, mid, result);
  return resultList;
}

/* Throws IllegalArgumentException if at least one of the diagnostic command
 * passed in argument is not supported by the JVM
 */
JNIEXPORT jobjectArray JNICALL
Java_sun_management_DiagnosticCommandImpl_getDiagnosticCommandInfo
(JNIEnv *env, jobject dummy, jobjectArray commands)
{
  int i;
  jclass dcmdInfoCls;
  jobject result;
  jobjectArray args;
  jobject obj;
  jmmOptionalSupport mos;
  jint ret = jmm_interface->GetOptionalSupport(env, &mos);
  jsize num_commands;
  dcmdInfo* dcmd_info_array;

  if (commands == NULL) {
      JNU_ThrowNullPointerException(env, "Invalid String Array");
      return NULL;
  }
  num_commands = (*env)->GetArrayLength(env, commands);
  dcmdInfoCls = (*env)->FindClass(env,
                                  "sun/management/DiagnosticCommandInfo");
  result = (*env)->NewObjectArray(env, num_commands, dcmdInfoCls, NULL);
  if (result == NULL) {
      JNU_ThrowOutOfMemoryError(env, 0);
      return NULL;
  }
  if (num_commands == 0) {
      /* Handle the 'zero commands' case specially to avoid calling 'malloc()' */
      /* with a zero argument because that may legally return a NULL pointer.  */
      return result;
  }
  dcmd_info_array = (dcmdInfo*) malloc(num_commands * sizeof(dcmdInfo));
  if (dcmd_info_array == NULL) {
      JNU_ThrowOutOfMemoryError(env, NULL);
      return NULL;
  }
  jmm_interface->GetDiagnosticCommandInfo(env, commands, dcmd_info_array);
  for (i=0; i<num_commands; i++) {
      args = getDiagnosticCommandArgumentInfoArray(env,
                                                   (*env)->GetObjectArrayElement(env,commands,i),
                                                   dcmd_info_array[i].num_arguments);
      if (args == NULL) {
          free(dcmd_info_array);
          JNU_ThrowOutOfMemoryError(env, 0);
          return NULL;
      }
      obj = JNU_NewObjectByName(env,
                                "sun/management/DiagnosticCommandInfo",
                                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/util/List;)V",
                                (*env)->NewStringUTF(env,dcmd_info_array[i].name),
                                (*env)->NewStringUTF(env,dcmd_info_array[i].description),
                                (*env)->NewStringUTF(env,dcmd_info_array[i].impact),
                                dcmd_info_array[i].permission_class==NULL?NULL:(*env)->NewStringUTF(env,dcmd_info_array[i].permission_class),
                                dcmd_info_array[i].permission_name==NULL?NULL:(*env)->NewStringUTF(env,dcmd_info_array[i].permission_name),
                                dcmd_info_array[i].permission_action==NULL?NULL:(*env)->NewStringUTF(env,dcmd_info_array[i].permission_action),
                                dcmd_info_array[i].enabled,
                                args);
      if (obj == NULL) {
          free(dcmd_info_array);
          JNU_ThrowOutOfMemoryError(env, 0);
          return NULL;
      }
      (*env)->SetObjectArrayElement(env, result, i, obj);
  }
  free(dcmd_info_array);
  return result;
}

/* Throws IllegalArgumentException if the diagnostic command
 * passed in argument is not supported by the JVM
 */
JNIEXPORT jstring JNICALL
Java_sun_management_DiagnosticCommandImpl_executeDiagnosticCommand
(JNIEnv *env, jobject dummy, jstring command) {
  return jmm_interface->ExecuteDiagnosticCommand(env, command);
}
