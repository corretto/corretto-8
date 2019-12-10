#
# Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

# If FDLIBM_CFLAGS is non-empty it holds CFLAGS needed to be passed to
# the compiler so as to be able to produce optimized objects
# without losing precision.
ifneq ($(FDLIBM_CFLAGS),)
  OPT_CFLAGS/sharedRuntimeTrig.o = $(OPT_CFLAGS/SPEED) $(FDLIBM_CFLAGS)
  OPT_CFLAGS/sharedRuntimeTrans.o = $(OPT_CFLAGS/SPEED) $(FDLIBM_CFLAGS)
else
  OPT_CFLAGS/sharedRuntimeTrig.o = $(OPT_CFLAGS/NOOPT)
  OPT_CFLAGS/sharedRuntimeTrans.o = $(OPT_CFLAGS/NOOPT)
endif
# Must also specify if CPU is little endian
CFLAGS += -DVM_LITTLE_ENDIAN

# CFLAGS += -D_LP64=1

OPT_CFLAGS/compactingPermGenGen.o = -O1
