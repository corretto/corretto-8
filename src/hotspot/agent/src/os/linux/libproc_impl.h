/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _LIBPROC_IMPL_H_
#define _LIBPROC_IMPL_H_

#include <unistd.h>
#include <limits.h>

struct ps_prochandle;

#include "libproc.h"
#include "symtab.h"

// data structures in this file mimic those of Solaris 8.0 - libproc's Pcontrol.h

#define BUF_SIZE     (PATH_MAX + NAME_MAX + 1)

// list of shared objects
typedef struct lib_info {
  char             name[BUF_SIZE];
  uintptr_t        base;
  struct symtab*   symtab;
  int              fd;        // file descriptor for lib
  struct lib_info* next;
} lib_info;

// list of threads
typedef struct thread_info {
   lwpid_t                  lwp_id;
   pthread_t                pthread_id; // not used cores, always -1
   struct user_regs_struct  regs;       // not for process, core uses for caching regset
   struct thread_info*      next;
} thread_info;

// list of virtual memory maps
typedef struct map_info {
   int              fd;       // file descriptor
   off_t            offset;   // file offset of this mapping
   uintptr_t        vaddr;    // starting virtual address
   size_t           memsz;    // size of the mapping
   struct map_info* next;
} map_info;

// vtable for ps_prochandle
typedef struct ps_prochandle_ops {
   // "derived class" clean-up
   void (*release)(struct ps_prochandle* ph);
   // read from debuggee
   bool (*p_pread)(struct ps_prochandle *ph,
            uintptr_t addr, char *buf, size_t size);
   // write into debuggee
   bool (*p_pwrite)(struct ps_prochandle *ph,
            uintptr_t addr, const char *buf , size_t size);
   // get integer regset of a thread
   bool (*get_lwp_regs)(struct ps_prochandle* ph, lwpid_t lwp_id, struct user_regs_struct* regs);
} ps_prochandle_ops;

// the ps_prochandle

struct core_data {
   int                core_fd;   // file descriptor of core file
   int                exec_fd;   // file descriptor of exec file
   int                interp_fd; // file descriptor of interpreter (ld-linux.so.2)
   // part of the class sharing workaround
   int                classes_jsa_fd; // file descriptor of class share archive
   uintptr_t          dynamic_addr;  // address of dynamic section of a.out
   uintptr_t          ld_base_addr;  // base address of ld.so
   size_t             num_maps;  // number of maps.
   map_info*          maps;      // maps in a linked list
   // part of the class sharing workaround
   map_info*          class_share_maps;// class share maps in a linked list
   map_info**         map_array; // sorted (by vaddr) array of map_info pointers
};

struct ps_prochandle {
   ps_prochandle_ops* ops;       // vtable ptr
   pid_t              pid;
   int                num_libs;
   lib_info*          libs;      // head of lib list
   lib_info*          lib_tail;  // tail of lib list - to append at the end
   int                num_threads;
   thread_info*       threads;   // head of thread list
   struct core_data*  core;      // data only used for core dumps, NULL for process
};

int pathmap_open(const char* name);

void print_debug(const char* format,...);
void print_error(const char* format,...);
bool is_debug();

typedef bool (*thread_info_callback)(struct ps_prochandle* ph, pthread_t pid, lwpid_t lwpid);

// reads thread info using libthread_db and calls above callback for each thread
bool read_thread_info(struct ps_prochandle* ph, thread_info_callback cb);

// deletes a thread from the thread list
void delete_thread_info(struct ps_prochandle* ph, thread_info* thr);

// adds a new shared object to lib list, returns NULL on failure
lib_info* add_lib_info(struct ps_prochandle* ph, const char* libname, uintptr_t base);

// adds a new shared object to lib list, supply open lib file descriptor as well
lib_info* add_lib_info_fd(struct ps_prochandle* ph, const char* libname, int fd,
                          uintptr_t base);

// adds a new thread to threads list, returns NULL on failure
thread_info* add_thread_info(struct ps_prochandle* ph, pthread_t pthread_id, lwpid_t lwp_id);

// a test for ELF signature without using libelf
bool is_elf_file(int fd);

// ps_getpid() is only defined on Linux to return a thread's process ID
pid_t ps_getpid(struct ps_prochandle *ph);

// ps_pglobal_lookup() looks up the symbol sym_name in the symbol table
// of the load object object_name in the target process identified by ph.
// It returns the symbol's value as an address in the target process in
// *sym_addr.

ps_err_e ps_pglobal_lookup(struct ps_prochandle *ph, const char *object_name,
                    const char *sym_name, psaddr_t *sym_addr);
// read "size" bytes of data from debuggee at address "addr"
ps_err_e ps_pdread(struct ps_prochandle *ph, psaddr_t  addr,
                   void *buf, size_t size);

// write "size" bytes of data to debuggee at address "addr"
ps_err_e ps_pdwrite(struct ps_prochandle *ph, psaddr_t addr,
                    const void *buf, size_t size);

ps_err_e ps_lsetfpregs(struct ps_prochandle *ph, lwpid_t lid, const prfpregset_t *fpregs);

ps_err_e ps_lsetregs(struct ps_prochandle *ph, lwpid_t lid, const prgregset_t gregset);

ps_err_e  ps_lgetfpregs(struct  ps_prochandle  *ph,  lwpid_t lid, prfpregset_t *fpregs);

ps_err_e ps_lgetregs(struct ps_prochandle *ph, lwpid_t lid, prgregset_t gregset);

// new libthread_db of NPTL seem to require this symbol
ps_err_e ps_get_thread_area();
#endif //_LIBPROC_IMPL_H_
