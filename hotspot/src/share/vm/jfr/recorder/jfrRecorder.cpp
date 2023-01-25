/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/dcmd/jfrDcmds.hpp"
#include "jfr/instrumentation/jfrJvmtiAgent.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/periodic/jfrOSInterface.hpp"
#include "jfr/periodic/sampling/jfrThreadSampler.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/repository/jfrRepository.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/service/jfrPostBox.hpp"
#include "jfr/recorder/service/jfrRecorderService.hpp"
#include "jfr/recorder/service/jfrRecorderThread.hpp"
#include "jfr/recorder/storage/jfrStorage.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/recorder/stringpool/jfrStringPool.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/growableArray.hpp"

bool JfrRecorder::_shutting_down = false;

bool JfrRecorder::is_disabled() {
  // True if -XX:-FlightRecorder has been explicitly set on the
  // command line
  return FLAG_IS_CMDLINE(FlightRecorder) ? !FlightRecorder : false;
}

static bool _enabled = false;

static bool enable() {
  assert(!_enabled, "invariant");
  FLAG_SET_MGMT(bool, FlightRecorder, true);
  _enabled = FlightRecorder;
  assert(_enabled, "invariant");
  return _enabled;
}

bool JfrRecorder::is_enabled() {
  return _enabled;
}

bool JfrRecorder::on_vm_init() {
  if (!is_disabled()) {
    if (FlightRecorder || StartFlightRecording != NULL) {
      enable();
    }
  }
  // fast time initialization
  return JfrTime::initialize();
}

static GrowableArray<JfrStartFlightRecordingDCmd*>* dcmd_recordings_array = NULL;

static void release_recordings() {
  if (dcmd_recordings_array != NULL) {
    const int length = dcmd_recordings_array->length();
    for (int i = 0; i < length; ++i) {
      delete dcmd_recordings_array->at(i);
    }
    delete dcmd_recordings_array;
    dcmd_recordings_array = NULL;
  }
}

static void teardown_startup_support() {
  release_recordings();
  JfrOptionSet::release_startup_recording_options();
}

// Parsing options here to detect errors as soon as possible
static bool parse_recording_options(const char* options, JfrStartFlightRecordingDCmd* dcmd_recording, TRAPS) {
  assert(options != NULL, "invariant");
  assert(dcmd_recording != NULL, "invariant");
  CmdLine cmdline(options, strlen(options), true);
  dcmd_recording->parse(&cmdline, ',', THREAD);
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, tty);
    CLEAR_PENDING_EXCEPTION;
    return false;
  }
  return true;
}

static bool validate_recording_options(TRAPS) {
  const GrowableArray<const char*>* options = JfrOptionSet::startup_recording_options();
  if (options == NULL) {
    return true;
  }
  const int length = options->length();
  assert(length >= 1, "invariant");
  assert(dcmd_recordings_array == NULL, "invariant");
  dcmd_recordings_array = new (ResourceObj::C_HEAP, mtTracing)GrowableArray<JfrStartFlightRecordingDCmd*>(length, true, mtTracing);
  assert(dcmd_recordings_array != NULL, "invariant");
  for (int i = 0; i < length; ++i) {
    JfrStartFlightRecordingDCmd* const dcmd_recording = new(ResourceObj::C_HEAP, mtTracing) JfrStartFlightRecordingDCmd(tty, true);
    assert(dcmd_recording != NULL, "invariant");
    dcmd_recordings_array->append(dcmd_recording);
    if (!parse_recording_options(options->at(i), dcmd_recording, THREAD)) {
      return false;
    }
  }
  return true;
}

static bool launch_recording(JfrStartFlightRecordingDCmd* dcmd_recording, TRAPS) {
  assert(dcmd_recording != NULL, "invariant");
  if (LogJFR && Verbose) tty->print_cr("Starting a recording");
  dcmd_recording->execute(DCmd_Source_Internal, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    if (LogJFR) tty->print_cr("Exception while starting a recording");
    CLEAR_PENDING_EXCEPTION;
    return false;
  }
  if (LogJFR && Verbose) tty->print_cr("Finished starting a recording");
  return true;
}

static bool launch_recordings(TRAPS) {
  bool result = true;
  if (dcmd_recordings_array != NULL) {
    const int length = dcmd_recordings_array->length();
    assert(length >= 1, "invariant");
    for (int i = 0; i < length; ++i) {
      if (!launch_recording(dcmd_recordings_array->at(i), THREAD)) {
        result = false;
        break;
      }
    }
  }
  teardown_startup_support();
  return result;
}

static bool is_cds_dump_requested() {
  // we will not be able to launch recordings if a cds dump is being requested
  if (DumpSharedSpaces && (JfrOptionSet::startup_recording_options() != NULL)) {
    warning("JFR will be disabled during CDS dumping");
    teardown_startup_support();
    return true;
  }
  return false;
}

bool JfrRecorder::on_vm_start() {
  if (is_cds_dump_requested()) {
    return true;
  }
  Thread* const thread = Thread::current();
  if (!JfrJavaEventWriter::has_required_classes(thread)) {
    // assume it is compact profile of jfr.jar is missed for some reasons
    // skip further initialization.
    return true;
  }
  if (!JfrOptionSet::initialize(thread)) {
    return false;
  }
  if (!register_jfr_dcmds()) {
    return false;
  }

  if (!validate_recording_options(thread)) {
    return false;
  }
  if (!JfrOptionSet::configure(thread)) {
    return false;
  }

  if (!is_enabled()) {
    return true;
  }

  return launch_recordings(thread);
}

static bool _created = false;

