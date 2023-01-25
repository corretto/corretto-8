/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1_SPECIALIZED_OOP_CLOSURES_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1_SPECIALIZED_OOP_CLOSURES_HPP

// The following OopClosure types get specialized versions of
// "oop_oop_iterate" that invoke the closures' do_oop methods
// non-virtually, using a mechanism defined in this file.  Extend these
// macros in the obvious way to add specializations for new closures.

enum G1Barrier {
  G1BarrierNone,
  G1BarrierEvac,
  G1BarrierKlass
};

enum G1Mark {
  G1MarkNone,
  G1MarkFromRoot,
  G1MarkPromotedFromRoot
};

// Forward declarations.

template<G1Barrier barrier, G1Mark do_mark_object>
class G1ParCopyClosure;

class G1ParScanClosure;
class G1ParPushHeapRSClosure;

class FilterIntoCSClosure;
class FilterOutOfRegionClosure;
class G1CMOopClosure;
class G1RootRegionScanClosure;

// Specialized oop closures from g1RemSet.cpp
class G1Mux2Closure;
class G1TriggerClosure;
class G1InvokeIfNotTriggeredClosure;
class G1UpdateRSOrPushRefOopClosure;

#ifdef FURTHER_SPECIALIZED_OOP_OOP_ITERATE_CLOSURES
#error "FURTHER_SPECIALIZED_OOP_OOP_ITERATE_CLOSURES already defined."
#endif

#define FURTHER_SPECIALIZED_OOP_OOP_ITERATE_CLOSURES(f) \
      f(G1ParScanClosure,_nv)                           \
      f(G1ParPushHeapRSClosure,_nv)                     \
      f(FilterIntoCSClosure,_nv)                        \
      f(FilterOutOfRegionClosure,_nv)                   \
      f(G1CMOopClosure,_nv)                             \
      f(G1RootRegionScanClosure,_nv)                    \
      f(G1Mux2Closure,_nv)                              \
      f(G1TriggerClosure,_nv)                           \
      f(G1InvokeIfNotTriggeredClosure,_nv)              \
      f(G1UpdateRSOrPushRefOopClosure,_nv)

#ifdef FURTHER_SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES
#error "FURTHER_SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES already defined."
#endif

#define FURTHER_SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES(f)

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1_SPECIALIZED_OOP_CLOSURES_HPP
