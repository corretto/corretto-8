#
# Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4313887 6907737
# @summary Tests that walkFileTree is consistent with the native find program
# @build CreateFileTree PrintFileTree
# @run shell find.sh

# if TESTJAVA isn't set then we assume an interactive run.

if [ -z "$TESTJAVA" ]; then
    TESTSRC=.
    TESTCLASSES=.
    JAVA=java
else
    JAVA="${TESTJAVA}/bin/java"
fi

OS=`uname -s`
case "$OS" in
    Windows_* | CYGWIN* )
        echo "This test does not run on Windows" 
        exit 0
        ;;
    AIX )
        CLASSPATH=${TESTCLASSES}:${TESTSRC}
        # On AIX "find -follow" may core dump on recursive links without '-L'
        # see: http://www-01.ibm.com/support/docview.wss?uid=isg1IV28143
        FIND_FOLLOW_OPT="-L"
        ;;
    * )
        FIND_FOLLOW_OPT=
        CLASSPATH=${TESTCLASSES}:${TESTSRC}
        ;;
esac
export CLASSPATH

# create the file tree
ROOT=`$JAVA CreateFileTree`
if [ $? != 0 ]; then exit 1; fi

failures=0

# print the file tree and compare output with find(1)
$JAVA ${TESTVMOPTS} PrintFileTree "$ROOT" > out1
find "$ROOT" > out2
diff out1 out2
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# repeat test following links. Some versions of find(1) output
# cycles (sym links to ancestor directories), other versions do
# not. For that reason we run PrintFileTree with the -printCycles
# option when the output without this option differs to find(1).
find $FIND_FOLLOW_OPT "$ROOT" -follow > out1
$JAVA ${TESTVMOPTS} PrintFileTree -follow "$ROOT" > out2
diff out1 out2
if [ $? != 0 ];
  then 
    # re-run printing cycles to stdout
    $JAVA ${TESTVMOPTS} PrintFileTree -follow -printCycles "$ROOT" > out2
    diff out1 out2
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
  fi

# clean-up
rm -r "$ROOT"

echo ''
if [ $failures -gt 0 ];
  then echo "$failures test(s) failed";
  else echo "Test passed"; fi
exit $failures
