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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Constructor of method handles used to guard call sites.
 */
public final class NashornGuards {
    private static final MethodHandle IS_MAP              = findOwnMH("isMap", boolean.class, ScriptObject.class, PropertyMap.class);
    private static final MethodHandle IS_MAP_SCRIPTOBJECT = findOwnMH("isMap", boolean.class, Object.class, PropertyMap.class);
    private static final MethodHandle IS_SCRIPTOBJECT     = findOwnMH("isScriptObject", boolean.class, Object.class);
    private static final MethodHandle IS_NOT_JSOBJECT     = findOwnMH("isNotJSObject", boolean.class, Object.class);
    private static final MethodHandle SAME_OBJECT         = findOwnMH("sameObject", boolean.class, Object.class, WeakReference.class);
    //TODO - maybe put this back in ScriptFunction instead of the ClassCastException.class relinkage
    //private static final MethodHandle IS_SCRIPTFUNCTION = findOwnMH("isScriptFunction", boolean.class, Object.class);

    private static final boolean CCE_ONLY = Options.getBooleanProperty("nashorn.cce");

    // don't create me!
    private NashornGuards() {
    }

    /**
     * Given a callsite descriptor and a link request, determine whether we should use an instanceof
     * check explicitly for the guard if needed, or if we should link it with a try/catch ClassCastException
     * combinator as its relink criteria - i.e. relink when CCE is thrown.
     *
     * @param desc     callsite descriptor
     * @param request  link request
     * @return true of explicit instanceof check is needed
     */
    public static boolean explicitInstanceOfCheck(final CallSiteDescriptor desc, final LinkRequest request) {
        //THIS is currently true, as the inliner encounters several problems with sun.misc.ValueConversions.castReference
        //otherwise. We should only use the exception based relink where we have no choice, and the result is faster code,
        //for example in the NativeArray, TypedArray, ContinuousArray getters. For the standard callsite, it appears that
        //we lose performance rather than gain it, due to JVM issues. :-(
        return !CCE_ONLY;
    }

    /**
     * Returns a guard that does an instanceof ScriptObject check on the receiver
     * @return guard
     */
    public static MethodHandle getScriptObjectGuard() {
        return IS_SCRIPTOBJECT;
    }

   /**
    * Get the guard that checks if an item is not a {@code JSObject}
    * @return method handle for guard
    */
   public static MethodHandle getNotJSObjectGuard() {
       return IS_NOT_JSOBJECT;
   }

    /**
     * Returns a guard that does an instanceof ScriptObject check on the receiver
     * @param explicitInstanceOfCheck - if false, then this is a nop, because it's all the guard does
     * @return guard
     */
    public static MethodHandle getScriptObjectGuard(final boolean explicitInstanceOfCheck) {
        return explicitInstanceOfCheck ? IS_SCRIPTOBJECT : null;
    }

    /**
     * Get the guard that checks if a {@link PropertyMap} is equal to
     * a known map, using reference comparison
     *
     * @param explicitInstanceOfCheck true if we should do an explicit script object instanceof check instead of just casting
     * @param map The map to check against. This will be bound to the guard method handle
     *
     * @return method handle for guard
     */
    public static MethodHandle getMapGuard(final PropertyMap map, final boolean explicitInstanceOfCheck) {
        return MH.insertArguments(explicitInstanceOfCheck ? IS_MAP_SCRIPTOBJECT : IS_MAP, 1, map);
    }

    /**
     * Determine whether the given callsite needs a guard.
     * @param property the property, or null
     * @param desc the callsite descriptor
     * @return true if a guard should be used for this callsite
     */
    static boolean needsGuard(final Property property, final CallSiteDescriptor desc) {
        return property == null || property.isConfigurable()
                || property.isBound() || property.hasDualFields()
                || !NashornCallSiteDescriptor.isFastScope(desc) || property.canChangeType();
    }

    /**
     * Get the guard for a property access. This returns an identity guard for non-configurable global properties
     * and a map guard for everything else.
     *
     * @param sobj the first object in the prototype chain
     * @param property the property
     * @param desc the callsite descriptor
     * @param explicitInstanceOfCheck true if we should do an explicit script object instanceof check instead of just casting
     * @return method handle for guard
     */
    public static MethodHandle getGuard(final ScriptObject sobj, final Property property, final CallSiteDescriptor desc, final boolean explicitInstanceOfCheck) {
        if (!needsGuard(property, desc)) {
            return null;
        }
        if (NashornCallSiteDescriptor.isScope(desc)) {
            if (property != null && property.isBound() && !property.canChangeType()) {
                // This is a declared top level variables in main script or eval, use identity guard.
                return getIdentityGuard(sobj);
            }
            if (!(sobj instanceof Global) && (property == null || property.isConfigurable())) {
                // Undeclared variables in nested evals need stronger guards
                return combineGuards(getIdentityGuard(sobj), getMapGuard(sobj.getMap(), explicitInstanceOfCheck));
            }
        }
        return getMapGuard(sobj.getMap(), explicitInstanceOfCheck);
    }


    /**
     * Get a guard that checks referential identity of the current object.
     *
     * @param sobj the self object
     * @return true if same self object instance
     */
    public static MethodHandle getIdentityGuard(final ScriptObject sobj) {
        return MH.insertArguments(SAME_OBJECT, 1, new WeakReference<>(sobj));
    }

    /**
     * Get a guard that checks if in item is a JS string.
     *
     * @return method handle for guard
     */
    public static MethodHandle getStringGuard() {
        return JSType.IS_STRING.methodHandle();
    }

    /**
     * Get a guard that checks if in item is a JS number.
     *
     * @return method handle for guard
     */
    public static MethodHandle getNumberGuard() {
        return JSType.IS_NUMBER.methodHandle();
    }

    /**
     * Combine two method handles of type {@code (Object)boolean} using logical AND.
     *
     * @param guard1 the first guard
     * @param guard2 the second guard, only invoked if guard1 returns true
     * @return true if both guard1 and guard2 returned true
     */
    public static MethodHandle combineGuards(final MethodHandle guard1, final MethodHandle guard2) {
        if (guard1 == null) {
            return guard2;
        } else if (guard2 == null) {
            return guard1;
        } else {
            return MH.guardWithTest(guard1, guard2, MH.dropArguments(MH.constant(boolean.class, false), 0, Object.class));
        }
    }

    @SuppressWarnings("unused")
    private static boolean isScriptObject(final Object self) {
        return self instanceof ScriptObject;
    }

    @SuppressWarnings("unused")
    private static boolean isScriptObject(final Class<? extends ScriptObject> clazz, final Object self) {
        return clazz.isInstance(self);
    }

    @SuppressWarnings("unused")
    private static boolean isMap(final ScriptObject self, final PropertyMap map) {
        return self.getMap() == map;
    }

    @SuppressWarnings("unused")
    private static boolean isNotJSObject(final Object self) {
        return !(self instanceof JSObject);
    }

    @SuppressWarnings("unused")
    private static boolean isMap(final Object self, final PropertyMap map) {
        return self instanceof ScriptObject && ((ScriptObject)self).getMap() == map;
    }


    @SuppressWarnings("unused")
    private static boolean sameObject(final Object self, final WeakReference<ScriptObject> ref) {
        return self == ref.get();
    }

    @SuppressWarnings("unused")
    private static boolean isScriptFunction(final Object self) {
        return self instanceof ScriptFunction;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), NashornGuards.class, name, MH.type(rtype, types));
    }
}
