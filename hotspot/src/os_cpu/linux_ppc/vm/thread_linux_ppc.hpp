/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#ifndef OS_CPU_LINUX_PPC_VM_THREAD_LINUX_PPC_HPP
#define OS_CPU_LINUX_PPC_VM_THREAD_LINUX_PPC_HPP

 private:

  void pd_initialize() {
    _anchor.clear();
    _last_interpreter_fp = NULL;
  }

  // The `last' frame is the youngest Java frame on the thread's stack.
  frame pd_last_frame() {
    assert(has_last_Java_frame(), "must have last_Java_sp() when suspended");

    intptr_t* sp = last_Java_sp();
    address pc = _anchor.last_Java_pc();

    // Last_Java_pc ist not set, if we come here from compiled code.
    if (pc == NULL) {
      pc = (address) *(sp + 2);
    }

    return frame(sp, pc);
  }

 public:

  void set_base_of_stack_pointer(intptr_t* base_sp) {}
  intptr_t* base_of_stack_pointer() { return NULL; }
  void record_base_of_stack_pointer() {}

  // These routines are only used on cpu architectures that
  // have separate register stacks (Itanium).
  static bool register_stack_overflow() { return false; }
  static void enable_register_stack_guard() {}
  static void disable_register_stack_guard() {}

  bool pd_get_top_frame_for_signal_handler(frame* fr_addr, void* ucontext, bool isInJava);

  bool pd_get_top_frame_for_profiling(frame* fr_addr, void* ucontext, bool isInJava);

 protected:

  // -Xprof support
  //
  // In order to find the last Java fp from an async profile
  // tick, we store the current interpreter fp in the thread.
  // This value is only valid while we are in the C++ interpreter
  // and profiling.
  intptr_t *_last_interpreter_fp;

 public:

  static ByteSize last_interpreter_fp_offset() {
    return byte_offset_of(JavaThread, _last_interpreter_fp);
  }

  intptr_t* last_interpreter_fp() { return _last_interpreter_fp; }

#endif // OS_CPU_LINUX_PPC_VM_THREAD_LINUX_PPC_HPP
