/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8003280 8013404
 * @summary Add lambda tests
 *  check that target type of cast is not propagated to conditional subexpressions
 * @compile/fail/ref=TargetType36.out -XDrawDiagnostics TargetType36.java
 */
class TargetType36 { //awaits spec wording on cast vs. poly

    interface SAM {
       int m(int i, int j);
    }

    void test() {
        SAM s1 = (SAM)((a,b)->a+b);
        SAM s2 = (SAM)(true? (SAM)((a,b)->a+b) : (SAM)((a,b)->a+b));
        SAM s3 = (SAM)(true? (a,b)->a+b : (a,b)->a+b);
    }
}
