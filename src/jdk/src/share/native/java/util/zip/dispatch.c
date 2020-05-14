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
 * Native method support for java.util.zip.Deflater
 */

#include <string.h>
#include <stdio.h>
#include "jni.h"
#include "dispatch.h"

#if defined (__linux__)
#include <link.h>
#include <dlfcn.h>
#endif

#if defined(ZLIB_CLOUDFLARE) || defined(ZLIB_CHROMIUM)

#if (defined (__linux__) || defined(MACOSX)) && defined (__x86_64__)
#include <cpuid.h>
#elif defined (__linux__) && defined (__aarch64__)
#include <asm/hwcap.h>
#include <sys/auxv.h>
#elif defined (_MSC_VER)
#include <intrin.h>
#else
#error CPU detection not implemented for your platform
#endif

/* Simple check for SSE4.2 & PCLMUL support on Linux/Windows. */
/* Needs extesnion for other OS/CPU combinations.             */
static int cpu_supported() {
    int regs[4];
#if defined (_MSC_VER)
    __cpuid(regs, 1);
#elif (defined (__linux__) || defined(MACOSX)) && defined (__x86_64__)
    __cpuid(1, regs[0], regs[1], regs[2], regs[3]);
#elif defined (__linux__) && defined (__aarch64__)
    unsigned long features = getauxval(AT_HWCAP);
    return (features & HWCAP_CRC32) && (features & HWCAP_PMULL);
#else
    return 0;
#endif
    return (regs[2] & 0x100000 /* SSE 4.2 */) && (regs[2] & 0x2 /* PCLMUL */);
}

#endif /* defined(ZLIB_CLOUDFLARE) || defined(ZLIB_CHROMIUM) */

inflateInit2_type inflateInit2_func_ = &inflateInit2_;
inflateReset_type inflateReset_func = &inflateReset;
inflate_type inflate_func = &inflate;
inflateSetDictionary_type inflateSetDictionary_func = &inflateSetDictionary;
inflateEnd_type inflateEnd_func = &inflateEnd;
deflateInit2_type deflateInit2_func_ = &deflateInit2_;
deflateParams_type deflateParams_func = &deflateParams;
deflate_type deflate_func = &deflate;
deflateReset_type deflateReset_func = &deflateReset;
deflateSetDictionary_type deflateSetDictionary_func = &deflateSetDictionary;
deflateEnd_type deflateEnd_func = &deflateEnd;
adler32_type adler32_func = &adler32;
crc32_type crc32_func = &crc32;

/*
 * The following functions are defined and exported by the bundeld zlib-cloudflare
 * which was configured with "--zprefix" meaning that all its exported functions
 * and types are prefixed with "z_" (see zlib-cloudflare/zconf.h).
 */
extern inflateInit2_type z_inflateInit2_;
extern inflateReset_type z_inflateReset;
extern inflate_type z_inflate;
extern inflateSetDictionary_type z_inflateSetDictionary;
extern inflateEnd_type z_inflateEnd;
extern deflateInit2_type z_deflateInit2_;
extern deflateParams_type z_deflateParams;
extern deflate_type z_deflate;
extern deflateReset_type z_deflateReset;
extern deflateSetDictionary_type z_deflateSetDictionary;
extern deflateEnd_type z_deflateEnd;
extern adler32_type z_adler32;
extern crc32_type z_crc32;

/*
 * The following functions are defined and exported by the bundeld zlib-chromium
 * which prefixes all exported functions and types with "Cr_z_"
 * (see zlib-chromium/chromeconf.h).
 */
extern inflateInit2_type Cr_z_inflateInit2_;
extern inflateReset_type Cr_z_inflateReset;
extern inflate_type Cr_z_inflate;
extern inflateSetDictionary_type Cr_z_inflateSetDictionary;
extern inflateEnd_type Cr_z_inflateEnd;
extern deflateInit2_type Cr_z_deflateInit2_;
extern deflateParams_type Cr_z_deflateParams;
extern deflate_type Cr_z_deflate;
extern deflateReset_type Cr_z_deflateReset;
extern deflateSetDictionary_type Cr_z_deflateSetDictionary;
extern deflateEnd_type Cr_z_deflateEnd;
extern adler32_type Cr_z_adler32;
extern crc32_type Cr_z_crc32;

