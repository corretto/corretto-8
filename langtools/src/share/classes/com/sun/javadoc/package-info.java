/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
The Doclet API (also called the Javadoc API) provides a mechanism
for clients to inspect the source-level structure of programs and
libraries, including javadoc comments embedded in the source.
This is useful for documentation, program checking, automatic
code generation and many other tools.
<p>

Doclets are invoked by javadoc and use this API to write out
program information to files.  For example, the standard doclet is called
by default and writes out documentation to HTML files.
<p>

The invocation is defined by the abstract {@link com.sun.javadoc.Doclet} class
-- the entry point is the {@link com.sun.javadoc.Doclet#start(RootDoc) start} method:
<pre>
    public static boolean <b>start</b>(RootDoc root)
</pre>
The {@link com.sun.javadoc.RootDoc} instance holds the root of the program structure
information. From this root all other program structure
information can be extracted.
<p>

<a name="terminology"></a>
<h3>Terminology</h3>

<a name="included"></a>
When calling javadoc, you pass in package names and source file names --
these are called the <em>specified</em> packages and classes.
You also pass in Javadoc options; the <em>access control</em> Javadoc options
(<code>-public</code>, <code>-protected</code>, <code>-package</code>,
and <code>-private</code>) filter program elements, producing a
result set, called the <em>included</em> set, or "documented" set.
(The unfiltered set is also available through
{@link com.sun.javadoc.PackageDoc#allClasses(boolean) allClasses(false)}.)
<p>

<a name="class"></a>
Throughout this API, the term <em>class</em> is normally a
shorthand for "class or interface", as in: {@link com.sun.javadoc.ClassDoc},
{@link com.sun.javadoc.PackageDoc#allClasses() allClasses()}, and
{@link com.sun.javadoc.PackageDoc#findClass(String) findClass(String)}.
In only a couple of other places, it means "class, as opposed to interface",
as in:  {@link com.sun.javadoc.Doc#isClass()}.
In the second sense, this API calls out four kinds of classes:
{@linkplain com.sun.javadoc.Doc#isOrdinaryClass() ordinary classes},
{@linkplain com.sun.javadoc.Doc#isEnum() enums},
{@linkplain com.sun.javadoc.Doc#isError() errors} and
{@linkplain com.sun.javadoc.Doc#isException() exceptions}.
Throughout the API, the detailed description of each program element
describes explicitly which meaning is being used.
<p>

<a name="qualified"></a>
A <em>qualified</em> class or interface name is one that has its package
name prepended to it, such as <code>java.lang.String</code>.  A non-qualified
name has no package name, such as <code>String</code>.
<p>

<a name="example"></a>
<h3>Example</h3>

The following is an example doclet that
displays information in the <code>@param</code> tags of the processed
classes:
<pre>
import com.sun.javadoc.*;

public class ListParams extends <font color=red title="Doclet API">Doclet</font> {

    public static boolean start(<font color=red title="Doclet API">RootDoc</font> root) {
        <font color=red title="Doclet API">ClassDoc</font>[] classes = root.<font color=red title="Doclet API">classes</font>();
        for (int i = 0; i < classes.length; ++i) {
            <font color=red title="Doclet API">ClassDoc</font> cd = classes[i];
            printMembers(cd.<font color=red title="Doclet API">constructors</font>());
            printMembers(cd.<font color=red title="Doclet API">methods</font>());
        }
        return true;
    }

    static void printMembers(<font color=red title="Doclet API">ExecutableMemberDoc</font>[] mems) {
        for (int i = 0; i < mems.length; ++i) {
            <font color=red title="Doclet API">ParamTag</font>[] params = mems[i].<font color=red title="Doclet API">paramTags</font>();
            System.out.println(mems[i].<font color=red title="Doclet API">qualifiedName</font>());
            for (int j = 0; j < params.length; ++j) {
                System.out.println("   " + params[j].<font color=red title="Doclet API">parameterName</font>()
                    + " - " + params[j].<font color=red title="Doclet API">parameterComment</font>());
            }
        }
    }
}
</pre>
Interfaces and methods from the Javadoc API are marked in
<font color=red title="Doclet API">red</font>.
{@link com.sun.javadoc.Doclet Doclet} is an abstract class that specifies
the invocation interface for doclets,
{@link com.sun.javadoc.Doclet Doclet} holds class or interface information,
{@link com.sun.javadoc.ExecutableMemberDoc} is a
superinterface of {@link com.sun.javadoc.MethodDoc} and
{@link com.sun.javadoc.ConstructorDoc},
and {@link com.sun.javadoc.ParamTag} holds information
from "<code>@param</code>" tags.
<p>
This doclet when invoked with a command line like:
<pre>
    javadoc -doclet ListParams -sourcepath &lt;source-location&gt; java.util
</pre>
producing output like:
<pre>
    ...
    java.util.ArrayList.add
       index - index at which the specified element is to be inserted.
       element - element to be inserted.
    java.util.ArrayList.remove
       index - the index of the element to removed.
    ...

</pre>
@see com.sun.javadoc.Doclet
@see com.sun.javadoc.RootDoc
*/
@jdk.Exported
package com.sun.javadoc;
