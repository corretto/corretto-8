/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.c1.*;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.interpreter.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.runtime.*;

/** <P> This class encapsulates the global state of the VM; the
    universe, object heap, interpreter, etc. It is a Singleton and
    must be initialized with a call to initialize() before calling
    getVM(). </P>

    <P> Many auxiliary classes (i.e., most of the VMObjects) keep
    needed field offsets in the form of static Field objects. In a
    debugging system, the VM might be shutdown and re-initialized (on
    a differently-configured build, i.e., 32- vs. 64-bit), and all old
    cached state (including fields and field offsets) must be
    flushed. </P>

    <P> An Observer pattern is used to implement the initialization of
    such classes. Each such class, in its static initializer,
    registers an Observer with the VM class via
    VM.registerVMInitializedObserver(). This Observer is guaranteed to
    be notified whenever the VM is initialized (or re-initialized). To
    implement the first-time initialization, the observer is also
    notified when it registers itself with the VM. (For bootstrapping
    reasons, this implies that the constructor of VM can not
    instantiate any such objects, since VM.soleInstance will not have
    been set yet. This is a bootstrapping issue which may have to be
    revisited later.) </P>
*/

public class VM {
  private static VM    soleInstance;
  private static List  vmInitializedObservers = new ArrayList();
  private List         vmResumedObservers   = new ArrayList();
  private List         vmSuspendedObservers = new ArrayList();
  private TypeDataBase db;
  private boolean      isBigEndian;
  /** This is only present if in a debugging system */
  private JVMDebugger  debugger;
  private long         stackBias;
  private long         logAddressSize;
  private Universe     universe;
  private ObjectHeap   heap;
  private SymbolTable  symbols;
  private StringTable  strings;
  private SystemDictionary dict;
  private Threads      threads;
  private ObjectSynchronizer synchronizer;
  private JNIHandles   handles;
  private Interpreter  interpreter;
  private StubRoutines stubRoutines;
  private Bytes        bytes;

  /** Flags indicating whether we are attached to a core, C1, or C2 build */
  private boolean      usingClientCompiler;
  private boolean      usingServerCompiler;
  /** alignment constants */
  private boolean      isLP64;
  private int          bytesPerLong;
  private int          bytesPerWord;
  private int          objectAlignmentInBytes;
  private int          minObjAlignmentInBytes;
  private int          logMinObjAlignmentInBytes;
  private int          heapWordSize;
  private int          heapOopSize;
  private int          klassPtrSize;
  private int          oopSize;
  /** This is only present in a non-core build */
  private CodeCache    codeCache;
  /** This is only present in a C1 build */
  private Runtime1     runtime1;
  /** These constants come from globalDefinitions.hpp */
  private int          invocationEntryBCI;
  private int          invalidOSREntryBCI;
  private ReversePtrs  revPtrs;
  private VMRegImpl    vmregImpl;
  private int          reserveForAllocationPrefetch;

  // System.getProperties from debuggee VM
  private Properties   sysProps;

  // VM version strings come from Abstract_VM_Version class
  private String       vmRelease;
  private String       vmInternalInfo;

  private Flag[] commandLineFlags;
  private Map flagsMap;

  private static Type intxType;
  private static Type uintxType;
  private static CIntegerType boolType;
  private Boolean sharingEnabled;
  private Boolean compressedOopsEnabled;
  private Boolean compressedKlassPointersEnabled;

  // command line flags supplied to VM - see struct Flag in globals.hpp
  public static final class Flag {
     private String type;
     private String name;
     private Address addr;
     private int flags;

     private Flag(String type, String name, Address addr, int flags) {
        this.type = type;
        this.name = name;
        this.addr = addr;
        this.flags = flags;
     }

     public String getType() {
        return type;
     }

     public String getName() {
        return name;
     }

     public Address getAddress() {
        return addr;
     }

