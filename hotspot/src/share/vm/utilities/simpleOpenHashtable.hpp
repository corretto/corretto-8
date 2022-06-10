/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_SIMPLEOPENHASHTABLE_HPP
#define SHARE_VM_UTILITIES_SIMPLEOPENHASHTABLE_HPP

#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/hashFns.hpp"
#include "utilities/globalDefinitions.hpp"

// Simple open hashtable implemention. Unlike a bucket chain hashtable,
// the array elements are themselves the key-value pairs. This is a "simple"
// open hashtable because rather than having each entry contain a bool field
// marking the slot as "empty", a designated key value is used as a sentinal
// value denoting an empty slot. The sentinal key value is notionally -1, but
// any key value can be used as the sentinal value by defining a conversion
// operator method for EMPTY_KEY appropriately.
//
// The get() method returns a value of type V. The null value is notionally
// NULL, but any null-equivalent value can be used by defined a conversion
// operator method of NULL appropriately.
//
// The table array length is always a power of two, so as to avoid a remainder
// operation when computing the slot index. Using a power of two table size can
// result in a table being almost half empty. Other expansion rules are possible
// if lower footprint becomes an issue.
//
// This class is intended primarily for use with key and value types that fit
// into a single word, i.e., scalar or pointer key and value types. For these
// types, constructors and destructors are nops, so no code is generated for
// methods that use them.
//
// The default allocation area is the C++ heap because we expect most uses
// to be unscoped.

static const float DEFAULT_LOAD_FACTOR  = 0.6875f; // 11/16
static const uintx MAX_CAPACITY         = ((uint32_t)(-1) >> 1) + 1; // 0x80000000
static const uintx INITIAL_CAPACITY     = 256;
static const intx  EMPTY_KEY            = -1;

template<typename K, typename V,
         unsigned (*HASH)  (K const&)            = HashFns<K>::primitive_hash,
         bool     (*EQUALS)(K const&, K const&)  = HashFns<K>::primitive_equals,
         MEMFLAGS MEM_TYPE                       = mtInternal
>
class SimpleOpenHashtable : public CHeapObj<MEM_TYPE> {
 private:
  struct Entry {
    private:
      K   _key;
      V   _value;
    public:
      Entry()   { set_empty(); }
      ~Entry()  { destroy_entry(); }

      K key()   { return _key; }
      V value() { return _value; }

      void set_key(K const& key)      { ::new((void*)&_key) K(key); }
      void set_value(V const& value)  { ::new((void*)&_value) V(value); }
      void set_empty()                { set_key((K)EMPTY_KEY); set_value((V)NULL); }

      void destroy_key()              { _key.~K(); }
      void destroy_value()            { _value.~V(); }
      void destroy_entry()            { destroy_key(); destroy_value(); }

      bool key_equals(K key)          { return EQUALS(_key, key); }
      bool is_empty()                 { return key_equals((K)EMPTY_KEY); }
  };

  size_t      _size_mask;     // Real table size = _size_mask + 1
  size_t      _entry_count;
  size_t      _threshold;
  float       _load_factor;

  Entry*      _table;

  // Returns a referrence of entry where the key resides or would reside
  Entry* find_entry(K const& key) const {
    size_t slot_index = ((uintx)HASH(key)) & _size_mask;
    size_t initial_index = slot_index;

    do {
      if (_table[slot_index].is_empty() || _table[slot_index].key_equals(key)) {
        break;
      }
      slot_index = (slot_index + 1) & _size_mask;
    } while (slot_index != initial_index);

    assert(_table[slot_index].is_empty() ||
            _table[slot_index].key_equals(key), "Illegal entry: table full");

    return &_table[slot_index];
  }

  void compact_at(size_t delete_index) {
    Entry * entry;
    size_t slot_index = delete_index;

    while (true) {
      slot_index = (slot_index + 1) & _size_mask;

      if (_table[slot_index].is_empty()) {
        return;
      }

      size_t found_hash = ((uintx)HASH(_table[slot_index].key())) & _size_mask;

      if ((slot_index < found_hash && (found_hash <= delete_index || delete_index <= slot_index)) // Collision that rolled past end of HT.
          || (found_hash <= delete_index && delete_index <= slot_index)) { // Basic contiguous collision.
        entry = &_table[delete_index];
        *entry = _table[slot_index];

        _table[slot_index].destroy_entry();
        _table[slot_index].set_empty();
        delete_index = slot_index;
      }
    }
  }

#ifndef PRODUCT
  // the distant between initial slot index and real index
  size_t find_cost(K const& key) const {
    size_t slot_index = ((uintx)HASH(key)) & _size_mask;
    size_t slot_real = find_entry(key) - _table;

    if (slot_real >= slot_index) {
      return slot_real - slot_index;
    }
    else {
      return capacity() - slot_index + slot_real;
    }
  }
#endif

