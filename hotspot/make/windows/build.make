#
# Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
# Copyright 2019 Red Hat, Inc.
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

# Note: this makefile is invoked both from build.bat and from the J2SE
# control workspace in exactly the same manner; the required
# environment variables (Variant, WorkSpace, BootStrapDir, BuildUser, HOTSPOT_BUILD_VERSION)
# are passed in as command line arguments.

# Note: Running nmake or build.bat from the Windows command shell requires
# that "sh" be accessible on the PATH. An MKS install does this.

# SA components are built if BUILD_WIN_SA=1 is specified.
# See notes in README. This produces files:
#  1. sa-jdi.jar       - This is built before building jvm.dll
#  2. sawindbg.dll     - Native library for SA - This is built after jvm.dll
#                      - Also, .lib, .map, .pdb.
#
# Please refer to ./makefiles/sa.make

# If we haven't set an ARCH yet use x86
# create.bat and build.bat will set it, if used.
!ifndef ARCH
ARCH=x86
!endif


# Must be one of these values (if value comes in from env, can't trust it)
!if "$(ARCH)" != "x86"
!if "$(ARCH)" != "ia64"
ARCH=x86
!endif
!endif

# At this point we should be certain that ARCH has a definition
# now determine the BUILDARCH
#

# the default BUILDARCH
BUILDARCH=i486

# Allow control workspace to force Itanium or AMD64 builds with LP64
ARCH_TEXT=
!ifdef LP64
!if "$(LP64)" == "1"
ARCH_TEXT=64-Bit
!if "$(ARCH)" == "x86"
BUILDARCH=amd64
!else
BUILDARCH=ia64
!endif
!endif
!endif

!if "$(BUILDARCH)" != "ia64"
!ifndef CC_INTERP
!ifndef FORCE_TIERED
FORCE_TIERED=1
!endif
!endif
!endif

!if "$(BUILDARCH)" == "amd64"
Platform_arch=x86
Platform_arch_model=x86_64
!endif
!if "$(BUILDARCH)" == "i486"
Platform_arch=x86
Platform_arch_model=x86_32
!endif

# Supply these from the command line or the environment
#  It doesn't make sense to default this one
Variant=
#  It doesn't make sense to default this one
WorkSpace=

variantDir = windows_$(BUILDARCH)_$(Variant)

realVariant=$(Variant)
VARIANT_TEXT=Core
!if "$(Variant)" == "compiler1"
VARIANT_TEXT=Client
!elseif "$(Variant)" == "compiler2"
!if "$(FORCE_TIERED)" == "1"
VARIANT_TEXT=Server
realVariant=tiered
!else
VARIANT_TEXT=Server
!endif
!elseif "$(Variant)" == "tiered"
VARIANT_TEXT=Tiered
!endif

#########################################################################
# Parameters for VERSIONINFO resource for jvm.dll.
# These can be overridden via the nmake.exe command line.
# They are overridden by RE during the control builds.
#
!include "$(WorkSpace)/make/hotspot_version"

# Define HOTSPOT_VM_DISTRO based on settings in make/openjdk_distro
# or make/hotspot_distro.
!ifndef HOTSPOT_VM_DISTRO
!if exists($(WorkSpace)\src\closed)

# if the build is for JDK6 or earlier version, it should include jdk6_hotspot_distro,
# instead of hotspot_distro.
JDK6_OR_EARLIER=0
!if "$(JDK_MAJOR_VERSION)" != "" && "$(JDK_MINOR_VERSION)" != "" && "$(JDK_MICRO_VERSION)" != ""
!if $(JDK_MAJOR_VERSION) == 1 && $(JDK_MINOR_VERSION) < 7
JDK6_OR_EARLIER=1
!endif
!else
!if $(JDK_MAJOR_VER) == 1 && $(JDK_MINOR_VER) < 7
JDK6_OR_EARLIER=1
!endif
!endif

!if $(JDK6_OR_EARLIER) == 1
!include $(WorkSpace)\make\jdk6_hotspot_distro
!else
!include $(WorkSpace)\make\hotspot_distro
!endif
!else
!include $(WorkSpace)\make\openjdk_distro
!endif
!endif

# Following the Web Start / Plugin model here....
# We can have update versions like "01a", but Windows requires
# we use only integers in the file version field.  So:
# JDK_UPDATE_VER = JDK_UPDATE_VERSION * 10 + EXCEPTION_VERSION
#
JDK_UPDATE_VER=0
JDK_BUILD_NUMBER=0

