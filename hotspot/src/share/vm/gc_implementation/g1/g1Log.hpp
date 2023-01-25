/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1LOG_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1LOG_HPP

#include "memory/allocation.hpp"

class G1Log : public AllStatic {
 public:
  typedef enum {
    LevelNone,
    LevelFine,
    LevelFiner,
    LevelFinest
  } LogLevel;

 private:
  static LogLevel _level;

 public:
  inline static bool fine() {
    return _level >= LevelFine;
  }

  inline static bool finer() {
    return _level >= LevelFiner;
  }

  inline static bool finest() {
    return _level == LevelFinest;
  }

  static LogLevel level() {
    return _level;
  }

  static void init();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1LOG_HPP
