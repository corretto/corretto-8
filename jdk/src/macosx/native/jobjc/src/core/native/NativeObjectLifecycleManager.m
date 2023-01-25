/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "com_apple_jobjc_NativeObjectLifecycleManager.h"

#include <JavaNativeFoundation/JavaNativeFoundation.h>


/*
 * Class:     com_apple_jobjc_NativeObjectLifecycleManager
 * Method:    retainNativeObject
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_apple_jobjc_NativeObjectLifecycleManager_retainNativeObject
(JNIEnv *env, jclass clazz, jlong ptr)
{
    if (ptr == 0L) return;
    CFRetain(jlong_to_ptr(ptr));
}

/*
 * Class:     com_apple_jobjc_NativeObjectLifecycleManager
 * Method:    releaseNativeObject
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_apple_jobjc_NativeObjectLifecycleManager_releaseNativeObject
(JNIEnv *env, jclass clazz, jlong ptr)
{
    if (ptr == 0L) return;
    CFRelease(jlong_to_ptr(ptr));
}

/*
 * Class:     com_apple_jobjc_NativeObjectLifecycleManager
 * Method:    freeNativeObject
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_apple_jobjc_NativeObjectLifecycleManager_freeNativeObject
(JNIEnv *env, jclass clazz, jlong ptr)
{
    if (ptr == 0L) return;
    free(jlong_to_ptr(ptr));
}