     public int getOrigin() {
        return flags & 0xF;  // XXX can we get the mask bits from somewhere?
     }

     public boolean isBool() {
        return type.equals("bool");
     }

     public boolean getBool() {
        if (Assert.ASSERTS_ENABLED) {
           Assert.that(isBool(), "not a bool flag!");
        }
        return addr.getCIntegerAt(0, boolType.getSize(), boolType.isUnsigned()) != 0;
     }

     public boolean isIntx() {
        return type.equals("intx");
     }

     public long getIntx() {
        if (Assert.ASSERTS_ENABLED) {
           Assert.that(isIntx(), "not a intx flag!");
        }
        return addr.getCIntegerAt(0, intxType.getSize(), false);
     }

     public boolean isUIntx() {
        return type.equals("uintx");
     }

     public long getUIntx() {
        if (Assert.ASSERTS_ENABLED) {
           Assert.that(isUIntx(), "not a uintx flag!");
        }
        return addr.getCIntegerAt(0, uintxType.getSize(), true);
     }

     public String getValue() {
        if (isBool()) {
           return new Boolean(getBool()).toString();
        } else if (isIntx()) {
           return new Long(getIntx()).toString();
        } else if (isUIntx()) {
           return new Long(getUIntx()).toString();
        } else {
           return null;
        }
     }
  };

  private static void checkVMVersion(String vmRelease) {
     if (System.getProperty("sun.jvm.hotspot.runtime.VM.disableVersionCheck") == null) {
        // read sa build version.
        String versionProp = "sun.jvm.hotspot.runtime.VM.saBuildVersion";
        String saVersion = saProps.getProperty(versionProp);
        if (saVersion == null)
           throw new RuntimeException("Missing property " + versionProp);

        // Strip nonproduct VM version substring (note: saVersion doesn't have it).
        String vmVersion = vmRelease.replaceAll("(-fastdebug)|(-debug)|(-jvmg)|(-optimized)|(-profiled)","");

        if (saVersion.equals(vmVersion)) {
           // Exact match
           return;
        }
        if (saVersion.indexOf('-') == saVersion.lastIndexOf('-') &&
            vmVersion.indexOf('-') == vmVersion.lastIndexOf('-')) {
           // Throw exception if different release versions:
           // <major>.<minor>-b<n>
           throw new VMVersionMismatchException(saVersion, vmRelease);
        } else {
           // Otherwise print warning to allow mismatch not release versions
           // during development.
           System.err.println("WARNING: Hotspot VM version " + vmRelease +
                              " does not match with SA version " + saVersion +
                              "." + " You may see unexpected results. ");
        }
     } else {
        System.err.println("WARNING: You have disabled SA and VM version check. You may be "  +
                           "using incompatible version of SA and you may see unexpected " +
                           "results.");
     }
  }

  private static final boolean disableDerivedPointerTableCheck;
  private static final Properties saProps;

  static {
     saProps = new Properties();
     URL url = null;
     try {
       url = VM.class.getClassLoader().getResource("sa.properties");
       saProps.load(new BufferedInputStream(url.openStream()));
     } catch (Exception e) {
       System.err.println("Unable to load properties  " +
                                  (url == null ? "null" : url.toString()) +
                                  ": " + e.getMessage());
     }

     disableDerivedPointerTableCheck = System.getProperty("sun.jvm.hotspot.runtime.VM.disableDerivedPointerTableCheck") != null;
  }

