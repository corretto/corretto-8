/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_UNIVERSE_INLINE_HPP
#define SHARE_VM_MEMORY_UNIVERSE_INLINE_HPP

#include "memory/universe.hpp"

// Check whether an element of a typeArrayOop with the given type must be
// aligned 0 mod 8.  The typeArrayOop itself must be aligned at least this
// strongly.

inline bool Universe::element_type_should_be_aligned(BasicType type) {
  return type == T_DOUBLE || type == T_LONG;
}

// Check whether an object field (static/non-static) of the given type must be aligned 0 mod 8.

inline bool Universe::field_type_should_be_aligned(BasicType type) {
  return type == T_DOUBLE || type == T_LONG;
}

inline oop Universe::allocation_context_notification_obj() {
  return _allocation_context_notification_obj;
}

inline void Universe::set_allocation_context_notification_obj(oop obj) {
  _allocation_context_notification_obj = obj;
}

#endif // SHARE_VM_MEMORY_UNIVERSE_INLINE_HPP
