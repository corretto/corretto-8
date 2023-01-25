/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/java.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "vm_version_sparc.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif

int VM_Version::_features = VM_Version::unknown_m;
const char* VM_Version::_features_str = "";
unsigned int VM_Version::_L2_data_cache_line_size = 0;

void VM_Version::initialize() {

  assert(_features != VM_Version::unknown_m, "System pre-initialization is not complete.");
  guarantee(VM_Version::has_v9(), "only SPARC v9 is supported");

  PrefetchCopyIntervalInBytes = prefetch_copy_interval_in_bytes();
  PrefetchScanIntervalInBytes = prefetch_scan_interval_in_bytes();
  PrefetchFieldsAhead         = prefetch_fields_ahead();

  assert(0 <= AllocatePrefetchInstr && AllocatePrefetchInstr <= 1, "invalid value");
  if( AllocatePrefetchInstr < 0 ) AllocatePrefetchInstr = 0;
  if( AllocatePrefetchInstr > 1 ) AllocatePrefetchInstr = 0;

  // Allocation prefetch settings
  intx cache_line_size = prefetch_data_size();
  if( cache_line_size > AllocatePrefetchStepSize )
    AllocatePrefetchStepSize = cache_line_size;

  assert(AllocatePrefetchLines > 0, "invalid value");
  if( AllocatePrefetchLines < 1 )     // set valid value in product VM
    AllocatePrefetchLines = 3;
  assert(AllocateInstancePrefetchLines > 0, "invalid value");
  if( AllocateInstancePrefetchLines < 1 ) // set valid value in product VM
    AllocateInstancePrefetchLines = 1;

  AllocatePrefetchDistance = allocate_prefetch_distance();
  AllocatePrefetchStyle    = allocate_prefetch_style();

  assert((AllocatePrefetchDistance % AllocatePrefetchStepSize) == 0 &&
         (AllocatePrefetchDistance > 0), "invalid value");
  if ((AllocatePrefetchDistance % AllocatePrefetchStepSize) != 0 ||
      (AllocatePrefetchDistance <= 0)) {
    AllocatePrefetchDistance = AllocatePrefetchStepSize;
  }

  if (AllocatePrefetchStyle == 3 && (!has_blk_init() || cache_line_size <= 0)) {
    warning("BIS instructions are not available on this CPU");
    FLAG_SET_DEFAULT(AllocatePrefetchStyle, 1);
  }

  assert(ArraycopySrcPrefetchDistance < 4096, "invalid value");
  if (ArraycopySrcPrefetchDistance >= 4096)
    ArraycopySrcPrefetchDistance = 4064;
  assert(ArraycopyDstPrefetchDistance < 4096, "invalid value");
  if (ArraycopyDstPrefetchDistance >= 4096)
    ArraycopyDstPrefetchDistance = 4064;

  UseSSE = 0; // Only on x86 and x64

  _supports_cx8 = has_v9();
  _supports_atomic_getset4 = true; // swap instruction

  // There are Fujitsu Sparc64 CPUs which support blk_init as well so
  // we have to take this check out of the 'is_niagara()' block below.
  if (has_blk_init()) {
    // When using CMS or G1, we cannot use memset() in BOT updates
    // because the sun4v/CMT version in libc_psr uses BIS which
    // exposes "phantom zeros" to concurrent readers. See 6948537.
    if (FLAG_IS_DEFAULT(UseMemSetInBOT) && (UseConcMarkSweepGC || UseG1GC)) {
      FLAG_SET_DEFAULT(UseMemSetInBOT, false);
    }
    // Issue a stern warning if the user has explicitly set
    // UseMemSetInBOT (it is known to cause issues), but allow
    // use for experimentation and debugging.
    if (UseConcMarkSweepGC || UseG1GC) {
      if (UseMemSetInBOT) {
        assert(!FLAG_IS_DEFAULT(UseMemSetInBOT), "Error");
        warning("Experimental flag -XX:+UseMemSetInBOT is known to cause instability"
                " on sun4v; please understand that you are using at your own risk!");
      }
    }
  }

  if (is_niagara()) {
    // Indirect branch is the same cost as direct
    if (FLAG_IS_DEFAULT(UseInlineCaches)) {
      FLAG_SET_DEFAULT(UseInlineCaches, false);
    }
    // Align loops on a single instruction boundary.
    if (FLAG_IS_DEFAULT(OptoLoopAlignment)) {
      FLAG_SET_DEFAULT(OptoLoopAlignment, 4);
    }
#ifdef _LP64
    // 32-bit oops don't make sense for the 64-bit VM on sparc
    // since the 32-bit VM has the same registers and smaller objects.
    Universe::set_narrow_oop_shift(LogMinObjAlignmentInBytes);
    Universe::set_narrow_klass_shift(LogKlassAlignmentInBytes);
#endif // _LP64
#ifdef COMPILER2
    // Indirect branch is the same cost as direct
    if (FLAG_IS_DEFAULT(UseJumpTables)) {
      FLAG_SET_DEFAULT(UseJumpTables, true);
    }
    // Single-issue, so entry and loop tops are
    // aligned on a single instruction boundary
    if (FLAG_IS_DEFAULT(InteriorEntryAlignment)) {
      FLAG_SET_DEFAULT(InteriorEntryAlignment, 4);
    }
    if (is_niagara_plus()) {
      if (has_blk_init() && (cache_line_size > 0) && UseTLAB &&
          FLAG_IS_DEFAULT(AllocatePrefetchInstr)) {
        if (!has_sparc5_instr()) {
          // Use BIS instruction for TLAB allocation prefetch
          // on Niagara plus processors other than those based on CoreS4.
          FLAG_SET_DEFAULT(AllocatePrefetchInstr, 1);
        } else {
          // On CoreS4 processors use prefetch instruction
          // to avoid partial RAW issue, also use prefetch style 3.
          FLAG_SET_DEFAULT(AllocatePrefetchInstr, 0);
          if (FLAG_IS_DEFAULT(AllocatePrefetchStyle)) {
            FLAG_SET_DEFAULT(AllocatePrefetchStyle, 3);
          }
        }
        if (FLAG_IS_DEFAULT(AllocatePrefetchDistance)) {
          // Use smaller prefetch distance with BIS
          FLAG_SET_DEFAULT(AllocatePrefetchDistance, 64);
        }
      }
      if (is_T4()) {
        // Double number of prefetched cache lines on T4
        // since L2 cache line size is smaller (32 bytes).
        if (FLAG_IS_DEFAULT(AllocatePrefetchLines)) {
          FLAG_SET_ERGO(intx, AllocatePrefetchLines, AllocatePrefetchLines*2);
        }
        if (FLAG_IS_DEFAULT(AllocateInstancePrefetchLines)) {
          FLAG_SET_ERGO(intx, AllocateInstancePrefetchLines, AllocateInstancePrefetchLines*2);
        }
      }
      if (AllocatePrefetchStyle != 3 && FLAG_IS_DEFAULT(AllocatePrefetchDistance)) {
        // Use different prefetch distance without BIS
        FLAG_SET_DEFAULT(AllocatePrefetchDistance, 256);
      }
      if (AllocatePrefetchInstr == 1) {

        // Use allocation prefetch style 3 because BIS instructions
        // require aligned memory addresses.
        FLAG_SET_DEFAULT(AllocatePrefetchStyle, 3);

        // Need a space at the end of TLAB for BIS since it
        // will fault when accessing memory outside of heap.

        // +1 for rounding up to next cache line, +1 to be safe
        int lines = AllocatePrefetchLines + 2;
        int step_size = AllocatePrefetchStepSize;
        int distance = AllocatePrefetchDistance;
        _reserve_for_allocation_prefetch = (distance + step_size*lines)/(int)HeapWordSize;
      }
    }
#endif
  }

  // Use hardware population count instruction if available.
  if (has_hardware_popc()) {
    if (FLAG_IS_DEFAULT(UsePopCountInstruction)) {
      FLAG_SET_DEFAULT(UsePopCountInstruction, true);
    }
  } else if (UsePopCountInstruction) {
    warning("POPC instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UsePopCountInstruction, false);
  }

  // T4 and newer Sparc cpus have new compare and branch instruction.
  if (has_cbcond()) {
    if (FLAG_IS_DEFAULT(UseCBCond)) {
      FLAG_SET_DEFAULT(UseCBCond, true);
    }
  } else if (UseCBCond) {
    warning("CBCOND instruction is not available on this CPU");
    FLAG_SET_DEFAULT(UseCBCond, false);
  }

  assert(BlockZeroingLowLimit > 0, "invalid value");
  if (has_block_zeroing() && cache_line_size > 0) {
    if (FLAG_IS_DEFAULT(UseBlockZeroing)) {
      FLAG_SET_DEFAULT(UseBlockZeroing, true);
    }
  } else if (UseBlockZeroing) {
    warning("BIS zeroing instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseBlockZeroing, false);
  }

  assert(BlockCopyLowLimit > 0, "invalid value");
  if (has_block_zeroing() && cache_line_size > 0) { // has_blk_init() && is_T4(): core's local L2 cache
    if (FLAG_IS_DEFAULT(UseBlockCopy)) {
      FLAG_SET_DEFAULT(UseBlockCopy, true);
    }
  } else if (UseBlockCopy) {
    warning("BIS instructions are not available or expensive on this CPU");
    FLAG_SET_DEFAULT(UseBlockCopy, false);
  }

#ifdef COMPILER2
  // T4 and newer Sparc cpus have fast RDPC.
  if (has_fast_rdpc() && FLAG_IS_DEFAULT(UseRDPCForConstantTableBase)) {
    FLAG_SET_DEFAULT(UseRDPCForConstantTableBase, true);
  }

  // Currently not supported anywhere.
  FLAG_SET_DEFAULT(UseFPUForSpilling, false);

  MaxVectorSize = 8;

  assert((InteriorEntryAlignment % relocInfo::addr_unit()) == 0, "alignment is not a multiple of NOP size");
#endif

  assert((CodeEntryAlignment % relocInfo::addr_unit()) == 0, "alignment is not a multiple of NOP size");
  assert((OptoLoopAlignment % relocInfo::addr_unit()) == 0, "alignment is not a multiple of NOP size");

  char buf[512];
  jio_snprintf(buf, sizeof(buf), "%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s",
               (has_v9() ? ", v9" : (has_v8() ? ", v8" : "")),
               (has_hardware_popc() ? ", popc" : ""),
               (has_vis1() ? ", vis1" : ""),
               (has_vis2() ? ", vis2" : ""),
               (has_vis3() ? ", vis3" : ""),
               (has_blk_init() ? ", blk_init" : ""),
               (has_cbcond() ? ", cbcond" : ""),
               (has_aes() ? ", aes" : ""),
               (has_sha1() ? ", sha1" : ""),
               (has_sha256() ? ", sha256" : ""),
               (has_sha512() ? ", sha512" : ""),
               (is_ultra3() ? ", ultra3" : ""),
               (has_sparc5_instr() ? ", sparc5" : ""),
               (is_sun4v() ? ", sun4v" : ""),
               (is_niagara_plus() ? ", niagara_plus" : (is_niagara() ? ", niagara" : "")),
               (is_sparc64() ? ", sparc64" : ""),
               (!has_hardware_mul32() ? ", no-mul32" : ""),
               (!has_hardware_div32() ? ", no-div32" : ""),
               (!has_hardware_fsmuld() ? ", no-fsmuld" : ""));

  // buf is started with ", " or is empty
  _features_str = strdup(strlen(buf) > 2 ? buf + 2 : buf);

  // UseVIS is set to the smallest of what hardware supports and what
  // the command line requires.  I.e., you cannot set UseVIS to 3 on
  // older UltraSparc which do not support it.
  if (UseVIS > 3) UseVIS=3;
  if (UseVIS < 0) UseVIS=0;
  if (!has_vis3()) // Drop to 2 if no VIS3 support
    UseVIS = MIN2((intx)2,UseVIS);
  if (!has_vis2()) // Drop to 1 if no VIS2 support
    UseVIS = MIN2((intx)1,UseVIS);
  if (!has_vis1()) // Drop to 0 if no VIS1 support
    UseVIS = 0;

  // SPARC T4 and above should have support for AES instructions
  if (has_aes()) {
    if (FLAG_IS_DEFAULT(UseAES)) {
      FLAG_SET_DEFAULT(UseAES, true);
    }
    if (!UseAES) {
      if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
        warning("AES intrinsics require UseAES flag to be enabled. Intrinsics will be disabled.");
      }
      FLAG_SET_DEFAULT(UseAESIntrinsics, false);
    } else {
      // The AES intrinsic stubs require AES instruction support (of course)
      // but also require VIS3 mode or higher for instructions it use.
      if (UseVIS > 2) {
        if (FLAG_IS_DEFAULT(UseAESIntrinsics)) {
          FLAG_SET_DEFAULT(UseAESIntrinsics, true);
        }
      } else {
        if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
          warning("SPARC AES intrinsics require VIS3 instructions. Intrinsics will be disabled.");
        }
        FLAG_SET_DEFAULT(UseAESIntrinsics, false);
      }
    }
  } else if (UseAES || UseAESIntrinsics) {
    if (UseAES && !FLAG_IS_DEFAULT(UseAES)) {
      warning("AES instructions are not available on this CPU");
      FLAG_SET_DEFAULT(UseAES, false);
    }
    if (UseAESIntrinsics && !FLAG_IS_DEFAULT(UseAESIntrinsics)) {
      warning("AES intrinsics are not available on this CPU");
      FLAG_SET_DEFAULT(UseAESIntrinsics, false);
    }
  }

  // GHASH/GCM intrinsics
  if (has_vis3() && (UseVIS > 2)) {
    if (FLAG_IS_DEFAULT(UseGHASHIntrinsics)) {
      UseGHASHIntrinsics = true;
    }
  } else if (UseGHASHIntrinsics) {
    if (!FLAG_IS_DEFAULT(UseGHASHIntrinsics))
      warning("GHASH intrinsics require VIS3 insructions support. Intriniscs will be disabled");
    FLAG_SET_DEFAULT(UseGHASHIntrinsics, false);
  }

  // SHA1, SHA256, and SHA512 instructions were added to SPARC T-series at different times
  if (has_sha1() || has_sha256() || has_sha512()) {
    if (UseVIS > 0) { // SHA intrinsics use VIS1 instructions
      if (FLAG_IS_DEFAULT(UseSHA)) {
        FLAG_SET_DEFAULT(UseSHA, true);
      }
    } else {
      if (UseSHA) {
        warning("SPARC SHA intrinsics require VIS1 instruction support. Intrinsics will be disabled.");
        FLAG_SET_DEFAULT(UseSHA, false);
      }
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
    if (has_sha1()) {
      if (FLAG_IS_DEFAULT(UseSHA1Intrinsics)) {
        FLAG_SET_DEFAULT(UseSHA1Intrinsics, true);
      }
    } else if (UseSHA1Intrinsics) {
      warning("SHA1 instruction is not available on this CPU.");
      FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
    }
    if (has_sha256()) {
      if (FLAG_IS_DEFAULT(UseSHA256Intrinsics)) {
        FLAG_SET_DEFAULT(UseSHA256Intrinsics, true);
      }
    } else if (UseSHA256Intrinsics) {
      warning("SHA256 instruction (for SHA-224 and SHA-256) is not available on this CPU.");
      FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
    }

    if (has_sha512()) {
      if (FLAG_IS_DEFAULT(UseSHA512Intrinsics)) {
        FLAG_SET_DEFAULT(UseSHA512Intrinsics, true);
      }
    } else if (UseSHA512Intrinsics) {
      warning("SHA512 instruction (for SHA-384 and SHA-512) is not available on this CPU.");
      FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
    }
    if (!(UseSHA1Intrinsics || UseSHA256Intrinsics || UseSHA512Intrinsics)) {
      FLAG_SET_DEFAULT(UseSHA, false);
    }
  }

  if (FLAG_IS_DEFAULT(ContendedPaddingWidth) &&
    (cache_line_size > ContendedPaddingWidth))
    ContendedPaddingWidth = cache_line_size;

#ifndef PRODUCT
  if (PrintMiscellaneous && Verbose) {
    tty->print_cr("L1 data cache line size: %u", L1_data_cache_line_size());
    tty->print_cr("L2 data cache line size: %u", L2_data_cache_line_size());
    tty->print("Allocation");
    if (AllocatePrefetchStyle <= 0) {
      tty->print_cr(": no prefetching");
    } else {
      tty->print(" prefetching: ");
      if (AllocatePrefetchInstr == 0) {
          tty->print("PREFETCH");
      } else if (AllocatePrefetchInstr == 1) {
          tty->print("BIS");
      }
      if (AllocatePrefetchLines > 1) {
        tty->print_cr(" at distance %d, %d lines of %d bytes", (int) AllocatePrefetchDistance, (int) AllocatePrefetchLines, (int) AllocatePrefetchStepSize);
      } else {
        tty->print_cr(" at distance %d, one line of %d bytes", (int) AllocatePrefetchDistance, (int) AllocatePrefetchStepSize);
      }
    }
    if (PrefetchCopyIntervalInBytes > 0) {
      tty->print_cr("PrefetchCopyIntervalInBytes %d", (int) PrefetchCopyIntervalInBytes);
    }
    if (PrefetchScanIntervalInBytes > 0) {
      tty->print_cr("PrefetchScanIntervalInBytes %d", (int) PrefetchScanIntervalInBytes);
    }
    if (PrefetchFieldsAhead > 0) {
      tty->print_cr("PrefetchFieldsAhead %d", (int) PrefetchFieldsAhead);
    }
    if (ContendedPaddingWidth > 0) {
      tty->print_cr("ContendedPaddingWidth %d", (int) ContendedPaddingWidth);
    }
  }
#endif // PRODUCT
}

void VM_Version::print_features() {
  tty->print_cr("Version:%s", cpu_features());
}

int VM_Version::determine_features() {
  if (UseV8InstrsOnly) {
    NOT_PRODUCT(if (PrintMiscellaneous && Verbose) tty->print_cr("Version is Forced-V8");)
    return generic_v8_m;
  }

  int features = platform_features(unknown_m); // platform_features() is os_arch specific

  if (features == unknown_m) {
    features = generic_v9_m;
    warning("Cannot recognize SPARC version. Default to V9");
  }

  assert(is_T_family(features) == is_niagara(features), "Niagara should be T series");
  if (UseNiagaraInstrs) { // Force code generation for Niagara
    if (is_T_family(features)) {
      // Happy to accomodate...
    } else {
      NOT_PRODUCT(if (PrintMiscellaneous && Verbose) tty->print_cr("Version is Forced-Niagara");)
      features |= T_family_m;
    }
  } else {
    if (is_T_family(features) && !FLAG_IS_DEFAULT(UseNiagaraInstrs)) {
      NOT_PRODUCT(if (PrintMiscellaneous && Verbose) tty->print_cr("Version is Forced-Not-Niagara");)
      features &= ~(T_family_m | T1_model_m);
    } else {
      // Happy to accomodate...
    }
  }

  return features;
}

static int saved_features = 0;

void VM_Version::allow_all() {
  saved_features = _features;
  _features      = all_features_m;
}

void VM_Version::revert() {
  _features = saved_features;
}

unsigned int VM_Version::calc_parallel_worker_threads() {
  unsigned int result;
  if (is_M_series() || is_S_series()) {
    // for now, use same gc thread calculation for M-series and S-series as for
    // niagara-plus. In future, we may want to tweak parameters for
    // nof_parallel_worker_thread
    result = nof_parallel_worker_threads(5, 16, 8);
  } else if (is_niagara_plus()) {
    result = nof_parallel_worker_threads(5, 16, 8);
  } else {
    result = nof_parallel_worker_threads(5, 8, 8);
  }
  return result;
}


int VM_Version::parse_features(const char* implementation) {
  int features = unknown_m;
  // Convert to UPPER case before compare.
  char* impl = os::strdup(implementation);

  for (int i = 0; impl[i] != 0; i++)
    impl[i] = (char)toupper((uint)impl[i]);

  if (strstr(impl, "SPARC64") != NULL) {
    features |= sparc64_family_m;
  } else if (strstr(impl, "SPARC-M") != NULL) {
    // M-series SPARC is based on T-series.
    features |= (M_family_m | T_family_m);
  } else if (strstr(impl, "SPARC-S") != NULL) {
    // S-series SPARC is based on T-series.
    features |= (S_family_m | T_family_m);
  } else if (strstr(impl, "SPARC-T") != NULL) {
    features |= T_family_m;
    if (strstr(impl, "SPARC-T1") != NULL) {
      features |= T1_model_m;
    }
  } else if (strstr(impl, "SUN4V-CPU") != NULL) {
    // Generic or migration class LDOM
    features |= T_family_m;
  } else {
#ifndef PRODUCT
    warning("Failed to parse CPU implementation = '%s'", impl);
#endif
  }
  os::free((void*)impl);
  return features;
}