  private VM(TypeDataBase db, JVMDebugger debugger, boolean isBigEndian) {
    this.db          = db;
    this.debugger    = debugger;
    this.isBigEndian = isBigEndian;

    // Note that we don't construct universe, heap, threads,
    // interpreter, or stubRoutines here (any more).  The current
    // initialization mechanisms require that the VM be completely set
    // up (i.e., out of its constructor, with soleInstance assigned)
    // before their static initializers are run.

    if (db.getAddressSize() == 4) {
      logAddressSize = 2;
    } else if (db.getAddressSize() == 8) {
      logAddressSize = 3;
    } else {
      throw new RuntimeException("Address size " + db.getAddressSize() + " not yet supported");
    }

    // read VM version info
    try {
       Type vmVersion = db.lookupType("Abstract_VM_Version");
       Address releaseAddr = vmVersion.getAddressField("_s_vm_release").getValue();
       vmRelease = CStringUtilities.getString(releaseAddr);
       Address vmInternalInfoAddr = vmVersion.getAddressField("_s_internal_vm_info_string").getValue();
       vmInternalInfo = CStringUtilities.getString(vmInternalInfoAddr);

       CIntegerType intType = (CIntegerType) db.lookupType("int");
       CIntegerField reserveForAllocationPrefetchField = vmVersion.getCIntegerField("_reserve_for_allocation_prefetch");
       reserveForAllocationPrefetch = (int)reserveForAllocationPrefetchField.getCInteger(intType);
    } catch (Exception exp) {
       throw new RuntimeException("can't determine target's VM version : " + exp.getMessage());
    }

    checkVMVersion(vmRelease);

    stackBias    = db.lookupIntConstant("STACK_BIAS").intValue();
    invocationEntryBCI = db.lookupIntConstant("InvocationEntryBci").intValue();
    invalidOSREntryBCI = db.lookupIntConstant("InvalidOSREntryBci").intValue();

    // We infer the presence of C1 or C2 from a couple of fields we
    // already have present in the type database
    {
      Type type = db.lookupType("Method");
      if (type.getField("_from_compiled_entry", false, false) == null) {
        // Neither C1 nor C2 is present
        usingClientCompiler = false;
        usingServerCompiler = false;
      } else {
        // Determine whether C2 is present
        if (db.lookupType("Matcher", false) != null) {
          usingServerCompiler = true;
        } else {
          usingClientCompiler = true;
        }
      }
    }

    if (debugger != null) {
      isLP64 = debugger.getMachineDescription().isLP64();
    }
    bytesPerLong = db.lookupIntConstant("BytesPerLong").intValue();
    bytesPerWord = db.lookupIntConstant("BytesPerWord").intValue();
    heapWordSize = db.lookupIntConstant("HeapWordSize").intValue();
    oopSize  = db.lookupIntConstant("oopSize").intValue();

    intxType = db.lookupType("intx");
    uintxType = db.lookupType("uintx");
    boolType = (CIntegerType) db.lookupType("bool");

    minObjAlignmentInBytes = getObjectAlignmentInBytes();
    if (minObjAlignmentInBytes == 8) {
      logMinObjAlignmentInBytes = 3;
    } else if (minObjAlignmentInBytes == 16) {
      logMinObjAlignmentInBytes = 4;
    } else {
      throw new RuntimeException("Object alignment " + minObjAlignmentInBytes + " not yet supported");
    }

    if (isCompressedOopsEnabled()) {
      // Size info for oops within java objects is fixed
      heapOopSize = (int)getIntSize();
    } else {
      heapOopSize = (int)getOopSize();
    }

    if (isCompressedKlassPointersEnabled()) {
      klassPtrSize = (int)getIntSize();
    } else {
      klassPtrSize = (int)getOopSize(); // same as an oop
    }
  }

  /** This could be used by a reflective runtime system */
  public static void initialize(TypeDataBase db, boolean isBigEndian) {
    if (soleInstance != null) {
      throw new RuntimeException("Attempt to initialize VM twice");
    }
    soleInstance = new VM(db, null, isBigEndian);
    for (Iterator iter = vmInitializedObservers.iterator(); iter.hasNext(); ) {
      ((Observer) iter.next()).update(null, null);
    }
  }

