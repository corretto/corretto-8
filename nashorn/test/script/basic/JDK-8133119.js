/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8133119: Error message associated with TypeError for call and new should include stringified Node
 *
 * @test
 * @run
 */

var obj = {}
try {
    obj.func();
} catch (e) {
    print(e);
}

var arr = [33];
try {
    arr[0].func();
} catch (e) {
    print(e);
}

try {
    new obj.func();
} catch (e) {
    print(e);
}

try {
    new arr[0].func();
} catch (e) {
    print(e);
}

obj.foo = {}
try {
    new obj.foo();
} catch (e) {
    print(e);
}

try {
    obj.foo();
} catch (e) {
    print(e);
}

var v = new java.util.Vector();
try {
    v();
} catch (e) {
    print(e);
}

try {
    new v();
} catch (e) {
    print(e);
}
