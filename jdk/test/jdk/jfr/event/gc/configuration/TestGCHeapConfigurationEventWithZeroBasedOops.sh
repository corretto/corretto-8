#
# Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
# @test TestGCHeapConfigurationEventWithZeroBasedOops
# @key jfr
#
#
# @library /lib /
# @build jdk.jfr.event.gc.configuration.TestGCHeapConfigurationEventWithZeroBasedOops
# @run shell TestGCHeapConfigurationEventWithZeroBasedOops.sh

uses_64_bit_testjava() {
  ${TESTJAVA}/bin/java ${TESTVMOPTS} -version 2>&1 | grep '64-Bit' > /dev/null
}

uses_windows_or_linux() {
    case `uname -s` in
      Linux | CYGWIN* | Windows* )
        return 0
        ;;
    esac
    return 1
}

TEST='jdk.jfr.event.gc.configuration.TestGCHeapConfigurationEventWithZeroBasedOops'

OPTIONS='-XX:+UnlockExperimentalVMOptions -XX:-UseFastUnorderedTimeStamps -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:+UseCompressedOops -Xmx4g'

if [ -z "${TESTCLASSPATH}" ]; then
    echo "Using TESTCLASSES"
    MY_CLASSPATH=${TESTCLASSES}
else
    echo "Using TESTCLASSPATH"
    MY_CLASSPATH=${TESTCLASSPATH}
fi

if uses_windows_or_linux && uses_64_bit_testjava; then
  printenv
  echo "${TESTJAVA}/bin/java ${TESTVMOPTS} ${OPTIONS} -cp ${MY_CLASSPATH} ${TEST}"
  ${TESTJAVA}/bin/java ${TESTVMOPTS} ${OPTIONS} -cp ${MY_CLASSPATH} ${TEST}
fi