  /** This is used by the debugging system */
  public static void initialize(TypeDataBase db, JVMDebugger debugger) {
    if (soleInstance != null) {
      // Using multiple SA Tool classes in the same process creates a call here.
      return;
    }
    soleInstance = new VM(db, debugger, debugger.getMachineDescription().isBigEndian());

    for (Iterator iter = vmInitializedObservers.iterator(); iter.hasNext(); ) {
      ((Observer) iter.next()).update(null, null);
    }

    debugger.putHeapConst(soleInstance.getHeapOopSize(), soleInstance.getKlassPtrSize(),
                          Universe.getNarrowOopBase(), Universe.getNarrowOopShift(),
                          Universe.getNarrowKlassBase(), Universe.getNarrowKlassShift());
  }

  /** This is used by the debugging system */
  public static void shutdown() {
    soleInstance = null;
  }

  /** This is used by both the debugger and any runtime system. It is
      the basic mechanism by which classes which mimic underlying VM
      functionality cause themselves to be initialized. The given
      observer will be notified (with arguments (null, null)) when the
      VM is re-initialized, as well as when it registers itself with
      the VM. */
  public static void registerVMInitializedObserver(Observer o) {
    vmInitializedObservers.add(o);
    o.update(null, null);
  }

  /** This is the primary accessor used by both the debugger and any
      potential runtime system */
  public static VM getVM() {
    if (soleInstance == null) {
      throw new RuntimeException("VM.initialize() was not yet called");
    }
    return soleInstance;
  }

  /** This is only used by the debugging system. The given observer
      will be notified if the underlying VM resumes execution. NOTE
      that the given observer is not triggered if the VM is currently
      running and therefore differs in behavior from {@link
      #registerVMInitializedObserver} (because of the possibility of
      race conditions if the observer is added while the VM is being
      suspended or resumed).  */
  public void registerVMResumedObserver(Observer o) {
    vmResumedObservers.add(o);
  }

  /** This is only used by the debugging system. The given observer
      will be notified if the underlying VM suspends execution. NOTE
      that the given observer is not triggered if the VM is currently
      suspended and therefore differs in behavior from {@link
      #registerVMInitializedObserver} (because of the possibility of
      race conditions if the observer is added while the VM is being
      suspended or resumed).  */
  public void registerVMSuspendedObserver(Observer o) {
    vmSuspendedObservers.add(o);
  }

  /** This is only used by the debugging system. Informs all
      registered resumption observers that the VM has been resumed.
      The application is responsible for actually having performed the
      resumption. No OopHandles must be used after this point, as they
      may move in the target address space due to garbage
      collection. */
  public void fireVMResumed() {
    for (Iterator iter = vmResumedObservers.iterator(); iter.hasNext(); ) {
      ((Observer) iter.next()).update(null, null);
    }
  }

  /** This is only used by the debugging system. Informs all
      registered suspension observers that the VM has been suspended.
      The application is responsible for actually having performed the
      suspension. Garbage collection must be forbidden at this point;
      for example, a JPDA-level suspension is not adequate since the
      VM thread may still be running. */
  public void fireVMSuspended() {
    for (Iterator iter = vmSuspendedObservers.iterator(); iter.hasNext(); ) {
      ((Observer) iter.next()).update(null, null);
    }
  }

  /** Returns the OS this VM is running on. Notice that by delegating
      to the debugger we can transparently support remote
      debugging. */
  public String getOS() {
    if (debugger != null) {
      return debugger.getOS();
    }
    return PlatformInfo.getOS();
  }

  /** Returns the CPU this VM is running on. Notice that by delegating
      to the debugger we can transparently support remote
      debugging. */
  public String getCPU() {
    if (debugger != null) {
      return debugger.getCPU();
    }
    return PlatformInfo.getCPU();
  }

  public Type lookupType(String cTypeName) {
    return db.lookupType(cTypeName);
  }

  public Integer lookupIntConstant(String name) {
    return db.lookupIntConstant(name);
  }

  // Convenience function for conversions
  static public long getAddressValue(Address addr) {
    return VM.getVM().getDebugger().getAddressValue(addr);
  }