HS_FILEDESC=$(HOTSPOT_VM_DISTRO) $(ARCH_TEXT) $(VARIANT_TEXT) VM

# JDK ProductVersion:
# 1.5.0_<wx>-b<yz> will have DLL version 5.0.wx*10.yz
# Thus, 1.5.0_10-b04  will be 5.0.100.4
#       1.6.0-b01     will be 6.0.0.1
#       1.6.0_01a-b02 will be 6.0.11.2
#
# JDK_* variables are defined in make/hotspot_version or on command line
#
JDK_VER=$(JDK_MINOR_VER),$(JDK_MICRO_VER),$(JDK_UPDATE_VER),$(JDK_BUILD_NUMBER)
JDK_DOTVER=$(JDK_MINOR_VER).$(JDK_MICRO_VER).$(JDK_UPDATE_VER).$(JDK_BUILD_NUMBER)
!if "$(JRE_RELEASE_VERSION)" == ""
JRE_RELEASE_VER=$(JDK_MAJOR_VER).$(JDK_MINOR_VER).$(JDK_MICRO_VER)
!else
JRE_RELEASE_VER=$(JRE_RELEASE_VERSION)
!endif
!if "$(JDK_MKTG_VERSION)" == ""
JDK_MKTG_VERSION=$(JDK_MINOR_VER).$(JDK_MICRO_VER)
!endif

# Hotspot Express VM FileVersion:
# 10.0-b<yz> will have DLL version 10.0.0.yz (need 4 numbers).
#
# HS_* variables are defined in make/hotspot_version
#
HS_VER=$(HS_MAJOR_VER),$(HS_MINOR_VER),0,$(HS_BUILD_NUMBER)
HS_DOTVER=$(HS_MAJOR_VER).$(HS_MINOR_VER).0.$(HS_BUILD_NUMBER)

!if "$(HOTSPOT_RELEASE_VERSION)" == ""
HOTSPOT_RELEASE_VERSION=$(HS_MAJOR_VER).$(HS_MINOR_VER)-b$(HS_BUILD_NUMBER)
!endif

!if "$(HOTSPOT_BUILD_VERSION)" == ""
HS_BUILD_VER=$(HOTSPOT_RELEASE_VERSION)
!else
HS_BUILD_VER=$(HOTSPOT_RELEASE_VERSION)-$(HOTSPOT_BUILD_VERSION)
!endif

# End VERSIONINFO parameters

# if hotspot-only build and/or OPENJDK isn't passed down, need to set OPENJDK
!ifndef OPENJDK
!if !exists($(WorkSpace)\src\closed)
OPENJDK=true
!endif
!endif

# We don't support SA on ia64, and we can't
# build it if we are using a version of Vis Studio
# older than .Net 2003.
# SA_INCLUDE and SA_LIB are hold-overs from a previous
# implementation in which we could build SA using
# Debugging Tools For Windows, in which the .h/.lib files
# and the .dlls are in different places than
# they are for Vis Studio .Net 2003.
# If that code ever needs to be resurrected, these vars
# can be set here.  They are used in makefiles/sa.make.

checkSA::

!if "$(BUILD_WIN_SA)" != "1"
checkSA::
	@echo     Not building SA:  BUILD_WIN_SA != 1

!elseif "$(ARCH)" == "ia64"
BUILD_WIN_SA = 0
checkSA::
	@echo     Not building SA:  ARCH = ia64

!endif  # ! "$(BUILD_WIN_SA)" != "1"

#########################################################################

defaultTarget: product

# The product or release build is an optimized build, and is the default

# note that since all the build targets depend on local.make that BUILDARCH
# and Platform_arch and Platform_arch_model will get set in local.make
# and there is no need to pass them thru here on the command line
#
product release optimized: checks $(variantDir) $(variantDir)\local.make sanity
	cd $(variantDir)
	nmake -nologo -f $(WorkSpace)\make\windows\makefiles\top.make BUILD_FLAVOR=product ARCH=$(ARCH)

# The debug build is an optional build
debug: checks $(variantDir) $(variantDir)\local.make sanity
	cd $(variantDir)
	nmake -nologo -f $(WorkSpace)\make\windows\makefiles\top.make BUILD_FLAVOR=debug ARCH=$(ARCH)
fastdebug: checks $(variantDir) $(variantDir)\local.make sanity
	cd $(variantDir)
	nmake -nologo -f $(WorkSpace)\make\windows\makefiles\top.make BUILD_FLAVOR=fastdebug ARCH=$(ARCH)

