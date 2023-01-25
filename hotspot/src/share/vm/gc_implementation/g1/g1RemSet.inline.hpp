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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1REMSET_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1REMSET_INLINE_HPP

#include "gc_implementation/g1/g1RemSet.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "oops/oop.inline.hpp"

inline uint G1RemSet::n_workers() {
  if (_g1->workers() != NULL) {
    return _g1->workers()->total_workers();
  } else {
    return 1;
  }
}

template <class T>
inline void G1RemSet::write_ref(HeapRegion* from, T* p) {
  par_write_ref(from, p, 0);
}

template <class T>
inline void G1RemSet::par_write_ref(HeapRegion* from, T* p, int tid) {
  oop obj = oopDesc::load_decode_heap_oop(p);
  if (obj == NULL) {
    return;
  }

#ifdef ASSERT
  // can't do because of races
  // assert(obj == NULL || obj->is_oop(), "expected an oop");

  // Do the safe subset of is_oop
#ifdef CHECK_UNHANDLED_OOPS
  oopDesc* o = obj.obj();
#else
  oopDesc* o = obj;
#endif // CHECK_UNHANDLED_OOPS
  assert((intptr_t)o % MinObjAlignmentInBytes == 0, "not oop aligned");
  assert(Universe::heap()->is_in_reserved(obj), "must be in heap");
#endif // ASSERT

  assert(from == NULL || from->is_in_reserved(p), "p is not in from");

  HeapRegion* to = _g1->heap_region_containing(obj);
  if (from != to) {
    assert(to->rem_set() != NULL, "Need per-region 'into' remsets.");
    to->rem_set()->add_reference(p, tid);
  }
}

template <class T>
inline void UpdateRSOopClosure::do_oop_work(T* p) {
  assert(_from != NULL, "from region must be non-NULL");
  _rs->par_write_ref(_from, p, _worker_i);
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1REMSET_INLINE_HPP
