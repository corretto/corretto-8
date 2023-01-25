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
 * JDK-8007060 : Primitive wrap filter throws ClassCastException in test262parallel
 *
 * @test
 * @run
 */

Object.prototype.T = function() {
    print(this, typeof this);
};

function F() {
    print(this, typeof this);
}


function test(obj) {
   obj.T();
}

// Ordinary callsite - call often so we go to megamorphic
test(1);
test({});
test("hello");
test(1);
test({});
test("hello");
test(1);
test({});
test("hello");
test(1);
test({});
test("hello");
test(1);
test({});
test("hello");
test(1);
test({});
test("hello");
test(1);
test({});
test("hello");
test(1);
test({});
test("hello");

// Dynamic invoker callsite used by NativeArray
[1, 2, 3].filter(F, 1);
[1, 2, 3].filter(F, {});
[1, 2, 3].filter(F, "hello");
[1, 2, 3].filter(F, 1);
[1, 2, 3].filter(F, {});
[1, 2, 3].filter(F, "hello");
[1, 2, 3].filter(F, 1);
[1, 2, 3].filter(F, {});
[1, 2, 3].filter(F, "hello");
[1, 2, 3].filter(F, 1);
[1, 2, 3].filter(F, {});
[1, 2, 3].filter(F, "hello");
[1, 2, 3].filter(F, 1);
[1, 2, 3].filter(F, {});
[1, 2, 3].filter(F, "hello");
[1, 2, 3].filter(F, 1);
[1, 2, 3].filter(F, {});
[1, 2, 3].filter(F, "hello");
[1, 2, 3].filter(F, 1);
[1, 2, 3].filter(F, {});
[1, 2, 3].filter(F, "hello");
[1, 2, 3].filter(F, 1);
[1, 2, 3].filter(F, {});
[1, 2, 3].filter(F, "hello");
