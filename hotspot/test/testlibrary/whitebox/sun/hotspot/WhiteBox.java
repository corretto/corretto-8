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

package sun.hotspot;

import java.lang.management.MemoryUsage;
import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import java.security.BasicPermission;
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
  public native long getHeapAlignment();
  public native int  getVMPageSize();
  public native long getVMLargePageSize();

  public native boolean isObjectInOldGen(Object o);
  public native long getObjectSize(Object o);

  // Runtime
  // Make sure class name is in the correct format
  public boolean isClassAlive(String name) {
    return isClassAlive0(name.replace('.', '/'));
  }
  private native boolean isClassAlive0(String name);
  public native boolean isMonitorInflated(Object obj);
  public native void forceSafepoint();

  // Resource/Class Lookup Cache
  public native boolean classKnownToNotExist(ClassLoader loader, String name);
  public native URL[] getLookupCacheURLs(ClassLoader loader);
  public native int[] getLookupCacheMatches(ClassLoader loader, String name);

  // JVMTI
  public native void addToBootstrapClassLoaderSearch(String segment);
  public native void addToSystemClassLoaderSearch(String segment);

  // G1
  public native boolean g1InConcurrentMark();
  public native boolean g1IsHumongous(Object o);
  public native long    g1NumMaxRegions();
  public native long    g1NumFreeRegions();
  public native int     g1RegionSize();
  public native MemoryUsage g1AuxiliaryMemoryUsage();
  public native Object[]    parseCommandLine(String commandline, DiagnosticCommand[] args);

  // NMT
  public native long NMTMalloc(long size);
  public native void NMTFree(long mem);
  public native long NMTReserveMemory(long size);
  public native void NMTCommitMemory(long addr, long size);
  public native void NMTUncommitMemory(long addr, long size);
  public native void NMTReleaseMemory(long addr, long size);
  public native long NMTMallocWithPseudoStack(long size, int index);
  public native long NMTMallocWithPseudoStackAndType(long size, int index, int type);
  public native boolean NMTIsDetailSupported();
  public native boolean NMTChangeTrackingLevel();
  public native int NMTGetHashSize();
  public native long NMTNewArena(long initSize);
  public native void NMTFreeArena(long arena);
  public native void NMTArenaMalloc(long arena, long size);

  // Compiler
  public native void    deoptimizeAll();
  public        boolean isMethodCompiled(Executable method) {
    return isMethodCompiled(method, false /*not osr*/);
  }
  public native boolean isMethodCompiled(Executable method, boolean isOsr);
  public        boolean isMethodCompilable(Executable method) {
    return isMethodCompilable(method, -1 /*any*/);
  }
  public        boolean isMethodCompilable(Executable method, int compLevel) {
    return isMethodCompilable(method, compLevel, false /*not osr*/);
  }
  public native boolean isMethodCompilable(Executable method, int compLevel, boolean isOsr);
  public native boolean isMethodQueuedForCompilation(Executable method);
  public        int     deoptimizeMethod(Executable method) {
    return deoptimizeMethod(method, false /*not osr*/);
  }
  public native int     deoptimizeMethod(Executable method, boolean isOsr);
  public        void    makeMethodNotCompilable(Executable method) {
    makeMethodNotCompilable(method, -1 /*any*/);
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
    return getCompileQueueSize(-1 /*any*/);
  }
  public native int     getCompileQueueSize(int compLevel);
  public native boolean testSetForceInlineMethod(Executable method, boolean value);
  public        boolean enqueueMethodForCompilation(Executable method, int compLevel) {
    return enqueueMethodForCompilation(method, compLevel, -1 /*InvocationEntryBci*/);
  }
  public boolean enqueueMethodForCompilation(Executable method, int compLevel, int entry_bci) {
    return enqueueMethodForCompilation0(method, compLevel, entry_bci);
  }

  public native boolean enqueueInitializerForCompilation0(Class clazz, int compLevel);
  public native boolean enqueueMethodForCompilation0(Executable method, int compLevel, int entry_bci);
  public native void    clearMethodState(Executable method);
  public native void    markMethodProfiled(Executable method);
  public native int     getMethodEntryBci(Executable method);
  public native Object[] getNMethod(Executable method, boolean isOsr);

  // Intered strings
  public native boolean isInStringTable(String str);

  // Memory
  public native void readReservedMemory();
  public native long allocateCodeBlob(int size, int blobType);
  public native void freeCodeBlob(long address);
  public native Object[] getCodeBlob(long address);
  public native long allocateMetaspace(ClassLoader classLoader, long size);
  public native void freeMetaspace(ClassLoader classLoader, long addr, long size);
  public native long incMetaspaceCapacityUntilGC(long increment);
  public native long metaspaceCapacityUntilGC();

  // Force Young GC
  public native void youngGC();

  // Force Full GC
  public native void fullGC();

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

  // Native extensions
  public native long getHeapUsageForContext(int context);
  public native long getHeapRegionCountForContext(int context);
  public native int getContextForObject(Object obj);
  public native void printRegionInfo(int context);

  // VM flags
  public native void    setBooleanVMFlag(String name, boolean value);
  public native void    setIntxVMFlag(String name, long value);
  public native void    setUintxVMFlag(String name, long value);
  public native void    setUint64VMFlag(String name, long value);
  public native void    setStringVMFlag(String name, String value);
  public native void    setDoubleVMFlag(String name, double value);
  public native Boolean getBooleanVMFlag(String name);
  public native Long    getIntxVMFlag(String name);
  public native Long    getUintxVMFlag(String name);
  public native Long    getUint64VMFlag(String name);
  public native String  getStringVMFlag(String name);
  public native Double  getDoubleVMFlag(String name);
  private final List<Function<String,Object>> flagsGetters = Arrays.asList(
    this::getBooleanVMFlag, this::getIntxVMFlag, this::getUintxVMFlag,
    this::getUint64VMFlag, this::getStringVMFlag, this::getDoubleVMFlag);

  public Object getVMFlag(String name) {
    return flagsGetters.stream()
                       .map(f -> f.apply(name))
                       .filter(x -> x != null)
                       .findAny()
                       .orElse(null);
  }
  public native int getOffsetForName0(String name);
  public int getOffsetForName(String name) throws Exception {
    int offset = getOffsetForName0(name);
    if (offset == -1) {
      throw new RuntimeException(name + " not found");
    }
    return offset;
  }

  // Class Data Sharing
  public native boolean isSharedClass(Class<?> c);

  // Returns true on linux if library has the noexecstack flag set.
  public native boolean checkLibSpecifiesNoexecstack(String libfilename);

  // Container testing
  public native boolean isContainerized();
  public native void printOsInfo();

}
