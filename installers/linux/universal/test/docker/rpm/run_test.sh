#!/usr/bin/env bash
#
#Copyright (c) 2018, Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

status=0
workDir="/distributions"
jdkInstallationDirectoryName="java-1.8.0-amazon-corretto"
simpleJdkPackageName="java-1.8.0-amazon-corretto-devel"
canonicalJdkPackageName="$(ls ${workDir}/java-1.8.0-amazon-corretto-devel*.rpm | xargs -n 1 basename)"

function test_jdk_installation() {
    yum localinstall -y "${workDir}/${canonicalJdkPackageName}" &> /dev/null
    if [[ $? != 0 ]]; then
        echo "[RPM Test] FAILED: test_jdk_installation"
        status=1
    else
        echo "[RPM Test] PASSED: test_jdk_installation"
    fi
}

# Examine alternatives setup for JDK and JRE.
# Valid pass-in: "jdk", "jre"
function test_alternatives_after_installation() {
    target=${1}
    if [[ ${target} = "jdk" ]]; then
        (alternatives --display javac | grep ${jdkInstallationDirectoryName}) &> /dev/null
    else
        (alternatives --display java | grep ${jdkInstallationDirectoryName}) &> /dev/null
    fi

    if [[ $? != 0  ]]; then
        status=1
        echo "[RPM Test] FAILED: test_${target}_alternatives_after_installation"
    else
        echo "[RPM Test] PASSED: test_${target}_alternatives_after_installation"
    fi
}

function test_jdk_uninstallation() {
    yum remove -y ${simpleJdkPackageName} &> /dev/null
    if [[ $? != 0 ]]; then
        echo "[RPM Test] FAILED: test_jdk_uninstallation"
        status=1
    else
        echo "[RPM Test] PASSED: test_jdk_uninstallation"
    fi
}

# Examine alternatives removal for JDK and JRE.
# Valid pass-in: "jdk", "jre"
function test_alternatives_after_uninstallation() {
    target=${1}
    if [[ ${target} = "jdk" ]]; then
        (alternatives --display javac | grep ${jdkInstallationDirectoryName}) &> /dev/null
    else
        (alternatives --display java | grep ${jdkInstallationDirectoryName}) &> /dev/null
    fi

    if [[ $? == 0  ]]; then
        status=1
        echo "[RPM Test] FAILED: test_${target}_alternatives_after_uninstallation"
    else
        echo "[RPM Test] PASSED: test_${target}_alternatives_after_uninstallation"
    fi
}

# Delay test execution to make sure Gradle console
# log output looks correct.
sleep 5

test_jdk_installation
test_alternatives_after_installation "jdk"
test_alternatives_after_installation "jre"
test_jdk_uninstallation
test_alternatives_after_uninstallation "jdk"
test_alternatives_after_uninstallation "jre"

exit ${status}