//
// Main entry point for starting Jfr functionality.
// Non-protected initializations assume single-threaded setup.
//
bool JfrRecorder::create(bool simulate_failure) {
  assert(!is_disabled(), "invariant");
  assert(!is_created(), "invariant");
  if (!is_enabled()) {
    enable();
  }
  if (!create_components() || simulate_failure) {
    destroy_components();
    return false;
  }
  if (!create_recorder_thread()) {
    destroy_components();
    return false;
  }
  _created = true;
  return true;
}

bool JfrRecorder::is_created() {
  return _created;
}

bool JfrRecorder::create_components() {
  ResourceMark rm;
  HandleMark hm;

  if (!create_java_event_writer()) {
    return false;
  }
  if (!create_jvmti_agent()) {
    return false;
  }
  if (!create_post_box()) {
    return false;
  }
  if (!create_chunk_repository()) {
    return false;
  }
  if (!create_storage()) {
    return false;
  }
  if (!create_checkpoint_manager()) {
    return false;
  }
  if (!create_stacktrace_repository()) {
    return false;
  }
  if (!create_os_interface()) {
    return false;
  }
  if (!create_stringpool()) {
    return false;
  }
  if (!create_thread_sampling()) {
    return false;
  }
  return true;
}

// subsystems
static JfrJvmtiAgent* _jvmti_agent = NULL;
static JfrPostBox* _post_box = NULL;
static JfrStorage* _storage = NULL;
static JfrCheckpointManager* _checkpoint_manager = NULL;
static JfrRepository* _repository = NULL;
static JfrStackTraceRepository* _stack_trace_repository;
static JfrStringPool* _stringpool = NULL;
static JfrOSInterface* _os_interface = NULL;
static JfrThreadSampling* _thread_sampling = NULL;

bool JfrRecorder::create_java_event_writer() {
  return JfrJavaEventWriter::initialize();
}

bool JfrRecorder::create_jvmti_agent() {
  return JfrOptionSet::allow_retransforms() ? JfrJvmtiAgent::create() : true;
}

bool JfrRecorder::create_post_box() {
  assert(_post_box == NULL, "invariant");
  _post_box = JfrPostBox::create();
  return _post_box != NULL;
}

bool JfrRecorder::create_chunk_repository() {
  assert(_repository == NULL, "invariant");
  assert(_post_box != NULL, "invariant");
  _repository = JfrRepository::create(*_post_box);
  return _repository != NULL && _repository->initialize();
}

bool JfrRecorder::create_os_interface() {
  assert(_os_interface == NULL, "invariant");
  _os_interface = JfrOSInterface::create();
  return _os_interface != NULL && _os_interface->initialize();
}

bool JfrRecorder::create_storage() {
  assert(_repository != NULL, "invariant");
  assert(_post_box != NULL, "invariant");
  _storage = JfrStorage::create(_repository->chunkwriter(), *_post_box);
  return _storage != NULL && _storage->initialize();
}

bool JfrRecorder::create_checkpoint_manager() {
  assert(_checkpoint_manager == NULL, "invariant");
  assert(_repository != NULL, "invariant");
  _checkpoint_manager = JfrCheckpointManager::create(_repository->chunkwriter());
  return _checkpoint_manager != NULL && _checkpoint_manager->initialize();
}

bool JfrRecorder::create_stacktrace_repository() {
  assert(_stack_trace_repository == NULL, "invariant");
  _stack_trace_repository = JfrStackTraceRepository::create();
  return _stack_trace_repository != NULL && _stack_trace_repository->initialize();
}

bool JfrRecorder::create_stringpool() {
  assert(_stringpool == NULL, "invariant");
  assert(_repository != NULL, "invariant");
  _stringpool = JfrStringPool::create(_repository->chunkwriter());
  return _stringpool != NULL && _stringpool->initialize();
}

bool JfrRecorder::create_thread_sampling() {
  assert(_thread_sampling == NULL, "invariant");
  _thread_sampling = JfrThreadSampling::create();
  return _thread_sampling != NULL;
}

void JfrRecorder::destroy_components() {
  JfrJvmtiAgent::destroy();
  if (_post_box != NULL) {
    JfrPostBox::destroy();
    _post_box = NULL;
  }
  if (_repository != NULL) {
    JfrRepository::destroy();
    _repository = NULL;
  }
  if (_storage != NULL) {
    JfrStorage::destroy();
    _storage = NULL;
  }
  if (_checkpoint_manager != NULL) {
    JfrCheckpointManager::destroy();
    _checkpoint_manager = NULL;
  }
  if (_stack_trace_repository != NULL) {
    JfrStackTraceRepository::destroy();
    _stack_trace_repository = NULL;
  }
  if (_stringpool != NULL) {
    JfrStringPool::destroy();
    _stringpool = NULL;
  }
  if (_os_interface != NULL) {
    JfrOSInterface::destroy();
    _os_interface = NULL;
  }
  if (_thread_sampling != NULL) {
    JfrThreadSampling::destroy();
    _thread_sampling = NULL;
  }
}

bool JfrRecorder::create_recorder_thread() {
  return JfrRecorderThread::start(_checkpoint_manager, _post_box, Thread::current());
}

void JfrRecorder::destroy() {
  assert(is_created(), "invariant");
  _post_box->post(MSG_SHUTDOWN);
  JfrJvmtiAgent::destroy();
}

void JfrRecorder::on_recorder_thread_exit() {
  assert(!is_recording(), "invariant");
  // intent is to destroy the recorder instance and components,
  // but need sensitive coordination not yet in place
  //
  // destroy_components();
  //
  if (LogJFR) tty->print_cr("Recorder thread STOPPED");
}

void JfrRecorder::start_recording() {
  _post_box->post(MSG_START);
}

bool JfrRecorder::is_recording() {
  return JfrRecorderService::is_recording();
}

void JfrRecorder::stop_recording() {
  _post_box->post(MSG_STOP);
}