# target to create just the directory structure
tree: checks $(variantDir) $(variantDir)\local.make sanity
	mkdir $(variantDir)\product
	mkdir $(variantDir)\debug
	mkdir $(variantDir)\fastdebug

sanity:
	@ echo;
	@ cd $(variantDir)
	@ nmake -nologo -f $(WorkSpace)\make\windows\makefiles\sanity.make
	@ cd ..
	@ echo;

clean: checkVariant
	- rm -r -f $(variantDir)

$(variantDir):
	mkdir $(variantDir)

$(variantDir)\local.make: checks
	@ echo # Generated file					>  $@
	@ echo Variant=$(realVariant)				>> $@
	@ echo WorkSpace=$(WorkSpace)				>> $@
	@ echo BootStrapDir=$(BootStrapDir)			>> $@
	@ if "$(USERNAME)" NEQ "" echo BuildUser=$(USERNAME)	>> $@
	@ echo HS_VER=$(HS_VER)					>> $@
	@ echo HS_DOTVER=$(HS_DOTVER)				>> $@
	@ echo HS_COMPANY=$(COMPANY_NAME)			>> $@
	@ echo HS_FILEDESC=$(HS_FILEDESC)			>> $@
	@ echo HOTSPOT_VM_DISTRO=$(HOTSPOT_VM_DISTRO)		>> $@
	@ echo VENDOR=$(COMPANY_NAME)				>> $@
	@ echo VENDOR_URL=$(VENDOR_URL)				>> $@
	@ echo VENDOR_URL_BUG=$(VENDOR_URL_BUG)			>> $@
	@ echo VENDOR_URL_VM_BUG=$(VENDOR_URL_VM_BUG)		>> $@
	@ if "$(OPENJDK)" NEQ "" echo OPENJDK=$(OPENJDK)	>> $@
	@ echo HS_COPYRIGHT=$(HOTSPOT_VM_COPYRIGHT)		>> $@
	@ echo HS_NAME=$(PRODUCT_NAME) $(JDK_MKTG_VERSION)	>> $@
	@ echo HS_BUILD_VER=$(HS_BUILD_VER)			>> $@
	@ echo BUILD_WIN_SA=$(BUILD_WIN_SA)    			>> $@
	@ echo SA_BUILD_VERSION=$(HS_BUILD_VER)                 >> $@
	@ echo SA_INCLUDE=$(SA_INCLUDE)      			>> $@
	@ echo SA_LIB=$(SA_LIB)         			>> $@
	@ echo JDK_VER=$(JDK_VER)				>> $@
	@ echo JDK_DOTVER=$(JDK_DOTVER)				>> $@
	@ echo JRE_RELEASE_VER=$(JRE_RELEASE_VER)		>> $@
	@ echo BUILDARCH=$(BUILDARCH)         			>> $@
	@ echo Platform_arch=$(Platform_arch)        		>> $@
	@ echo Platform_arch_model=$(Platform_arch_model)	>> $@
	@ echo CXX=$(CXX)					>> $@
	@ echo LD=$(LD)						>> $@
	@ echo MT=$(MT)						>> $@
	@ echo RC=$(RC)						>> $@
	@ echo ENABLE_JFR=$(ENABLE_JFR)				>> $@
	@ sh $(WorkSpace)/make/windows/get_msc_ver.sh		>> $@
	@ if "$(ENABLE_FULL_DEBUG_SYMBOLS)" NEQ "" echo ENABLE_FULL_DEBUG_SYMBOLS=$(ENABLE_FULL_DEBUG_SYMBOLS) >> $@
	@ if "$(ZIP_DEBUGINFO_FILES)" NEQ "" echo ZIP_DEBUGINFO_FILES=$(ZIP_DEBUGINFO_FILES) >> $@
	@ if "$(RM)" NEQ "" echo RM=$(RM)                       >> $@
	@ if "$(ZIPEXE)" NEQ "" echo ZIPEXE=$(ZIPEXE)           >> $@

checks: checkVariant checkWorkSpace checkSA

checkVariant:
	@ if "$(Variant)"=="" echo Need to specify "Variant=[tiered|compiler2|compiler1|core]" && false
	@ if "$(Variant)" NEQ "tiered" if "$(Variant)" NEQ "compiler2" if "$(Variant)" NEQ "compiler1" if "$(Variant)" NEQ "core" \
          echo Need to specify "Variant=[tiered|compiler2|compiler1|core]" && false

checkWorkSpace:
	@ if "$(WorkSpace)"=="" echo Need to specify "WorkSpace=..." && false

checkBuildID:
	@ if "$(BuildID)"=="" echo Need to specify "BuildID=..." && false
