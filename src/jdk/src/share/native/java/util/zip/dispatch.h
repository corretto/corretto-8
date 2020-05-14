/*
 * Copyright (c) 2020, Amazon and/or its affiliates. All rights reserved.
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
 * Support for switching between various zlib implemntations.
 */

#ifndef _ZLIB_DISPATCH_H_
#define _ZLIB_DISPATCH_H_

#include <zlib.h>
#include "jni.h"

typedef int (*inflateInit2_type) (z_streamp, int, const char*, int);
typedef int (*inflateReset_type) (z_streamp);
typedef int (*inflate_type) (z_streamp, int);
typedef int (*inflateSetDictionary_type) (z_streamp, const Bytef*, uInt);
typedef int (*inflateEnd_type) (z_streamp);
typedef int (*deflateInit2_type) (z_streamp, int, int, int, int, int, const char*, int);
typedef int (*deflateParams_type) (z_streamp, int, int);
typedef int (*deflate_type) (z_streamp, int);
typedef int (*deflateReset_type) (z_streamp);
typedef int (*deflateSetDictionary_type) (z_streamp, const Bytef*, uInt);
typedef int (*deflateEnd_type) (z_streamp);
typedef uLong (*adler32_type) (uLong, const Bytef*, uInt);
typedef uLong (*crc32_type) (uLong, const Bytef*, uInt);

#define deflateInit2_func(strm, level, method, windowBits, memLevel, strategy) \
        deflateInit2_func_((strm),(level),(method),(windowBits),(memLevel),\
                           (strategy), ZLIB_VERSION, (int)sizeof(z_stream))
#define inflateInit2_func(strm, windowBits) \
        inflateInit2_func_((strm), (windowBits), ZLIB_VERSION, \
                           (int)sizeof(z_stream))

extern inflateInit2_type inflateInit2_func_;
extern inflateReset_type inflateReset_func;
extern inflate_type inflate_func;
extern inflateSetDictionary_type inflateSetDictionary_func;
extern inflateEnd_type inflateEnd_func;
extern deflateInit2_type deflateInit2_func_;
extern deflateParams_type deflateParams_func;
extern deflate_type deflate_func;
extern deflateReset_type deflateReset_func;
extern deflateSetDictionary_type deflateSetDictionary_func;
extern deflateEnd_type deflateEnd_func;
extern adler32_type adler32_func;
extern crc32_type crc32_func;

void JNICALL
ZIP_SwitchImplementation(const char *implementation, const char *feature);

#endif /* !_ZLIB_DISPATCH_H_ */
