# Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
#!/bin/sh

#
# @test testme.sh
# @bug 8197429
# @summary Linux kernel stack guard should not cause segfaults on x86-32
# @compile T.java
# @run shell testme.sh
#

if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

if [ "${VM_OS}" != "linux" ]
then
  echo "Test only valid for Linux"
  exit 0
fi

gcc_cmd=`which gcc`
if [ "x$gcc_cmd" = "x" ]; then
    echo "WARNING: gcc not found. Cannot execute test." 2>&1
    exit 0;
fi

CFLAGS="-m${VM_BITS}"

LD_LIBRARY_PATH=.:${COMPILEJAVA}/jre/lib/${VM_CPU}/${VM_TYPE}:/usr/lib:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

cp ${TESTSRC}${FS}exestack-gap.c .

# Copy the result of our @compile action:
cp ${TESTCLASSES}${FS}T.class .

echo "Compilation flag: ${COMP_FLAG}"
# Note pthread may not be found thus invoke creation will fail to be created.
# Check to ensure you have a /usr/lib/libpthread.so if you don't please look
# for /usr/lib/`uname -m`-linux-gnu version ensure to add that path to below compilation.

$gcc_cmd -DLINUX ${CFLAGS} -o stack-gap \
    -I${COMPILEJAVA}/include -I${COMPILEJAVA}/include/linux \
    -L${COMPILEJAVA}/jre/lib/${VM_CPU}/${VM_TYPE} \
    -ljvm -lpthread exestack-gap.c

./stack-gap || exit $?
./stack-gap -XX:+DisablePrimordialThreadGuardPages || exit $?
