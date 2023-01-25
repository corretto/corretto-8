#
# Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4990825
# @run shell jstatHelp.sh
# @summary Test that output of 'jstat -?' matches the usage.out file

. ${TESTSRC-.}/../../jvmstat/testlibrary/utils.sh

setup

JSTAT="${TESTJAVA}/bin/jstat"

rm -f jstat.out 2>/dev/null
${JSTAT} -J-XX:+UsePerfData -? > jstat.out 2>&1

diff -w jstat.out ${TESTSRC}/usage.out
if [ $? != 0 ]
then
  echo "Output of jstat -? differ from expected output. Failed."
  exit 1
fi

rm -f jstat.out 2>/dev/null
${JSTAT} -J-XX:+UsePerfData -help > jstat.out 2>&1

diff -w jstat.out ${TESTSRC}/usage.out
if [ $? != 0 ]
then
  echo "Output of jstat -help differ from expected output. Failed."
  exit 1
fi

exit 0
