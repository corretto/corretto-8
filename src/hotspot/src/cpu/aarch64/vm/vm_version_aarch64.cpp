/*
 * Copyright (c) 2013, Red Hat Inc.
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/java.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "vm_version_aarch64.hpp"

int VM_Version::_cpu;
int VM_Version::_model;
int VM_Version::_model2;
int VM_Version::_variant;
int VM_Version::_revision;
int VM_Version::_stepping;
int VM_Version::_cpuFeatures;
int VM_Version::_icache_line_size;
int VM_Version::_dcache_line_size;
int VM_Version::_zva_length;
bool VM_Version::_zva_enabled;
const char*           VM_Version::_features_str = "";

void VM_Version::initialize() {
  get_processor_features();

  char buf[512];

  strcpy(buf, "simd");
  if (_cpuFeatures & CPU_CRC32) strcat(buf, ", crc");
  if (_cpuFeatures & CPU_AES)   strcat(buf, ", aes");
  if (_cpuFeatures & CPU_SHA1)  strcat(buf, ", sha1");
  if (_cpuFeatures & CPU_SHA2)  strcat(buf, ", sha256");
  if (_cpuFeatures & CPU_LSE) strcat(buf, ", lse");

  _features_str = strdup(buf);

  // Limit AllocatePrefetchDistance so that it does not exceed the
  // constraint in AllocatePrefetchDistanceConstraintFunc.
  if (FLAG_IS_DEFAULT(AllocatePrefetchDistance))
    FLAG_SET_DEFAULT(AllocatePrefetchDistance, MIN2(512, 3*_dcache_line_size));

  if (FLAG_IS_DEFAULT(AllocatePrefetchStepSize))
    FLAG_SET_DEFAULT(AllocatePrefetchStepSize, _dcache_line_size);
  if (FLAG_IS_DEFAULT(PrefetchScanIntervalInBytes))
    FLAG_SET_DEFAULT(PrefetchScanIntervalInBytes, 3*_dcache_line_size);
  if (FLAG_IS_DEFAULT(PrefetchCopyIntervalInBytes))
    FLAG_SET_DEFAULT(PrefetchCopyIntervalInBytes, 3*_dcache_line_size);

  if (PrefetchCopyIntervalInBytes != -1 &&
       ((PrefetchCopyIntervalInBytes & 7) || (PrefetchCopyIntervalInBytes >= 32768))) {
    warning("PrefetchCopyIntervalInBytes must be -1, or a multiple of 8 and < 32768");
    PrefetchCopyIntervalInBytes &= ~7;
    if (PrefetchCopyIntervalInBytes >= 32768)
      PrefetchCopyIntervalInBytes = 32760;
  }

  if (AllocatePrefetchDistance !=-1 && (AllocatePrefetchDistance & 7)) {
    warning("AllocatePrefetchDistance must be multiple of 8");
    AllocatePrefetchDistance &= ~7;
  }

  if (AllocatePrefetchStepSize & 7) {
    warning("AllocatePrefetchStepSize must be multiple of 8");
    AllocatePrefetchStepSize &= ~7;
  }

  FLAG_SET_DEFAULT(UseSSE42Intrinsics, true);

  // Enable vendor specific features
  if (_cpu == CPU_CAVIUM) {
    if (_variant == 0) _cpuFeatures |= CPU_DMB_ATOMICS;
    if (FLAG_IS_DEFAULT(AvoidUnalignedAccesses)) {
      FLAG_SET_DEFAULT(AvoidUnalignedAccesses, true);
    }
    if (FLAG_IS_DEFAULT(UseSIMDForMemoryOps)) {
      FLAG_SET_DEFAULT(UseSIMDForMemoryOps, (_variant > 0));
    }
  }
  if (_cpu == CPU_ARM && (_model == 0xd03 || _model2 == 0xd03)) _cpuFeatures |= CPU_A53MAC;
  if (_cpu == CPU_ARM && (_model == 0xd07 || _model2 == 0xd07)) _cpuFeatures |= CPU_STXR_PREFETCH;

  if (FLAG_IS_DEFAULT(UseCRC32)) {
    UseCRC32 = (_cpuFeatures & CPU_CRC32) != 0;
  }
  if (UseCRC32 && (_cpuFeatures & CPU_CRC32) == 0) {
    warning("UseCRC32 specified, but not supported on this CPU");
  }

  if (_cpuFeatures & CPU_LSE) {
    if (FLAG_IS_DEFAULT(UseLSE))
      FLAG_SET_DEFAULT(UseLSE, true);
  } else {
    if (UseLSE) {
      warning("UseLSE specified, but not supported on this CPU");
    }
  }

  if (_cpuFeatures & CPU_AES) {
    UseAES = UseAES || FLAG_IS_DEFAULT(UseAES);
    UseAESIntrinsics =
        UseAESIntrinsics || (UseAES && FLAG_IS_DEFAULT(UseAESIntrinsics));
    if (UseAESIntrinsics && !UseAES) {
      warning("UseAESIntrinsics enabled, but UseAES not, enabling");
      UseAES = true;
    }
  } else {
    if (UseAES) {
      warning("UseAES specified, but not supported on this CPU");
    }
    if (UseAESIntrinsics) {
      warning("UseAESIntrinsics specified, but not supported on this CPU");
    }
  }

  if (_cpuFeatures & CPU_PMULL) {
    if (FLAG_IS_DEFAULT(UseGHASHIntrinsics)) {
      FLAG_SET_DEFAULT(UseGHASHIntrinsics, true);
    }
  } else if (UseGHASHIntrinsics) {
    warning("GHASH intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseGHASHIntrinsics, false);
  }

  if (FLAG_IS_DEFAULT(UseCRC32Intrinsics)) {
    UseCRC32Intrinsics = true;
  }

  if (_cpuFeatures & (CPU_SHA1 | CPU_SHA2)) {
    if (FLAG_IS_DEFAULT(UseSHA)) {
      FLAG_SET_DEFAULT(UseSHA, true);
    }
  } else if (UseSHA) {
    warning("SHA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (!UseSHA) {
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
    FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
  } else {
    if (_cpuFeatures & CPU_SHA1) {
      if (FLAG_IS_DEFAULT(UseSHA1Intrinsics)) {
        FLAG_SET_DEFAULT(UseSHA1Intrinsics, true);
      }
    } else if (UseSHA1Intrinsics) {
      warning("SHA1 instruction is not available on this CPU.");
      FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
    }
    if (_cpuFeatures & CPU_SHA2) {
      if (FLAG_IS_DEFAULT(UseSHA256Intrinsics)) {
        FLAG_SET_DEFAULT(UseSHA256Intrinsics, true);
      }
    } else if (UseSHA256Intrinsics) {
      warning("SHA256 instruction (for SHA-224 and SHA-256) is not available on this CPU.");
      FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
    }
    if (UseSHA512Intrinsics) {
      warning("SHA512 instruction (for SHA-384 and SHA-512) is not available on this CPU.");
      FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
    }
  }

  if (_zva_enabled) {
    if (FLAG_IS_DEFAULT(UseBlockZeroing)) {
      FLAG_SET_DEFAULT(UseBlockZeroing, true);
    }
    if (FLAG_IS_DEFAULT(BlockZeroingLowLimit)) {
      FLAG_SET_DEFAULT(BlockZeroingLowLimit, 4 * _zva_length);
    }
  } else if (UseBlockZeroing) {
    warning("DC ZVA is not available on this CPU");
    FLAG_SET_DEFAULT(UseBlockZeroing, false);
  }

  if (FLAG_IS_DEFAULT(UseMultiplyToLenIntrinsic)) {
    UseMultiplyToLenIntrinsic = true;
  }

  if (FLAG_IS_DEFAULT(UseBarriersForVolatile)) {
    UseBarriersForVolatile = (_cpuFeatures & CPU_DMB_ATOMICS) != 0;
  }

  if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
    UsePopCountInstruction = true;
  }

  if (FLAG_IS_DEFAULT(UseMontgomeryMultiplyIntrinsic)) {
    UseMontgomeryMultiplyIntrinsic = true;
  }
  if (FLAG_IS_DEFAULT(UseMontgomerySquareIntrinsic)) {
    UseMontgomerySquareIntrinsic = true;
  }

#ifdef COMPILER2
  if (FLAG_IS_DEFAULT(OptoScheduling)) {
    OptoScheduling = true;
  }
#else
  if (ReservedCodeCacheSize > 128*M) {
    vm_exit_during_initialization("client compiler does not support ReservedCodeCacheSize > 128M");
  }
#endif
}
