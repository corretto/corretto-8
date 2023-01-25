/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_RECORDER_STRINGPOOL_JFRSTRINGPOOL_HPP
#define SHARE_VM_JFR_RECORDER_STRINGPOOL_JFRSTRINGPOOL_HPP

#include "jni.h"
#include "jfr/recorder/storage/jfrMemorySpace.hpp"
#include "jfr/recorder/storage/jfrMemorySpaceRetrieval.hpp"
#include "jfr/recorder/stringpool/jfrStringPoolBuffer.hpp"

class JfrChunkWriter;
class JfrStringPool;
class Mutex;

typedef JfrMemorySpace<JfrStringPoolBuffer, JfrMspaceSequentialRetrieval, JfrStringPool> JfrStringPoolMspace;

//
// Although called JfrStringPool, a more succinct description would be
// "backing storage for the string pool located in Java"
//
// There are no lookups in native, only the encoding of string constants to the stream.
//
class JfrStringPool : public JfrCHeapObj {
 public:
  static bool add(bool epoch, jlong id, jstring string, JavaThread* jt);
  size_t write();
  size_t write_at_safepoint();
  size_t clear();

  typedef JfrStringPoolMspace::Type Buffer;
 private:
  JfrStringPoolMspace* _free_list_mspace;
  Mutex* _lock;
  JfrChunkWriter& _chunkwriter;

  // mspace callback
  void register_full(Buffer* t, Thread* thread);
  void lock();
  void unlock();
  DEBUG_ONLY(bool is_locked() const;)

  static Buffer* lease_buffer(Thread* thread, size_t size = 0);
  static Buffer* flush(Buffer* old, size_t used, size_t requested, Thread* t);

  JfrStringPool(JfrChunkWriter& cw);
  ~JfrStringPool();

  static JfrStringPool& instance();
  static JfrStringPool* create(JfrChunkWriter& cw);
  bool initialize();
  static void destroy();

  friend class JfrRecorder;
  friend class JfrRecorderService;
  friend class JfrStringPoolFlush;
  friend class JfrStringPoolWriter;
  template <typename, template <typename> class, typename>
  friend class JfrMemorySpace;
};

#endif // SHARE_VM_JFR_RECORDER_STRINGPOOL_JFRSTRINGPOOL_HPP
