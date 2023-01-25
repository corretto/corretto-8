/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
   Copyright 2009-2013 Attila Szegedi

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

package jdk.internal.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.beans.ApplicableOverloadedMethods.ApplicabilityTest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.support.TypeUtilities;

/**
 * Represents a group of {@link SingleDynamicMethod} objects that represents all overloads of a particular name (or all
 * constructors) for a particular class. Correctly handles overload resolution, variable arity methods, and caller
 * sensitive methods within the overloads.
 *
 * @author Attila Szegedi
 */
class OverloadedDynamicMethod extends DynamicMethod {
    /**
     * Holds a list of all methods.
     */
    private final LinkedList<SingleDynamicMethod> methods;
    private final ClassLoader classLoader;

    /**
     * Creates a new overloaded dynamic method.
     *
     * @param clazz the class this method belongs to
     * @param name the name of the method
     */
    OverloadedDynamicMethod(final Class<?> clazz, final String name) {
        this(new LinkedList<SingleDynamicMethod>(), clazz.getClassLoader(), getClassAndMethodName(clazz, name));
    }

    private OverloadedDynamicMethod(final LinkedList<SingleDynamicMethod> methods, final ClassLoader classLoader, final String name) {
        super(name);
        this.methods = methods;
        this.classLoader = classLoader;
    }

    @Override
    SingleDynamicMethod getMethodForExactParamTypes(final String paramTypes) {
        final LinkedList<SingleDynamicMethod> matchingMethods = new LinkedList<>();
        for(final SingleDynamicMethod method: methods) {
            final SingleDynamicMethod matchingMethod = method.getMethodForExactParamTypes(paramTypes);
            if(matchingMethod != null) {
                matchingMethods.add(matchingMethod);
            }
        }
        switch(matchingMethods.size()) {
            case 0: {
                return null;
            }
            case 1: {
                return matchingMethods.getFirst();
            }
            default: {
                throw new BootstrapMethodError("Can't choose among " + matchingMethods + " for argument types "
                        + paramTypes + " for method " + getName());
            }
        }
    }

    @Override
    public MethodHandle getInvocation(final CallSiteDescriptor callSiteDescriptor, final LinkerServices linkerServices) {
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        // First, find all methods applicable to the call site by subtyping (JLS 15.12.2.2)
        final ApplicableOverloadedMethods subtypingApplicables = getApplicables(callSiteType,
                ApplicableOverloadedMethods.APPLICABLE_BY_SUBTYPING);
        // Next, find all methods applicable by method invocation conversion to the call site (JLS 15.12.2.3).
        final ApplicableOverloadedMethods methodInvocationApplicables = getApplicables(callSiteType,
                ApplicableOverloadedMethods.APPLICABLE_BY_METHOD_INVOCATION_CONVERSION);
        // Finally, find all methods applicable by variable arity invocation. (JLS 15.12.2.4).
        final ApplicableOverloadedMethods variableArityApplicables = getApplicables(callSiteType,
                ApplicableOverloadedMethods.APPLICABLE_BY_VARIABLE_ARITY);

        // Find the methods that are maximally specific based on the call site signature
        List<SingleDynamicMethod> maximallySpecifics = subtypingApplicables.findMaximallySpecificMethods();
        if(maximallySpecifics.isEmpty()) {
            maximallySpecifics = methodInvocationApplicables.findMaximallySpecificMethods();
            if(maximallySpecifics.isEmpty()) {
                maximallySpecifics = variableArityApplicables.findMaximallySpecificMethods();
            }
        }

        // Now, get a list of the rest of the methods; those that are *not* applicable to the call site signature based
        // on JLS rules. As paradoxical as that might sound, we have to consider these for dynamic invocation, as they
        // might match more concrete types passed in invocations. That's why we provisionally call them "invokables".
        // This is typical for very generic signatures at call sites. Typical example: call site specifies
        // (Object, Object), and we have a method whose parameter types are (String, int). None of the JLS applicability
        // rules will trigger, but we must consider the method, as it can be the right match for a concrete invocation.
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final List<SingleDynamicMethod> invokables = (List)methods.clone();
        invokables.removeAll(subtypingApplicables.getMethods());
        invokables.removeAll(methodInvocationApplicables.getMethods());
        invokables.removeAll(variableArityApplicables.getMethods());
        for(final Iterator<SingleDynamicMethod> it = invokables.iterator(); it.hasNext();) {
            final SingleDynamicMethod m = it.next();
            if(!isApplicableDynamically(linkerServices, callSiteType, m)) {
                it.remove();
            }
        }

        // If no additional methods can apply at invocation time, and there's more than one maximally specific method
        // based on call site signature, that is a link-time ambiguity. In a static scenario, javac would report an
        // ambiguity error.
        if(invokables.isEmpty() && maximallySpecifics.size() > 1) {
            throw new BootstrapMethodError("Can't choose among " + maximallySpecifics + " for argument types "
                    + callSiteType);
        }

        // Merge them all.
        invokables.addAll(maximallySpecifics);
        switch(invokables.size()) {
            case 0: {
                // No overloads can ever match the call site type
                return null;
            }
            case 1: {
                // Very lucky, we ended up with a single candidate method handle based on the call site signature; we
                // can link it very simply by delegating to the SingleDynamicMethod.
                return invokables.iterator().next().getInvocation(callSiteDescriptor, linkerServices);
            }
            default: {
                // We have more than one candidate. We have no choice but to link to a method that resolves overloads on
                // every invocation (alternatively, we could opportunistically link the one method that resolves for the
                // current arguments, but we'd need to install a fairly complex guard for that and when it'd fail, we'd
                // go back all the way to candidate selection. Note that we're resolving any potential caller sensitive
                // methods here to their handles, as the OverloadedMethod instance is specific to a call site, so it
                // has an already determined Lookup.
                final List<MethodHandle> methodHandles = new ArrayList<>(invokables.size());
                final MethodHandles.Lookup lookup = callSiteDescriptor.getLookup();
                for(final SingleDynamicMethod method: invokables) {
                    methodHandles.add(method.getTarget(lookup));
                }
                return new OverloadedMethod(methodHandles, this, callSiteType, linkerServices).getInvoker();
            }
        }

    }

