/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_SHARED_ADAPTIVESIZEPOLICY_HPP
#define SHARE_VM_GC_IMPLEMENTATION_SHARED_ADAPTIVESIZEPOLICY_HPP

#include "gc_implementation/shared/gcUtil.hpp"
#include "gc_interface/collectedHeap.hpp"
#include "gc_interface/gcCause.hpp"
#include "memory/allocation.hpp"
#include "memory/universe.hpp"

// This class keeps statistical information and computes the
// size of the heap.

// Forward decls
class elapsedTimer;
class CollectorPolicy;

class AdaptiveSizePolicy : public CHeapObj<mtGC> {
 friend class GCAdaptivePolicyCounters;
 friend class PSGCAdaptivePolicyCounters;
 friend class CMSGCAdaptivePolicyCounters;
 protected:

  enum GCPolicyKind {
    _gc_adaptive_size_policy,
    _gc_ps_adaptive_size_policy,
    _gc_cms_adaptive_size_policy
  };
  virtual GCPolicyKind kind() const { return _gc_adaptive_size_policy; }

  enum SizePolicyTrueValues {
    decrease_old_gen_for_throughput_true = -7,
    decrease_young_gen_for_througput_true = -6,

    increase_old_gen_for_min_pauses_true = -5,
    decrease_old_gen_for_min_pauses_true = -4,
    decrease_young_gen_for_maj_pauses_true = -3,
    increase_young_gen_for_min_pauses_true = -2,
    increase_old_gen_for_maj_pauses_true = -1,

    decrease_young_gen_for_min_pauses_true = 1,
    decrease_old_gen_for_maj_pauses_true = 2,
    increase_young_gen_for_maj_pauses_true = 3,

    increase_old_gen_for_throughput_true = 4,
    increase_young_gen_for_througput_true = 5,

    decrease_young_gen_for_footprint_true = 6,
    decrease_old_gen_for_footprint_true = 7,
    decide_at_full_gc_true = 8
  };

  // Goal for the fraction of the total time during which application
  // threads run.
  const double _throughput_goal;

  // Last calculated sizes, in bytes, and aligned
  size_t _eden_size;        // calculated eden free space in bytes
  size_t _promo_size;       // calculated cms gen free space in bytes

  size_t _survivor_size;    // calculated survivor size in bytes

  // This is a hint for the heap:  we've detected that gc times
  // are taking longer than GCTimeLimit allows.
  bool _gc_overhead_limit_exceeded;
  // Use for diagnostics only.  If UseGCOverheadLimit is false,
  // this variable is still set.
  bool _print_gc_overhead_limit_would_be_exceeded;
  // Count of consecutive GC that have exceeded the
  // GC time limit criterion.
  uint _gc_overhead_limit_count;
  // This flag signals that GCTimeLimit is being exceeded
  // but may not have done so for the required number of consequetive
  // collections.

  // Minor collection timers used to determine both
  // pause and interval times for collections.
  static elapsedTimer _minor_timer;

  // Major collection timers, used to determine both
  // pause and interval times for collections
  static elapsedTimer _major_timer;

  // Time statistics
  AdaptivePaddedAverage*   _avg_minor_pause;
  AdaptiveWeightedAverage* _avg_minor_interval;
  AdaptiveWeightedAverage* _avg_minor_gc_cost;

  AdaptiveWeightedAverage* _avg_major_interval;
  AdaptiveWeightedAverage* _avg_major_gc_cost;

  // Footprint statistics
  AdaptiveWeightedAverage* _avg_young_live;
  AdaptiveWeightedAverage* _avg_eden_live;
  AdaptiveWeightedAverage* _avg_old_live;

  // Statistics for survivor space calculation for young generation
  AdaptivePaddedAverage*   _avg_survived;

  // Objects that have been directly allocated in the old generation.
  AdaptivePaddedNoZeroDevAverage*   _avg_pretenured;

