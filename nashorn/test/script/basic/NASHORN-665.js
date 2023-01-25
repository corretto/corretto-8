/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * NASHORN-665 : delete on string index should return false
 *
 * @test
 * @run
 */

function func(str) {
   'use strict';

   try {
       delete str[0];
       fail("expected TypeError");
   } catch (e) {
       if (! (e instanceof TypeError)) {
           fail("expected TypeError, got " + e);
       }
   }
}

func(new String("hello"));
func("hello");

var str = new String("hello");
if (delete str[0] != false) {
    fail("delete str[0] does not result in false");
}

str = "hello";
if (delete str[0] != false) {
    fail("delete str[0] does not result in false");
}
