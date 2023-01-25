#
# Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
#

# Sets make macros for making debug version of VM

# Compiler specific DEBUG_CFLAGS are passed in from gcc.make, sparcWorks.make
# They may also specify FASTDEBUG_CFLAGS, but it defaults to DEBUG_CFLAGS.

FASTDEBUG_CFLAGS$(FASTDEBUG_CFLAGS) = $(DEBUG_CFLAGS)

# Compiler specific OPT_CFLAGS are passed in from gcc.make, sparcWorks.make
OPT_CFLAGS/DEFAULT= $(OPT_CFLAGS)
OPT_CFLAGS/BYFILE = $(OPT_CFLAGS/$@)$(OPT_CFLAGS/DEFAULT$(OPT_CFLAGS/$@))

ifeq ("${Platform_compiler}", "sparcWorks")
OPT_CFLAGS/SLOWER = -xO2

ifeq ($(COMPILER_REV_NUMERIC), 510)
# Avoid apparent crash because of corrupted methodHandle in a tail call
OPT_CFLAGS/simpleThresholdPolicy.o = $(OPT_CFLAGS/DEFAULT) $(OPT_CCFLAGS/NO_TAIL_CALL_OPT)
# CC 5.10 has bug XXXXX with -xO4
OPT_CFLAGS/jvmtiClassFileReconstituter.o = $(OPT_CFLAGS/SLOWER)
endif # COMPILER_REV_NUMERIC == 510

ifeq ($(COMPILER_REV_NUMERIC), 509)
# To avoid jvm98 crash
OPT_CFLAGS/instanceKlass.o = $(OPT_CFLAGS/SLOWER)
endif # COMPILER_NUMERIC_REV == 509

ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 509), 1)
# dtrace cannot handle tail call optimization (6672627, 6693876)
OPT_CFLAGS/jni.o = $(OPT_CFLAGS/DEFAULT) $(OPT_CCFLAGS/NO_TAIL_CALL_OPT)
# this hangs in iropt now (7113504)
OPT_CFLAGS/compileBroker.o = $(OPT_CFLAGS/SLOWER)
endif # COMPILER_NUMERIC_REV >= 509

ifeq ($(COMPILER_REV_NUMERIC), 505)
# CC 5.5 has bug 4908364 with -xO4  (Fixed in 5.6)
OPT_CFLAGS/library_call.o = $(OPT_CFLAGS/SLOWER)
endif # COMPILER_REV_NUMERIC == 505

ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \<= 504), 1)
# Compilation of *_<arch>.cpp can take an hour or more at O3.  Use O2
# See comments at top of sparc.make.
OPT_CFLAGS/ad_$(Platform_arch_model).o = $(OPT_CFLAGS/SLOWER)
OPT_CFLAGS/dfa_$(Platform_arch_model).o = $(OPT_CFLAGS/SLOWER)
endif # COMPILER_REV_NUMERIC <= 504

ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \< 505), 1)
# Same problem with Solaris/x86 compiler (both 5.0 and 5.2) on ad_x86_{32,64}.cpp.
# CC build time is also too long for ad_$(Platform_arch_model)_{gen,misc}.o
OPT_CFLAGS/ad_$(Platform_arch_model).o = -c
OPT_CFLAGS/ad_$(Platform_arch_model)_gen.o = -c
OPT_CFLAGS/ad_$(Platform_arch_model)_misc.o = -c
ifeq ($(Platform_arch), x86)
# Same problem for the wrapper roosts: jni.o jvm.o
OPT_CFLAGS/jni.o = -c
OPT_CFLAGS/jvm.o = -c
# Same problem in parse2.o (probably the Big Switch over bytecodes)
OPT_CFLAGS/parse2.o = -c
endif # Platform_arch == x86
endif

# Frame size > 100k  if we allow inlining via -g0!
DEBUG_CFLAGS/bytecodeInterpreter.o = -g
DEBUG_CFLAGS/bytecodeInterpreterWithChecks.o = -g
ifeq ($(Platform_arch), x86)
# ube explodes on x86
OPT_CFLAGS/bytecodeInterpreter.o = -xO1
OPT_CFLAGS/bytecodeInterpreterWithChecks.o =  -xO1
endif # Platform_arch == x86

endif # Platform_compiler == sparcWorks

# Workaround for a bug in dtrace.  If ciEnv::post_compiled_method_load_event()
# is inlined, the resulting dtrace object file needs a reference to this
# function, whose symbol name is too long for dtrace.  So disable inlining
# for this method for now. (fix this when dtrace bug 6258412 is fixed)
OPT_CFLAGS/ciEnv.o = $(OPT_CFLAGS) -xinline=no%__1cFciEnvbFpost_compiled_method_load_event6MpnHnmethod__v_

# (OPT_CFLAGS/SLOWER is also available, to alter compilation of buggy files)

# If you set HOTSPARC_GENERIC=yes, you disable all OPT_CFLAGS settings
CFLAGS$(HOTSPARC_GENERIC) += $(OPT_CFLAGS/BYFILE)

# Set the environment variable HOTSPARC_GENERIC to "true"
# to inhibit the effect of the previous line on CFLAGS.

# The following lines are copied from debug.make, except that we
# consult FASTDEBUG_CFLAGS instead of DEBUG_CFLAGS.
# Compiler specific DEBUG_CFLAGS are passed in from gcc.make, sparcWorks.make
DEBUG_CFLAGS/DEFAULT= $(FASTDEBUG_CFLAGS)
DEBUG_CFLAGS/BYFILE = $(DEBUG_CFLAGS/$@)$(DEBUG_CFLAGS/DEFAULT$(DEBUG_CFLAGS/$@))
CFLAGS += $(DEBUG_CFLAGS/BYFILE)

# Linker mapfiles
MAPFILE = $(GAMMADIR)/make/solaris/makefiles/mapfile-vers \
	  $(GAMMADIR)/make/solaris/makefiles/mapfile-vers-debug

# This mapfile is only needed when compiling with dtrace support,
# and mustn't be otherwise.
MAPFILE_DTRACE = $(GAMMADIR)/make/solaris/makefiles/mapfile-vers-$(TYPE)

VERSION = optimized
SYSDEFS += -DASSERT -DCHECK_UNHANDLED_OOPS
PICFLAGS = DEFAULT
