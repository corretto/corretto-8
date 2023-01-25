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

package jdk.nashorn.internal.runtime.linker;

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.JSType.GET_UNDEFINED;
import static jdk.nashorn.internal.runtime.JSType.TYPE_OBJECT_INDEX;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.beans.BeansLinker;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardedTypeConversion;
import jdk.internal.dynalink.linker.GuardingDynamicLinker;
import jdk.internal.dynalink.linker.GuardingTypeConverterFactory;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.support.Guards;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.UnwarrantedOptimismException;

/**
 * Nashorn bottom linker; used as a last-resort catch-all linker for all linking requests that fall through all other
 * linkers (see how {@link Bootstrap} class configures the dynamic linker in its static initializer). It will throw
 * appropriate ECMAScript errors for attempts to invoke operations on {@code null}, link no-op property getters and
 * setters for Java objects that couldn't be linked by any other linker, and throw appropriate ECMAScript errors for
 * attempts to invoke arbitrary Java objects as functions or constructors.
 */
final class NashornBottomLinker implements GuardingDynamicLinker, GuardingTypeConverterFactory {

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest linkRequest, final LinkerServices linkerServices)
            throws Exception {
        final Object self = linkRequest.getReceiver();

        if (self == null) {
            return linkNull(linkRequest);
        }

        // None of the objects that can be linked by NashornLinker should ever reach here. Basically, anything below
        // this point is a generic Java bean. Therefore, reaching here with a ScriptObject is a Nashorn bug.
        assert isExpectedObject(self) : "Couldn't link " + linkRequest.getCallSiteDescriptor() + " for " + self.getClass().getName();

        return linkBean(linkRequest, linkerServices);
    }

    private static final MethodHandle EMPTY_PROP_GETTER =
            MH.dropArguments(MH.constant(Object.class, UNDEFINED), 0, Object.class);
    private static final MethodHandle EMPTY_ELEM_GETTER =
            MH.dropArguments(EMPTY_PROP_GETTER, 0, Object.class);
    private static final MethodHandle EMPTY_PROP_SETTER =
            MH.asType(EMPTY_ELEM_GETTER, EMPTY_ELEM_GETTER.type().changeReturnType(void.class));
    private static final MethodHandle EMPTY_ELEM_SETTER =
            MH.dropArguments(EMPTY_PROP_SETTER, 0, Object.class);

    private static GuardedInvocation linkBean(final LinkRequest linkRequest, final LinkerServices linkerServices) throws Exception {
        final NashornCallSiteDescriptor desc = (NashornCallSiteDescriptor)linkRequest.getCallSiteDescriptor();
        final Object self = linkRequest.getReceiver();
        final String operator = desc.getFirstOperator();
        switch (operator) {
        case "new":
            if(BeansLinker.isDynamicConstructor(self)) {
                throw typeError("no.constructor.matches.args", ScriptRuntime.safeToString(self));
            }
            if(BeansLinker.isDynamicMethod(self)) {
                throw typeError("method.not.constructor", ScriptRuntime.safeToString(self));
            }
            throw typeError("not.a.function", desc.getFunctionErrorMessage(self));
        case "call":
            if(BeansLinker.isDynamicConstructor(self)) {
                throw typeError("constructor.requires.new", ScriptRuntime.safeToString(self));
            }
            if(BeansLinker.isDynamicMethod(self)) {
                throw typeError("no.method.matches.args", ScriptRuntime.safeToString(self));
            }
            throw typeError("not.a.function", desc.getFunctionErrorMessage(self));
        case "callMethod":
            throw typeError("no.such.function", getArgument(linkRequest), ScriptRuntime.safeToString(self));
        case "getMethod":
            // evaluate to undefined, later on Undefined will take care of throwing TypeError
            return getInvocation(MH.dropArguments(GET_UNDEFINED.get(TYPE_OBJECT_INDEX), 0, Object.class), self, linkerServices, desc);
        case "getProp":
        case "getElem":
            if(NashornCallSiteDescriptor.isOptimistic(desc)) {
                throw new UnwarrantedOptimismException(UNDEFINED, NashornCallSiteDescriptor.getProgramPoint(desc), Type.OBJECT);
            }
            if (desc.getOperand() != null) {
                return getInvocation(EMPTY_PROP_GETTER, self, linkerServices, desc);
            }
            return getInvocation(EMPTY_ELEM_GETTER, self, linkerServices, desc);
        case "setProp":
        case "setElem": {
            final boolean strict = NashornCallSiteDescriptor.isStrict(desc);
            if (strict) {
                throw typeError("cant.set.property", getArgument(linkRequest), ScriptRuntime.safeToString(self));
            }
            if (desc.getOperand() != null) {
                return getInvocation(EMPTY_PROP_SETTER, self, linkerServices, desc);
            }
            return getInvocation(EMPTY_ELEM_SETTER, self, linkerServices, desc);
        }
        default:
            break;
        }
        throw new AssertionError("unknown call type " + desc);
    }

    @Override
    public GuardedTypeConversion convertToType(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final GuardedInvocation gi = convertToTypeNoCast(sourceType, targetType);
        return gi == null ? null : new GuardedTypeConversion(gi.asType(MH.type(targetType, sourceType)), true);
    }

    /**
     * Main part of the implementation of {@link GuardingTypeConverterFactory#convertToType} that doesn't
     * care about adapting the method signature; that's done by the invoking method. Returns conversion
     * from Object to String/number/boolean (JS primitive types).
     * @param sourceType the source type
     * @param targetType the target type
     * @return a guarded invocation that converts from the source type to the target type.
     * @throws Exception if something goes wrong
     */
    private static GuardedInvocation convertToTypeNoCast(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final MethodHandle mh = CONVERTERS.get(targetType);
        if (mh != null) {
            return new GuardedInvocation(mh);
        }

        return null;
    }

    private static GuardedInvocation getInvocation(final MethodHandle handle, final Object self, final LinkerServices linkerServices, final CallSiteDescriptor desc) {
        return Bootstrap.asTypeSafeReturn(new GuardedInvocation(handle, Guards.getClassGuard(self.getClass())), linkerServices, desc);
    }

    // Used solely in an assertion to figure out if the object we get here is something we in fact expect. Objects
    // linked by NashornLinker should never reach here.
    private static boolean isExpectedObject(final Object obj) {
        return !(NashornLinker.canLinkTypeStatic(obj.getClass()));
    }

    private static GuardedInvocation linkNull(final LinkRequest linkRequest) {
        final NashornCallSiteDescriptor desc = (NashornCallSiteDescriptor)linkRequest.getCallSiteDescriptor();
        final String operator = desc.getFirstOperator();
        switch (operator) {
        case "new":
        case "call":
            throw typeError("not.a.function", "null");
        case "callMethod":
        case "getMethod":
            throw typeError("no.such.function", getArgument(linkRequest), "null");
        case "getProp":
        case "getElem":
            throw typeError("cant.get.property", getArgument(linkRequest), "null");
        case "setProp":
        case "setElem":
            throw typeError("cant.set.property", getArgument(linkRequest), "null");
        default:
            break;
        }
        throw new AssertionError("unknown call type " + desc);
    }

    private static final Map<Class<?>, MethodHandle> CONVERTERS = new HashMap<>();
    static {
        CONVERTERS.put(boolean.class, JSType.TO_BOOLEAN.methodHandle());
        CONVERTERS.put(double.class, JSType.TO_NUMBER.methodHandle());
        CONVERTERS.put(int.class, JSType.TO_INTEGER.methodHandle());
        CONVERTERS.put(long.class, JSType.TO_LONG.methodHandle());
        CONVERTERS.put(String.class, JSType.TO_STRING.methodHandle());
    }

    private static String getArgument(final LinkRequest linkRequest) {
        final CallSiteDescriptor desc = linkRequest.getCallSiteDescriptor();
        if (desc.getNameTokenCount() > 2) {
            return desc.getNameToken(2);
        }
        return ScriptRuntime.safeToString(linkRequest.getArguments()[1]);
    }
}