void JNICALL
ZIP_SwitchImplementation(const char *implementation, const char *feature) {
    static const char *BUNDLED = "bundled";
    static const char *SYSTEM = "system";
    static const char *CLOUDFLARE = "cloudflare";
    static const char *CHROMIUM = "chromium";
    static const char *INFLATE = "INFLATE";
    static const char *DEFLATE = "DEFLATE";
    static const char *ALL_FEATURES = "ALL";
    static const int INF = 1;
    static const int DEF = 2;
    static const int ALL = 3;

    if (implementation == NULL) return;
    int feat = 0;
    if (!strncmp(ALL_FEATURES, feature, strlen(ALL_FEATURES))) {
        feat = INF | DEF;
    }
    else if (!strncmp(INFLATE, feature, strlen(INFLATE))) {
        feat = INF;
    }
    else if (!strncmp(DEFLATE, feature, strlen(DEFLATE))) {
        feat = DEF;
    }
    if (!strncmp(BUNDLED, implementation, strlen(BUNDLED))) {
        if (feat & INF) {
            inflateInit2_func_ = &inflateInit2_;
            inflateReset_func = &inflateReset;
            inflate_func = &inflate;
            inflateSetDictionary_func = &inflateSetDictionary;
            inflateEnd_func = &inflateEnd;
        }
        if (feat & DEF) {
            deflateInit2_func_ = &deflateInit2_;
            deflateParams_func = &deflateParams;
            deflate_func = &deflate;
            deflateReset_func = &deflateReset;
            deflateSetDictionary_func = &deflateSetDictionary;
            deflateEnd_func = &deflateEnd;
        }
        if ((feat & ALL) == ALL) {
            adler32_func = &adler32;
            crc32_func = &crc32;
        }
    }
    else if (!strncmp(SYSTEM, implementation, strlen(SYSTEM))) {
#if defined(__linux__)
        void *zlib = dlopen("libz.so", RTLD_LAZY | RTLD_LOCAL);
        if (zlib != NULL) {
            char libz_location[PATH_MAX];
            if (dlinfo(zlib, RTLD_DI_ORIGIN, libz_location) == 0) {
                fprintf(stdout, "Info: loaded libz.so from %s\n", libz_location);
            }
            if (feat & INF) {
                inflateInit2_func_ = (inflateInit2_type)dlsym(zlib, "inflateInit2_");
                inflateReset_func = (inflateReset_type)dlsym(zlib, "inflateReset");
                inflate_func = (inflate_type)dlsym(zlib, "inflate");
                inflateSetDictionary_func = (inflateSetDictionary_type)dlsym(zlib, "inflateSetDictionary");
                inflateEnd_func = (inflateEnd_type)dlsym(zlib, "inflateEnd");
            }
            if (feat & DEF) {
                deflateInit2_func_ = (deflateInit2_type)dlsym(zlib, "deflateInit2_");
                deflateParams_func = (deflateParams_type)dlsym(zlib, "deflateParams");
                deflate_func = (deflate_type)dlsym(zlib, "deflate");
                deflateReset_func = (deflateReset_type)dlsym(zlib, "deflateReset");
                deflateSetDictionary_func = (deflateSetDictionary_type)dlsym(zlib, "deflateSetDictionary");
                deflateEnd_func = (deflateEnd_type)dlsym(zlib, "deflateEnd");
            }
            if ((feat & ALL) == ALL) {
                adler32_func = (adler32_type)dlsym(zlib, "adler32");
                crc32_func = (crc32_type)dlsym(zlib, "crc32");
            }
        }
#endif /* __linux__ */
    }
#if defined(ZLIB_CLOUDFLARE)
    else if (!strncmp(CLOUDFLARE, implementation, strlen(CLOUDFLARE))) {
        if (!cpu_supported()) {
            fprintf(stdout, "Warning: can't load zlib \"%s\" because your CPU doesn't support SSE4.2/PCLMUL!\n", implementation);
            fprintf(stdout, "         Falling back to \"bundled\".\n");
            return;
        }
        /*
         * We know that the functions in zlib-cloudflare have the same signatures
         * like the bundled zlib, but because the internal types of zlib-cloudflare
         * are all prefixed with "z_", the compiler thinks, they are different.
         * That's why we need the explicit casts here.
         */
        if (feat & INF) {
            inflateInit2_func_ = (inflateInit2_type)&z_inflateInit2_;
            inflateReset_func = (inflateReset_type)&z_inflateReset;
            inflate_func = (inflate_type)&z_inflate;
            inflateSetDictionary_func = (inflateSetDictionary_type)&z_inflateSetDictionary;
            inflateEnd_func = (inflateEnd_type)&z_inflateEnd;
        }
        if (feat & DEF) {
            deflateInit2_func_ = (deflateInit2_type)&z_deflateInit2_;
            deflateParams_func = (deflateParams_type)&z_deflateParams;
            deflate_func = (deflate_type)&z_deflate;
            deflateReset_func = (deflateReset_type)&z_deflateReset;
            deflateSetDictionary_func = (deflateSetDictionary_type)&z_deflateSetDictionary;
            deflateEnd_func = (deflateEnd_type)&z_deflateEnd;
        }
        if ((feat & ALL) == ALL) {
            adler32_func = (adler32_type)&z_adler32;
            crc32_func = (crc32_type)&z_crc32;
        }
    }
#endif
#if defined(ZLIB_CHROMIUM)
    else if (!strncmp(CHROMIUM, implementation, strlen(CHROMIUM))) {
        if (!cpu_supported()) {
            fprintf(stdout, "Warning: can't load zlib \"%s\" because your CPU doesn't support SSE4.2/PCLMUL!\n", implementation);
            fprintf(stdout, "         Falling back to \"bundled\".\n");
            return;
        }
        /*
         * We know that the functions in zlib-chromium have the same signatures
         * like the bundled zlib, but because the internal types of zlib-chromium
         * are all prefixed with "Cr_z_", the compiler thinks, they are different.
         * That's why we need the explicit casts here.
         */
        if (feat & INF) {
            inflateInit2_func_ = (inflateInit2_type)&Cr_z_inflateInit2_;
            inflateReset_func = (inflateReset_type)&Cr_z_inflateReset;
            inflate_func = (inflate_type)&Cr_z_inflate;
            inflateSetDictionary_func = (inflateSetDictionary_type)&Cr_z_inflateSetDictionary;
            inflateEnd_func = (inflateEnd_type)&Cr_z_inflateEnd;
        }
        if (feat & DEF) {
            deflateInit2_func_ = (deflateInit2_type)&Cr_z_deflateInit2_;
            deflateParams_func = (deflateParams_type)&Cr_z_deflateParams;
            deflate_func = (deflate_type)&Cr_z_deflate;
            deflateReset_func = (deflateReset_type)&Cr_z_deflateReset;
            deflateSetDictionary_func = (deflateSetDictionary_type)&Cr_z_deflateSetDictionary;
            deflateEnd_func = (deflateEnd_type)&Cr_z_deflateEnd;
        }
        if ((feat & ALL) == ALL) {
            adler32_func = (adler32_type)&Cr_z_adler32;
            crc32_func = (crc32_type)&Cr_z_crc32;
        }
    }
#endif
    else {
        fprintf(stdout, "Warning: unknown zlib implementation \"%s\"!\n", implementation);
        fprintf(stdout, "         Falling back to \"bundled\".\n");
    }
}
