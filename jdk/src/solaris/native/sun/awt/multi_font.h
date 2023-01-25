/*
 * Copyright (c) 1996, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * header for Multi Font String
 */
#ifndef _MULTI_FONT_H_
#define _MULTI_FONT_H_

#ifndef HEADLESS
jboolean awtJNI_IsMultiFont(JNIEnv *env,jobject this);
jboolean awtJNI_IsMultiFontMetrics(JNIEnv *env,jobject this);
XFontSet awtJNI_MakeFontSet(JNIEnv *env,jobject font);
struct FontData *awtJNI_GetFontData(JNIEnv *env,jobject font, char **errmsg);
int32_t awtJNI_GetMFStringWidth(JNIEnv * env, jcharArray s, int32_t offset,
                                int32_t length, jobject font);
#endif /* !HEADLESS */

#endif /* _MULTI_FONT_H_ */
