/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_SHARED_PARGCALLOCBUFFER_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_SHARED_PARGCALLOCBUFFER_INLINE_HPP

#include "gc_implementation/shared/parGCAllocBuffer.hpp"
#include "gc_interface/collectedHeap.inline.hpp"

HeapWord* ParGCAllocBuffer::allocate_aligned(size_t word_sz, unsigned short alignment_in_bytes) {

  HeapWord* res = CollectedHeap::align_allocation_or_fail(_top, _end, alignment_in_bytes);
  if (res == NULL) {
    return NULL;
  }

  // Set _top so that allocate(), which expects _top to be correctly set,
  // can be used below.
  _top = res;
  return allocate(word_sz);
}

#endif // SHARE_VM_GC_IMPLEMENTATION_SHARED_PARGCALLOCBUFFER_INLINE_HPP
