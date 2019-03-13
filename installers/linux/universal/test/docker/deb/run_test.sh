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
simpleJdkPackageName="java-1.8.0-amazon-corretto-jdk"
canonicalJdkPackageName="$(ls ${workDir}/java-1.8.0-amazon-corretto-jdk*.deb | xargs -n 1 basename)"

function test_jdk_installation() {
    dpkg -i "${workDir}/${canonicalJdkPackageName}" &> /dev/null
    if [[ $? != 0 ]]; then
        echo "[DEB Test] FAILED: test_jdk_installation"
        status=1
    else
        echo "[DEB Test] PASSED: test_jdk_installation"
    fi
}

# Examine alternatives setup for JDK and JRE.
# Valid pass-in: "jdk", "jre"
function test_alternatives_after_installation() {
    target=${1}
    alternative_check=0
    if [[ ${target} = "jdk" ]]; then
        tools=${jdk_tools}
    else
        tools=${jre_tools}
    fi

    for i in ${tools}; do
        (update-alternatives --display ${i} | grep ${jdkInstallationDirectoryName}) &> /dev/null
        if [[ $? != 0 ]]; then
            echo "  $i alternatives is not installed correctly."
            alternative_check=1
            status=1
        fi
    done

    if [[ ${alternative_check} != 0 ]]; then
        echo "[DEB Test] FAILED: test_${target}_alternatives_after_installation"
    else
        echo "[DEB Test] PASSED: test_${target}_alternatives_after_installation"
    fi
}

# Examine Corretto shows up in update_java_alternatives command
# correctly and is switchable.
function test_update_java_alternatives_after_installation() {
    update-java-alternatives -s ${jdkInstallationDirectoryName} &> /dev/null
    if [[ $? != 0 ]]; then
        echo "[DEB Test] FAILED: test_update_java_alternatives_after_installation"
        status=1
    else
        echo "[DEB Test] PASSED: test_update_java_alternatives_after_installation"
    fi
}

function test_jdk_uninstallation() {
    dpkg -r ${simpleJdkPackageName} &> /dev/null
    if [[ $? != 0 ]]; then
        echo "[DEB Test] FAILED: test_jdk_uninstallation"
        status=1
    else
        echo "[DEB Test] PASSED: test_jdk_uninstallation"
    fi
}

# Examine alternatives removal for JDK and JRE.
# Valid pass-in: "jdk", "jre"
function test_alternatives_after_uninstallation() {
    target=${1}
    alternative_check=0
    if [[ ${target} = "jdk" ]]; then
        tools=${jdk_tools}
    else
        tools=${jre_tools}
    fi

    for i in ${tools}; do
        (update-alternatives --display ${i} | grep ${jdkInstallationDirectoryName}) &> /dev/null
        if [[ $? == 0 ]]; then
            echo "  $i alternatives is not removed correctly."
            alternative_check=1
            status=1
        fi
    done

    if [[ ${alternative_check} != 0 ]]; then
        echo "[DEB Test] FAILED: test_${target}_alternatives_after_uninstallation"
    else
        echo "[DEB Test] PASSED: test_${target}_alternatives_after_uninstallation"
    fi
}

function test_update_java_alternatives_after_uninstallation() {
    # Corretto entry is expected to be removed from update-java-alternatives list
    # after uninstallation.
    update-java-alternatives -l | grep ${jdkInstallationDirectoryName} &> /dev/null
    if [[ $? == 0 ]]; then
        echo "[DEB Test] FAILED: test_update_java_alternatives_after_uninstallation"
        status=1
    else
        echo "[DEB Test] PASSED: test_update_java_alternatives_after_uninstallation"
    fi
}

# Delay test execution to make sure Gradle console
# log output looks correct.
sleep 5

test_jdk_installation
test_alternatives_after_installation "jdk"
test_alternatives_after_installation "jre"
test_update_java_alternatives_after_installation
test_jdk_uninstallation
test_alternatives_after_uninstallation "jdk"
test_alternatives_after_uninstallation "jre"
test_update_java_alternatives_after_uninstallation

exit ${status}
