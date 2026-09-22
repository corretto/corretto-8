#
# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

ifneq "x$(ALT_BOOTDIR)" "x"
	BOOTDIR := $(ALT_BOOTDIR)
endif

ifeq "x$(BOOTDIR)" "x"
	JDK_HOME := $(shell dirname $(shell which java))/..
else
	JDK_HOME := $(BOOTDIR)
endif

SRC_DIR = src
BUILD_DIR = build
OUTPUT_DIR = $(BUILD_DIR)/classes
TESTLIBRARY_DIR = ../../../../test/lib

JAVAC = $(JDK_HOME)/bin/javac
JAR = $(JDK_HOME)/bin/jar

SRC_FILES = $(shell find $(SRC_DIR) -name '*.java')
WB_SRC_FILES = $(shell find $(TESTLIBRARY_DIR)/sun/hotspot -name '*.java')

MAIN_CLASS = sun.hotspot.tools.ctw.CompileTheWorld

.PHONY: clean cleantmp

all: ctw.jar cleantmp

clean: cleantmp
	@rm -rf ctw.jar wb.jar

cleantmp:
	@rm -rf filelist wb_filelist manifest.mf
	@rm -rf $(BUILD_DIR)

ctw.jar: filelist wb.jar manifest.mf
	@mkdir -p $(OUTPUT_DIR)
	$(JAVAC) -sourcepath $(SRC_DIR) -d $(OUTPUT_DIR) -cp wb.jar @filelist
	$(JAR) cfm ctw.jar manifest.mf -C $(OUTPUT_DIR) .

wb.jar: wb_filelist
	@mkdir -p $(OUTPUT_DIR)
	$(JAVAC)  -sourcepath $(TESTLIBRARY_DIR) \
		-d $(OUTPUT_DIR) \
		-cp $(OUTPUT_DIR) \
		@wb_filelist
	$(JAR) cf $@ -C $(OUTPUT_DIR) .

wb_filelist: $(WB_SRC_FILES)
	@rm -f $@
	@echo $(WB_SRC_FILES) > $@

filelist: $(SRC_FILES)
	@rm -f $@
	@echo $(SRC_FILES) > $@

manifest.mf:
	@echo "Main-Class: ${MAIN_CLASS}" > manifest.mf
