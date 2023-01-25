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
 */

package sun.hotspot;

import java.lang.management.MemoryUsage;
import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.security.BasicPermission;
import java.util.Objects;
import java.net.URL;

import sun.hotspot.parser.DiagnosticCommand;

public class WhiteBox {
  @SuppressWarnings("serial")
  public static class WhiteBoxPermission extends BasicPermission {
    public WhiteBoxPermission(String s) {
      super(s);
    }
  }

  private WhiteBox() {}
  private static final WhiteBox instance = new WhiteBox();
  private static native void registerNatives();

  /**
   * Returns the singleton WhiteBox instance.
   *
   * The returned WhiteBox object should be carefully guarded
   * by the caller, since it can be used to read and write data
   * at arbitrary memory addresses. It must never be passed to
   * untrusted code.
   */
  public synchronized static WhiteBox getWhiteBox() {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
      sm.checkPermission(new WhiteBoxPermission("getInstance"));
    }
    return instance;
  }

  static {
    registerNatives();
  }

  // Get the maximum heap size supporting COOPs
  public native long getCompressedOopsMaxHeapSize();
  // Arguments
  public native void printHeapSizes();

  // Memory
  public native long getObjectAddress(Object o);
  public native int  getHeapOopSize();
  public native int  getVMPageSize();
  public native long getVMAllocationGranularity();
  public native long getVMLargePageSize();
  public native long getHeapSpaceAlignment();
  public native long getHeapAlignment();

  public native boolean isObjectInOldGen(Object o);
  public native long getObjectSize(Object o);

  public native boolean classKnownToNotExist(ClassLoader loader, String name);
  public native URL[] getLookupCacheURLs(ClassLoader loader);
  public native int[] getLookupCacheMatches(ClassLoader loader, String name);

  // Runtime
  // Make sure class name is in the correct format
  public boolean isClassAlive(String name) {
    return isClassAlive0(name.replace('.', '/'));
  }
  private native boolean isClassAlive0(String name);

  public native boolean isMonitorInflated(Object obj);

  public native void forceSafepoint();

  private native long getConstantPool0(Class<?> aClass);
  public         long getConstantPool(Class<?> aClass) {
    Objects.requireNonNull(aClass);
    return getConstantPool0(aClass);
  }

  private native int getConstantPoolCacheIndexTag0();
  public         int getConstantPoolCacheIndexTag() {
    return getConstantPoolCacheIndexTag0();
  }

  private native int getConstantPoolCacheLength0(Class<?> aClass);
  public         int getConstantPoolCacheLength(Class<?> aClass) {
    Objects.requireNonNull(aClass);
    return getConstantPoolCacheLength0(aClass);
  }

  private native int remapInstructionOperandFromCPCache0(Class<?> aClass, int index);
  public         int remapInstructionOperandFromCPCache(Class<?> aClass, int index) {
    Objects.requireNonNull(aClass);
    return remapInstructionOperandFromCPCache0(aClass, index);
  }

  private native int encodeConstantPoolIndyIndex0(int index);
  public         int encodeConstantPoolIndyIndex(int index) {
    return encodeConstantPoolIndyIndex0(index);
  }

  // JVMTI
  public native void addToBootstrapClassLoaderSearch(String segment);
  public native void addToSystemClassLoaderSearch(String segment);

  // G1
  public native boolean g1InConcurrentMark();
  public native boolean g1IsHumongous(Object o);
  public native boolean g1BelongsToHumongousRegion(long adr);
  public native boolean g1BelongsToFreeRegion(long adr);
  public native long    g1NumMaxRegions();
  public native long    g1NumFreeRegions();
  public native int     g1RegionSize();
  public native MemoryUsage g1AuxiliaryMemoryUsage();
  public native Object[]    parseCommandLine(String commandline, DiagnosticCommand[] args);

  // Parallel GC
  public native long psVirtualSpaceAlignment();
  public native long psHeapGenerationAlignment();

  /**
   * Enumerates old regions with liveness less than specified and produces some statistics
   * @param liveness percent of region's liveness (live_objects / total_region_size * 100).
   * @return long[3] array where long[0] - total count of old regions
   *                             long[1] - total memory of old regions
   *                             long[2] - lowest estimation of total memory of old regions to be freed (non-full
   *                             regions are not included)
   */
  public native long[] g1GetMixedGCInfo(int liveness);

  // NMT
  public native long NMTMalloc(long size);
  public native void NMTFree(long mem);
  public native long NMTReserveMemory(long size);
  public native long NMTAttemptReserveMemoryAt(long addr, long size);
  public native void NMTCommitMemory(long addr, long size);
  public native void NMTUncommitMemory(long addr, long size);
  public native void NMTReleaseMemory(long addr, long size);
  public native long NMTMallocWithPseudoStack(long size, int index);
  public native long NMTMallocWithPseudoStackAndType(long size, int index, int type);
  public native boolean NMTIsDetailSupported();
  public native boolean NMTChangeTrackingLevel();
  public native int NMTGetHashSize();

  // Compiler
  public native int     matchesMethod(Executable method, String pattern);
  public native int     matchesInline(Executable method, String pattern);
  public native boolean shouldPrintAssembly(Executable method, int comp_level);
  public native int     deoptimizeFrames(boolean makeNotEntrant);
  public native void    deoptimizeAll();

  public        boolean isMethodCompiled(Executable method) {
    return isMethodCompiled(method, false /*not osr*/);
  }
  public native boolean isMethodCompiled(Executable method, boolean isOsr);
  public        boolean isMethodCompilable(Executable method) {
    return isMethodCompilable(method, -2 /*any*/);
  }
  public        boolean isMethodCompilable(Executable method, int compLevel) {
    return isMethodCompilable(method, compLevel, false /*not osr*/);
  }
  public native boolean isMethodCompilable(Executable method, int compLevel, boolean isOsr);

  public native boolean isMethodQueuedForCompilation(Executable method);

  // Determine if the compiler corresponding to the compilation level 'compLevel'
  // and to the compilation context 'compilation_context' provides an intrinsic
  // for the method 'method'. An intrinsic is available for method 'method' if:
  //  - the intrinsic is enabled (by using the appropriate command-line flag) and
  //  - the platform on which the VM is running provides the instructions necessary
  //    for the compiler to generate the intrinsic code.
  //
  // The compilation context is related to using the DisableIntrinsic flag on a
  // per-method level, see hotspot/src/share/vm/compiler/abstractCompiler.hpp
  // for more details.
  public boolean isIntrinsicAvailable(Executable method,
                                      Executable compilationContext,
                                      int compLevel) {
      Objects.requireNonNull(method);
      return isIntrinsicAvailable0(method, compilationContext, compLevel);
  }
  // If usage of the DisableIntrinsic flag is not expected (or the usage can be ignored),
  // use the below method that does not require the compilation context as argument.
  public boolean isIntrinsicAvailable(Executable method, int compLevel) {
      return isIntrinsicAvailable(method, null, compLevel);
  }
  private native boolean isIntrinsicAvailable0(Executable method,
                                               Executable compilationContext,
                                               int compLevel);
  public        int     deoptimizeMethod(Executable method) {
    return deoptimizeMethod(method, false /*not osr*/);
  }
  public native int     deoptimizeMethod(Executable method, boolean isOsr);
  public        void    makeMethodNotCompilable(Executable method) {
    makeMethodNotCompilable(method, -2 /*any*/);
  }
  public        void    makeMethodNotCompilable(Executable method, int compLevel) {
    makeMethodNotCompilable(method, compLevel, false /*not osr*/);
  }
  public native void    makeMethodNotCompilable(Executable method, int compLevel, boolean isOsr);
  public        int     getMethodCompilationLevel(Executable method) {
    return getMethodCompilationLevel(method, false /*not ost*/);
  }
  public native int     getMethodCompilationLevel(Executable method, boolean isOsr);
  public native boolean testSetDontInlineMethod(Executable method, boolean value);
  public        int     getCompileQueuesSize() {
    return getCompileQueueSize(-2 /*any*/);
  }
  public native int     getCompileQueueSize(int compLevel);
  public native boolean testSetForceInlineMethod(Executable method, boolean value);

  public        boolean enqueueMethodForCompilation(Executable method, int compLevel) {
    return enqueueMethodForCompilation(method, compLevel, -1 /*InvocationEntryBci*/);
  }
  private native boolean enqueueMethodForCompilation0(Executable method, int compLevel, int entry_bci);
  public  boolean enqueueMethodForCompilation(Executable method, int compLevel, int entry_bci) {
    Objects.requireNonNull(method);
    return enqueueMethodForCompilation0(method, compLevel, entry_bci);
  }
  private native boolean enqueueInitializerForCompilation0(Class<?> aClass, int compLevel);
  public  boolean enqueueInitializerForCompilation(Class<?> aClass, int compLevel) {
    Objects.requireNonNull(aClass);
    return enqueueInitializerForCompilation0(aClass, compLevel);
  }
  public native void    clearMethodState(Executable method);
  public native void    markMethodProfiled(Executable method);
  public native void    lockCompilation();
  public native void    unlockCompilation();
  public native int     getMethodEntryBci(Executable method);
  public native Object[] getNMethod(Executable method, boolean isOsr);
  public native long    allocateCodeBlob(int size, int type);
  public        long    allocateCodeBlob(long size, int type) {
      int intSize = (int) size;
      if ((long) intSize != size || size < 0) {
          throw new IllegalArgumentException(
                "size argument has illegal value " + size);
      }
      return allocateCodeBlob( intSize, type);
  }
  public native void    freeCodeBlob(long addr);
  public native Object[] getCodeHeapEntries(int type);
  public native int     getCompilationActivityMode();
  private native long getMethodData0(Executable method);
  public         long getMethodData(Executable method) {
    Objects.requireNonNull(method);
    return getMethodData0(method);
  }
  public native Object[] getCodeBlob(long addr);

  private native void clearInlineCaches0(boolean preserve_static_stubs);
  public void clearInlineCaches() {
    clearInlineCaches0(false);
  }
  public void clearInlineCaches(boolean preserve_static_stubs) {
    clearInlineCaches0(preserve_static_stubs);
  }

  // Intered strings
  public native boolean isInStringTable(String str);

  // Memory
  public native void readReservedMemory();
  public native long allocateMetaspace(ClassLoader classLoader, long size);
  public native void freeMetaspace(ClassLoader classLoader, long addr, long size);
  public native long incMetaspaceCapacityUntilGC(long increment);
  public native long metaspaceCapacityUntilGC();
  public native boolean metaspaceShouldConcurrentCollect();
  public native long metaspaceReserveAlignment();

  // Don't use these methods directly
  // Use sun.hotspot.gc.GC class instead.
  public native boolean isGCSupported(int name);
  public native boolean isGCSelected(int name);
  public native boolean isGCSelectedErgonomically();

  // Force Young GC
  public native void youngGC();

  // Force Full GC
  public native void fullGC();

  // Returns true if the current GC supports control of its concurrent
  // phase via requestConcurrentGCPhase().  If false, a request will
  // always fail.
  public native boolean supportsConcurrentGCPhaseControl();

  // Returns an array of concurrent phase names provided by this
  // collector.  These are the names recognized by
  // requestConcurrentGCPhase().
  public native String[] getConcurrentGCPhases();

  // Attempt to put the collector into the indicated concurrent phase,
  // and attempt to remain in that state until a new request is made.
  //
  // Returns immediately if already in the requested phase.
  // Otherwise, waits until the phase is reached.
  //
  // Throws IllegalStateException if unsupported by the current collector.
  // Throws NullPointerException if phase is null.
  // Throws IllegalArgumentException if phase is not valid for the current collector.
  public void requestConcurrentGCPhase(String phase) {
    if (!supportsConcurrentGCPhaseControl()) {
      throw new IllegalStateException("Concurrent GC phase control not supported");
    } else if (phase == null) {
      throw new NullPointerException("null phase");
    } else if (!requestConcurrentGCPhase0(phase)) {
      throw new IllegalArgumentException("Unknown concurrent GC phase: " + phase);
    }
  }

  // Helper for requestConcurrentGCPhase().  Returns true if request
  // succeeded, false if the phase is invalid.
  private native boolean requestConcurrentGCPhase0(String phase);

  // Method tries to start concurrent mark cycle.
  // It returns false if CM Thread is always in concurrent cycle.
  public native boolean g1StartConcMarkCycle();

  // Tests on ReservedSpace/VirtualSpace classes
  public native int stressVirtualSpaceResize(long reservedSpaceSize, long magnitude, long iterations);
  public native void runMemoryUnitTests();
  public native void readFromNoaccessArea();
  public native long getThreadStackSize();
  public native long getThreadRemainingStackSize();

  // CPU features
  public native String getCPUFeatures();

  // VM flags
  public native boolean isConstantVMFlag(String name);
  public native boolean isLockedVMFlag(String name);
  public native void    setBooleanVMFlag(String name, boolean value);
  public native void    setIntVMFlag(String name, long value);
  public native void    setUintVMFlag(String name, long value);
  public native void    setIntxVMFlag(String name, long value);
  public native void    setUintxVMFlag(String name, long value);
  public native void    setUint64VMFlag(String name, long value);
  public native void    setSizeTVMFlag(String name, long value);
  public native void    setStringVMFlag(String name, String value);
  public native void    setDoubleVMFlag(String name, double value);
  public native Boolean getBooleanVMFlag(String name);
  public native Long    getIntVMFlag(String name);
  public native Long    getUintVMFlag(String name);
  public native Long    getIntxVMFlag(String name);
  public native Long    getUintxVMFlag(String name);
  public native Long    getUint64VMFlag(String name);
  public native Long    getSizeTVMFlag(String name);
  public native String  getStringVMFlag(String name);
  public native Double  getDoubleVMFlag(String name);
  private final List<Function<String,Object>> flagsGetters = Arrays.asList(
    this::getBooleanVMFlag, this::getIntVMFlag, this::getUintVMFlag,
    this::getIntxVMFlag, this::getUintxVMFlag, this::getUint64VMFlag,
    this::getSizeTVMFlag, this::getStringVMFlag, this::getDoubleVMFlag);

  public Object getVMFlag(String name) {
    return flagsGetters.stream()
                       .map(f -> f.apply(name))
                       .filter(x -> x != null)
                       .findAny()
                       .orElse(null);
  }

  // Jigsaw
  public native void DefineModule(Object module, boolean is_open, String version,
                                  String location, Object[] packages);
  public native void AddModuleExports(Object from_module, String pkg, Object to_module);
  public native void AddReadsModule(Object from_module, Object source_module);
  public native void AddModuleExportsToAllUnnamed(Object module, String pkg);
  public native void AddModuleExportsToAll(Object module, String pkg);

  public native int getOffsetForName0(String name);
  public int getOffsetForName(String name) throws Exception {
    int offset = getOffsetForName0(name);
    if (offset == -1) {
      throw new RuntimeException(name + " not found");
    }
    return offset;
  }
  public native Boolean getMethodBooleanOption(Executable method, String name);
  public native Long    getMethodIntxOption(Executable method, String name);
  public native Long    getMethodUintxOption(Executable method, String name);
  public native Double  getMethodDoubleOption(Executable method, String name);
  public native String  getMethodStringOption(Executable method, String name);
  private final List<BiFunction<Executable,String,Object>> methodOptionGetters
      = Arrays.asList(this::getMethodBooleanOption, this::getMethodIntxOption,
          this::getMethodUintxOption, this::getMethodDoubleOption,
          this::getMethodStringOption);

  public Object getMethodOption(Executable method, String name) {
    return methodOptionGetters.stream()
                              .map(f -> f.apply(method, name))
                              .filter(x -> x != null)
                              .findAny()
                              .orElse(null);
  }

  // Safepoint Checking
  public native void assertMatchingSafepointCalls(boolean mutexSafepointValue, boolean attemptedNoSafepointValue);

  // Sharing & archiving
  public native boolean isShared(Object o);
  public native boolean isSharedClass(Class<?> c);
  public native boolean areSharedStringsIgnored();
  public native boolean isCDSIncludedInVmBuild();
  public native boolean isJFRIncludedInVmBuild();
  public native boolean isJavaHeapArchiveSupported();
  public native Object  getResolvedReferences(Class<?> c);
  public native boolean areOpenArchiveHeapObjectsMapped();

  // Handshakes
  public native int handshakeWalkStack(Thread t, boolean all_threads);

  // Returns true on linux if library has the noexecstack flag set.
  public native boolean checkLibSpecifiesNoexecstack(String libfilename);

  // Container testing
  public native boolean isContainerized();
  public native void printOsInfo();

  // Decoder
  public native void disableElfSectionCache();
}