  // Variable for estimating the major and minor pause times.
  // These variables represent linear least-squares fits of
  // the data.
  //   minor pause time vs. old gen size
  LinearLeastSquareFit* _minor_pause_old_estimator;
  //   minor pause time vs. young gen size
  LinearLeastSquareFit* _minor_pause_young_estimator;

  // Variables for estimating the major and minor collection costs
  //   minor collection time vs. young gen size
  LinearLeastSquareFit* _minor_collection_estimator;
  //   major collection time vs. cms gen size
  LinearLeastSquareFit* _major_collection_estimator;

  // These record the most recent collection times.  They
  // are available as an alternative to using the averages
  // for making ergonomic decisions.
  double _latest_minor_mutator_interval_seconds;

  // Allowed difference between major and minor gc times, used
  // for computing tenuring_threshold.
  const double _threshold_tolerance_percent;

  const double _gc_pause_goal_sec; // goal for maximum gc pause

  // Flag indicating that the adaptive policy is ready to use
  bool _young_gen_policy_is_ready;

  // decrease/increase the young generation for minor pause time
  int _change_young_gen_for_min_pauses;

  // decrease/increase the old generation for major pause time
  int _change_old_gen_for_maj_pauses;

  //   change old geneneration for throughput
  int _change_old_gen_for_throughput;

  //   change young generation for throughput
  int _change_young_gen_for_throughput;

  // Flag indicating that the policy would
  //   increase the tenuring threshold because of the total major gc cost
  //   is greater than the total minor gc cost
  bool _increment_tenuring_threshold_for_gc_cost;
  //   decrease the tenuring threshold because of the the total minor gc
  //   cost is greater than the total major gc cost
  bool _decrement_tenuring_threshold_for_gc_cost;
  //   decrease due to survivor size limit
  bool _decrement_tenuring_threshold_for_survivor_limit;

  //   decrease generation sizes for footprint
  int _decrease_for_footprint;

  // Set if the ergonomic decisions were made at a full GC.
  int _decide_at_full_gc;

  // Changing the generation sizing depends on the data that is
  // gathered about the effects of changes on the pause times and
  // throughput.  These variable count the number of data points
  // gathered.  The policy may use these counters as a threshhold
  // for reliable data.
  julong _young_gen_change_for_minor_throughput;
  julong _old_gen_change_for_major_throughput;

  static const uint GCWorkersPerJavaThread  = 2;

  // Accessors

  double gc_pause_goal_sec() const { return _gc_pause_goal_sec; }
  // The value returned is unitless:  it's the proportion of time
  // spent in a particular collection type.
  // An interval time will be 0.0 if a collection type hasn't occurred yet.
  // The 1.4.2 implementation put a floor on the values of major_gc_cost
  // and minor_gc_cost.  This was useful because of the way major_gc_cost
  // and minor_gc_cost was used in calculating the sizes of the generations.
  // Do not use a floor in this implementation because any finite value
  // will put a limit on the throughput that can be achieved and any
  // throughput goal above that limit will drive the generations sizes
  // to extremes.
  double major_gc_cost() const {
    return MAX2(0.0F, _avg_major_gc_cost->average());
  }

  // The value returned is unitless:  it's the proportion of time
  // spent in a particular collection type.
  // An interval time will be 0.0 if a collection type hasn't occurred yet.
  // The 1.4.2 implementation put a floor on the values of major_gc_cost
  // and minor_gc_cost.  This was useful because of the way major_gc_cost
  // and minor_gc_cost was used in calculating the sizes of the generations.
  // Do not use a floor in this implementation because any finite value
  // will put a limit on the throughput that can be achieved and any
  // throughput goal above that limit will drive the generations sizes
  // to extremes.

  double minor_gc_cost() const {
    return MAX2(0.0F, _avg_minor_gc_cost->average());
  }

