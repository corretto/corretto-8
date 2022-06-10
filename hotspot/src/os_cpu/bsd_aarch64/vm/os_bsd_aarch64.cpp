/*
 * Copyright (c) 1999, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

// no precompiled headers
#include "jvm.h"
#include "asm/macroAssembler.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/allocation.inline.hpp"
#include "os_share_bsd.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/timer.hpp"
#include "utilities/align.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"

// put OS-includes here
# include <sys/types.h>
# include <sys/mman.h>
# include <pthread.h>
# include <signal.h>
# include <errno.h>
# include <dlfcn.h>
# include <stdlib.h>
# include <stdio.h>
# include <unistd.h>
# include <sys/resource.h>
# include <pthread.h>
# include <sys/stat.h>
# include <sys/time.h>
# include <sys/utsname.h>
# include <sys/socket.h>
# include <sys/wait.h>
# include <pwd.h>
# include <poll.h>
#ifndef __OpenBSD__
# include <ucontext.h>
#endif

#if !defined(__APPLE__) && !defined(__NetBSD__)
# include <pthread_np.h>
#endif

// needed by current_stack_region() workaround for Mavericks
#if defined(__APPLE__)
# include <errno.h>
# include <sys/types.h>
# include <sys/sysctl.h>
# define DEFAULT_MAIN_THREAD_STACK_PAGES 2048
# define OS_X_10_9_0_KERNEL_MAJOR_VERSION 13
#endif

#define SPELL_REG_SP "sp"
#define SPELL_REG_FP "fp"

#ifdef __APPLE__
# if __DARWIN_UNIX03 && (MAC_OS_X_VERSION_MAX_ALLOWED >= MAC_OS_X_VERSION_10_5)
  // 10.5 UNIX03 member name prefixes
  #define DU3_PREFIX(s, m) __ ## s.__ ## m
# else
  #define DU3_PREFIX(s, m) s ## . ## m
# endif
#endif

#define context_x    uc_mcontext->DU3_PREFIX(ss,x)
#define context_fp   uc_mcontext->DU3_PREFIX(ss,fp)
#define context_lr   uc_mcontext->DU3_PREFIX(ss,lr)
#define context_sp   uc_mcontext->DU3_PREFIX(ss,sp)
#define context_pc   uc_mcontext->DU3_PREFIX(ss,pc)
#define context_cpsr uc_mcontext->DU3_PREFIX(ss,cpsr)

address os::current_stack_pointer() {
#if defined(__clang__) || defined(__llvm__)
  void *sp;
  __asm__("mov %0, " SPELL_REG_SP : "=r"(sp));
  return (address) sp;
#else
  register void *sp __asm__ (SPELL_REG_SP);                                                                                                                  │
  return (address) sp;                                                                                                                                       │
#endif
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).

  // the return value used in computation of Universe::non_oop_word(), which
  // is loaded by cpu/aarch64 by MacroAssembler::movptr(Register, uintptr_t)
  return (char*) 0xffffffffffff;
}

void os::initialize_thread(Thread *thr) {
}

address os::Bsd::ucontext_get_pc(ucontext_t * uc) {
  return (address)uc->context_pc;
}

intptr_t* os::Bsd::ucontext_get_sp(ucontext_t * uc) {
  return (intptr_t*)uc->context_sp;
}

intptr_t* os::Bsd::ucontext_get_fp(ucontext_t * uc) {
  return (intptr_t*)uc->context_fp;
}

// For Forte Analyzer AsyncGetCallTrace profiling support - thread
// is currently interrupted by SIGPROF.
// os::Solaris::fetch_frame_from_ucontext() tries to skip nested signal
// frames. Currently we don't do that on Bsd, so it's the same as
// os::fetch_frame_from_context().
ExtendedPC os::Bsd::fetch_frame_from_ucontext(Thread* thread,
  ucontext_t* uc, intptr_t** ret_sp, intptr_t** ret_fp) {

  assert(thread != NULL, "just checking");
  assert(ret_sp != NULL, "just checking");
  assert(ret_fp != NULL, "just checking");

  return os::fetch_frame_from_context(uc, ret_sp, ret_fp);
}

ExtendedPC os::fetch_frame_from_context(void* ucVoid,
                    intptr_t** ret_sp, intptr_t** ret_fp) {

  ExtendedPC  epc;
  ucontext_t* uc = (ucontext_t*)ucVoid;

  if (uc != NULL) {
    epc = ExtendedPC(os::Bsd::ucontext_get_pc(uc));
    if (ret_sp) *ret_sp = os::Bsd::ucontext_get_sp(uc);
    if (ret_fp) *ret_fp = os::Bsd::ucontext_get_fp(uc);
  } else {
    epc = ExtendedPC(NULL);
    if (ret_sp) *ret_sp = (intptr_t *)NULL;
    if (ret_fp) *ret_fp = (intptr_t *)NULL;
  }

  return epc;
}

frame os::fetch_frame_from_context(void* ucVoid) {
  intptr_t* sp;
  intptr_t* fp;
  ExtendedPC epc = fetch_frame_from_context(ucVoid, &sp, &fp);
  return frame(sp, fp, epc.pc());
}

// By default, gcc always saves frame pointer rfp on this stack. This
// may get turned off by -fomit-frame-pointer.
frame os::get_sender_for_C_frame(frame* fr) {
  return frame(fr->link(), fr->link(), fr->sender_pc());
}

NOINLINE frame os::current_frame() {
  intptr_t *fp = *(intptr_t **)__builtin_frame_address(0);
  frame myframe((intptr_t*)os::current_stack_pointer(),
                (intptr_t*)fp,
                CAST_FROM_FN_PTR(address, os::current_frame));
  if (os::is_first_C_frame(&myframe)) {
    // stack is not walkable
    return frame();
  } else {
    return os::get_sender_for_C_frame(&myframe);
  }
}

// Utility functions
extern "C" JNIEXPORT int
JVM_handle_bsd_signal(int sig,
                        siginfo_t* info,
                        void* ucVoid,
                        int abort_if_unrecognized) {
  ucontext_t* uc = (ucontext_t*) ucVoid;

  Thread* t = ThreadLocalStorage::get_thread_slow();

  // Must do this before SignalHandlerMark, if crash protection installed we will longjmp away
  // (no destructors can be run)
  os::ThreadCrashProtection::check_crash_protection(sig, t);

  SignalHandlerMark shm(t);

  // Note: it's not uncommon that JNI code uses signal/sigset to install
  // then restore certain signal handler (e.g. to temporarily block SIGPIPE,
  // or have a SIGILL handler when detecting CPU type). When that happens,
  // JVM_handle_bsd_signal() might be invoked with junk info/ucVoid. To
  // avoid unnecessary crash when libjsig is not preloaded, try handle signals
  // that do not require siginfo/ucontext first.

  if (sig == SIGPIPE || sig == SIGXFSZ) {
    // allow chained handler to go first
    if (os::Bsd::chained_handler(sig, info, ucVoid)) {
      return true;
	    } else {
      if (PrintMiscellaneous && (WizardMode || Verbose)) {
        char buf[64];
        warning("Ignoring %s - see bugs 4229104 or 646499219",
                os::exception_name(sig, buf, sizeof(buf)));
      }
      return true;
    }
  }

  JavaThread* thread = NULL;
  VMThread* vmthread = NULL;
  if (os::Bsd::signal_handlers_are_installed) {
    if (t != NULL ){
      if(t->is_Java_thread()) {
        thread = (JavaThread*)t;
      }
      else if(t->is_VM_thread()){
        vmthread = (VMThread *)t;
      }
    }
  }
/*
  NOTE: does not seem to work on bsd.
  if (info == NULL || info->si_code <= 0 || info->si_code == SI_NOINFO) {
    // can't decode this kind of signal
    info = NULL;
  } else {
    assert(sig == info->si_signo, "bad siginfo");
  }
*/
  // decide if this trap can be handled by a stub
  address stub = NULL;

  address pc          = NULL;

  //%note os_trap_1
  if (info != NULL && uc != NULL && thread != NULL) {
    pc = (address) os::Bsd::ucontext_get_pc(uc);

    if (StubRoutines::is_safefetch_fault(pc)) {
      uc->context_pc = intptr_t(StubRoutines::continuation_for_safefetch_fault(pc));
      return 1;
    }

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV || sig == SIGBUS) {
      address addr = (address) info->si_addr;

      // check if fault address is within thread stack
      if (addr < thread->stack_base() &&
          addr >= thread->stack_base() - thread->stack_size()) {
        Thread::WXWriteFromExecSetter wx_write;
        // stack overflow
        if (thread->in_stack_yellow_zone(addr)) {
          thread->disable_stack_yellow_zone();
          if (thread->thread_state() == _thread_in_Java) {
            // Throw a stack overflow exception.  Guard pages will be reenabled
            // while unwinding the stack.
            stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW);
          } else {
            // Thread was in the vm or native code.  Return and try to finish.
            return 1;
          }
        } else if (thread->in_stack_red_zone(addr)) {
          // Fatal red zone violation.  Disable the guard pages and fall through
          // to handle_unexpected_exception way down below.
          thread->disable_stack_red_zone();
          tty->print_raw_cr("An irrecoverable stack overflow has occurred.");
        }
      }
    }

    // We test if stub is already set (by the stack overflow code
    // above) so it is not overwritten by the code that follows. This
    // check is not required on other platforms, because on other
    // platforms we check for SIGSEGV only or SIGBUS only, where here
    // we have to check for both SIGSEGV and SIGBUS.
    if (thread->thread_state() == _thread_in_Java && stub == NULL) {
      // Java thread running in Java code => find exception handler if any
      // a fault inside compiled code, the interpreter, or a stub
      Thread::WXWriteFromExecSetter wx_write;

      // Handle signal from NativeJump::patch_verified_entry().
      if ((sig == SIGILL)
          && nativeInstruction_at(pc)->is_sigill_zombie_not_entrant()) {
        if (TraceTraps) {
          tty->print_cr("trap: zombie_not_entrant");
        }
        stub = SharedRuntime::get_handle_wrong_method_stub();
      } else if ((sig == SIGSEGV || sig == SIGBUS) && os::is_poll_address((address)info->si_addr)) {
        stub = SharedRuntime::get_poll_stub(pc);
#if defined(__APPLE__)
      // 32-bit Darwin reports a SIGBUS for nearly all memory access exceptions.
      // 64-bit Darwin may also use a SIGBUS (seen with compressed oops).
      // Catching SIGBUS here prevents the implicit SIGBUS NULL check below from
      // being called, so only do so if the implicit NULL check is not necessary.
      } else if (sig == SIGBUS && MacroAssembler::needs_explicit_null_check((intptr_t)info->si_addr)) {
#else
      } else if (sig == SIGBUS /* && info->si_code == BUS_OBJERR */) {
#endif
        // BugId 4454115: A read from a MappedByteBuffer can fault
        // here if the underlying file has been truncated.
        // Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob_unsafe(pc);
        nmethod* nm = (cb != NULL && cb->is_nmethod()) ? (nmethod*)cb : NULL;
        if (nm != NULL && nm->has_unsafe_access()) {
          stub = StubRoutines::handler_for_unsafe_access();
        }
      }
      else

      if (sig == SIGFPE  &&
          (info->si_code == FPE_INTDIV || info->si_code == FPE_FLTDIV)) {
        stub =
          SharedRuntime::
          continuation_for_implicit_exception(thread,
                                              pc,
                                              SharedRuntime::
                                              IMPLICIT_DIVIDE_BY_ZERO);
#ifdef __APPLE__
      } else if (sig == SIGFPE && info->si_code == FPE_NOOP) {
        Unimplemented();
#endif /* __APPLE__ */

      } else if ((sig == SIGSEGV || sig == SIGBUS) &&
               !MacroAssembler::needs_explicit_null_check((intptr_t)info->si_addr)) {
          // Determination of interpreter/vtable stub/compiled code null exception
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }
    } else if (thread->thread_state() == _thread_in_vm &&
               sig == SIGBUS && /* info->si_code == BUS_OBJERR && */
               thread->doing_unsafe_access()) {
        stub = StubRoutines::handler_for_unsafe_access();
    }

    // jni_fast_Get<Primitive>Field can trap at certain pc's if a GC kicks in
    // and the heap gets shrunk before the field access.
    if ((sig == SIGSEGV) || (sig == SIGBUS)) {
      address addr = JNI_FastGetField::find_slowcase_pc(pc);
      if (addr != (address)-1) {
        stub = addr;
      }
    }

    // Check to see if we caught the safepoint code in the
    // process of write protecting the memory serialization page.
    // It write enables the page immediately after protecting it
    // so we can just return to retry the write.
    if ((sig == SIGSEGV || sig == SIGBUS) &&
        os::is_memory_serialize_page(thread, (address) info->si_addr)) {
      // Block current thread until the memory serialize page permission restored.
      os::block_on_serialize_page_trap();
      return true;
    }
  }


