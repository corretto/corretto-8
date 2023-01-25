/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_SPARC_VM_ATOMIC_LINUX_SPARC_INLINE_HPP
#define OS_CPU_LINUX_SPARC_VM_ATOMIC_LINUX_SPARC_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "vm_version_sparc.hpp"

// Implementation of class atomic

inline void Atomic::store    (jbyte    store_value, jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, jint*     dest) { *dest = store_value; }
inline void Atomic::store    (jlong    store_value, jlong*    dest) { *dest = store_value; }
inline void Atomic::store_ptr(intptr_t store_value, intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, void*     dest) { *(void**)dest = store_value; }

inline void Atomic::store    (jbyte    store_value, volatile jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, volatile jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, volatile jint*     dest) { *dest = store_value; }
inline void Atomic::store    (jlong    store_value, volatile jlong*    dest) { *dest = store_value; }
inline void Atomic::store_ptr(intptr_t store_value, volatile intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, volatile void*     dest) { *(void* volatile *)dest = store_value; }

inline void Atomic::inc    (volatile jint*     dest) { (void)add    (1, dest); }
inline void Atomic::inc_ptr(volatile intptr_t* dest) { (void)add_ptr(1, dest); }
inline void Atomic::inc_ptr(volatile void*     dest) { (void)add_ptr(1, dest); }

inline void Atomic::dec    (volatile jint*     dest) { (void)add    (-1, dest); }
inline void Atomic::dec_ptr(volatile intptr_t* dest) { (void)add_ptr(-1, dest); }
inline void Atomic::dec_ptr(volatile void*     dest) { (void)add_ptr(-1, dest); }

inline jlong Atomic::load(volatile jlong* src) { return *src; }

inline jint     Atomic::add    (jint     add_value, volatile jint*     dest) {
  intptr_t rv;
  __asm__ volatile(
    "1: \n\t"
    " ld     [%2], %%o2\n\t"
    " add    %1, %%o2, %%o3\n\t"
    " cas    [%2], %%o2, %%o3\n\t"
    " cmp    %%o2, %%o3\n\t"
    " bne    1b\n\t"
    "  nop\n\t"
    " add    %1, %%o2, %0\n\t"
    : "=r" (rv)
    : "r" (add_value), "r" (dest)
    : "memory", "o2", "o3");
  return rv;
}

inline intptr_t Atomic::add_ptr(intptr_t add_value, volatile intptr_t* dest) {
  intptr_t rv;
#ifdef _LP64
  __asm__ volatile(
    "1: \n\t"
    " ldx    [%2], %%o2\n\t"
    " add    %1, %%o2, %%o3\n\t"
    " casx   [%2], %%o2, %%o3\n\t"
    " cmp    %%o2, %%o3\n\t"
    " bne    %%xcc, 1b\n\t"
    "  nop\n\t"
    " add    %1, %%o2, %0\n\t"
    : "=r" (rv)
    : "r" (add_value), "r" (dest)
    : "memory", "o2", "o3");
#else
  __asm__ volatile(
    "1: \n\t"
    " ld     [%2], %%o2\n\t"
    " add    %1, %%o2, %%o3\n\t"
    " cas    [%2], %%o2, %%o3\n\t"
    " cmp    %%o2, %%o3\n\t"
    " bne    1b\n\t"
    "  nop\n\t"
    " add    %1, %%o2, %0\n\t"
    : "=r" (rv)
    : "r" (add_value), "r" (dest)
    : "memory", "o2", "o3");
#endif // _LP64
  return rv;
}

inline void*    Atomic::add_ptr(intptr_t add_value, volatile void*     dest) {
  return (void*)add_ptr((intptr_t)add_value, (volatile intptr_t*)dest);
}


inline jint     Atomic::xchg    (jint     exchange_value, volatile jint*     dest) {
  intptr_t rv = exchange_value;
  __asm__ volatile(
    " swap   [%2],%1\n\t"
    : "=r" (rv)
    : "0" (exchange_value) /* we use same register as for return value */, "r" (dest)
    : "memory");
  return rv;
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest) {
  intptr_t rv = exchange_value;
#ifdef _LP64
  __asm__ volatile(
    "1:\n\t"
    " mov    %1, %%o3\n\t"
    " ldx    [%2], %%o2\n\t"
    " casx   [%2], %%o2, %%o3\n\t"
    " cmp    %%o2, %%o3\n\t"
    " bne    %%xcc, 1b\n\t"
    "  nop\n\t"
    " mov    %%o2, %0\n\t"
    : "=r" (rv)
    : "r" (exchange_value), "r" (dest)
    : "memory", "o2", "o3");
#else
  __asm__ volatile(
    "swap    [%2],%1\n\t"
    : "=r" (rv)
    : "0" (exchange_value) /* we use same register as for return value */, "r" (dest)
    : "memory");
#endif // _LP64
  return rv;
}

inline void*    Atomic::xchg_ptr(void*    exchange_value, volatile void*     dest) {
  return (void*)xchg_ptr((intptr_t)exchange_value, (volatile intptr_t*)dest);
}


inline jint     Atomic::cmpxchg    (jint     exchange_value, volatile jint*     dest, jint     compare_value) {
  jint rv;
  __asm__ volatile(
    " cas    [%2], %3, %0"
    : "=r" (rv)
    : "0" (exchange_value), "r" (dest), "r" (compare_value)
    : "memory");
  return rv;
}

inline jlong    Atomic::cmpxchg    (jlong    exchange_value, volatile jlong*    dest, jlong    compare_value) {
#ifdef _LP64
  jlong rv;
  __asm__ volatile(
    " casx   [%2], %3, %0"
    : "=r" (rv)
    : "0" (exchange_value), "r" (dest), "r" (compare_value)
    : "memory");
  return rv;
#else
  volatile jlong_accessor evl, cvl, rv;
  evl.long_value = exchange_value;
  cvl.long_value = compare_value;

  __asm__ volatile(
    " sllx   %2, 32, %2\n\t"
    " srl    %3, 0,  %3\n\t"
    " or     %2, %3, %2\n\t"
    " sllx   %5, 32, %5\n\t"
    " srl    %6, 0,  %6\n\t"
    " or     %5, %6, %5\n\t"
    " casx   [%4], %5, %2\n\t"
    " srl    %2, 0, %1\n\t"
    " srlx   %2, 32, %0\n\t"
    : "=r" (rv.words[0]), "=r" (rv.words[1])
    : "r"  (evl.words[0]), "r" (evl.words[1]), "r" (dest), "r" (cvl.words[0]), "r" (cvl.words[1])
    : "memory");

  return rv.long_value;
#endif
}

inline intptr_t Atomic::cmpxchg_ptr(intptr_t exchange_value, volatile intptr_t* dest, intptr_t compare_value) {
  intptr_t rv;
#ifdef _LP64
  __asm__ volatile(
    " casx    [%2], %3, %0"
    : "=r" (rv)
    : "0" (exchange_value), "r" (dest), "r" (compare_value)
    : "memory");
#else
  __asm__ volatile(
    " cas     [%2], %3, %0"
    : "=r" (rv)
    : "0" (exchange_value), "r" (dest), "r" (compare_value)
    : "memory");
#endif // _LP64
  return rv;
}

inline void*    Atomic::cmpxchg_ptr(void*    exchange_value, volatile void*     dest, void*    compare_value) {
  return (void*)cmpxchg_ptr((intptr_t)exchange_value, (volatile intptr_t*)dest, (intptr_t)compare_value);
}

#endif // OS_CPU_LINUX_SPARC_VM_ATOMIC_LINUX_SPARC_INLINE_HPP
