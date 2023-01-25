/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_VMSTRUCTS_PARALLELGC_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_VMSTRUCTS_PARALLELGC_HPP

#define VM_STRUCTS_PARALLELGC(nonstatic_field, \
                   static_field) \
                                                                                                                                     \
  /**********************/                                                                                                           \
  /* Parallel GC fields */                                                                                                           \
  /**********************/                                                                                                           \
  nonstatic_field(PSVirtualSpace,              _alignment,                                    const size_t)                          \
  nonstatic_field(PSVirtualSpace,              _reserved_low_addr,                            char*)                                 \
  nonstatic_field(PSVirtualSpace,              _reserved_high_addr,                           char*)                                 \
  nonstatic_field(PSVirtualSpace,              _committed_low_addr,                           char*)                                 \
  nonstatic_field(PSVirtualSpace,              _committed_high_addr,                          char*)                                 \
                                                                                                                                     \
  nonstatic_field(ImmutableSpace,              _bottom,                                       HeapWord*)                             \
  nonstatic_field(ImmutableSpace,              _end,                                          HeapWord*)                             \
                                                                                                                                     \
  nonstatic_field(MutableSpace,                _top,                                          HeapWord*)                             \
                                                                                                                                     \
  nonstatic_field(PSYoungGen,                  _reserved,                                     MemRegion)                             \
  nonstatic_field(PSYoungGen,                  _virtual_space,                                PSVirtualSpace*)                       \
  nonstatic_field(PSYoungGen,                  _eden_space,                                   MutableSpace*)                         \
  nonstatic_field(PSYoungGen,                  _from_space,                                   MutableSpace*)                         \
  nonstatic_field(PSYoungGen,                  _to_space,                                     MutableSpace*)                         \
  nonstatic_field(PSYoungGen,                  _init_gen_size,                                const size_t)                          \
  nonstatic_field(PSYoungGen,                  _min_gen_size,                                 const size_t)                          \
  nonstatic_field(PSYoungGen,                  _max_gen_size,                                 const size_t)                          \
                                                                                                                                     \
  nonstatic_field(PSOldGen,                    _reserved,                                     MemRegion)                             \
  nonstatic_field(PSOldGen,                    _virtual_space,                                PSVirtualSpace*)                       \
  nonstatic_field(PSOldGen,                    _object_space,                                 MutableSpace*)                         \
  nonstatic_field(PSOldGen,                    _init_gen_size,                                const size_t)                          \
  nonstatic_field(PSOldGen,                    _min_gen_size,                                 const size_t)                          \
  nonstatic_field(PSOldGen,                    _max_gen_size,                                 const size_t)                          \
                                                                                                                                     \
                                                                                                                                     \
     static_field(ParallelScavengeHeap,        _young_gen,                                    PSYoungGen*)                           \
     static_field(ParallelScavengeHeap,        _old_gen,                                      PSOldGen*)                             \
     static_field(ParallelScavengeHeap,        _psh,                                          ParallelScavengeHeap*)                 \
                                                                                                                                     \

#define VM_TYPES_PARALLELGC(declare_type,                                 \
                            declare_toplevel_type)                        \
                                                                          \
  /*****************************************/                             \
  /* Parallel GC - space, gen abstractions */                             \
  /*****************************************/                             \
           declare_type(ParallelScavengeHeap,         CollectedHeap)      \
                                                                          \
  declare_toplevel_type(PSVirtualSpace)                                   \
  declare_toplevel_type(ImmutableSpace)                                   \
           declare_type(MutableSpace, ImmutableSpace)                     \
  declare_toplevel_type(PSYoungGen)                                       \
           declare_type(ASPSYoungGen, PSYoungGen)                         \
  declare_toplevel_type(PSOldGen)                                         \
           declare_type(ASPSOldGen, PSOldGen)                             \
                                                                          \
  /*****************************/                                         \
  /* Parallel GC pointer types */                                         \
  /*****************************/                                         \
                                                                          \
  declare_toplevel_type(PSVirtualSpace*)                                  \
  declare_toplevel_type(ImmutableSpace*)                                  \
  declare_toplevel_type(MutableSpace*)                                    \
  declare_toplevel_type(PSYoungGen*)                                      \
  declare_toplevel_type(ASPSYoungGen*)                                    \
  declare_toplevel_type(PSOldGen*)                                        \
  declare_toplevel_type(ASPSOldGen*)                                      \
  declare_toplevel_type(ParallelScavengeHeap*)

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_VMSTRUCTS_PARALLELGC_HPP
