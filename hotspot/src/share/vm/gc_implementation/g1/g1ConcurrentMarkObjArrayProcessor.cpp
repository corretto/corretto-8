/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc_implementation/g1/concurrentMark.inline.hpp"
#include "gc_implementation/g1/g1ConcurrentMarkObjArrayProcessor.inline.hpp"

oop G1CMObjArrayProcessor::encode_array_slice(HeapWord* addr) {
  return oop((void*)((uintptr_t)addr | ArraySliceBit));
}

HeapWord* G1CMObjArrayProcessor::decode_array_slice(oop value) {
  assert(is_array_slice(value), err_msg("Given value " PTR_FORMAT " is not an array slice", p2i(value)));
  return (HeapWord*)((uintptr_t)(void*)value & ~ArraySliceBit);
}

void G1CMObjArrayProcessor::push_array_slice(HeapWord* what) {
  oop obj = encode_array_slice(what);
  _task->push(obj);
}

size_t G1CMObjArrayProcessor::process_array_slice(objArrayOop obj, HeapWord* start_from, size_t remaining) {
  size_t words_to_scan = MIN2(remaining, ObjArrayMarkingStride);

  if (remaining > ObjArrayMarkingStride) {
    push_array_slice(start_from + ObjArrayMarkingStride);
  }

  // Then process current area.
  MemRegion mr(start_from, words_to_scan);
  return _task->scan_objArray(obj, mr);
}

size_t G1CMObjArrayProcessor::process_obj(oop obj) {
  assert(should_be_sliced(obj), err_msg("Must be an array object %d and large " SIZE_FORMAT, obj->is_objArray(), (size_t)obj->size()));

  return process_array_slice(objArrayOop(obj), (HeapWord*)obj, (size_t)objArrayOop(obj)->size());
}

size_t G1CMObjArrayProcessor::process_slice(oop obj) {
  HeapWord* const decoded_address = decode_array_slice(obj);

  // Find the start address of the objArrayOop.
  // Shortcut the BOT access if the given address is from a humongous object. The BOT
  // slide is fast enough for "smaller" objects in non-humongous regions, but is slower
  // than directly using heap region table.
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  HeapRegion* r = g1h->heap_region_containing(decoded_address);

  HeapWord* const start_address = r->isHumongous() ?
                                  r->humongous_start_region()->bottom() :
                                  g1h->block_start(decoded_address);

  assert(oop(start_address)->is_objArray(), err_msg("Address " PTR_FORMAT " does not refer to an object array ", p2i(start_address)));
  assert(start_address < decoded_address,
         err_msg("Object start address " PTR_FORMAT " must be smaller than decoded address " PTR_FORMAT,
         p2i(start_address),
         p2i(decoded_address)));

  objArrayOop objArray = objArrayOop(start_address);

  size_t already_scanned = decoded_address - start_address;
  size_t remaining = objArray->size() - already_scanned;

  return process_array_slice(objArray, decoded_address, remaining);
}