  bool put_entry(K const& key, V const& value) {
    // Check table status, make sure there's at least one empty entry
    guarantee(_entry_count < capacity(), "Table overflow");
    // Check table occupation rate
    if (_entry_count >= _threshold) {
      resize();
    }

    Entry* entry = find_entry(key);
    bool is_updated = false;
    if (entry->is_empty()) {
      entry->destroy_key();
      entry->set_key(key);
      _entry_count++;
    } else {
      // If slot is occupied, update its value
      entry->destroy_value();
      is_updated = true;
    }
    entry->set_value(value);
    return is_updated;
  }

  // Construct a new table and resize
  void resize() {
    // No more resize if reaching maximum table size
    if (capacity() == MAX_CAPACITY) {
      return;
    }

    Entry* old_table = _table;
    size_t old_table_mask = _size_mask;
    // Initialize new table
    init(capacity() << 1);

    for (size_t i = 0; i <= old_table_mask; i++) {
      Entry* old_entry = &old_table[i];
      if (!old_entry->is_empty()) {
        (void)put_entry(old_entry->key(), old_entry->value());
      }
      old_entry->destroy_entry();
    }
    deallocate_table(old_table);
  }

  // Initialize SimpleOpenHashtable
  void init(size_t size) {
    _size_mask = size - 1;
    _entry_count = 0;
    _threshold = (size_t)(size * _load_factor);

    _table = allocate_table(size);

    for (size_t i = 0; i < size; i++) {
      _table[i].set_empty();
    }
  }

  // Destroy table and all its entries
  static void destroy(size_t size, Entry* table) {
    for (size_t i = 0; i< size; i++) {
      table[i].destroy_entry();
    }
    deallocate_table(table);
  }

  static Entry* allocate_table(size_t size) {
    return (Entry*)NEW_C_HEAP_ARRAY(Entry, size, MEM_TYPE);
  }

  static void deallocate_table(Entry* table) {
    FREE_C_HEAP_ARRAY(Entry, table, MEM_TYPE);
  }

  static size_t round_up_to_next_pow_2(size_t target) {
    size_t result = 1;
    while(result < target) {
      result <<= 1;
    }
    return result;
  }

 public:
  SimpleOpenHashtable(size_t initial_size = INITIAL_CAPACITY,
                      float load_factor = DEFAULT_LOAD_FACTOR) {
    guarantee(initial_size <= MAX_CAPACITY, "Invalid table size");
    guarantee(load_factor > 0 && load_factor < 1.0f, "Invalid load factor");
    _load_factor = load_factor;
    init(round_up_to_next_pow_2(MIN2(initial_size, MAX_CAPACITY)));
  }

  ~SimpleOpenHashtable() { destroy(capacity(), _table); }

  size_t entry_count() const    { return _entry_count; }
  size_t capacity() const       { return _size_mask + 1; }

  static size_t entry_size()    { return sizeof(Entry); }

  V get(K const& key) const {
    Entry* entry = find_entry(key);
    return entry->is_empty() ? (V)NULL : entry->value();
  }

  bool contains(K const& key) const {
    return !find_entry(key)->is_empty();
  }

  // Inserts or replace entry in the table. Return
  // true if it's a replacement, and false otherwise.
  bool put(K const& key, V const& value) {
    return put_entry(key, value);
  }

  // Remove an entry if it exists. Return true if
  // entry exists, and false otherwise.
  bool remove(K const& key) {
    Entry* entry = find_entry(key);
    if (entry->is_empty()) {
      return false;
    }

    entry->destroy_entry();
    entry->set_empty();
    compact_at(entry - &_table[0]);
    _entry_count--;
    return true;
  }

  // ITER contains bool do_entry(K const&, V const&), which will be
  // called for each entry in the table.  If do_entry() returns false,
  // the iteration is cancelled.
  template<class ITER>
  void iterate(ITER* iter) const {
    for (size_t i = 0; i <= _size_mask; i++) {
      Entry* entry = &_table[i];
      if (!entry->is_empty()) {
        bool cont = iter->do_entry(entry->key(), entry->value());
        if (!cont) {
          return;
        }
      }
    }
  }
};


#endif // SHARE_VM_UTILITIES_SIMPLEOPENHASHTABLE_HPP
