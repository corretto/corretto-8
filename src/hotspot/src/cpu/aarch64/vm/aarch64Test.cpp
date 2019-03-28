/*
 * Copyright (c) 2013, Red Hat Inc.
 * All rights reserved.
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
 */

#include <stdlib.h>

#include "precompiled.hpp"
#include "code/codeBlob.hpp"
#include "asm/macroAssembler.hpp"

// hook routine called during JVM bootstrap to test AArch64 assembler

extern "C" void entry(CodeBuffer*);

void aarch64TestHook()
{
  BufferBlob* b = BufferBlob::create("aarch64Test", 500000);
  CodeBuffer code(b);
  MacroAssembler _masm(&code);
  entry(&code);
}