    @Override
    public boolean contains(final SingleDynamicMethod m) {
        for(final SingleDynamicMethod method: methods) {
            if(method.contains(m)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConstructor() {
        assert !methods.isEmpty();
        return methods.getFirst().isConstructor();
    }

    @Override
    public String toString() {
        // First gather the names and sort them. This makes it consistent and easier to read.
        final List<String> names = new ArrayList<>(methods.size());
        int len = 0;
        for (final SingleDynamicMethod m: methods) {
            final String name = m.getName();
            len += name.length();
            names.add(name);
        }
        // Case insensitive sorting, so e.g. "Object" doesn't come before "boolean".
        final Collator collator = Collator.getInstance();
        collator.setStrength(Collator.SECONDARY);
        Collections.sort(names, collator);

        final String className = getClass().getName();
        // Class name length + length of signatures + 2 chars/per signature for indentation and newline +
        // 3 for brackets and initial newline
        final int totalLength = className.length() + len + 2 * names.size() + 3;
        final StringBuilder b = new StringBuilder(totalLength);
        b.append('[').append(className).append('\n');
        for(final String name: names) {
            b.append(' ').append(name).append('\n');
        }
        b.append(']');
        assert b.length() == totalLength;
        return b.toString();
    };

    ClassLoader getClassLoader() {
        return classLoader;
    }

    private static boolean isApplicableDynamically(final LinkerServices linkerServices, final MethodType callSiteType,
            final SingleDynamicMethod m) {
        final MethodType methodType = m.getMethodType();
        final boolean varArgs = m.isVarArgs();
        final int fixedArgLen = methodType.parameterCount() - (varArgs ? 1 : 0);
        final int callSiteArgLen = callSiteType.parameterCount();

        // Arity checks
        if(varArgs) {
            if(callSiteArgLen < fixedArgLen) {
                return false;
            }
        } else if(callSiteArgLen != fixedArgLen) {
            return false;
        }

        // Fixed arguments type checks, starting from 1, as receiver type doesn't participate
        for(int i = 1; i < fixedArgLen; ++i) {
            if(!isApplicableDynamically(linkerServices, callSiteType.parameterType(i), methodType.parameterType(i))) {
                return false;
            }
        }
        if(!varArgs) {
            // Not vararg; both arity and types matched.
            return true;
        }

        final Class<?> varArgArrayType = methodType.parameterType(fixedArgLen);
        final Class<?> varArgType = varArgArrayType.getComponentType();

        if(fixedArgLen == callSiteArgLen - 1) {
            // Exactly one vararg; check both array type matching and array component type matching.
            final Class<?> callSiteArgType = callSiteType.parameterType(fixedArgLen);
            return isApplicableDynamically(linkerServices, callSiteArgType, varArgArrayType)
                    || isApplicableDynamically(linkerServices, callSiteArgType, varArgType);
        }

        // Either zero, or more than one vararg; check if all actual vararg types match the vararg array component type.
        for(int i = fixedArgLen; i < callSiteArgLen; ++i) {
            if(!isApplicableDynamically(linkerServices, callSiteType.parameterType(i), varArgType)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isApplicableDynamically(final LinkerServices linkerServices, final Class<?> callSiteType,
            final Class<?> methodType) {
        return TypeUtilities.isPotentiallyConvertible(callSiteType, methodType)
                || linkerServices.canConvert(callSiteType, methodType);
    }

    private ApplicableOverloadedMethods getApplicables(final MethodType callSiteType, final ApplicabilityTest test) {
        return new ApplicableOverloadedMethods(methods, callSiteType, test);
    }

    /**
     * Add a method to this overloaded method's set.
     *
     * @param method a method to add
     */
    public void addMethod(final SingleDynamicMethod method) {
        assert constructorFlagConsistent(method);
        methods.add(method);
    }

    private boolean constructorFlagConsistent(final SingleDynamicMethod method) {
        return methods.isEmpty()? true : (methods.getFirst().isConstructor() == method.isConstructor());
    }
}