  // Because we're dealing with averages, gc_cost() can be
  // larger than 1.0 if just the sum of the minor cost the
  // the major cost is used.  Worse than that is the
  // fact that the minor cost and the major cost each
  // tend toward 1.0 in the extreme of high gc costs.
  // Limit the value of gc_cost to 1.0 so that the mutator
  // cost stays non-negative.
  virtual double gc_cost() const {
    double result = MIN2(1.0, minor_gc_cost() + major_gc_cost());
    assert(result >= 0.0, "Both minor and major costs are non-negative");
    return result;
  }

  // Elapsed time since the last major collection.
  virtual double time_since_major_gc() const;

  // Average interval between major collections to be used
  // in calculating the decaying major gc cost.  An overestimate
  // of this time would be a conservative estimate because
  // this time is used to decide if the major GC cost
  // should be decayed (i.e., if the time since the last
  // major gc is long compared to the time returned here,
  // then the major GC cost will be decayed).  See the
  // implementations for the specifics.
  virtual double major_gc_interval_average_for_decay() const {
    return _avg_major_interval->average();
  }

  // Return the cost of the GC where the major gc cost
  // has been decayed based on the time since the last
  // major collection.
  double decaying_gc_cost() const;

  // Decay the major gc cost.  Use this only for decisions on
  // whether to adjust, not to determine by how much to adjust.
  // This approximation is crude and may not be good enough for the
  // latter.
  double decaying_major_gc_cost() const;

  // Return the mutator cost using the decayed
  // GC cost.
  double adjusted_mutator_cost() const {
    double result = 1.0 - decaying_gc_cost();
    assert(result >= 0.0, "adjusted mutator cost calculation is incorrect");
    return result;
  }

  virtual double mutator_cost() const {
    double result = 1.0 - gc_cost();
    assert(result >= 0.0, "mutator cost calculation is incorrect");
    return result;
  }


  bool young_gen_policy_is_ready() { return _young_gen_policy_is_ready; }

  void update_minor_pause_young_estimator(double minor_pause_in_ms);
  virtual void update_minor_pause_old_estimator(double minor_pause_in_ms) {
    // This is not meaningful for all policies but needs to be present
    // to use minor_collection_end() in its current form.
  }

  virtual size_t eden_increment(size_t cur_eden);
  virtual size_t eden_increment(size_t cur_eden, uint percent_change);
  virtual size_t eden_decrement(size_t cur_eden);
  virtual size_t promo_increment(size_t cur_eden);
  virtual size_t promo_increment(size_t cur_eden, uint percent_change);
  virtual size_t promo_decrement(size_t cur_eden);

  virtual void clear_generation_free_space_flags();

  int change_old_gen_for_throughput() const {
    return _change_old_gen_for_throughput;
  }
  void set_change_old_gen_for_throughput(int v) {
    _change_old_gen_for_throughput = v;
  }
  int change_young_gen_for_throughput() const {
    return _change_young_gen_for_throughput;
  }
  void set_change_young_gen_for_throughput(int v) {
    _change_young_gen_for_throughput = v;
  }

  int change_old_gen_for_maj_pauses() const {
    return _change_old_gen_for_maj_pauses;
  }
  void set_change_old_gen_for_maj_pauses(int v) {
    _change_old_gen_for_maj_pauses = v;
  }

  bool decrement_tenuring_threshold_for_gc_cost() const {
    return _decrement_tenuring_threshold_for_gc_cost;
  }
  void set_decrement_tenuring_threshold_for_gc_cost(bool v) {
    _decrement_tenuring_threshold_for_gc_cost = v;
  }
  bool increment_tenuring_threshold_for_gc_cost() const {
    return _increment_tenuring_threshold_for_gc_cost;
  }
  void set_increment_tenuring_threshold_for_gc_cost(bool v) {
    _increment_tenuring_threshold_for_gc_cost = v;
  }
  bool decrement_tenuring_threshold_for_survivor_limit() const {
    return _decrement_tenuring_threshold_for_survivor_limit;
  }
  void set_decrement_tenuring_threshold_for_survivor_limit(bool v) {
    _decrement_tenuring_threshold_for_survivor_limit = v;
  }
  // Return true if the policy suggested a change.
  bool tenuring_threshold_change() const;

