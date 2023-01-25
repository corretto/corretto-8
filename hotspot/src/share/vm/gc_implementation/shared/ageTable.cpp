/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/ageTable.hpp"
#include "gc_implementation/shared/ageTableTracer.hpp"
#include "gc_implementation/shared/gcPolicyCounters.hpp"
#include "memory/collectorPolicy.hpp"
#include "memory/resourceArea.hpp"
#include "memory/sharedHeap.hpp"
#include "utilities/copy.hpp"

/* Copyright (c) 1992-2009 Oracle and/or its affiliates, and Stanford University.
   See the LICENSE file for license information. */

ageTable::ageTable(bool global) {

  clear();

  if (UsePerfData && global) {

    ResourceMark rm;
    EXCEPTION_MARK;

    const char* agetable_ns = "generation.0.agetable";
    const char* bytes_ns = PerfDataManager::name_space(agetable_ns, "bytes");

    for(int age = 0; age < table_size; age ++) {
      char age_name[10];
      jio_snprintf(age_name, sizeof(age_name), "%2.2d", age);
      const char* cname = PerfDataManager::counter_name(bytes_ns, age_name);
      _perf_sizes[age] = PerfDataManager::create_variable(SUN_GC, cname,
                                                          PerfData::U_Bytes,
                                                          CHECK);
    }

    const char* cname = PerfDataManager::counter_name(agetable_ns, "size");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_None,
                                     table_size, CHECK);
  }
}

void ageTable::clear() {
  for (size_t* p = sizes; p < sizes + table_size; ++p) {
    *p = 0;
  }
}

void ageTable::merge(ageTable* subTable) {
  for (int i = 0; i < table_size; i++) {
    sizes[i]+= subTable->sizes[i];
  }
}

void ageTable::merge_par(ageTable* subTable) {
  for (int i = 0; i < table_size; i++) {
    Atomic::add_ptr(subTable->sizes[i], &sizes[i]);
  }
}

uint ageTable::compute_tenuring_threshold(size_t survivor_capacity, GCTracer &tracer) {
  size_t desired_survivor_size = (size_t)((((double) survivor_capacity)*TargetSurvivorRatio)/100);
  size_t total = 0;
  uint age = 1;
  assert(sizes[0] == 0, "no objects with age zero should be recorded");
  while (age < table_size) {
    total += sizes[age];
    // check if including objects of age 'age' made us pass the desired
    // size, if so 'age' is the new threshold
    if (total > desired_survivor_size) break;
    age++;
  }
  uint result = age < MaxTenuringThreshold ? age : MaxTenuringThreshold;

  if (PrintTenuringDistribution || UsePerfData || AgeTableTracer::is_tenuring_distribution_event_enabled()) {

    if (PrintTenuringDistribution) {
      gclog_or_tty->cr();
      gclog_or_tty->print_cr("Desired survivor size " SIZE_FORMAT " bytes, new threshold %u (max %u)",
        desired_survivor_size*oopSize, result, (int) MaxTenuringThreshold);
    }

    total = 0;
    age = 1;
    while (age < table_size) {
      total += sizes[age];
      if (sizes[age] > 0) {
        if (PrintTenuringDistribution) {
          gclog_or_tty->print_cr("- age %3u: " SIZE_FORMAT_W(10) " bytes, " SIZE_FORMAT_W(10) " total",
                                        age,    sizes[age]*oopSize,          total*oopSize);
        }
      }
      AgeTableTracer::send_tenuring_distribution_event(age, wordSize * oopSize, tracer);
      if (UsePerfData) {
        _perf_sizes[age]->set_value(sizes[age]*oopSize);
      }
      age++;
    }
    if (UsePerfData) {
      SharedHeap* sh = SharedHeap::heap();
      CollectorPolicy* policy = sh->collector_policy();
      GCPolicyCounters* gc_counters = policy->counters();
      gc_counters->tenuring_threshold()->set_value(result);
      gc_counters->desired_survivor_size()->set_value(
        desired_survivor_size*oopSize);
    }
  }

  return result;
}
