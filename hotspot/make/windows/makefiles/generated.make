#
# Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

!include ../local.make
!include $(WorkSpace)/make/windows/makefiles/projectcreator.make
!include local.make

# Pick up rules for building JVMTI (JSR-163)
JvmtiOutDir=jvmtifiles
!include $(WorkSpace)/make/windows/makefiles/jvmti.make

# Pick up rules for building JFR
JfrOutDir=jfrfiles
!include $(WorkSpace)/make/windows/makefiles/jfr.make

# Pick up rules for building SA
!include $(WorkSpace)/make/windows/makefiles/sa.make

AdlcOutDir=adfiles

!if ("$(Variant)" == "compiler2") || ("$(Variant)" == "tiered")
default:: $(AdlcOutDir)/ad_$(Platform_arch_model).cpp $(AdlcOutDir)/dfa_$(Platform_arch_model).cpp $(JvmtiGeneratedFiles) $(JfrGeneratedFiles) buildobjfiles
!else
default:: $(JvmtiGeneratedFiles) $(JfrGeneratedFiles) buildobjfiles
!endif

buildobjfiles:
	@ sh $(WorkSpace)/make/windows/create_obj_files.sh $(Variant) $(Platform_arch) $(Platform_arch_model) $(WorkSpace) .	> objfiles.make

classes/ProjectCreator.class: $(ProjectCreatorSources)
	if exist classes rmdir /s /q classes
	mkdir classes
	$(COMPILE_JAVAC) -classpath $(WorkSpace)\src\share\tools\ProjectCreator -d classes $(ProjectCreatorSources)

!if ("$(Variant)" == "compiler2") || ("$(Variant)" == "tiered")

!include $(WorkSpace)/make/windows/makefiles/compile.make
!include $(WorkSpace)/make/windows/makefiles/adlc.make

!endif

!include $(WorkSpace)/make/windows/makefiles/shared.make
