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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.SOURCE;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import jdk.nashorn.internal.scripts.JS;

/**
 * This class provides support for external debuggers.  Its primary purpose is
 * is to simplify the debugger tasks and provide better performance.
 * Even though the methods are not public, there are still part of the
 * external debugger interface.
 */
final class DebuggerSupport {
    /**
     * Hook to force the loading of the DebuggerSupport class so that it is
     * available to external debuggers.
     */
    static boolean FORCELOAD = true;

    static {
        /**
         * Hook to force the loading of the DebuggerValueDesc class so that it is
         * available to external debuggers.
         */
        @SuppressWarnings("unused")
        final
        DebuggerValueDesc forceLoad = new DebuggerValueDesc(null, false, null, null);

        // Hook to force the loading of the SourceInfo class
        @SuppressWarnings("unused")
        final
        SourceInfo srcInfo = new SourceInfo(null, 0, null, null);
    }

    /** This class is used to send a bulk description of a value. */
    static class DebuggerValueDesc {
        /** Property key (or index) or field name. */
        final String key;

        /** If the value is expandable. */
        final boolean expandable;

        /** Property or field value as object. */
        final Object valueAsObject;

        /** Property or field value as string. */
        final String valueAsString;

        DebuggerValueDesc(final String key, final boolean expandable, final Object valueAsObject, final String valueAsString) {
            this.key           = key;
            this.expandable    = expandable;
            this.valueAsObject = valueAsObject;
            this.valueAsString = valueAsString;
        }
    }

    static class SourceInfo {
        final String name;
        final URL    url;
        final int    hash;
        final char[] content;

        SourceInfo(final String name, final int hash, final URL url, final char[] content) {
            this.name    = name;
            this.hash    = hash;
            this.url     = url;
            this.content = content;
        }
    }

    /**
     * Hook that is called just before invoking method handle
     * from ScriptFunctionData via invoke, constructor method calls.
     *
     * @param mh script class method about to be invoked.
     */
    static void notifyInvoke(final MethodHandle mh) {
        // Do nothing here. This is placeholder method on which a
        // debugger can place a breakpoint so that it can access the
        // (script class) method handle that is about to be invoked.
        // See ScriptFunctionData.invoke and ScriptFunctionData.construct.
    }

    /**
     * Return the script source info for the given script class.
     *
     * @param clazz compiled script class
     * @return SourceInfo
     */
    static SourceInfo getSourceInfo(final Class<?> clazz) {
        if (JS.class.isAssignableFrom(clazz)) {
            try {
                final Field sourceField = clazz.getDeclaredField(SOURCE.symbolName());
                sourceField.setAccessible(true);
                final Source src = (Source) sourceField.get(null);
                return src.getSourceInfo();
            } catch (final IllegalAccessException | NoSuchFieldException ignored) {
                return null;
            }
        }

        return null;
    }

    /**
     * Return the current context global.
     * @return context global.
     */
    static Object getGlobal() {
        return Context.getGlobal();
    }

    /**
     * Call eval on the current global.
     * @param scope           Scope to use.
     * @param self            Receiver to use.
     * @param string          String to evaluate.
     * @param returnException true if exceptions are to be returned.
     * @return Result of eval, or, an exception or null depending on returnException.
     */
    static Object eval(final ScriptObject scope, final Object self, final String string, final boolean returnException) {
        final ScriptObject global = Context.getGlobal();
        final ScriptObject initialScope = scope != null ? scope : global;
        final Object callThis = self != null ? self : global;
        final Context context = global.getContext();

        try {
            return context.eval(initialScope, string, callThis, ScriptRuntime.UNDEFINED);
        } catch (final Throwable ex) {
            return returnException ? ex : null;
        }
    }

    /**
     * This method returns a bulk description of an object's properties.
     * @param object Script object to be displayed by the debugger.
     * @param all    true if to include non-enumerable values.
     * @return An array of DebuggerValueDesc.
     */
    static DebuggerValueDesc[] valueInfos(final Object object, final boolean all) {
        assert object instanceof ScriptObject;
        return getDebuggerValueDescs((ScriptObject)object, all, new HashSet<>());
    }

    /**
     * This method returns a debugger description of the value.
     * @param name  Name of value (property name).
     * @param value Data value.
     * @param all   true if to include non-enumerable values.
     * @return A DebuggerValueDesc.
     */
    static DebuggerValueDesc valueInfo(final String name, final Object value, final boolean all) {
        return valueInfo(name, value, all, new HashSet<>());
    }

