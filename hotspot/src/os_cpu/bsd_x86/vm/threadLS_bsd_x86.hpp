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

#ifndef OS_CPU_BSD_X86_VM_THREADLS_BSD_X86_HPP
#define OS_CPU_BSD_X86_VM_THREADLS_BSD_X86_HPP

  // Processor dependent parts of ThreadLocalStorage

#ifndef AMD64
  // map stack pointer to thread pointer - see notes in threadLS_bsd_x86.cpp
  #define SP_BITLENGTH  32
#ifndef PAGE_SHIFT
  #define PAGE_SHIFT    12
  #define PAGE_SIZE     (1UL << PAGE_SHIFT)
#endif
  static Thread* _sp_map[1UL << (SP_BITLENGTH - PAGE_SHIFT)];
#endif // !AMD64

public:

#ifndef AMD64
  static Thread** sp_map_addr() { return _sp_map; }
#endif // !AMD64

  static Thread* thread() {
#ifdef AMD64
    return (Thread*) os::thread_local_storage_at(thread_index());
#else
    uintptr_t sp;
    __asm__ volatile ("movl %%esp, %0" : "=r" (sp));
    return _sp_map[sp >> PAGE_SHIFT];
#endif // AMD64
  }

#endif // OS_CPU_BSD_X86_VM_THREADLS_BSD_X86_HPP
