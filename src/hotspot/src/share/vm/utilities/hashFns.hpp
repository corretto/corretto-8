/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_HASHFNS_HPP
#define SHARE_VM_UTILITIES_HASHFNS_HPP

#include "utilities/globalDefinitions.hpp"

template<typename K> struct HashFns {
  typedef unsigned (*hash_fn)(K const&);
  typedef bool (*equals_fn)(K const&, K const&);

  static unsigned primitive_hash(const K& k) {
    unsigned hash = (unsigned)((uintptr_t)k);
    return hash ^ (hash >> 3); // just in case we're dealing with aligned ptrs
  }

  static bool primitive_equals(const K& k0, const K& k1) {
    return k0 == k1;
  }
};

// Intended use as template class arguments is:
//     typename HashFns<K>::hash_fn   HASH   = HashFns<K>::primitive_hash,
//     typename HashFns<K>::equals_fn EQUALS = HashFns<K>::primitive_equals,
// But, xlC does not compile that. See
//     http://stackoverflow.com/questions/8532961/template-argument-of-type-that-is-defined-by-inner-typedef-from-other-template-c
// so instead use
//     unsigned (*HASH)  (K const&)           = HashFns<K>::primitive_hash,
//     bool     (*EQUALS)(K const&, K const&) = HashFns<K>::primitive_equals,


#endif // SHARE_VM_UTILITIES_HASHFNS_HPP
