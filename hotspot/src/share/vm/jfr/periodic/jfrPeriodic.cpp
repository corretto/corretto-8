/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "classfile/classLoaderStats.hpp"
#include "classfile/javaClasses.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "gc_implementation/g1/g1HeapRegionEventSender.hpp"
#include "gc_implementation/shared/gcConfiguration.hpp"
#include "gc_implementation/shared/gcTrace.hpp"
#include "gc_implementation/shared/objectCountEventSender.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/jfrOSInterface.hpp"
#include "jfr/periodic/jfrThreadCPULoadEvent.hpp"
#include "jfr/periodic/jfrThreadDumpEvent.hpp"
#include "jfr/periodic/jfrNetworkUtilization.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/support/jfrThreadId.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfrfiles/jfrPeriodic.hpp"
#include "memory/heapInspection.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/os_perf.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/vmThread.hpp"
#include "services/classLoadingService.hpp"
#include "services/management.hpp"
#include "services/threadService.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"

/**
 *  JfrPeriodic class
 *  Implementation of declarations in
 *  xsl generated traceRequestables.hpp
 */
#define TRACE_REQUEST_FUNC(id)    void JfrPeriodicEventSet::request##id(void)

TRACE_REQUEST_FUNC(JVMInformation) {
  ResourceMark rm;
  EventJVMInformation event;
  event.set_jvmName(VM_Version::vm_name());
  event.set_jvmVersion(VM_Version::internal_vm_info_string());
  event.set_javaArguments(Arguments::java_command());
  event.set_jvmArguments(Arguments::jvm_args());
  event.set_jvmFlags(Arguments::jvm_flags());
  event.set_jvmStartTime(Management::vm_init_done_time());
  event.set_pid(os::current_process_id());
  event.commit();
 }

TRACE_REQUEST_FUNC(OSInformation) {
  ResourceMark rm;
  char* os_name = NEW_RESOURCE_ARRAY(char, 2048);
  JfrOSInterface::os_version(&os_name);
  EventOSInformation event;
  event.set_osVersion(os_name);
  event.commit();
}

/*
 * This is left empty on purpose, having ExecutionSample as a requestable
 * is a way of getting the period. The period is passed to ThreadSampling::update_period.
 * Implementation in jfrSamples.cpp
 */
TRACE_REQUEST_FUNC(ExecutionSample) {
}
TRACE_REQUEST_FUNC(NativeMethodSample) {
}

TRACE_REQUEST_FUNC(ThreadDump) {
  ResourceMark rm;
  EventThreadDump event;
  event.set_result(JfrDcmdEvent::thread_dump());
  event.commit();
}

static int _native_library_callback(const char* name, address base, address top, void *param) {
  EventNativeLibrary event(UNTIMED);
  event.set_name(name);
  event.set_baseAddress((u8)base);
  event.set_topAddress((u8)top);
  event.set_endtime(*(JfrTicks*) param);
  event.commit();
  return 0;
}

TRACE_REQUEST_FUNC(NativeLibrary) {
  JfrTicks ts= JfrTicks::now();
  os::get_loaded_modules_info(&_native_library_callback, (void *)&ts);
}

TRACE_REQUEST_FUNC(InitialEnvironmentVariable) {
  JfrOSInterface::generate_initial_environment_variable_events();
}

TRACE_REQUEST_FUNC(CPUInformation) {
  CPUInformation cpu_info;
  int ret_val = JfrOSInterface::cpu_information(cpu_info);
  if (ret_val == OS_ERR) {
    if (LogJFR) tty->print_cr( "Unable to generate requestable event CPUInformation");
    return;
  }
  if (ret_val == FUNCTIONALITY_NOT_IMPLEMENTED) {
     return;
  }
  if (ret_val == OS_OK) {
    EventCPUInformation event;
    event.set_cpu(cpu_info.cpu_name());
    event.set_description(cpu_info.cpu_description());
    event.set_sockets(cpu_info.number_of_sockets());
    event.set_cores(cpu_info.number_of_cores());
    event.set_hwThreads(cpu_info.number_of_hardware_threads());
    event.commit();
  }
}

