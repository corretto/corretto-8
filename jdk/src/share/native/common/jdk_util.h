/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef JDK_UTIL_H
#define JDK_UTIL_H

#include "jni.h"
#include "jvm.h"
#include "jdk_util_md.h"

#ifdef __cplusplus
extern "C" {
#endif

/*-------------------------------------------------------
 * Exported interfaces for both JDK and JVM to use
 *-------------------------------------------------------
 */

/*
 *
 */
JNIEXPORT void
JDK_GetVersionInfo0(jdk_version_info* info, size_t info_size);


/*-------------------------------------------------------
 * Internal interface for JDK to use
 *-------------------------------------------------------
 */

/* Init JVM handle for symbol lookup;
 * Return 0 if JVM handle not found.
 */
int JDK_InitJvmHandle();

/* Find the named JVM entry; returns NULL if not found. */
void* JDK_FindJvmEntry(const char* name);

#ifdef __cplusplus
} /* extern "C" */
#endif /* __cplusplus */

#endif /* JDK_UTIL_H */
