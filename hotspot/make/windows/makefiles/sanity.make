#
# Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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

!include local.make

all: checkCL checkLink

checkCL:
	@ if "$(MSC_VER)" NEQ "1310" if "$(MSC_VER)" NEQ "1399" if "$(MSC_VER)" NEQ "1400" if "$(MSC_VER)" NEQ "1500" if "$(MSC_VER)" NEQ "1600" if "$(MSC_VER)" NEQ "1700" \
	if "$(MSC_VER)" NEQ "1800" \
	if "$(MSC_VER)" NEQ "1900" \
	if "$(MSC_VER)" NEQ "1912" \
	echo *** WARNING *** unrecognized cl.exe version $(MSC_VER) ($(RAW_MSC_VER)).  Use FORCE_MSC_VER to override automatic detection.

checkLink:
	@ if "$(LD_VER)" NEQ "710" if "$(LD_VER)" NEQ "800" if "$(LD_VER)" NEQ "900" if "$(LD_VER)" NEQ "1000" if "$(LD_VER)" NEQ "1100" \
	if "$(LD_VER)" NEQ "1200" \
	if "$(LD_VER)" NEQ "1300" \
	if "$(LD_VER)" NEQ "1400" \
	if "$(LD_VER)" NEQ "1412" \
	echo *** WARNING *** unrecognized link.exe version $(LD_VER) ($(RAW_LD_VER)).  Use FORCE_LD_VER to override automatic detection.