#if defined(ASSERT) && defined(__APPLE__)
  // Execution protection violation
  //
  // This should be kept as the last step in the triage.  We don't
  // have a dedicated trap number for a no-execute fault, so be
  // conservative and allow other handlers the first shot.
  if (UnguardOnExecutionViolation > 0 &&
      (sig == SIGBUS)) {
    static __thread address last_addr = (address) -1;

    address addr = (address) info->si_addr;
    address pc = os::Bsd::ucontext_get_pc(uc);

    uint32_t esr = uc->uc_mcontext->__es.__esr;

    if (pc != addr && esr == 0x9200004F) { //TODO: figure out what this value means
      // We are faulting trying to write a R-X page
      pthread_jit_write_protect_np(false);
      tty->print_cr("Writing protection violation 0x%016 unprotecting.", p2i(addr));
      stub = pc;
      last_addr = (address) -1;
    } else if (pc == addr && esr == 0x8200000f) { //TODO: figure out what this value means
      // We are faulting trying to execute a RW- page
      if (addr != last_addr) {
        pthread_jit_write_protect_np(true);
        tty->print_cr("Executing protection violation 0x%016 protecting.", p2i(addr));
        stub = pc;
        // Set last_addr so if we fault again at the same address, we don't end
        // up in an endless loop.
        last_addr = addr;
      }
    }
  }
