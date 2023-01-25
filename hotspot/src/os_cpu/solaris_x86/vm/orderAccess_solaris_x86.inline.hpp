/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_SOLARIS_X86_VM_ORDERACCESS_SOLARIS_X86_INLINE_HPP
#define OS_CPU_SOLARIS_X86_VM_ORDERACCESS_SOLARIS_X86_INLINE_HPP

#include "runtime/atomic.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "vm_version_x86.hpp"

// Implementation of class OrderAccess.

// For Sun Studio - implementation is in solaris_i486.il.
// For gcc - implementation is just below.
extern "C" void _OrderAccess_acquire();
extern "C" void _OrderAccess_fence();

inline void OrderAccess::loadload()   { acquire(); }
inline void OrderAccess::storestore() { release(); }
inline void OrderAccess::loadstore()  { acquire(); }
inline void OrderAccess::storeload()  { fence(); }

inline void OrderAccess::acquire() {
  _OrderAccess_acquire();

}

inline void OrderAccess::release() {
  // Avoid hitting the same cache-line from
  // different threads.
  volatile jint local_dummy = 0;
}

inline void OrderAccess::fence() {
  if (os::is_MP()) {
    _OrderAccess_fence();
  }
}

#ifdef _GNU_SOURCE

extern "C" {
  inline void _OrderAccess_acquire() {
    volatile intptr_t local_dummy;
#ifdef AMD64
    __asm__ volatile ("movq 0(%%rsp), %0" : "=r" (local_dummy) : : "memory");
#else
    __asm__ volatile ("movl 0(%%esp),%0" : "=r" (local_dummy) : : "memory");
#endif // AMD64
  }
  inline void _OrderAccess_fence() {
    // Always use locked addl since mfence is sometimes expensive
    __asm__ volatile ("lock; addl $0,0(%%esp)" : : : "cc", "memory");
  }

}

#endif // GNU_SOURCE

inline jbyte    OrderAccess::load_acquire(volatile jbyte*   p) { return *p; }
inline jshort   OrderAccess::load_acquire(volatile jshort*  p) { return *p; }
inline jint     OrderAccess::load_acquire(volatile jint*    p) { return *p; }
inline jlong    OrderAccess::load_acquire(volatile jlong*   p) { return Atomic::load(p); }
inline jubyte   OrderAccess::load_acquire(volatile jubyte*  p) { return *p; }
inline jushort  OrderAccess::load_acquire(volatile jushort* p) { return *p; }
inline juint    OrderAccess::load_acquire(volatile juint*   p) { return *p; }
inline julong   OrderAccess::load_acquire(volatile julong*  p) { return Atomic::load((volatile jlong*)p); }
inline jfloat   OrderAccess::load_acquire(volatile jfloat*  p) { return *p; }
inline jdouble  OrderAccess::load_acquire(volatile jdouble* p) { return jdouble_cast(Atomic::load((volatile jlong*)p)); }

inline intptr_t OrderAccess::load_ptr_acquire(volatile intptr_t*   p) { return *p; }
inline void*    OrderAccess::load_ptr_acquire(volatile void*       p) { return *(void* volatile *)p; }
inline void*    OrderAccess::load_ptr_acquire(const volatile void* p) { return *(void* const volatile *)p; }

inline void     OrderAccess::release_store(volatile jbyte*   p, jbyte   v) { *p = v; }
inline void     OrderAccess::release_store(volatile jshort*  p, jshort  v) { *p = v; }
inline void     OrderAccess::release_store(volatile jint*    p, jint    v) { *p = v; }
inline void     OrderAccess::release_store(volatile jlong*   p, jlong   v) { Atomic::store(v, p); }
inline void     OrderAccess::release_store(volatile jubyte*  p, jubyte  v) { *p = v; }
inline void     OrderAccess::release_store(volatile jushort* p, jushort v) { *p = v; }
inline void     OrderAccess::release_store(volatile juint*   p, juint   v) { *p = v; }
inline void     OrderAccess::release_store(volatile julong*  p, julong  v) { Atomic::store((jlong)v, (volatile jlong*)p); }
inline void     OrderAccess::release_store(volatile jfloat*  p, jfloat  v) { *p = v; }
inline void     OrderAccess::release_store(volatile jdouble* p, jdouble v) { release_store((volatile jlong*)p, jlong_cast(v)); }

inline void     OrderAccess::release_store_ptr(volatile intptr_t* p, intptr_t v) { *p = v; }
inline void     OrderAccess::release_store_ptr(volatile void*     p, void*    v) { *(void* volatile *)p = v; }

inline void     OrderAccess::store_fence(jbyte*   p, jbyte   v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jshort*  p, jshort  v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jint*    p, jint    v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jlong*   p, jlong   v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jubyte*  p, jubyte  v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jushort* p, jushort v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(juint*   p, juint   v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(julong*  p, julong  v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jfloat*  p, jfloat  v) { *p = v; fence(); }
inline void     OrderAccess::store_fence(jdouble* p, jdouble v) { *p = v; fence(); }

inline void     OrderAccess::store_ptr_fence(intptr_t* p, intptr_t v) { *p = v; fence(); }
inline void     OrderAccess::store_ptr_fence(void**    p, void*    v) { *p = v; fence(); }

inline void     OrderAccess::release_store_fence(volatile jbyte*   p, jbyte   v) { *p = v; fence(); }
inline void     OrderAccess::release_store_fence(volatile jshort*  p, jshort  v) { *p = v; fence(); }
inline void     OrderAccess::release_store_fence(volatile jint*    p, jint    v) { *p = v; fence(); }
inline void     OrderAccess::release_store_fence(volatile jlong*   p, jlong   v) { release_store(p, v); fence(); }
inline void     OrderAccess::release_store_fence(volatile jubyte*  p, jubyte  v) { *p = v; fence(); }
inline void     OrderAccess::release_store_fence(volatile jushort* p, jushort v) { *p = v; fence(); }
inline void     OrderAccess::release_store_fence(volatile juint*   p, juint   v) { *p = v; fence(); }
inline void     OrderAccess::release_store_fence(volatile julong*  p, julong  v) { release_store((jlong *)p, (jlong)v); fence(); }
inline void     OrderAccess::release_store_fence(volatile jfloat*  p, jfloat  v) { *p = v; fence(); }
inline void     OrderAccess::release_store_fence(volatile jdouble* p, jdouble v) { release_store_fence((volatile jlong*)p, jlong_cast(v)); }

inline void     OrderAccess::release_store_ptr_fence(volatile intptr_t* p, intptr_t v) { *p = v; fence(); }
inline void     OrderAccess::release_store_ptr_fence(volatile void*     p, void*    v) { *(void* volatile *)p = v; fence(); }

#endif // OS_CPU_SOLARIS_X86_VM_ORDERACCESS_SOLARIS_X86_INLINE_HPP
