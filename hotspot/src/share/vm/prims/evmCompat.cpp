/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/debug.hpp"

// This file contains definitions for functions that exist
// in the ExactVM, but not in HotSpot. They are stubbed out
// here to prevent linker errors when attempting to use HotSpot
// with the ExactVM jdk.

extern "C" void JVM_Process_DestroyProcess(void);
extern "C" void JVM_Process_ForkAndExec(void);
extern "C" void JVM_Process_WaitForProcessExit(void);
extern "C" void gc(void);

void JVM_Process_DestroyProcess(void) {
  ShouldNotReachHere();
}

void JVM_Process_ForkAndExec(void) {
  ShouldNotReachHere();
}

void JVM_Process_WaitForProcessExit(void) {
  ShouldNotReachHere();
}

void gc(void) {
  ShouldNotReachHere();
}