  static bool _debug_perturbation;

 public:
  AdaptiveSizePolicy(size_t init_eden_size,
                     size_t init_promo_size,
                     size_t init_survivor_size,
                     double gc_pause_goal_sec,
                     uint gc_cost_ratio);

  // Return number default  GC threads to use in the next GC.
  static int calc_default_active_workers(uintx total_workers,
                                         const uintx min_workers,
                                         uintx active_workers,
                                         uintx application_workers);

  // Return number of GC threads to use in the next GC.
  // This is called sparingly so as not to change the
  // number of GC workers gratuitously.
  //   For ParNew collections
  //   For PS scavenge and ParOld collections
  //   For G1 evacuation pauses (subject to update)
  // Other collection phases inherit the number of
  // GC workers from the calls above.  For example,
  // a CMS parallel remark uses the same number of GC
  // workers as the most recent ParNew collection.
  static int calc_active_workers(uintx total_workers,
                                 uintx active_workers,
                                 uintx application_workers);

  // Return number of GC threads to use in the next concurrent GC phase.
  static int calc_active_conc_workers(uintx total_workers,
                                      uintx active_workers,
                                      uintx application_workers);

  bool is_gc_cms_adaptive_size_policy() {
    return kind() == _gc_cms_adaptive_size_policy;
  }
  bool is_gc_ps_adaptive_size_policy() {
    return kind() == _gc_ps_adaptive_size_policy;
  }

  AdaptivePaddedAverage*   avg_minor_pause() const { return _avg_minor_pause; }
  AdaptiveWeightedAverage* avg_minor_interval() const {
    return _avg_minor_interval;
  }
  AdaptiveWeightedAverage* avg_minor_gc_cost() const {
    return _avg_minor_gc_cost;
  }

  AdaptiveWeightedAverage* avg_major_gc_cost() const {
    return _avg_major_gc_cost;
  }

  AdaptiveWeightedAverage* avg_young_live() const { return _avg_young_live; }
  AdaptiveWeightedAverage* avg_eden_live() const { return _avg_eden_live; }
  AdaptiveWeightedAverage* avg_old_live() const { return _avg_old_live; }

  AdaptivePaddedAverage*  avg_survived() const { return _avg_survived; }
  AdaptivePaddedNoZeroDevAverage*  avg_pretenured() { return _avg_pretenured; }

  // Methods indicating events of interest to the adaptive size policy,
  // called by GC algorithms. It is the responsibility of users of this
  // policy to call these methods at the correct times!
  virtual void minor_collection_begin();
  virtual void minor_collection_end(GCCause::Cause gc_cause);
  virtual LinearLeastSquareFit* minor_pause_old_estimator() const {
    return _minor_pause_old_estimator;
  }

  LinearLeastSquareFit* minor_pause_young_estimator() {
    return _minor_pause_young_estimator;
  }
  LinearLeastSquareFit* minor_collection_estimator() {
    return _minor_collection_estimator;
  }

  LinearLeastSquareFit* major_collection_estimator() {
    return _major_collection_estimator;
  }

  float minor_pause_young_slope() {
    return _minor_pause_young_estimator->slope();
  }

  float minor_collection_slope() { return _minor_collection_estimator->slope();}
  float major_collection_slope() { return _major_collection_estimator->slope();}

  float minor_pause_old_slope() {
    return _minor_pause_old_estimator->slope();
  }

  void set_eden_size(size_t new_size) {
    _eden_size = new_size;
  }
  void set_survivor_size(size_t new_size) {
    _survivor_size = new_size;
  }

  size_t calculated_eden_size_in_bytes() const {
    return _eden_size;
  }

  size_t calculated_promo_size_in_bytes() const {
    return _promo_size;
  }

  size_t calculated_survivor_size_in_bytes() const {
    return _survivor_size;
  }

