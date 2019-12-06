/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _LIBPROC_H_
#define _LIBPROC_H_

#include <jni.h>
#include <unistd.h>
#include <stdint.h>
#include "proc_service.h"

#ifdef ALT_SASRCDIR
#include "libproc_md.h"
#endif

#include <sys/ptrace.h>

#if defined(aarch64)
#include "asm/ptrace.h"
#endif

/************************************************************************************

0. This is very minimal subset of Solaris libproc just enough for current application.
Please note that the bulk of the functionality is from proc_service interface. This
adds Pgrab__ and some missing stuff. We hide the difference b/w live process and core
file by this interface.

1. pthread_id unique in both NPTL & LinuxThreads. We store this in
OSThread::_pthread_id in JVM code.

2. All threads see the same pid when they call getpid() under NPTL.
Threads receive different pid under LinuxThreads. We used to save the result of
::getpid() call in OSThread::_thread_id. This way uniqueness of OSThread::_thread_id
was lost under NPTL. Now, we store the result of ::gettid() call in
OSThread::_thread_id. Because gettid returns actual pid of thread (lwp id), this is
unique again. We therefore use OSThread::_thread_id as unique identifier.

3. There is a unique LWP id under both thread libraries. libthread_db  maps pthread_id
to its underlying lwp_id under both the thread libraries. thread_info.lwp_id stores
lwp_id of the thread. The lwp id is nothing but the actual pid of clone'd processes. But
unfortunately libthread_db does not work very well for core dumps. So, we get pthread_id
only for processes. For core dumps, we don't use libthread_db at all (like gdb).

4. ptrace operates on this LWP id under both the thread libraries. When we say 'pid' for
ptrace call, we refer to lwp_id of the thread.

5. for core file, we parse ELF files and read data from them. For processes we  use
combination of ptrace and /proc calls.

*************************************************************************************/


#if defined(sparc) || defined(sparcv9) || defined(ppc64)
#include <asm/ptrace.h>
#define user_regs_struct  pt_regs
#endif
#if defined(aarch64)
#define user_regs_struct user_pt_regs
#endif

// This C bool type must be int for compatibility with Linux calls and
// it would be a mistake to equivalence it to C++ bool on many platforms

typedef int bool;
#define true  1
#define false 0

struct ps_prochandle;

// attach to a process
struct ps_prochandle* Pgrab(pid_t pid, char* err_buf, size_t err_buf_len);

// attach to a core dump
struct ps_prochandle* Pgrab_core(const char* execfile, const char* corefile);

// release a process or core
void Prelease(struct ps_prochandle* ph);

// functions not directly available in Solaris libproc

// initialize libproc (call this only once per app)
// pass true to make library verbose
bool init_libproc(bool verbose);

// get number of threads
int get_num_threads(struct ps_prochandle* ph);

// get lwp_id of n'th thread
lwpid_t get_lwp_id(struct ps_prochandle* ph, int index);

// get regs for a given lwp
bool get_lwp_regs(struct ps_prochandle* ph, lwpid_t lid, struct user_regs_struct* regs);

// get number of shared objects
int get_num_libs(struct ps_prochandle* ph);

// get name of n'th lib
const char* get_lib_name(struct ps_prochandle* ph, int index);

// get base of lib
uintptr_t get_lib_base(struct ps_prochandle* ph, int index);

// returns true if given library is found in lib list
bool find_lib(struct ps_prochandle* ph, const char *lib_name);

// symbol lookup
uintptr_t lookup_symbol(struct ps_prochandle* ph,  const char* object_name,
                       const char* sym_name);

// address->nearest symbol lookup. return NULL for no symbol
const char* symbol_for_pc(struct ps_prochandle* ph, uintptr_t addr, uintptr_t* poffset);

struct ps_prochandle* get_proc_handle(JNIEnv* env, jobject this_obj);

void throw_new_debugger_exception(JNIEnv* env, const char* errMsg);

#endif //__LIBPROC_H_