#endif

  if (stub != NULL) {
    // save all thread context in case we need to restore it
    if (thread != NULL) thread->set_saved_exception_pc(pc);

    uc->context_pc = (intptr_t)stub;
    return true;
  }

  // signal-chaining
  if (os::Bsd::chained_handler(sig, info, ucVoid)) {
     return true;
  }

  if (!abort_if_unrecognized) {
    // caller wants another chance, so give it to him
    return false;
  }

  if (pc == NULL && uc != NULL) {
    pc = os::Bsd::ucontext_get_pc(uc);
  }

  // unmask current signal
  sigset_t newset;
  sigemptyset(&newset);
  sigaddset(&newset, sig);
  sigprocmask(SIG_UNBLOCK, &newset, NULL);

  VMError err(t, sig, pc, info, ucVoid);
  err.report_and_die();

  ShouldNotReachHere();
  return false;
}

void os::Bsd::init_thread_fpu_state(void) {
}

bool os::is_allocatable(size_t bytes) {
  return true;
}

////////////////////////////////////////////////////////////////////////////////
// thread stack

size_t os::Bsd::min_stack_allowed = 72 * K;

bool os::Bsd::supports_variable_stack_size() {  return true; }

// return default stack size for thr_type
size_t os::Bsd::default_stack_size(os::ThreadType thr_type) {
  // default stack size (compiler thread needs larger stack)
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
  return s;
}

