/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_AARCH64_VM_THREAD_LINUX_AARCH64_HPP
#define OS_CPU_LINUX_AARCH64_VM_THREAD_LINUX_AARCH64_HPP

 private:
#ifdef ASSERT
  // spill stack holds N callee-save registers at each Java call and
  // grows downwards towards limit
  // we need limit to check we have space for a spill and base so we
  // can identify all live spill frames at GC (eventually)
  address          _spill_stack;
  address          _spill_stack_base;
  address          _spill_stack_limit;
#endif // ASSERT

  void pd_initialize() {
    _anchor.clear();
  }

  frame pd_last_frame() {
    assert(has_last_Java_frame(), "must have last_Java_sp() when suspended");
    return frame(_anchor.last_Java_sp(), _anchor.last_Java_fp(), _anchor.last_Java_pc());
  }

 public:
  // Mutators are highly dangerous....
  intptr_t* last_Java_fp()                       { return _anchor.last_Java_fp(); }
  void  set_last_Java_fp(intptr_t* fp)           { _anchor.set_last_Java_fp(fp);   }

  void set_base_of_stack_pointer(intptr_t* base_sp) {
  }

  static ByteSize last_Java_fp_offset()          {
    return byte_offset_of(JavaThread, _anchor) + JavaFrameAnchor::last_Java_fp_offset();
  }

  intptr_t* base_of_stack_pointer() {
    return NULL;
  }
  void record_base_of_stack_pointer() {
  }

  bool pd_get_top_frame_for_signal_handler(frame* fr_addr, void* ucontext,
    bool isInJava);

  bool pd_get_top_frame_for_profiling(frame* fr_addr, void* ucontext, bool isInJava);
private:
  bool pd_get_top_frame(frame* fr_addr, void* ucontext, bool isInJava);
public:

  // These routines are only used on cpu architectures that
  // have separate register stacks (Itanium).
  static bool register_stack_overflow() { return false; }
  static void enable_register_stack_guard() {}
  static void disable_register_stack_guard() {}

#endif // OS_CPU_LINUX_AARCH64_VM_THREAD_LINUX_AARCH64_HPP
