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

assertOpenJdkNotInstalled() {
  if yum list installed java-1.8.0-openjdk >/dev/null 2>&1; then
    echo "Fail: java-1.8.0-openjdk was installed"
    exit 1
  fi
  if yum list installed java-1.8.0-openjdk-devel >/dev/null 2>&1; then
    echo "Fail: java-1.8.0-openjdk-devel was installed"
    exit 1
  fi
}

assertOpenJdkNotInstalled
yum install -y tomcat
assertOpenJdkNotInstalled

