/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/debug.hpp"
#include "utilities/simpleOpenHashtable.hpp"

#ifndef PRODUCT

/////////////// Unit tests ///////////////
class TestSimpleHashtable : public AllStatic {
    typedef intx K;
    typedef int V;

    static unsigned identity_hash(const K& k) {
        return (unsigned)(uintptr_t)k;
    }

    static unsigned bad_hash(const K& k) {
        return 1;
    }

    class EqualityTestIter {
    public:
        bool do_entry(K const& k, V const& v) {
            assert((uintptr_t)k == (uintptr_t)v, "");
            return true; // continue iteration
        }
    };

  template<
  unsigned (*HASH)  (K const&)           = HashFns<K>::primitive_hash,
  bool     (*EQUALS)(K const&, K const&) = HashFns<K>::primitive_equals
  >
  class Runner : public AllStatic {
    static K as_K(intx val) { return val; }

   public:
    static void test_small() {
      EqualityTestIter et;
      SimpleOpenHashtable<K, V, HASH, EQUALS> rh;

      assert(!rh.contains(as_K(0x1)), "");

      assert(!rh.put(as_K(0x1), 0x1), "put 1 failed! duplication detected.");
      assert(rh.contains(as_K(0x1)), "");

      assert(rh.put(as_K(0x1), 0x1), "put 1 failed! should update");

      assert(!rh.put(as_K(0x2), 0x2), "");
      assert(!rh.put(as_K(0x3), 0x3), "");
      assert(!rh.put(as_K(0x4), 0x4), "");
      assert(!rh.put(as_K(0x5), 0x5), "");

      assert(!rh.remove(as_K(0x0)), "Failed to remove 0");
      rh.iterate(&et);

      assert(rh.remove(as_K(0x1)), "");
      rh.iterate(&et);
    }

    // We use keys with the low bits cleared since the default hash will do some shifting
    static void test_small_shifted() {
      EqualityTestIter et;
      SimpleOpenHashtable<K, V, HASH, EQUALS> rh;

      assert(!rh.contains(as_K(0x10)), "");

      assert(!rh.put(as_K(0x10), 0x10), "put 1 failed! duplication detectected");
      assert(rh.contains(as_K(0x10)), "");

      assert(rh.put(as_K(0x10), 0x10), "put 1 failed! should update");

      assert(!rh.put(as_K(0x20), 0x20), "");
      assert(!rh.put(as_K(0x30), 0x30), "");
      assert(!rh.put(as_K(0x40), 0x40), "");
      assert(!rh.put(as_K(0x50), 0x50), "");

      assert(!rh.remove(as_K(0x00)), "");

      assert(rh.remove(as_K(0x10)), "");

      rh.iterate(&et);
    }

    static void test(unsigned num_elements) {
      EqualityTestIter et;
      SimpleOpenHashtable<K, V, HASH, EQUALS> rh;

      for (uintptr_t i = 0; i < num_elements; ++i) {
        assert(!rh.put(as_K(i), i), "");
      }

      rh.iterate(&et);

      for (uintptr_t i = num_elements; i > 0; --i) {
        uintptr_t index = i - 1;
        assert(rh.remove(as_K(index)), "");
      }
      rh.iterate(&et);
      for (uintptr_t i = num_elements; i > 0; --i) {
        uintptr_t index = i - 1;
        assert(!rh.remove(as_K(index)), "");
      }
      rh.iterate(&et);
    }
  };

  template<typename K>
  static unsigned colliding_hash(const K& k) {
    unsigned hash = (unsigned)((uintptr_t)k);
    return hash >> 3; // Force collisions at a stride of 8.
  }

public:
    static void run_tests() {
        {
            typedef SimpleOpenHashtable<intx, intx, bad_hash> IntMap;
            IntMap map(1000, DEFAULT_LOAD_FACTOR);

            assert(!map.put(2, 2), "");
            assert(!map.put(3, 2), "");
            assert(!map.put(4, 3), "");
            assert(!map.put(5, 3), "");
            assert(map.put(2, 1), "Failed to update");
            assert(!map.put(6, 4), "");
            assert(map.put(6, 4), "");

            assert(map.remove(2), "Failed to remove 2");
            assert(map.contains(3), "Failed to get 3");
            assert(map.contains(4), "failed to get 4");
            assert(map.remove(3), "Failed to remove 3");
            assert(map.contains(4), "Failed to get 4");
            assert(map.remove(4), "Failed to remove 4");
            assert(map.contains(5), "Failed to get 5");

            assert(map.remove(5), "Failed to remove 5");
            assert(map.contains(6), "Failed to get 6");
            assert(map.remove(6), "Failed to remove 6");
            assert(map.entry_count() == 0, "Not empty");
        }

        {
            typedef SimpleOpenHashtable<intx, intx, colliding_hash> IntMap;

            IntMap map(1024, DEFAULT_LOAD_FACTOR);

            assert(!map.put(0, 1), "Failed to insert");
            assert(!map.put(8, 8), "Failed to insert");

            assert(!map.put(8180, 8180), "Failed to insert");
            assert(!map.put(8184, 8184), "Failed to insert");

            assert(!map.put(8181, 8181), "Failed to insert");
            assert(!map.put(8182, 8182), "Failed to insert");
            assert(!map.put(8183, 8183), "Failed to insert");

            assert(7 == map.entry_count(), "Size is 7");

            assert(map.remove(0), "Failed to remove 0");

            assert(map.get(8) == 8, "Failed to get");
            assert(map.get(8180) == 8180, "Failed to get");
            assert(map.get(8181) == 8181, "Failed to get");
            assert(map.get(8182) == 8182, "Failed to get");
            assert(map.get(8183) == 8183, "Failed to get");
            assert(map.get(8184) == 8184, "Failed to get");

            assert(map.remove(8180), "Failed to remove 8180");
            assert(map.remove(8181), "Failed to remove 8181");

            assert(map.get(8) == 8, "Failed to get");
            assert(map.get(8182) == 8182, "Failed to get");
            assert(map.get(8183) == 8183, "Failed to get");
            assert(map.get(8184) == 8184, "Failed to get");

            assert(map.remove(8184), "Failed to remove 8184");

            assert(map.get(8) == 8, "Failed to get");
            assert(map.get(8182) == 8182, "Failed to get");
            assert(map.get(8183) == 8183, "Failed to get");
        }

        {
            Runner<>::test_small();
            Runner<>::test_small_shifted();
            Runner<>::test(16);
            Runner<>::test(128);
            Runner<>::test(256);
            Runner<>::test(512);
        }

        {
            Runner<identity_hash>::test_small();
            Runner<identity_hash>::test_small_shifted();
            Runner<identity_hash>::test(16);
            Runner<identity_hash>::test(128);
            Runner<identity_hash>::test(256);
            Runner<identity_hash>::test(512);
        }

        {
            Runner<bad_hash>::test_small();
            Runner<bad_hash>::test_small_shifted();
            Runner<bad_hash>::test(16);
            Runner<bad_hash>::test(128);
            Runner<bad_hash>::test(256);
            Runner<bad_hash>::test(512);
        }
    }
};

void TestSimpleHashtable_test() {
  TestSimpleHashtable::run_tests();
}

#endif // not PRODUCT