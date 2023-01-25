/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Native method support for java.util.zip.Inflater
 */

#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include "jlong.h"
#include "jni.h"
#include "jvm.h"
#include "jni_util.h"
#include <zlib.h>
#include "java_util_zip_Inflater.h"

#define ThrowDataFormatException(env, msg) \
        JNU_ThrowByName(env, "java/util/zip/DataFormatException", msg)

static jfieldID needDictID;
static jfieldID finishedID;
static jfieldID bufID, offID, lenID;

JNIEXPORT void JNICALL
Java_java_util_zip_Inflater_initIDs(JNIEnv *env, jclass cls)
{
    needDictID = (*env)->GetFieldID(env, cls, "needDict", "Z");
    CHECK_NULL(needDictID);
    finishedID = (*env)->GetFieldID(env, cls, "finished", "Z");
    CHECK_NULL(finishedID);
    bufID = (*env)->GetFieldID(env, cls, "buf", "[B");
    CHECK_NULL(bufID);
    offID = (*env)->GetFieldID(env, cls, "off", "I");
    CHECK_NULL(offID);
    lenID = (*env)->GetFieldID(env, cls, "len", "I");
    CHECK_NULL(lenID);
}

JNIEXPORT jlong JNICALL
Java_java_util_zip_Inflater_init(JNIEnv *env, jclass cls, jboolean nowrap)
{
    z_stream *strm = calloc(1, sizeof(z_stream));

    if (strm == NULL) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return jlong_zero;
    } else {
        const char *msg;
        int ret = inflateInit2(strm, nowrap ? -MAX_WBITS : MAX_WBITS);
        switch (ret) {
          case Z_OK:
            return ptr_to_jlong(strm);
          case Z_MEM_ERROR:
            free(strm);
            JNU_ThrowOutOfMemoryError(env, 0);
            return jlong_zero;
          default:
            msg = ((strm->msg != NULL) ? strm->msg :
                   (ret == Z_VERSION_ERROR) ?
                   "zlib returned Z_VERSION_ERROR: "
                   "compile time and runtime zlib implementations differ" :
                   (ret == Z_STREAM_ERROR) ?
                   "inflateInit2 returned Z_STREAM_ERROR" :
                   "unknown error initializing zlib library");
            free(strm);
            JNU_ThrowInternalError(env, msg);
            return jlong_zero;
        }
    }
}

JNIEXPORT void JNICALL
Java_java_util_zip_Inflater_setDictionary(JNIEnv *env, jclass cls, jlong addr,
                                          jarray b, jint off, jint len)
{
    Bytef *buf = (*env)->GetPrimitiveArrayCritical(env, b, 0);
    int res;
    if (buf == 0) /* out of memory */
        return;
    res = inflateSetDictionary(jlong_to_ptr(addr), buf + off, len);
    (*env)->ReleasePrimitiveArrayCritical(env, b, buf, 0);
    switch (res) {
    case Z_OK:
        break;
    case Z_STREAM_ERROR:
    case Z_DATA_ERROR:
        JNU_ThrowIllegalArgumentException(env, ((z_stream *)jlong_to_ptr(addr))->msg);
        break;
    default:
        JNU_ThrowInternalError(env, ((z_stream *)jlong_to_ptr(addr))->msg);
        break;
    }
}

JNIEXPORT jint JNICALL
Java_java_util_zip_Inflater_inflateBytes(JNIEnv *env, jobject this, jlong addr,
                                         jarray b, jint off, jint len)
{
    z_stream *strm = jlong_to_ptr(addr);
    jarray this_buf = (jarray)(*env)->GetObjectField(env, this, bufID);
    jint this_off = (*env)->GetIntField(env, this, offID);
    jint this_len = (*env)->GetIntField(env, this, lenID);

    jbyte *in_buf;
    jbyte *out_buf;
    int ret;

    in_buf  = (*env)->GetPrimitiveArrayCritical(env, this_buf, 0);
    if (in_buf == NULL) {
        if (this_len != 0 && (*env)->ExceptionOccurred(env) == NULL)
            JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
    }
    out_buf = (*env)->GetPrimitiveArrayCritical(env, b, 0);
    if (out_buf == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, this_buf, in_buf, 0);
        if (len != 0 && (*env)->ExceptionOccurred(env) == NULL)
            JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
    }
    strm->next_in  = (Bytef *) (in_buf + this_off);
    strm->next_out = (Bytef *) (out_buf + off);
    strm->avail_in  = this_len;
    strm->avail_out = len;
    ret = inflate(strm, Z_PARTIAL_FLUSH);
    (*env)->ReleasePrimitiveArrayCritical(env, b, out_buf, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, this_buf, in_buf, 0);

    switch (ret) {
    case Z_STREAM_END:
        (*env)->SetBooleanField(env, this, finishedID, JNI_TRUE);
        /* fall through */
    case Z_OK:
        this_off += this_len - strm->avail_in;
        (*env)->SetIntField(env, this, offID, this_off);
        (*env)->SetIntField(env, this, lenID, strm->avail_in);
        return (jint) (len - strm->avail_out);
    case Z_NEED_DICT:
        (*env)->SetBooleanField(env, this, needDictID, JNI_TRUE);
        /* Might have consumed some input here! */
        this_off += this_len - strm->avail_in;
        (*env)->SetIntField(env, this, offID, this_off);
        (*env)->SetIntField(env, this, lenID, strm->avail_in);
        return 0;
    case Z_BUF_ERROR:
        return 0;
    case Z_DATA_ERROR:
        ThrowDataFormatException(env, strm->msg);
        return 0;
    case Z_MEM_ERROR:
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
    default:
        JNU_ThrowInternalError(env, strm->msg);
        return 0;
    }
}

JNIEXPORT jint JNICALL
Java_java_util_zip_Inflater_getAdler(JNIEnv *env, jclass cls, jlong addr)
{
    return ((z_stream *)jlong_to_ptr(addr))->adler;
}

JNIEXPORT void JNICALL
Java_java_util_zip_Inflater_reset(JNIEnv *env, jclass cls, jlong addr)
{
    if (inflateReset(jlong_to_ptr(addr)) != Z_OK) {
        JNU_ThrowInternalError(env, 0);
    }
}

JNIEXPORT void JNICALL
Java_java_util_zip_Inflater_end(JNIEnv *env, jclass cls, jlong addr)
{
    if (inflateEnd(jlong_to_ptr(addr)) == Z_STREAM_ERROR) {
        JNU_ThrowInternalError(env, 0);
    } else {
        free(jlong_to_ptr(addr));
    }
}