// Java thread:
//
//   Low memory addresses
//    +------------------------+
//    |                        |\  Java thread created by VM does not have glibc
//    |    glibc guard page    | - guard, attached Java thread usually has
//    |                        |/  1 glibc guard page.
// P1 +------------------------+ Thread::stack_base() - Thread::stack_size()
//    |                        |\
//    |  HotSpot Guard Pages   | - red, yellow and reserved pages
//    |                        |/
//    +------------------------+ JavaThread::stack_reserved_zone_base()
//    |                        |\
//    |      Normal Stack      | -
//    |                        |/
// P2 +------------------------+ Thread::stack_base()
//
// Non-Java thread:
//
//   Low memory addresses
//    +------------------------+
//    |                        |\
//    |  glibc guard page      | - usually 1 page
//    |                        |/
// P1 +------------------------+ Thread::stack_base() - Thread::stack_size()
//    |                        |\
//    |      Normal Stack      | -
//    |                        |/
// P2 +------------------------+ Thread::stack_base()
//
// ** P1 (aka bottom) and size ( P2 = P1 - size) are the address and stack size returned from
//    pthread_attr_getstack()

static void current_stack_region(address * bottom, size_t * size) {
#ifdef __APPLE__
  pthread_t self = pthread_self();
  void *stacktop = pthread_get_stackaddr_np(self);
  *size = pthread_get_stacksize_np(self);
  // workaround for OS X 10.9.0 (Mavericks)
  // pthread_get_stacksize_np returns 128 pages even though the actual size is 2048 pages
  if (pthread_main_np() == 1) {
    // At least on Mac OS 10.12 we have observed stack sizes not aligned
    // to pages boundaries. This can be provoked by e.g. setrlimit() (ulimit -s xxxx in the
    // shell). Apparently Mac OS actually rounds upwards to next multiple of page size,
    // however, we round downwards here to be on the safe side.
    *size = align_down(*size, getpagesize());

    if ((*size) < (DEFAULT_MAIN_THREAD_STACK_PAGES * (size_t)getpagesize())) {
      char kern_osrelease[256];
      size_t kern_osrelease_size = sizeof(kern_osrelease);
      int ret = sysctlbyname("kern.osrelease", kern_osrelease, &kern_osrelease_size, NULL, 0);
      if (ret == 0) {
        // get the major number, atoi will ignore the minor amd micro portions of the version string
        if (atoi(kern_osrelease) >= OS_X_10_9_0_KERNEL_MAJOR_VERSION) {
          *size = (DEFAULT_MAIN_THREAD_STACK_PAGES*getpagesize());
        }
      }
    }
  }
  *bottom = (address) stacktop - *size;
#elif defined(__OpenBSD__)
  stack_t ss;
  int rslt = pthread_stackseg_np(pthread_self(), &ss);

  if (rslt != 0)
    fatal("pthread_stackseg_np failed with error = %d", rslt);

  *bottom = (address)((char *)ss.ss_sp - ss.ss_size);
  *size   = ss.ss_size;
#else
  pthread_attr_t attr;

  int rslt = pthread_attr_init(&attr);

  // JVM needs to know exact stack location, abort if it fails
  if (rslt != 0)
    fatal("pthread_attr_init failed with error = %d", rslt);

  rslt = pthread_attr_get_np(pthread_self(), &attr);

  if (rslt != 0)
    fatal("pthread_attr_get_np failed with error = %d", rslt);

  if (pthread_attr_getstackaddr(&attr, (void **)bottom) != 0 ||
    pthread_attr_getstacksize(&attr, size) != 0) {
    fatal("Can not locate current stack attributes!");
  }

  pthread_attr_destroy(&attr);
#endif
  assert(os::current_stack_pointer() >= *bottom &&
         os::current_stack_pointer() < *bottom + *size, "just checking");
}

