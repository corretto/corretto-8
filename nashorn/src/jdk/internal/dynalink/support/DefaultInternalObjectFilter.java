/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file, and Oracle licenses the original version of this file under the BSD
 * license:
 */
/*
   Copyright 2009-2015 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jdk.internal.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.internal.dynalink.DynamicLinkerFactory;
import jdk.internal.dynalink.linker.MethodHandleTransformer;

/**
 * Default implementation for a {@link DynamicLinkerFactory#setInternalObjectsFilter(MethodHandleTransformer)}.
 * Given a method handle of {@code Object(Object)} type for filtering parameter and another one of the same type for
 * filtering return values, applies them to passed method handles where their parameter types and/or return value types
 * are declared to be {@link Object}.
 */
public class DefaultInternalObjectFilter implements MethodHandleTransformer {
    private static final MethodHandle FILTER_VARARGS = new Lookup(MethodHandles.lookup()).findStatic(
            DefaultInternalObjectFilter.class, "filterVarArgs", MethodType.methodType(Object[].class, MethodHandle.class, Object[].class));

    private final MethodHandle parameterFilter;
    private final MethodHandle returnFilter;
    private final MethodHandle varArgFilter;

    /**
     * Creates a new filter.
     * @param parameterFilter the filter for method parameters. Must be of type {@code Object(Object)}, or null.
     * @param returnFilter the filter for return values. Must be of type {@code Object(Object)}, or null.
     * @throws IllegalArgumentException if one or both filters are not of the expected type.
     */
    public DefaultInternalObjectFilter(final MethodHandle parameterFilter, final MethodHandle returnFilter) {
        this.parameterFilter = checkHandle(parameterFilter, "parameterFilter");
        this.returnFilter = checkHandle(returnFilter, "returnFilter");
        this.varArgFilter = parameterFilter == null ? null : FILTER_VARARGS.bindTo(parameterFilter);
    }

    @Override
    public MethodHandle transform(final MethodHandle target) {
        assert target != null;
        MethodHandle[] filters = null;
        final MethodType type = target.type();
        final boolean isVarArg = target.isVarargsCollector();
        final int paramCount = type.parameterCount();
        final MethodHandle paramsFiltered;
        // Filter parameters
        if (parameterFilter != null) {
            int firstFilter = -1;
            // Ignore receiver, start from argument 1
            for(int i = 1; i < paramCount; ++i) {
                final Class<?> paramType = type.parameterType(i);
                final boolean filterVarArg = isVarArg && i == paramCount - 1 && paramType == Object[].class;
                if (filterVarArg || paramType == Object.class) {
                    if (filters == null) {
                        firstFilter = i;
                        filters = new MethodHandle[paramCount - firstFilter];
                    }
                    filters[i - firstFilter] = filterVarArg ? varArgFilter : parameterFilter;
                }
            }
            paramsFiltered = filters != null ? MethodHandles.filterArguments(target, firstFilter, filters) : target;
        } else {
            paramsFiltered = target;
        }
        // Filter return value if needed
        final MethodHandle returnFiltered = returnFilter != null && type.returnType() == Object.class ? MethodHandles.filterReturnValue(paramsFiltered, returnFilter) : paramsFiltered;
        // Preserve varargs collector state
        return isVarArg && !returnFiltered.isVarargsCollector() ? returnFiltered.asVarargsCollector(type.parameterType(paramCount - 1)) : returnFiltered;

    }

    private static MethodHandle checkHandle(final MethodHandle handle, final String handleKind) {
        if (handle != null) {
            final MethodType objectObjectType = MethodType.methodType(Object.class, Object.class);
            if (!handle.type().equals(objectObjectType)) {
                throw new IllegalArgumentException("Method type for " + handleKind + " must be " + objectObjectType);
            }
        }
        return handle;
    }

    @SuppressWarnings("unused")
    private static Object[] filterVarArgs(final MethodHandle parameterFilter, final Object[] args) throws Throwable {
        Object[] newArgs = null;
        for(int i = 0; i < args.length; ++i) {
            final Object arg = args[i];
            final Object newArg = parameterFilter.invokeExact(arg);
            if (arg != newArg) {
                if (newArgs == null) {
                    newArgs = args.clone();
                }
                newArgs[i] = newArg;
            }
        }
        return newArgs == null ? args : newArgs;
    }
}
