/*
 * Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "vm_version_aarch64.hpp"

#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/java.hpp"
#include "runtime/stubCodeGenerator.hpp"

#include "os_linux.inline.hpp"
#include <sys/auxv.h>
#include <asm/hwcap.h>

#ifndef HWCAP_AES
#define HWCAP_AES   (1<<3)
#endif

#ifndef HWCAP_PMULL
#define HWCAP_PMULL (1<<4)
#endif

#ifndef HWCAP_SHA1
#define HWCAP_SHA1  (1<<5)
#endif

#ifndef HWCAP_SHA2
#define HWCAP_SHA2  (1<<6)
#endif

#ifndef HWCAP_CRC32
#define HWCAP_CRC32 (1<<7)
#endif

#ifndef HWCAP_ATOMICS
#define HWCAP_ATOMICS (1<<8)
#endif

struct PsrInfo {
  uint32_t dczid_el0;
  uint32_t ctr_el0;
};

extern "C" {
  typedef void (*getPsrInfo_stub_t)(void*);
}
static getPsrInfo_stub_t getPsrInfo_stub = NULL;


class VM_Version_StubGenerator: public StubCodeGenerator {
 public:

  VM_Version_StubGenerator(CodeBuffer *c) : StubCodeGenerator(c) {}

  address generate_getPsrInfo() {
    StubCodeMark mark(this, "VM_Version", "getPsrInfo_stub");
#   define __ _masm->
    address start = __ pc();

    // void getPsrInfo(VM_Version::PsrInfo* psr_info);

    address entry = __ pc();

    __ enter();

    __ get_dczid_el0(rscratch1);
    __ strw(rscratch1, Address(c_rarg0, in_bytes(byte_offset_of(PsrInfo, dczid_el0))));

    __ get_ctr_el0(rscratch1);
    __ strw(rscratch1, Address(c_rarg0, in_bytes(byte_offset_of(PsrInfo, ctr_el0))));

    __ leave();
    __ ret(lr);

#   undef __

    return start;
  }
};


void VM_Version::get_processor_features() {
  PsrInfo _psr_info;
  {
    ResourceMark rm;
    getPsrInfo_stub_t getPsrInfo_stub = NULL;
    BufferBlob* stub_blob = BufferBlob::create("getPsrInfo_stub", 550 /* size */);
    if (stub_blob == NULL) {
      vm_exit_during_initialization("Unable to allocate getPsrInfo_stub");
    }

    CodeBuffer c(stub_blob);
    VM_Version_StubGenerator g(&c);
    getPsrInfo_stub = CAST_TO_FN_PTR(getPsrInfo_stub_t,
                                     g.generate_getPsrInfo());
    getPsrInfo_stub(&_psr_info);
  }
  _supports_cx8 = true;
  _supports_atomic_getset4 = true;
  _supports_atomic_getadd4 = true;
  _supports_atomic_getset8 = true;
  _supports_atomic_getadd8 = true;

  _icache_line_size = (1 << (_psr_info.ctr_el0 & 0x0f)) * 4;
  _dcache_line_size = (1 << ((_psr_info.ctr_el0 >> 16) & 0x0f)) * 4;

  // Check the DZP bit (bit 4) of dczid_el0 is zero
  // and block size (bit 0~3) is not zero.
  _zva_enabled = ((_psr_info.dczid_el0 & 0x10) == 0 && (_psr_info.dczid_el0 & 0xf) != 0);
  if (_zva_enabled) {
    _zva_length = 4 << (_psr_info.dczid_el0 & 0xf);
  }

  _cpuFeatures = getauxval(AT_HWCAP);

  int cpu_lines = 0;
  if (FILE *f = fopen("/proc/cpuinfo", "r")) {
    char buf[128], *p;
    while (fgets(buf, sizeof (buf), f) != NULL) {
      if ((p = strchr(buf, ':')) != NULL) {
        long v = strtol(p+1, NULL, 0);
        if (strncmp(buf, "CPU implementer", sizeof "CPU implementer" - 1) == 0) {
          _cpu = v;
          cpu_lines++;
        } else if (strncmp(buf, "CPU variant", sizeof "CPU variant" - 1) == 0) {
          _variant = v;
        } else if (strncmp(buf, "CPU part", sizeof "CPU part" - 1) == 0) {
          if (_model != v)  _model2 = _model;
          _model = v;
        } else if (strncmp(buf, "CPU revision", sizeof "CPU revision" - 1) == 0) {
          _revision = v;
        }
      }
    }
    fclose(f);
  }
  // If an olde style /proc/cpuinfo (cpu_lines == 1) then if _model is an A57 (0xd07)
  // we assume the worst and assume we could be on a big little system and have
  // undisclosed A53 cores which we could be swapped to at any stage
  if (_cpu == CPU_ARM && cpu_lines == 1 && _model == 0xd07) _cpuFeatures |= CPU_A53MAC;
}
