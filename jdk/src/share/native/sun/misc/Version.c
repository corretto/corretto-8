/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jdk_util.h"

#include "sun_misc_Version.h"

char jvm_special_version = '\0';
char jdk_special_version = '\0';
static void setStaticIntField(JNIEnv* env, jclass cls, const char* name, jint value)
{
    char errmsg[100];
    jfieldID fid;
    fid = (*env)->GetStaticFieldID(env, cls, name, "I");
    if (fid != 0) {
        (*env)->SetStaticIntField(env, cls, fid, value);
    } else {
        sprintf(errmsg, "Static int field %s not found", name);
        JNU_ThrowInternalError(env, errmsg);
    }
}

static void setStaticBooleanField(JNIEnv* env, jclass cls, const char* name, jboolean value)
{
    char errmsg[100];
    jfieldID fid;
    fid = (*env)->GetStaticFieldID(env, cls, name, "Z");
    if (fid != 0) {
        (*env)->SetStaticBooleanField(env, cls, fid, value);
    } else {
        sprintf(errmsg, "Static boolean field %s not found", name);
        JNU_ThrowInternalError(env, errmsg);
    }
}

static void setStaticStringField(JNIEnv* env, jclass cls, const char* name, jstring value)
{
    char errmsg[100];
    jfieldID fid;
    fid = (*env)->GetStaticFieldID(env, cls, name, "Ljava/lang/String");
    if (fid != 0) {
        (*env)->SetStaticObjectField(env, cls, fid, value);
    } else {
        sprintf(errmsg, "Static String field %s not found", name);
        JNU_ThrowInternalError(env, errmsg);
    }
}


typedef void (JNICALL *GetJvmVersionInfo_fp)(JNIEnv*, jvm_version_info*, size_t);

JNIEXPORT jboolean JNICALL
Java_sun_misc_Version_getJvmVersionInfo(JNIEnv *env, jclass cls)
{
    jvm_version_info info;
    GetJvmVersionInfo_fp func_p;

    if (!JDK_InitJvmHandle()) {
        JNU_ThrowInternalError(env, "Handle for JVM not found for symbol lookup");
    }
    func_p = (GetJvmVersionInfo_fp) JDK_FindJvmEntry("JVM_GetVersionInfo");
    if (func_p == NULL) {
        return JNI_FALSE;
    }

    (*func_p)(env, &info, sizeof(info));
    setStaticIntField(env, cls, "jvm_major_version", JVM_VERSION_MAJOR(info.jvm_version));
    JNU_CHECK_EXCEPTION_RETURN(env, JNI_FALSE);
    setStaticIntField(env, cls, "jvm_minor_version", JVM_VERSION_MINOR(info.jvm_version));
    JNU_CHECK_EXCEPTION_RETURN(env, JNI_FALSE);
    setStaticIntField(env, cls, "jvm_micro_version", JVM_VERSION_MICRO(info.jvm_version));
    JNU_CHECK_EXCEPTION_RETURN(env, JNI_FALSE);
    setStaticIntField(env, cls, "jvm_build_number", JVM_VERSION_BUILD(info.jvm_version));
    JNU_CHECK_EXCEPTION_RETURN(env, JNI_FALSE);
    setStaticIntField(env, cls, "jvm_update_version", info.update_version);
    JNU_CHECK_EXCEPTION_RETURN(env, JNI_FALSE);
    jvm_special_version = info.special_update_version;

    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_sun_misc_Version_getJvmSpecialVersion(JNIEnv *env, jclass cls) {
    char s[2];
    jstring special;
    s[0] = jvm_special_version;
    s[1] = '\0';
    special = (*env)->NewStringUTF(env, s);
    return special;
}

JNIEXPORT void JNICALL
Java_sun_misc_Version_getJdkVersionInfo(JNIEnv *env, jclass cls)
{
    jdk_version_info info;

    JDK_GetVersionInfo0(&info, sizeof(info));
    setStaticIntField(env, cls, "jdk_major_version", JDK_VERSION_MAJOR(info.jdk_version));
    JNU_CHECK_EXCEPTION(env);
    setStaticIntField(env, cls, "jdk_minor_version", JDK_VERSION_MINOR(info.jdk_version));
    JNU_CHECK_EXCEPTION(env);
    setStaticIntField(env, cls, "jdk_micro_version", JDK_VERSION_MICRO(info.jdk_version));
    JNU_CHECK_EXCEPTION(env);
    setStaticIntField(env, cls, "jdk_build_number", JDK_VERSION_BUILD(info.jdk_version));
    JNU_CHECK_EXCEPTION(env);
    setStaticIntField(env, cls, "jdk_update_version", info.update_version);
    JNU_CHECK_EXCEPTION(env);
    jdk_special_version = info.special_update_version;
}

JNIEXPORT jstring JNICALL
Java_sun_misc_Version_getJdkSpecialVersion(JNIEnv *env, jclass cls) {
    char s[2];
    jstring special;
    s[0] = jdk_special_version;
    s[1] = '\0';
    special = (*env)->NewStringUTF(env, s);
    return special;
}