   /**
     * This method returns a debugger description of the value.
     * @param name       Name of value (property name).
     * @param value      Data value.
     * @param all        true if to include non-enumerable values.
     * @param duplicates Duplication set to avoid cycles.
     * @return A DebuggerValueDesc.
     */
    private static DebuggerValueDesc valueInfo(final String name, final Object value, final boolean all, final Set<Object> duplicates) {
        if (value instanceof ScriptObject && !(value instanceof ScriptFunction)) {
            final ScriptObject object = (ScriptObject)value;
            return new DebuggerValueDesc(name, !object.isEmpty(), value, objectAsString(object, all, duplicates));
        }
        return new DebuggerValueDesc(name, false, value, valueAsString(value));
    }

    /**
     * Generate the descriptions for an object's properties.
     * @param object     Object to introspect.
     * @param all        true if to include non-enumerable values.
     * @param duplicates Duplication set to avoid cycles.
     * @return An array of DebuggerValueDesc.
     */
    private static DebuggerValueDesc[] getDebuggerValueDescs(final ScriptObject object, final boolean all, final Set<Object> duplicates) {
        if (duplicates.contains(object)) {
            return null;
        }

        duplicates.add(object);

        final String[] keys = object.getOwnKeys(all);
        final DebuggerValueDesc[] descs = new DebuggerValueDesc[keys.length];

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            descs[i] = valueInfo(key, object.get(key), all, duplicates);
        }

        duplicates.remove(object);

        return descs;
    }

    /**
     * Generate a string representation of a Script object.
     * @param object     Script object to represent.
     * @param all        true if to include non-enumerable values.
     * @param duplicates Duplication set to avoid cycles.
     * @return String representation.
     */
    private static String objectAsString(final ScriptObject object, final boolean all, final Set<Object> duplicates) {
        final StringBuilder sb = new StringBuilder();

        if (ScriptObject.isArray(object)) {
            sb.append('[');
            final long length = (long) object.getDouble("length", INVALID_PROGRAM_POINT);

            for (long i = 0; i < length; i++) {
                if (object.has(i)) {
                    final Object valueAsObject = object.get(i);
                    final boolean isUndefined = valueAsObject == ScriptRuntime.UNDEFINED;

                    if (isUndefined) {
                        if (i != 0) {
                            sb.append(",");
                        }
                    } else {
                        if (i != 0) {
                            sb.append(", ");
                        }

                        if (valueAsObject instanceof ScriptObject && !(valueAsObject instanceof ScriptFunction)) {
                            final String objectString = objectAsString((ScriptObject)valueAsObject, all, duplicates);
                            sb.append(objectString != null ? objectString : "{...}");
                        } else {
                            sb.append(valueAsString(valueAsObject));
                        }
                    }
                } else {
                    if (i != 0) {
                        sb.append(',');
                    }
                }
            }

            sb.append(']');
        } else {
            sb.append('{');
            final DebuggerValueDesc[] descs = getDebuggerValueDescs(object, all, duplicates);

            if (descs != null) {
                for (int i = 0; i < descs.length; i++) {
                    if (i != 0) {
                        sb.append(", ");
                    }

                    final String valueAsString = descs[i].valueAsString;
                    sb.append(descs[i].key);
                    sb.append(": ");
                    sb.append(valueAsString);
                }
            }

            sb.append('}');
        }

        return sb.toString();
    }

    /**
     * This method returns a string representation of a value.
     * @param value Arbitrary value to be displayed by the debugger.
     * @return A string representation of the value or an array of DebuggerValueDesc.
     */
    static String valueAsString(final Object value) {
        final JSType type = JSType.of(value);

        switch (type) {
        case BOOLEAN:
            return value.toString();

        case STRING:
            return escape(value.toString());

        case NUMBER:
            return JSType.toString(((Number)value).doubleValue());

        case NULL:
            return "null";

        case UNDEFINED:
            return "undefined";

        case OBJECT:
            return ScriptRuntime.safeToString(value);

        case FUNCTION:
            if (value instanceof ScriptFunction) {
                return ((ScriptFunction)value).toSource();
            }
            return value.toString();

        default:
            return value.toString();
        }
    }

    /**
     * Escape a string into a form that can be parsed by JavaScript.
     * @param value String to be escaped.
     * @return Escaped string.
     */
    private static String escape(final String value) {
        final StringBuilder sb = new StringBuilder();

        sb.append("\"");

        for (final char ch : value.toCharArray()) {
            switch (ch) {
            case '\\':
                sb.append("\\\\");
                break;
            case '"':
                sb.append("\\\"");
                break;
            case '\'':
                sb.append("\\\'");
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            default:
                if (ch < ' ' || ch >= 0xFF) {
                    sb.append("\\u");

                    final String hex = Integer.toHexString(ch);
                    for (int i = hex.length(); i < 4; i++) {
                        sb.append('0');
                    }
                    sb.append(hex);
                } else {
                    sb.append(ch);
                }

                break;
            }
        }

        sb.append("\"");

        return sb.toString();
    }
}


