#
# Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4215035
# @summary standard extensions path is hard-coded in default system policy file
#
# @build Ext_AllPolicy
# @run shell Ext_AllPolicy.sh

#
# For testing extenions, the classes live in jar files, and therefore
# we shouldn't be able to find the raw class file.
#

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory
if [ "${TESTSRC}" = "" ] ; then
  TESTSRC="."
fi
if [ "${TESTCLASSES}" = "" ] ; then
  TESTCLASSES="."
fi
if [ "${TESTJAVA}" = "" ] ; then
  echo "TESTJAVA not set.  Test cannot execute."
  echo "FAILED!!!"
  exit 1
fi
if [ "${COMPILEJAVA}" = "" ]; then
  COMPILEJAVA="${TESTJAVA}"
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin | AIX )
    NULL=/dev/null
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    NULL=/dev/null
    PS=";"
    FS="/"
    ;;
  Windows_95 | Windows_98 | Windows_NT )
    NULL=NUL
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# the test code

cd ${TESTCLASSES}
${COMPILEJAVA}${FS}bin${FS}jar ${TESTTOOLVMOPTS} -cvf Ext_AllPolicy.jar Ext_AllPolicy.class

rm Ext_AllPolicy.class
${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} \
        -Djava.security.manager -Djava.ext.dirs="${TESTCLASSES}" Ext_AllPolicy

exit $?
