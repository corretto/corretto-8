/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CODE_CODECACHE_HPP
#define SHARE_VM_CODE_CODECACHE_HPP

#include "code/codeBlob.hpp"
#include "memory/allocation.hpp"
#include "memory/heap.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oopsHierarchy.hpp"

// The CodeCache implements the code cache for various pieces of generated
// code, e.g., compiled java methods, runtime stubs, transition frames, etc.
// The entries in the CodeCache are all CodeBlob's.

// Implementation:
//   - Each CodeBlob occupies one chunk of memory.
//   - Like the offset table in oldspace the zone has at table for
//     locating a method given a addess of an instruction.

class OopClosure;
class DepChange;

class CodeCache : AllStatic {
  friend class VMStructs;
 private:
  // CodeHeap is malloc()'ed at startup and never deleted during shutdown,
  // so that the generated assembly code is always there when it's needed.
  // This may cause memory leak, but is necessary, for now. See 4423824,
  // 4422213 or 4436291 for details.
  static CodeHeap * _heap;
  static int _number_of_blobs;
  static int _number_of_adapters;
  static int _number_of_nmethods;
  static int _number_of_nmethods_with_dependencies;
  static bool _needs_cache_clean;
  static nmethod* _scavenge_root_nmethods;  // linked via nm->scavenge_root_link()

  static void verify_if_often() PRODUCT_RETURN;

  static void mark_scavenge_root_nmethods() PRODUCT_RETURN;
  static void verify_perm_nmethods(CodeBlobClosure* f_or_null) PRODUCT_RETURN;

  static int _codemem_full_count;

  static void set_scavenge_root_nmethods(nmethod* nm) { _scavenge_root_nmethods = nm; }
  static void prune_scavenge_root_nmethods();
  static void unlink_scavenge_root_nmethod(nmethod* nm, nmethod* prev);

 public:

  // Initialization
  static void initialize();

  static void report_codemem_full();

  // Allocation/administration
  static CodeBlob* allocate(int size, bool is_critical = false); // allocates a new CodeBlob
  static void commit(CodeBlob* cb);                 // called when the allocated CodeBlob has been filled
  static int alignment_unit();                      // guaranteed alignment of all CodeBlobs
  static int alignment_offset();                    // guaranteed offset of first CodeBlob byte within alignment unit (i.e., allocation header)
  static void free(CodeBlob* cb);                   // frees a CodeBlob
  static void flush();                              // flushes all CodeBlobs
  static bool contains(void *p);                    // returns whether p is included
  static void blobs_do(void f(CodeBlob* cb));       // iterates over all CodeBlobs
  static void blobs_do(CodeBlobClosure* f);         // iterates over all CodeBlobs
  static void nmethods_do(void f(nmethod* nm));     // iterates over all nmethods
  static void alive_nmethods_do(void f(nmethod* nm)); // iterates over all alive nmethods

  // Lookup
  static CodeBlob* find_blob(void* start);
  static nmethod*  find_nmethod(void* start);

  // Lookup that does not fail if you lookup a zombie method (if you call this, be sure to know
  // what you are doing)
  static CodeBlob* find_blob_unsafe(void* start) {
    // NMT can walk the stack before code cache is created
    if (_heap == NULL) return NULL;

    CodeBlob* result = (CodeBlob*)_heap->find_start(start);
    // this assert is too strong because the heap code will return the
    // heapblock containing start. That block can often be larger than
    // the codeBlob itself. If you look up an address that is within
    // the heapblock but not in the codeBlob you will assert.
    //
    // Most things will not lookup such bad addresses. However
    // AsyncGetCallTrace can see intermediate frames and get that kind
    // of invalid address and so can a developer using hsfind.
    //
    // The more correct answer is to return NULL if blob_contains() returns
    // false.
    // assert(result == NULL || result->blob_contains((address)start), "found wrong CodeBlob");

    if (result != NULL && !result->blob_contains((address)start)) {
      result = NULL;
    }
    return result;
  }

  // Iteration
  static CodeBlob* first();
  static CodeBlob* next (CodeBlob* cb);
  static CodeBlob* alive(CodeBlob *cb);
  static nmethod* alive_nmethod(CodeBlob *cb);
  static nmethod* first_nmethod();
  static nmethod* next_nmethod (CodeBlob* cb);
  static int       nof_blobs()                 { return _number_of_blobs; }
  static int       nof_adapters()              { return _number_of_adapters; }
  static int       nof_nmethods()              { return _number_of_nmethods; }

  // GC support
  static void gc_epilogue();
  static void gc_prologue();
  static void verify_oops();
  // If "unloading_occurred" is true, then unloads (i.e., breaks root links
  // to) any unmarked codeBlobs in the cache.  Sets "marked_for_unloading"
  // to "true" iff some code got unloaded.
  static void do_unloading(BoolObjectClosure* is_alive, bool unloading_occurred);
  static void asserted_non_scavengable_nmethods_do(CodeBlobClosure* f = NULL) PRODUCT_RETURN;

  // Apply f to every live code blob in scavengable nmethods. Prune nmethods
  // from the list of scavengable nmethods if f->fix_relocations() and a nmethod
  // no longer has scavengable oops.  If f->fix_relocations(), then f must copy
  // objects to their new location immediately to avoid fixing nmethods on the
  // basis of the old object locations.
  static void scavenge_root_nmethods_do(CodeBlobToOopClosure* f);

  static nmethod* scavenge_root_nmethods()          { return _scavenge_root_nmethods; }
  static void add_scavenge_root_nmethod(nmethod* nm);
  static void drop_scavenge_root_nmethod(nmethod* nm);

  // Printing/debugging
  static void print();                           // prints summary
  static void print_internals();
  static void verify();                          // verifies the code cache
  static void print_trace(const char* event, CodeBlob* cb, int size = 0) PRODUCT_RETURN;
  static void print_summary(outputStream* st, bool detailed = true); // Prints a summary of the code cache usage
  static void log_state(outputStream* st);

  // The full limits of the codeCache
  static address  low_bound()                    { return (address) _heap->low_boundary(); }
  static address  high_bound()                   { return (address) _heap->high_boundary(); }
  static address  high()                         { return (address) _heap->high(); }

  // Profiling
  static address first_address();                // first address used for CodeBlobs
  static address last_address();                 // last  address used for CodeBlobs
  static size_t  capacity()                      { return _heap->capacity(); }
  static size_t  max_capacity()                  { return _heap->max_capacity(); }
  static size_t  unallocated_capacity()          { return _heap->unallocated_capacity(); }
  static double  reverse_free_ratio();

  static bool needs_cache_clean()                { return _needs_cache_clean; }
  static void set_needs_cache_clean(bool v)      { _needs_cache_clean = v;    }
  static void clear_inline_caches();             // clear all inline caches

  static void verify_clean_inline_caches();
  static void verify_icholder_relocations();

  // Deoptimization
  static int  mark_for_deoptimization(DepChange& changes);
#ifdef HOTSWAP
  static int  mark_for_evol_deoptimization(instanceKlassHandle dependee);
#endif // HOTSWAP

  static void mark_all_nmethods_for_deoptimization();
  static int  mark_for_deoptimization(Method* dependee);
  static void make_marked_nmethods_not_entrant();

    // tells how many nmethods have dependencies
  static int number_of_nmethods_with_dependencies();

  static int get_codemem_full_count() { return _codemem_full_count; }
};

#endif // SHARE_VM_CODE_CODECACHE_HPP
