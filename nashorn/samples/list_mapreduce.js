/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// Usage: jjs list_mapreduce.js

// Many Array.prototype functions such as map,
// filter, reduce, reduceRight, every, some are generic.
// These functions accept ECMAScript array as well as
// many array-like objects including java.util.ArrayLists.
// So, you can do map/filter/reduce with Java streams or
// you can also use Array.prototype functions as below.
// See also http://en.wikipedia.org/wiki/MapReduce

var ArrayList = Java.type("java.util.ArrayList");
var list = new ArrayList();
list.add("nashorn");
list.add("ecmascript");
list.add("javascript");
list.add("js");
list.add("scheme");

var map = Array.prototype.map;
var filter = Array.prototype.filter;
var reduce = Array.prototype.reduce;

// sum of word lengths
print("Sum word length:",
    reduce.call(
        map.call(list, function(x) x.length),
        function(x, y) x + y)
);

// filter use to filter out "j*" and concatenate rest with ":"
// after uppercasing all strings
print(
    reduce.call(
        map.call(
            filter.call(list, function(x) !x.startsWith("j")),
            function(x) x.toUpperCase()),
        function(x, y) x + ":" + y)
);

// another list example involving numbers
list.clear();
// make random list of numbers
for (var i = 0; i < 10; i++)
    list.add(Math.random());

var forEach = Array.prototype.forEach;
// print numbers in the list
forEach.call(list, function(x) print(x));

// print sum of squares of the random numbers
print("Square sum:",
    reduce.call(
        map.call(list, function(x) x*x),
        function(x, y) x + y)
);
