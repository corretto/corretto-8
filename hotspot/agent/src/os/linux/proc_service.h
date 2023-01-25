/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _PROC_SERVICE_H_
#define _PROC_SERVICE_H_

#include <stdio.h>
#include <thread_db.h>

// Linux does not have the proc service library, though it does provide the
// thread_db library which can be used to manipulate threads without having
// to know the details of LinuxThreads or NPTL

// copied from Solaris "proc_service.h"
typedef enum {
        PS_OK,          /* generic "call succeeded" */
        PS_ERR,         /* generic error */
        PS_BADPID,      /* bad process handle */
        PS_BADLID,      /* bad lwp identifier */
        PS_BADADDR,     /* bad address */
        PS_NOSYM,       /* p_lookup() could not find given symbol */
        PS_NOFREGS      /* FPU register set not available for given lwp */
} ps_err_e;

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

#endif /* _PROC_SERVICE_H_ */