address os::current_stack_base() {
  address bottom;
  size_t size;
  current_stack_region(&bottom, &size);
  return (bottom + size);
}

size_t os::current_stack_size() {
  // stack size includes normal stack and HotSpot guard pages
  address bottom;
  size_t size;
  current_stack_region(&bottom, &size);
  return size;
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

void os::print_context(outputStream *st, void *context) {
  if (context == NULL) return;

  ucontext_t *uc = (ucontext_t*)context;
  st->print_cr("Registers:");
  st->print( " x0=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 0]);
  st->print("  x1=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 1]);
  st->print("  x2=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 2]);
  st->print("  x3=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 3]);
  st->cr();
  st->print( " x4=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 4]);
  st->print("  x5=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 5]);
  st->print("  x6=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 6]);
  st->print("  x7=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 7]);
  st->cr();
  st->print( " x8=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 8]);
  st->print("  x9=" INTPTR_FORMAT, (intptr_t)uc->context_x[ 9]);
  st->print(" x10=" INTPTR_FORMAT, (intptr_t)uc->context_x[10]);
  st->print(" x11=" INTPTR_FORMAT, (intptr_t)uc->context_x[11]);
  st->cr();
  st->print( "x12=" INTPTR_FORMAT, (intptr_t)uc->context_x[12]);
  st->print(" x13=" INTPTR_FORMAT, (intptr_t)uc->context_x[13]);
  st->print(" x14=" INTPTR_FORMAT, (intptr_t)uc->context_x[14]);
  st->print(" x15=" INTPTR_FORMAT, (intptr_t)uc->context_x[15]);
  st->cr();
  st->print( "x16=" INTPTR_FORMAT, (intptr_t)uc->context_x[16]);
  st->print(" x17=" INTPTR_FORMAT, (intptr_t)uc->context_x[17]);
  st->print(" x18=" INTPTR_FORMAT, (intptr_t)uc->context_x[18]);
  st->print(" x19=" INTPTR_FORMAT, (intptr_t)uc->context_x[19]);
  st->cr();
  st->print( "x20=" INTPTR_FORMAT, (intptr_t)uc->context_x[20]);
  st->print(" x21=" INTPTR_FORMAT, (intptr_t)uc->context_x[21]);
  st->print(" x22=" INTPTR_FORMAT, (intptr_t)uc->context_x[22]);
  st->print(" x23=" INTPTR_FORMAT, (intptr_t)uc->context_x[23]);
  st->cr();
  st->print( "x24=" INTPTR_FORMAT, (intptr_t)uc->context_x[24]);
  st->print(" x25=" INTPTR_FORMAT, (intptr_t)uc->context_x[25]);
  st->print(" x26=" INTPTR_FORMAT, (intptr_t)uc->context_x[26]);
  st->print(" x27=" INTPTR_FORMAT, (intptr_t)uc->context_x[27]);
  st->cr();
  st->print( "x28=" INTPTR_FORMAT, (intptr_t)uc->context_x[28]);
  st->print("  fp=" INTPTR_FORMAT, (intptr_t)uc->context_fp);
  st->print("  lr=" INTPTR_FORMAT, (intptr_t)uc->context_lr);
  st->print("  sp=" INTPTR_FORMAT, (intptr_t)uc->context_sp);
  st->cr();
  st->print(  "pc=" INTPTR_FORMAT,  (intptr_t)uc->context_pc);
  st->print(" cpsr=" INTPTR_FORMAT, (intptr_t)uc->context_cpsr);
  st->cr();

  intptr_t *sp = (intptr_t *)os::Bsd::ucontext_get_sp(uc);
  st->print_cr("Top of Stack: (sp=" INTPTR_FORMAT ")", (intptr_t)sp);
  print_hex_dump(st, (address)sp, (address)(sp + 8*sizeof(intptr_t)), sizeof(intptr_t));
  st->cr();

  // Note: it may be unsafe to inspect memory near pc. For example, pc may
  // point to garbage if entry point in an nmethod is corrupted. Leave
  // this at the end, and hope for the best.
  address pc = os::Bsd::ucontext_get_pc(uc);
  st->print_cr("Instructions: (pc=" PTR_FORMAT ")", pc);
  print_hex_dump(st, pc - 32, pc + 32, sizeof(char));
  st->cr();
}

void os::print_register_info(outputStream *st, void *context) {
  if (context == NULL) return;

  ucontext_t *uc = (ucontext_t*)context;

  st->print_cr("Register to memory mapping:");
  st->cr();

  // this is horrendously verbose but the layout of the registers in the
  // context does not match how we defined our abstract Register set, so
  // we can't just iterate through the gregs area

  // this is only for the "general purpose" registers

  st->print(" x0="); print_location(st, uc->context_x[ 0]);
  st->print(" x1="); print_location(st, uc->context_x[ 1]);
  st->print(" x2="); print_location(st, uc->context_x[ 2]);
  st->print(" x3="); print_location(st, uc->context_x[ 3]);
  st->print(" x4="); print_location(st, uc->context_x[ 4]);
  st->print(" x5="); print_location(st, uc->context_x[ 5]);
  st->print(" x6="); print_location(st, uc->context_x[ 6]);
  st->print(" x7="); print_location(st, uc->context_x[ 7]);
  st->print(" x8="); print_location(st, uc->context_x[ 8]);
  st->print(" x9="); print_location(st, uc->context_x[ 9]);
  st->print("x10="); print_location(st, uc->context_x[10]);
  st->print("x11="); print_location(st, uc->context_x[11]);
  st->print("x12="); print_location(st, uc->context_x[12]);
  st->print("x13="); print_location(st, uc->context_x[13]);
  st->print("x14="); print_location(st, uc->context_x[14]);
  st->print("x15="); print_location(st, uc->context_x[15]);
  st->print("x16="); print_location(st, uc->context_x[16]);
  st->print("x17="); print_location(st, uc->context_x[17]);
  st->print("x18="); print_location(st, uc->context_x[18]);
  st->print("x19="); print_location(st, uc->context_x[19]);
  st->print("x20="); print_location(st, uc->context_x[20]);
  st->print("x21="); print_location(st, uc->context_x[21]);
  st->print("x22="); print_location(st, uc->context_x[22]);
  st->print("x23="); print_location(st, uc->context_x[23]);
  st->print("x24="); print_location(st, uc->context_x[24]);
  st->print("x25="); print_location(st, uc->context_x[25]);
  st->print("x26="); print_location(st, uc->context_x[26]);
  st->print("x27="); print_location(st, uc->context_x[27]);
  st->print("x28="); print_location(st, uc->context_x[28]);

  st->cr();
}

void os::setup_fpu() {
}

#ifndef PRODUCT
void os::verify_stack_alignment() {
  assert(((intptr_t)os::current_stack_pointer() & (StackAlignmentInBytes-1)) == 0, "incorrect stack alignment");
}
#endif

extern "C" {
  int SpinPause() {
    return 0;
  }

  void _Copy_conjoint_jshorts_atomic(jshort* from, jshort* to, size_t count) {
    if (from > to) {
      jshort *end = from + count;
      while (from < end)
        *(to++) = *(from++);
    }
    else if (from < to) {
      jshort *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end)
        *(to--) = *(from--);
    }
  }
  void _Copy_conjoint_jints_atomic(jint* from, jint* to, size_t count) {
    if (from > to) {
      jint *end = from + count;
      while (from < end)
        *(to++) = *(from++);
    }
    else if (from < to) {
      jint *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end)
        *(to--) = *(from--);
    }
  }
  void _Copy_conjoint_jlongs_atomic(jlong* from, jlong* to, size_t count) {
    if (from > to) {
      jlong *end = from + count;
      while (from < end)
        os::atomic_copy64(from++, to++);
    }
    else if (from < to) {
      jlong *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end)
        os::atomic_copy64(from--, to--);
    }
  }

  void _Copy_arrayof_conjoint_bytes(HeapWord* from,
                                    HeapWord* to,
                                    size_t    count) {
    memmove(to, from, count);
  }
  void _Copy_arrayof_conjoint_jshorts(HeapWord* from,
                                      HeapWord* to,
                                      size_t    count) {
    memmove(to, from, count * 2);
  }
  void _Copy_arrayof_conjoint_jints(HeapWord* from,
                                    HeapWord* to,
                                    size_t    count) {
    memmove(to, from, count * 4);
  }
  void _Copy_arrayof_conjoint_jlongs(HeapWord* from,
                                     HeapWord* to,
                                     size_t    count) {
    memmove(to, from, count * 8);
  }
};
