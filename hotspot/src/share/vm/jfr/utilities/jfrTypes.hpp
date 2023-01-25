/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_UTILITIES_JFRTYPES_HPP
#define SHARE_VM_JFR_UTILITIES_JFRTYPES_HPP

#include "jfrfiles/jfrEventIds.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

typedef u8 traceid;
typedef int fio_fd;
const int invalid_fd = -1;
const jlong invalid_offset = -1;
const u4 STACK_DEPTH_DEFAULT = 64;
const u4 MIN_STACK_DEPTH = 1;
const u4 MAX_STACK_DEPTH = 2048;

enum EventStartTime {
  UNTIMED,
  TIMED
};

jlong atomic_add_jlong(jlong value, jlong volatile* const dest);

#endif // SHARE_VM_JFR_UTILITIES_JFRTYPES_HPP
