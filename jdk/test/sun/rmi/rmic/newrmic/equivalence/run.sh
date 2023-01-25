#
# Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 4911536
# @summary This test verifies that the new implementation of rmic
# generates equivalent classes as the old implementation, for a set
# of sample input classes.
# @author Peter Jones
#
# @library ../../../../../java/rmi/testlibrary
#
# @build TestLibrary
#     AgentServerImpl
#     AppleImpl
#     AppleUserImpl
#     ComputeServerImpl
#     CountServerImpl
#     DayTimeServerImpl
#     G1Impl
#     MyObjectImpl
#     NotActivatableServerImpl
#     OrangeEchoImpl
#     OrangeImpl
#     ServerImpl
#
# @run shell run.sh

if [ "${TESTJAVA}" = "" ]
then
    echo "TESTJAVA not set.  Test cannot execute.  Failed."
    exit 1
fi

set -ex

#
# miscellaneous remote classes collected from other tests
#

sh ${TESTSRC:-.}/batch.sh ${TESTCLASSES:-.} \
	AgentServerImpl \
	AppleImpl \
	AppleUserImpl \
	ComputeServerImpl \
	CountServerImpl \
	DayTimeServerImpl \
	G1Impl \
	MyObjectImpl \
	NotActivatableServerImpl \
	OrangeEchoImpl \
	OrangeImpl \
	ServerImpl

#
# remote classes in the J2SE implementation
#

sh ${TESTSRC:-.}/batch.sh ${TESTCLASSES:-.} \
	sun.rmi.registry.RegistryImpl \
	sun.rmi.server.Activation\$ActivationMonitorImpl \
	sun.rmi.server.Activation\$ActivationSystemImpl \
	sun.rmi.server.Activation\$ActivatorImpl \
	java.rmi.activation.ActivationGroup