  public long getAddressSize() {
    return db.getAddressSize();
  }

  public long getOopSize() {
    return oopSize;
  }

  public long getLogAddressSize() {
    return logAddressSize;
  }

  public long getIntSize() {
    return db.getJIntType().getSize();
  }

  /** NOTE: this offset is in BYTES in this system! */
  public long getStackBias() {
    return stackBias;
  }

  /** Indicates whether the underlying machine supports the LP64 data
      model. This is needed for conditionalizing code in a few places */
  public boolean isLP64() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isDebugging(), "Debugging system only for now");
    }
    return isLP64;
  }

  /** Get bytes-per-long == long/double natural alignment. */
  public int getBytesPerLong() {
    return bytesPerLong;
  }

  public int getBytesPerWord() {
    return bytesPerWord;
  }

  /** Get minimum object alignment in bytes. */
  public int getMinObjAlignmentInBytes() {
    return minObjAlignmentInBytes;
  }
  public int getLogMinObjAlignmentInBytes() {
    return logMinObjAlignmentInBytes;
  }

  public int getHeapWordSize() {
    return heapWordSize;
  }

  public int getHeapOopSize() {
    return heapOopSize;
  }

  public int getKlassPtrSize() {
    return klassPtrSize;
  }
  /** Utility routine for getting data structure alignment correct */
  public long alignUp(long size, long alignment) {
    return (size + alignment - 1) & ~(alignment - 1);
  }

  /** Utility routine for getting data structure alignment correct */
  public long alignDown(long size, long alignment) {
    return size & ~(alignment - 1);
  }

  /** Utility routine for building an int from two "unsigned" 16-bit
      shorts */
  public int buildIntFromShorts(short low, short high) {
    return (((int) high) << 16) | (((int) low) & 0xFFFF);
  }

  /** Utility routine for building a long from two "unsigned" 32-bit
      ints in <b>platform-dependent</b> order */
  public long buildLongFromIntsPD(int oneHalf, int otherHalf) {
    if (isBigEndian) {
      return (((long) otherHalf) << 32) | (((long) oneHalf) & 0x00000000FFFFFFFFL);
    } else{
      return (((long) oneHalf) << 32) | (((long) otherHalf) & 0x00000000FFFFFFFFL);
    }
  }

  public TypeDataBase getTypeDataBase() {
    return db;
  }

  public Universe    getUniverse() {
    if (universe == null) {
      universe = new Universe();
    }
    return universe;
  }

  public ObjectHeap  getObjectHeap() {
    if (heap == null) {
      heap = new ObjectHeap(db);
    }
    return heap;
  }

  public SymbolTable getSymbolTable() {
    if (symbols == null) {
      symbols = SymbolTable.getTheTable();
    }
    return symbols;
  }

  public StringTable getStringTable() {
    if (strings == null) {
      strings = StringTable.getTheTable();
    }
    return strings;
  }

  public SystemDictionary getSystemDictionary() {
    if (dict == null) {
      dict = new SystemDictionary();
    }
    return dict;
  }

  public Threads     getThreads() {
    if (threads == null) {
      threads = new Threads();
    }
    return threads;
  }

  public ObjectSynchronizer getObjectSynchronizer() {
    if (synchronizer == null) {
      synchronizer = new ObjectSynchronizer();
    }
    return synchronizer;
  }

  public JNIHandles getJNIHandles() {
    if (handles == null) {
      handles = new JNIHandles();
    }
    return handles;
  }

  public Interpreter getInterpreter() {
    if (interpreter == null) {
      interpreter = new Interpreter();
    }
    return interpreter;
  }

  public StubRoutines getStubRoutines() {
    if (stubRoutines == null) {
      stubRoutines = new StubRoutines();
    }
    return stubRoutines;
  }

  public VMRegImpl getVMRegImplInfo() {
    if (vmregImpl == null) {
      vmregImpl = new VMRegImpl();
    }
    return vmregImpl;
  }

  public Bytes getBytes() {
    if (bytes == null) {
      bytes = new Bytes(debugger.getMachineDescription());
    }
    return bytes;
  }

  /** Returns true if this is a isBigEndian, false otherwise */
  public boolean isBigEndian() {
    return isBigEndian;
  }

  /** Returns true if this is a "core" build, false if either C1 or C2
      is present */
  public boolean isCore() {
    return (!(usingClientCompiler || usingServerCompiler));
  }

  /** Returns true if this is a C1 build, false otherwise */
  public boolean isClientCompiler() {
    return usingClientCompiler;
  }

  /** Returns true if this is a C2 build, false otherwise */
  public boolean isServerCompiler() {
    return usingServerCompiler;
  }

  /** Returns true if C2 derived pointer table should be used, false otherwise */
  public boolean useDerivedPointerTable() {
    return !disableDerivedPointerTableCheck;
  }

  /** Returns the code cache; should not be used if is core build */
  public CodeCache getCodeCache() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(!isCore(), "noncore builds only");
    }
    if (codeCache == null) {
      codeCache = new CodeCache();
    }
    return codeCache;
  }

  /** Should only be called for C1 builds */
  public Runtime1 getRuntime1() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isClientCompiler(), "C1 builds only");
    }
    if (runtime1 == null) {
      runtime1 = new Runtime1();
    }
    return runtime1;
  }

  /** Test to see whether we're in debugging mode (NOTE: this really
      should not be tested by this code; currently only used in
      StackFrameStream) */
  public boolean isDebugging() {
    return (debugger != null);
  }

  /** This is only used by the debugging (i.e., non-runtime) system */
  public JVMDebugger getDebugger() {
    if (debugger == null) {
      throw new RuntimeException("Attempt to use debugger in runtime system");
    }
    return debugger;
  }

  /** Indicates whether a given program counter is in Java code. This
      includes but is not spanned by the interpreter and code cache.
      Only used in the debugging system, for implementing
      JavaThread.currentFrameGuess() on x86. */
  public boolean isJavaPCDbg(Address addr) {
    // FIXME: this is not a complete enough set: must include areas
    // like vtable stubs
    return (getInterpreter().contains(addr) ||
            getCodeCache().contains(addr));
  }

  /** FIXME: figure out where to stick this */
  public int getInvocationEntryBCI() {
    return invocationEntryBCI;
  }

  /** FIXME: figure out where to stick this */
  public int getInvalidOSREntryBCI() {
    return invalidOSREntryBCI;
  }

  // FIXME: figure out where to stick this
  public boolean wizardMode() {
    return true;
  }

  public ReversePtrs getRevPtrs() {
    return revPtrs;
  }

  public void setRevPtrs(ReversePtrs rp) {
    revPtrs = rp;
  }

  // returns null, if not available.
  public String getVMRelease() {
    return vmRelease;
  }

  // returns null, if not available.
  public String getVMInternalInfo() {
    return vmInternalInfo;
  }

  public int getReserveForAllocationPrefetch() {
    return reserveForAllocationPrefetch;
  }

  public boolean isSharingEnabled() {
    if (sharingEnabled == null) {
      Flag flag = getCommandLineFlag("UseSharedSpaces");
      sharingEnabled = (flag == null)? Boolean.FALSE :
          (flag.getBool()? Boolean.TRUE: Boolean.FALSE);
    }
    return sharingEnabled.booleanValue();
  }

  public boolean isCompressedOopsEnabled() {
    if (compressedOopsEnabled == null) {
        Flag flag = getCommandLineFlag("UseCompressedOops");
        compressedOopsEnabled = (flag == null) ? Boolean.FALSE:
             (flag.getBool()? Boolean.TRUE: Boolean.FALSE);
    }
    return compressedOopsEnabled.booleanValue();
  }

  public boolean isCompressedKlassPointersEnabled() {
    if (compressedKlassPointersEnabled == null) {
        Flag flag = getCommandLineFlag("UseCompressedClassPointers");
        compressedKlassPointersEnabled = (flag == null) ? Boolean.FALSE:
             (flag.getBool()? Boolean.TRUE: Boolean.FALSE);
    }
    return compressedKlassPointersEnabled.booleanValue();
  }

  public int getObjectAlignmentInBytes() {
    if (objectAlignmentInBytes == 0) {
        Flag flag = getCommandLineFlag("ObjectAlignmentInBytes");
        objectAlignmentInBytes = (flag == null) ? 8 : (int)flag.getIntx();
    }
    return objectAlignmentInBytes;
  }

  /** Indicates whether Thread-Local Allocation Buffers are used */
  public boolean getUseTLAB() {
      Flag flag = getCommandLineFlag("UseTLAB");
      return (flag == null) ? false: flag.getBool();
  }

  // returns null, if not available.
  public Flag[] getCommandLineFlags() {
    if (commandLineFlags == null) {
       readCommandLineFlags();
    }

    return commandLineFlags;
  }

  public Flag getCommandLineFlag(String name) {
    if (flagsMap == null) {
      flagsMap = new HashMap();
      Flag[] flags = getCommandLineFlags();
      for (int i = 0; i < flags.length; i++) {
        flagsMap.put(flags[i].getName(), flags[i]);
      }
    }
    return (Flag) flagsMap.get(name);
  }

  private void readCommandLineFlags() {
    // get command line flags
    TypeDataBase db = getTypeDataBase();
    Type flagType = db.lookupType("Flag");
    int numFlags = (int) flagType.getCIntegerField("numFlags").getValue();
    // NOTE: last flag contains null values.
    commandLineFlags = new Flag[numFlags - 1];

    Address flagAddr = flagType.getAddressField("flags").getValue();

    AddressField typeFld = flagType.getAddressField("_type");
    AddressField nameFld = flagType.getAddressField("_name");
    AddressField addrFld = flagType.getAddressField("_addr");
    CIntField flagsFld = new CIntField(flagType.getCIntegerField("_flags"), 0);

    long flagSize = flagType.getSize(); // sizeof(Flag)

    // NOTE: last flag contains null values.
    for (int f = 0; f < numFlags - 1; f++) {
      String type = CStringUtilities.getString(typeFld.getValue(flagAddr));
      String name = CStringUtilities.getString(nameFld.getValue(flagAddr));
      Address addr = addrFld.getValue(flagAddr);
      int flags = (int)flagsFld.getValue(flagAddr);
      commandLineFlags[f] = new Flag(type, name, addr, flags);
      flagAddr = flagAddr.addOffsetTo(flagSize);
    }

    // sort flags by name
    Arrays.sort(commandLineFlags, new Comparator() {
        public int compare(Object o1, Object o2) {
          Flag f1 = (Flag) o1;
          Flag f2 = (Flag) o2;
          return f1.getName().compareTo(f2.getName());
        }
      });
  }

  public String getSystemProperty(String key) {
    Properties props = getSystemProperties();
    return (props != null)? props.getProperty(key) : null;
  }

  public Properties getSystemProperties() {
    if (sysProps == null) {
       readSystemProperties();
    }
    return sysProps;
  }

  private void readSystemProperties() {
    final InstanceKlass systemKls = getSystemDictionary().getSystemKlass();
    systemKls.iterateStaticFields(new DefaultOopVisitor() {
        ObjectReader objReader = new ObjectReader();
        public void doOop(sun.jvm.hotspot.oops.OopField field, boolean isVMField) {
          if (field.getID().getName().equals("props")) {
            try {
              sysProps = (Properties) objReader.readObject(field.getValue(getObj()));
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }
      });
  }
}
