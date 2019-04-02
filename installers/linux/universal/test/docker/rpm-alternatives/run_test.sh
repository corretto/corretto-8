#!/usr/bin/env bash
#
#Copyright (c) 2019, Amazon.com, Inc. or its affiliates. All Rights Reserved.
#DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
#This code is free software; you can redistribute it and/or modify it
#under the terms of the GNU General Public License version 2 only, as
#published by the Free Software Foundation. Amazon designates this
#particular file as subject to the "Classpath" exception as provided
#by Oracle in the LICENSE file that accompanied this code.
#
#This code is distributed in the hope that it will be useful, but WITHOUT
#ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
#FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
#version 2 for more details (a copy is included in the LICENSE file that
#accompanied this code).
#
#You should have received a copy of the GNU General Public License version
#2 along with this work; if not, write to the Free Software Foundation,
#Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#

set -Eeuo pipefail

#
# Test 1: Ensure that alternatives installs tools.
#
for b in ${jdk_tools}; do
    if [ "/usr/bin/$b" -ef "/usr/lib/jvm/java-1.8.0-amazon-corretto/bin/$b" ]; then
        echo "$b - OK"
    else
        echo "$b - Error. Not on path."
        exit 1
    fi
done

for b in ${jre_tools}; do
    if [ "/usr/bin/$b" -ef "/usr/lib/jvm/java-1.8.0-amazon-corretto/jre/bin/$b" ]; then
        echo "$b - OK"
    else
        echo "$b - Error. Not on path."
        exit 1
    fi
done

#
# Test 2: Remove the RPM and ensure that tools are removed.
#
yum remove -y java-1.8.0-amazon-corretto-devel

for b in ${tools}; do
    if [ ! -f "/usr/bin/$b" ]; then
        echo "$b - OK"
    else
        echo "$b - Error. $b still installed."
        exit 1
    fi
done