TRACE_REQUEST_FUNC(CPULoad) {
  double u = 0; // user time
  double s = 0; // kernel time
  double t = 0; // total time
  int ret_val = JfrOSInterface::cpu_loads_process(&u, &s, &t);
  if (ret_val == OS_ERR) {
    if (LogJFR) tty->print_cr( "Unable to generate requestable event CPULoad");
    return;
  }
  if (ret_val == OS_OK) {
    EventCPULoad event;
    event.set_jvmUser((float)u);
    event.set_jvmSystem((float)s);
    event.set_machineTotal((float)t);
    event.commit();
  }
}

TRACE_REQUEST_FUNC(ThreadCPULoad) {
  JfrThreadCPULoadEvent::send_events();
}

TRACE_REQUEST_FUNC(NetworkUtilization) {
  JfrNetworkUtilization::send_events();
}

TRACE_REQUEST_FUNC(CPUTimeStampCounter) {
  EventCPUTimeStampCounter event;
  event.set_fastTimeEnabled(JfrTime::is_ft_enabled());
  event.set_fastTimeAutoEnabled(JfrTime::is_ft_supported());
  event.set_osFrequency(os::elapsed_frequency());
  event.set_fastTimeFrequency(JfrTime::frequency());
  event.commit();
}

TRACE_REQUEST_FUNC(SystemProcess) {
  char pid_buf[16];
  SystemProcess* processes = NULL;
  int num_of_processes = 0;
  JfrTicks start_time = JfrTicks::now();
  int ret_val = JfrOSInterface::system_processes(&processes, &num_of_processes);
  if (ret_val == OS_ERR) {
    if (LogJFR) tty->print_cr( "Unable to generate requestable event SystemProcesses");
    return;
  }
  JfrTicks end_time = JfrTicks::now();
  if (ret_val == FUNCTIONALITY_NOT_IMPLEMENTED) {
    return;
  }
  if (ret_val == OS_OK) {
    // feature is implemented, write real event
    while (processes != NULL) {
      SystemProcess* tmp = processes;
      const char* info = processes->command_line();
      if (info == NULL) {
         info = processes->path();
      }
      if (info == NULL) {
         info = processes->name();
      }
      if (info == NULL) {
         info = "?";
      }
      jio_snprintf(pid_buf, sizeof(pid_buf), "%d", processes->pid());
      EventSystemProcess event(UNTIMED);
      event.set_pid(pid_buf);
      event.set_commandLine(info);
      event.set_starttime(start_time);
      event.set_endtime(end_time);
      event.commit();
      processes = processes->next();
      delete tmp;
    }
  }
}

TRACE_REQUEST_FUNC(ThreadContextSwitchRate) {
  double rate = 0.0;
  int ret_val = JfrOSInterface::context_switch_rate(&rate);
  if (ret_val == OS_ERR) {
    if (LogJFR) tty->print_cr( "Unable to generate requestable event ThreadContextSwitchRate");
    return;
  }
  if (ret_val == FUNCTIONALITY_NOT_IMPLEMENTED) {
    return;
  }
  if (ret_val == OS_OK) {
    EventThreadContextSwitchRate event;
    event.set_switchRate((float)rate + 0.0f);
    event.commit();
  }
}