  // This is a hint for the heap:  we've detected that gc times
  // are taking longer than GCTimeLimit allows.
  // Most heaps will choose to throw an OutOfMemoryError when
  // this occurs but it is up to the heap to request this information
  // of the policy
  bool gc_overhead_limit_exceeded() {
    return _gc_overhead_limit_exceeded;
  }
  void set_gc_overhead_limit_exceeded(bool v) {
    _gc_overhead_limit_exceeded = v;
  }

  // Tests conditions indicate the GC overhead limit is being approached.
  bool gc_overhead_limit_near() {
    return gc_overhead_limit_count() >=
        (AdaptiveSizePolicyGCTimeLimitThreshold - 1);
  }
  uint gc_overhead_limit_count() { return _gc_overhead_limit_count; }
  void reset_gc_overhead_limit_count() { _gc_overhead_limit_count = 0; }
  void inc_gc_overhead_limit_count() { _gc_overhead_limit_count++; }
  // accessors for flags recording the decisions to resize the
  // generations to meet the pause goal.

  int change_young_gen_for_min_pauses() const {
    return _change_young_gen_for_min_pauses;
  }
  void set_change_young_gen_for_min_pauses(int v) {
    _change_young_gen_for_min_pauses = v;
  }
  void set_decrease_for_footprint(int v) { _decrease_for_footprint = v; }
  int decrease_for_footprint() const { return _decrease_for_footprint; }
  int decide_at_full_gc() { return _decide_at_full_gc; }
  void set_decide_at_full_gc(int v) { _decide_at_full_gc = v; }

  // Check the conditions for an out-of-memory due to excessive GC time.
  // Set _gc_overhead_limit_exceeded if all the conditions have been met.
  void check_gc_overhead_limit(size_t young_live,
                               size_t eden_live,
                               size_t max_old_gen_size,
                               size_t max_eden_size,
                               bool   is_full_gc,
                               GCCause::Cause gc_cause,
                               CollectorPolicy* collector_policy);

  // Printing support
  virtual bool print_adaptive_size_policy_on(outputStream* st) const;
  bool print_adaptive_size_policy_on(outputStream* st,
                                     uint tenuring_threshold) const;
};

// Class that can be used to print information about the
// adaptive size policy at intervals specified by
// AdaptiveSizePolicyOutputInterval.  Only print information
// if an adaptive size policy is in use.
class AdaptiveSizePolicyOutput : StackObj {
  AdaptiveSizePolicy* _size_policy;
  bool _do_print;
  bool print_test(uint count) {
    // A count of zero is a special value that indicates that the
    // interval test should be ignored.  An interval is of zero is
    // a special value that indicates that the interval test should
    // always fail (never do the print based on the interval test).
    return PrintGCDetails &&
           UseAdaptiveSizePolicy &&
           (UseParallelGC || UseConcMarkSweepGC) &&
           (AdaptiveSizePolicyOutputInterval > 0) &&
           ((count == 0) ||
             ((count % AdaptiveSizePolicyOutputInterval) == 0));
  }
 public:
  // The special value of a zero count can be used to ignore
  // the count test.
  AdaptiveSizePolicyOutput(uint count) {
    if (UseAdaptiveSizePolicy && (AdaptiveSizePolicyOutputInterval > 0)) {
      CollectedHeap* heap = Universe::heap();
      _size_policy = heap->size_policy();
      _do_print = print_test(count);
    } else {
      _size_policy = NULL;
      _do_print = false;
    }
  }
  AdaptiveSizePolicyOutput(AdaptiveSizePolicy* size_policy,
                           uint count) :
    _size_policy(size_policy) {
    if (UseAdaptiveSizePolicy && (AdaptiveSizePolicyOutputInterval > 0)) {
      _do_print = print_test(count);
    } else {
      _do_print = false;
    }
  }
  ~AdaptiveSizePolicyOutput() {
    if (_do_print) {
      assert(UseAdaptiveSizePolicy, "Should not be in use");
      _size_policy->print_adaptive_size_policy_on(gclog_or_tty);
    }
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_SHARED_ADAPTIVESIZEPOLICY_HPP