#define SEND_FLAGS_OF_TYPE(eventType, flagType)                   \
  do {                                                            \
    Flag *flag = Flag::flags;                                     \
    while (flag->_name != NULL) {                                 \
      if (flag->is_ ## flagType()) {                              \
        if (flag->is_unlocked()) {                                \
          Event ## eventType event;                               \
          event.set_name(flag->_name);                            \
          event.set_value(flag->get_ ## flagType());              \
          event.set_origin(flag->get_origin());                   \
          event.commit();                                         \
        }                                                         \
      }                                                           \
      ++flag;                                                     \
    }                                                             \
  } while (0)

TRACE_REQUEST_FUNC(IntFlag) {
  SEND_FLAGS_OF_TYPE(IntFlag, intx);
}

TRACE_REQUEST_FUNC(UnsignedIntFlag) {
  SEND_FLAGS_OF_TYPE(UnsignedIntFlag, uintx);
}

TRACE_REQUEST_FUNC(LongFlag) {
  SEND_FLAGS_OF_TYPE(LongFlag, intx);
}

TRACE_REQUEST_FUNC(UnsignedLongFlag) {
  SEND_FLAGS_OF_TYPE(UnsignedLongFlag, uintx);
  SEND_FLAGS_OF_TYPE(UnsignedLongFlag, uint64_t);
}

TRACE_REQUEST_FUNC(DoubleFlag) {
  SEND_FLAGS_OF_TYPE(DoubleFlag, double);
}

TRACE_REQUEST_FUNC(BooleanFlag) {
  SEND_FLAGS_OF_TYPE(BooleanFlag, bool);
}

TRACE_REQUEST_FUNC(StringFlag) {
  SEND_FLAGS_OF_TYPE(StringFlag, ccstr);
}

class VM_GC_SendObjectCountEvent : public VM_GC_HeapInspection {
 public:
  VM_GC_SendObjectCountEvent() : VM_GC_HeapInspection(NULL, true) {}
  virtual void doit() {
    ObjectCountEventSender::enable_requestable_event();
    collect();
    ObjectCountEventSender::disable_requestable_event();
  }
};

TRACE_REQUEST_FUNC(ObjectCount) {
  VM_GC_SendObjectCountEvent op;
  VMThread::execute(&op);
}

class VM_G1SendHeapRegionInfoEvents : public VM_Operation {
  virtual void doit() {
    G1HeapRegionEventSender::send_events();
  }
  virtual VMOp_Type type() const { return VMOp_HeapIterateOperation; }
};

TRACE_REQUEST_FUNC(G1HeapRegionInformation) {
  if (UseG1GC) {
    VM_G1SendHeapRegionInfoEvents op;
    VMThread::execute(&op);
  }
}

// Java Mission Control (JMC) uses (Java) Long.MIN_VALUE to describe that a
// long value is undefined.
static jlong jmc_undefined_long = min_jlong;

TRACE_REQUEST_FUNC(GCConfiguration) {
  GCConfiguration conf;
  jlong pause_target = conf.has_pause_target_default_value() ? jmc_undefined_long : conf.pause_target();
  EventGCConfiguration event;
  event.set_youngCollector(conf.young_collector());
  event.set_oldCollector(conf.old_collector());
  event.set_parallelGCThreads(conf.num_parallel_gc_threads());
  event.set_concurrentGCThreads(conf.num_concurrent_gc_threads());
  event.set_usesDynamicGCThreads(conf.uses_dynamic_gc_threads());
  event.set_isExplicitGCConcurrent(conf.is_explicit_gc_concurrent());
  event.set_isExplicitGCDisabled(conf.is_explicit_gc_disabled());
  event.set_gcTimeRatio(conf.gc_time_ratio());
  event.set_pauseTarget((s8)pause_target);
  event.commit();
}

TRACE_REQUEST_FUNC(GCTLABConfiguration) {
  GCTLABConfiguration conf;
  EventGCTLABConfiguration event;
  event.set_usesTLABs(conf.uses_tlabs());
  event.set_minTLABSize(conf.min_tlab_size());
  event.set_tlabRefillWasteLimit(conf.tlab_refill_waste_limit());
  event.commit();
}

TRACE_REQUEST_FUNC(GCSurvivorConfiguration) {
  GCSurvivorConfiguration conf;
  EventGCSurvivorConfiguration event;
  event.set_maxTenuringThreshold(conf.max_tenuring_threshold());
  event.set_initialTenuringThreshold(conf.initial_tenuring_threshold());
  event.commit();
}

TRACE_REQUEST_FUNC(GCHeapConfiguration) {
  GCHeapConfiguration conf;
  EventGCHeapConfiguration event;
  event.set_minSize(conf.min_size());
  event.set_maxSize(conf.max_size());
  event.set_initialSize(conf.initial_size());
  event.set_usesCompressedOops(conf.uses_compressed_oops());
  event.set_compressedOopsMode(conf.narrow_oop_mode());
  event.set_objectAlignment(conf.object_alignment_in_bytes());
  event.set_heapAddressBits(conf.heap_address_size_in_bits());
  event.commit();
}

TRACE_REQUEST_FUNC(YoungGenerationConfiguration) {
  GCYoungGenerationConfiguration conf;
  jlong max_size = conf.has_max_size_default_value() ? jmc_undefined_long : conf.max_size();
  EventYoungGenerationConfiguration event;
  event.set_maxSize((u8)max_size);
  event.set_minSize(conf.min_size());
  event.set_newRatio(conf.new_ratio());
  event.commit();
}

TRACE_REQUEST_FUNC(InitialSystemProperty) {
  SystemProperty* p = Arguments::system_properties();
  JfrTicks time_stamp = JfrTicks::now();
  while (p !=  NULL) {
    if (true/* XXX fix me if you want !p->internal()*/) {
      EventInitialSystemProperty event(UNTIMED);
      event.set_key(p->key());
      event.set_value(p->value());
      event.set_endtime(time_stamp);
      event.commit();
    }
    p = p->next();
  }
}

TRACE_REQUEST_FUNC(ThreadAllocationStatistics) {
  ResourceMark rm;
  int initial_size = Threads::number_of_threads();
  GrowableArray<jlong> allocated(initial_size);
  GrowableArray<traceid> thread_ids(initial_size);
  JfrTicks time_stamp = JfrTicks::now();
  {
    // Collect allocation statistics while holding threads lock
    MutexLockerEx ml(Threads_lock);
    for (JavaThread *thread = Threads::first(); thread != NULL; thread = thread->next()) {
      allocated.append(thread->cooked_allocated_bytes());
      thread_ids.append(JFR_THREAD_ID(thread));
    }
  }

  // Write allocation statistics to buffer.
  for(int i = 0; i < thread_ids.length(); i++) {
    EventThreadAllocationStatistics event(UNTIMED);
    event.set_allocated(allocated.at(i));
    event.set_thread(thread_ids.at(i));
    event.set_endtime(time_stamp);
    event.commit();
  }
}

/**
 *  PhysicalMemory event represents:
 *
 *  @totalSize == The amount of physical memory (hw) installed and reported by the OS, in bytes.
 *  @usedSize  == The amount of physical memory currently in use in the system (reserved/committed), in bytes.
 *
 *  Both fields are systemwide, i.e. represents the entire OS/HW environment.
 *  These fields do not include virtual memory.
 *
 *  If running inside a guest OS on top of a hypervisor in a virtualized environment,
 *  the total memory reported is the amount of memory configured for the guest OS by the hypervisor.
 */
TRACE_REQUEST_FUNC(PhysicalMemory) {
  u8 totalPhysicalMemory = os::physical_memory();
  EventPhysicalMemory event;
  event.set_totalSize(totalPhysicalMemory);
  event.set_usedSize(totalPhysicalMemory - os::available_memory());
  event.commit();
}

TRACE_REQUEST_FUNC(JavaThreadStatistics) {
  EventJavaThreadStatistics event;
  event.set_activeCount(ThreadService::get_live_thread_count());
  event.set_daemonCount(ThreadService::get_daemon_thread_count());
  event.set_accumulatedCount(ThreadService::get_total_thread_count());
  event.set_peakCount(ThreadService::get_peak_thread_count());
  event.commit();
}

TRACE_REQUEST_FUNC(ClassLoadingStatistics) {
  EventClassLoadingStatistics event;
  event.set_loadedClassCount(ClassLoadingService::loaded_class_count());
  event.set_unloadedClassCount(ClassLoadingService::unloaded_class_count());
  event.commit();
}

class JfrClassLoaderStatsClosure : public ClassLoaderStatsClosure {
public:
  JfrClassLoaderStatsClosure() : ClassLoaderStatsClosure(NULL) {}

  bool do_entry(oop const& key, ClassLoaderStats* const& cls) {
    const ClassLoaderData* this_cld = cls->_class_loader != NULL ?
      java_lang_ClassLoader::loader_data(cls->_class_loader) : (ClassLoaderData*)NULL;
    const ClassLoaderData* parent_cld = cls->_parent != NULL ?
      java_lang_ClassLoader::loader_data(cls->_parent) : (ClassLoaderData*)NULL;
    EventClassLoaderStatistics event;
    event.set_classLoader(this_cld);
    event.set_parentClassLoader(parent_cld);
    event.set_classLoaderData((intptr_t)cls->_cld);
    event.set_classCount(cls->_classes_count);
    event.set_chunkSize(cls->_chunk_sz);
    event.set_blockSize(cls->_block_sz);
    event.set_anonymousClassCount(cls->_anon_classes_count);
    event.set_anonymousChunkSize(cls->_anon_chunk_sz);
    event.set_anonymousBlockSize(cls->_anon_block_sz);
    event.commit();
    return true;
  }

  void createEvents(void) {
    _stats->iterate(this);
  }
};

class JfrClassLoaderStatsVMOperation : public ClassLoaderStatsVMOperation {
 public:
  JfrClassLoaderStatsVMOperation() : ClassLoaderStatsVMOperation(NULL) { }

  void doit() {
    JfrClassLoaderStatsClosure clsc;
    ClassLoaderDataGraph::cld_do(&clsc);
    clsc.createEvents();
  }
};

TRACE_REQUEST_FUNC(ClassLoaderStatistics) {
  JfrClassLoaderStatsVMOperation op;
  VMThread::execute(&op);
}

TRACE_REQUEST_FUNC(CompilerStatistics) {
  EventCompilerStatistics event;
  event.set_compileCount(CompileBroker::get_total_compile_count());
  event.set_bailoutCount(CompileBroker::get_total_bailout_count());
  event.set_invalidatedCount(CompileBroker::get_total_invalidated_count());
  event.set_osrCompileCount(CompileBroker::get_total_osr_compile_count());
  event.set_standardCompileCount(CompileBroker::get_total_standard_compile_count());
  event.set_osrBytesCompiled(CompileBroker::get_sum_osr_bytes_compiled());
  event.set_standardBytesCompiled(CompileBroker::get_sum_standard_bytes_compiled());
  event.set_nmetodsSize(CompileBroker::get_sum_nmethod_size());
  event.set_nmetodCodeSize(CompileBroker::get_sum_nmethod_code_size());
  event.set_peakTimeSpent(CompileBroker::get_peak_compilation_time());
  event.set_totalTimeSpent(CompileBroker::get_total_compilation_time());
  event.commit();
}

TRACE_REQUEST_FUNC(CompilerConfiguration) {
  EventCompilerConfiguration event;
  event.set_threadCount(CICompilerCount);
  event.set_tieredCompilation(TieredCompilation);
  event.commit();
}

TRACE_REQUEST_FUNC(CodeCacheStatistics) {
  EventCodeCacheStatistics event;
  event.set_codeBlobType((u1)0/*bt*/); // XXX
  event.set_startAddress((u8)CodeCache::low_bound());
  event.set_reservedTopAddress((u8)CodeCache::high_bound());
  event.set_entryCount(CodeCache::nof_blobs());
  event.set_methodCount(CodeCache::nof_nmethods());
  event.set_adaptorCount(CodeCache::nof_adapters());
  event.set_unallocatedCapacity(CodeCache::unallocated_capacity());
  event.set_fullCount(CodeCache::get_codemem_full_count());
  event.commit();
}

TRACE_REQUEST_FUNC(CodeCacheConfiguration) {
  EventCodeCacheConfiguration event;
  event.set_initialSize(InitialCodeCacheSize);
  event.set_reservedSize(ReservedCodeCacheSize);
  event.set_nonNMethodSize(0/*NonNMethodCodeHeapSize*/); // XXX
  event.set_profiledSize(0/*ProfiledCodeHeapSize*/); // XXX
  event.set_nonProfiledSize(0/*NonProfiledCodeHeapSize*/); // XXX
  event.set_expansionSize(CodeCacheExpansionSize);
  event.set_minBlockLength(CodeCacheMinBlockLength);
  event.set_startAddress((u8)CodeCache::low_bound());
  event.set_reservedTopAddress((u8)CodeCache::high_bound());
  event.commit();
}

TRACE_REQUEST_FUNC(CodeSweeperStatistics) {
  EventCodeSweeperStatistics event;
  event.set_sweepCount(NMethodSweeper::traversal_count());
  event.set_methodReclaimedCount(NMethodSweeper::total_nof_methods_reclaimed());
  event.set_totalSweepTime(NMethodSweeper::total_time_sweeping());
  event.set_peakFractionTime(NMethodSweeper::peak_sweep_fraction_time());
  event.set_peakSweepTime(NMethodSweeper::peak_sweep_time());
  event.commit();
}

TRACE_REQUEST_FUNC(CodeSweeperConfiguration) {
  EventCodeSweeperConfiguration event;
  event.set_sweeperEnabled(MethodFlushing);
  event.set_flushingEnabled(UseCodeCacheFlushing);
  event.commit();
}
