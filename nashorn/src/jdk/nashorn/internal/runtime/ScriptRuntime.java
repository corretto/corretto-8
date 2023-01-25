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

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static jdk.nashorn.internal.runtime.ECMAErrors.referenceError;
import static jdk.nashorn.internal.runtime.ECMAErrors.syntaxError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.JSType.isRepresentableAsInt;
import static jdk.nashorn.internal.runtime.JSType.isString;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import jdk.internal.dynalink.beans.StaticClass;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.codegen.ApplySpecialization;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.ir.debug.JSONWriter;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.objects.NativeObject;
import jdk.nashorn.internal.parser.Lexer;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Utilities to be called by JavaScript runtime API and generated classes.
 */

public final class ScriptRuntime {
    private ScriptRuntime() {
    }

    /** Singleton representing the empty array object '[]' */
    public static final Object[] EMPTY_ARRAY = new Object[0];

    /** Unique instance of undefined. */
    public static final Undefined UNDEFINED = Undefined.getUndefined();

    /**
     * Unique instance of undefined used to mark empty array slots.
     * Can't escape the array.
     */
    public static final Undefined EMPTY = Undefined.getEmpty();

    /** Method handle to generic + operator, operating on objects */
    public static final Call ADD = staticCallNoLookup(ScriptRuntime.class, "ADD", Object.class, Object.class, Object.class);

    /** Method handle to generic === operator, operating on objects */
    public static final Call EQ_STRICT = staticCallNoLookup(ScriptRuntime.class, "EQ_STRICT", boolean.class, Object.class, Object.class);

    /** Method handle used to enter a {@code with} scope at runtime. */
    public static final Call OPEN_WITH = staticCallNoLookup(ScriptRuntime.class, "openWith", ScriptObject.class, ScriptObject.class, Object.class);

    /**
     * Method used to place a scope's variable into the Global scope, which has to be done for the
     * properties declared at outermost script level.
     */
    public static final Call MERGE_SCOPE = staticCallNoLookup(ScriptRuntime.class, "mergeScope", ScriptObject.class, ScriptObject.class);

    /**
     * Return an appropriate iterator for the elements in a for-in construct
     */
    public static final Call TO_PROPERTY_ITERATOR = staticCallNoLookup(ScriptRuntime.class, "toPropertyIterator", Iterator.class, Object.class);

    /**
     * Return an appropriate iterator for the elements in a for-each construct
     */
    public static final Call TO_VALUE_ITERATOR = staticCallNoLookup(ScriptRuntime.class, "toValueIterator", Iterator.class, Object.class);

    /**
      * Method handle for apply. Used from {@link ScriptFunction} for looking up calls to
      * call sites that are known to be megamorphic. Using an invoke dynamic here would
      * lead to the JVM deoptimizing itself to death
      */
    public static final Call APPLY = staticCall(MethodHandles.lookup(), ScriptRuntime.class, "apply", Object.class, ScriptFunction.class, Object.class, Object[].class);

    /**
     * Throws a reference error for an undefined variable.
     */
    public static final Call THROW_REFERENCE_ERROR = staticCall(MethodHandles.lookup(), ScriptRuntime.class, "throwReferenceError", void.class, String.class);

    /**
     * Throws a reference error for an undefined variable.
     */
    public static final Call THROW_CONST_TYPE_ERROR = staticCall(MethodHandles.lookup(), ScriptRuntime.class, "throwConstTypeError", void.class, String.class);

    /**
     * Used to invalidate builtin names, e.g "Function" mapping to all properties in Function.prototype and Function.prototype itself.
     */
    public static final Call INVALIDATE_RESERVED_BUILTIN_NAME = staticCallNoLookup(ScriptRuntime.class, "invalidateReservedBuiltinName", void.class, String.class);

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final Object tag, final int deflt) {
        if (tag instanceof Number) {
            final double d = ((Number)tag).doubleValue();
            if (isRepresentableAsInt(d)) {
                return (int)d;
            }
        }
        return deflt;
    }

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final boolean tag, final int deflt) {
        return deflt;
    }

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final long tag, final int deflt) {
        return isRepresentableAsInt(tag) ? (int)tag : deflt;
    }

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final double tag, final int deflt) {
        return isRepresentableAsInt(tag) ? (int)tag : deflt;
    }

    /**
     * This is the builtin implementation of {@code Object.prototype.toString}
     * @param self reference
     * @return string representation as object
     */
    public static String builtinObjectToString(final Object self) {
        String className;
        // Spec tells us to convert primitives by ToObject..
        // But we don't need to -- all we need is the right class name
        // of the corresponding primitive wrapper type.

        final JSType type = JSType.ofNoFunction(self);

        switch (type) {
        case BOOLEAN:
            className = "Boolean";
            break;
        case NUMBER:
            className = "Number";
            break;
        case STRING:
            className = "String";
            break;
        // special case of null and undefined
        case NULL:
            className = "Null";
            break;
        case UNDEFINED:
            className = "Undefined";
            break;
        case OBJECT:
            if (self instanceof ScriptObject) {
                className = ((ScriptObject)self).getClassName();
            } else if (self instanceof JSObject) {
                className = ((JSObject)self).getClassName();
            } else {
                className = self.getClass().getName();
            }
            break;
        default:
            // Nashorn extension: use Java class name
            className = self.getClass().getName();
            break;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("[object ");
        sb.append(className);
        sb.append(']');

        return sb.toString();
    }

    /**
     * This is called whenever runtime wants to throw an error and wants to provide
     * meaningful information about an object. We don't want to call toString which
     * ends up calling "toString" from script world which may itself throw error.
     * When we want to throw an error, we don't additional error from script land
     * -- which may sometimes lead to infinite recursion.
     *
     * @param obj Object to converted to String safely (without calling user script)
     * @return safe String representation of the given object
     */
    public static String safeToString(final Object obj) {
        return JSType.toStringImpl(obj, true);
    }

    /**
     * Returns an iterator over property identifiers used in the {@code for...in} statement. Note that the ECMAScript
     * 5.1 specification, chapter 12.6.4. uses the terminology "property names", which seems to imply that the property
     * identifiers are expected to be strings, but this is not actually spelled out anywhere, and Nashorn will in some
     * cases deviate from this. Namely, we guarantee to always return an iterator over {@link String} values for any
     * built-in JavaScript object. We will however return an iterator over {@link Integer} objects for native Java
     * arrays and {@link List} objects, as well as arbitrary objects representing keys of a {@link Map}. Therefore, the
     * expression {@code typeof i} within a {@code for(i in obj)} statement can return something other than
     * {@code string} when iterating over native Java arrays, {@code List}, and {@code Map} objects.
     * @param obj object to iterate on.
     * @return iterator over the object's property names.
     */
    public static Iterator<?> toPropertyIterator(final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).propertyIterator();
        }

        if (obj != null && obj.getClass().isArray()) {
            return new RangeIterator(Array.getLength(obj));
        }

        if (obj instanceof JSObject) {
            return ((JSObject)obj).keySet().iterator();
        }

        if (obj instanceof List) {
            return new RangeIterator(((List<?>)obj).size());
        }

        if (obj instanceof Map) {
            return ((Map<?,?>)obj).keySet().iterator();
        }

        final Object wrapped = Global.instance().wrapAsObject(obj);
        if (wrapped instanceof ScriptObject) {
            return ((ScriptObject)wrapped).propertyIterator();
        }

        return Collections.emptyIterator();
    }

    private static final class RangeIterator implements Iterator<Integer> {
        private final int length;
        private int index;

        RangeIterator(final int length) {
            this.length = length;
        }

        @Override
        public boolean hasNext() {
            return index < length;
        }

        @Override
        public Integer next() {
            return index++;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }

    /**
     * Returns an iterator over property values used in the {@code for each...in} statement. Aside from built-in JS
     * objects, it also operates on Java arrays, any {@link Iterable}, as well as on {@link Map} objects, iterating over
     * map values.
     * @param obj object to iterate on.
     * @return iterator over the object's property values.
     */
    public static Iterator<?> toValueIterator(final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).valueIterator();
        }

        if (obj != null && obj.getClass().isArray()) {
            final Object array  = obj;
            final int    length = Array.getLength(obj);

            return new Iterator<Object>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < length;
                }

                @Override
                public Object next() {
                    if (index >= length) {
                        throw new NoSuchElementException();
                    }
                    return Array.get(array, index++);
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("remove");
                }
            };
        }

        if (obj instanceof JSObject) {
            return ((JSObject)obj).values().iterator();
        }

        if (obj instanceof Map) {
            return ((Map<?,?>)obj).values().iterator();
        }

        if (obj instanceof Iterable) {
            return ((Iterable<?>)obj).iterator();
        }

        final Object wrapped = Global.instance().wrapAsObject(obj);
        if (wrapped instanceof ScriptObject) {
            return ((ScriptObject)wrapped).valueIterator();
        }

        return Collections.emptyIterator();
    }

    /**
     * Merge a scope into its prototype's map.
     * Merge a scope into its prototype.
     *
     * @param scope Scope to merge.
     * @return prototype object after merge
     */
    public static ScriptObject mergeScope(final ScriptObject scope) {
        final ScriptObject parentScope = scope.getProto();
        parentScope.addBoundProperties(scope);
        return parentScope;
    }

    /**
     * Call a function given self and args. If the number of the arguments is known in advance, you can likely achieve
     * better performance by {@link Bootstrap#createDynamicInvoker(String, Class, Class...) creating a dynamic invoker}
     * for operation {@code "dyn:call"}, then using its {@link MethodHandle#invokeExact(Object...)} method instead.
     *
     * @param target ScriptFunction object.
     * @param self   Receiver in call.
     * @param args   Call arguments.
     * @return Call result.
     */
    public static Object apply(final ScriptFunction target, final Object self, final Object... args) {
        try {
            return target.invoke(self, args);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Throws a reference error for an undefined variable.
     *
     * @param name the variable name
     */
    public static void throwReferenceError(final String name) {
        throw referenceError("not.defined", name);
    }

    /**
     * Throws a type error for an assignment to a const.
     *
     * @param name the const name
     */
    public static void throwConstTypeError(final String name) {
        throw typeError("assign.constant", name);
    }

    /**
     * Call a script function as a constructor with given args.
     *
     * @param target ScriptFunction object.
     * @param args   Call arguments.
     * @return Constructor call result.
     */
    public static Object construct(final ScriptFunction target, final Object... args) {
        try {
            return target.construct(args);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Generic implementation of ECMA 9.12 - SameValue algorithm
     *
     * @param x first value to compare
     * @param y second value to compare
     *
     * @return true if both objects have the same value
     */
    public static boolean sameValue(final Object x, final Object y) {
        final JSType xType = JSType.ofNoFunction(x);
        final JSType yType = JSType.ofNoFunction(y);

        if (xType != yType) {
            return false;
        }

        if (xType == JSType.UNDEFINED || xType == JSType.NULL) {
            return true;
        }

        if (xType == JSType.NUMBER) {
            final double xVal = ((Number)x).doubleValue();
            final double yVal = ((Number)y).doubleValue();

            if (Double.isNaN(xVal) && Double.isNaN(yVal)) {
                return true;
            }

            // checking for xVal == -0.0 and yVal == +0.0 or vice versa
            if (xVal == 0.0 && Double.doubleToLongBits(xVal) != Double.doubleToLongBits(yVal)) {
                return false;
            }

            return xVal == yVal;
        }

        if (xType == JSType.STRING || yType == JSType.BOOLEAN) {
            return x.equals(y);
        }

        return x == y;
    }

    /**
     * Returns AST as JSON compatible string. This is used to
     * implement "parse" function in resources/parse.js script.
     *
     * @param code code to be parsed
     * @param name name of the code source (used for location)
     * @param includeLoc tells whether to include location information for nodes or not
     * @return JSON string representation of AST of the supplied code
     */
    public static String parse(final String code, final String name, final boolean includeLoc) {
        return JSONWriter.parse(Context.getContextTrusted(), code, name, includeLoc);
    }

    /**
     * Test whether a char is valid JavaScript whitespace
     * @param ch a char
     * @return true if valid JavaScript whitespace
     */
    public static boolean isJSWhitespace(final char ch) {
        return Lexer.isJSWhitespace(ch);
    }

    /**
     * Entering a {@code with} node requires new scope. This is the implementation. When exiting the with statement,
     * use {@link ScriptObject#getProto()} on the scope.
     *
     * @param scope      existing scope
     * @param expression expression in with
     *
     * @return {@link WithObject} that is the new scope
     */
    public static ScriptObject openWith(final ScriptObject scope, final Object expression) {
        final Global global = Context.getGlobal();
        if (expression == UNDEFINED) {
            throw typeError(global, "cant.apply.with.to.undefined");
        } else if (expression == null) {
            throw typeError(global, "cant.apply.with.to.null");
        }

        if (expression instanceof ScriptObjectMirror) {
            final Object unwrapped = ScriptObjectMirror.unwrap(expression, global);
            if (unwrapped instanceof ScriptObject) {
                return new WithObject(scope, (ScriptObject)unwrapped);
            }
            // foreign ScriptObjectMirror
            final ScriptObject exprObj = global.newObject();
            NativeObject.bindAllProperties(exprObj, (ScriptObjectMirror)expression);
            return new WithObject(scope, exprObj);
        }

        final Object wrappedExpr = JSType.toScriptObject(global, expression);
        if (wrappedExpr instanceof ScriptObject) {
            return new WithObject(scope, (ScriptObject)wrappedExpr);
        }

        throw typeError(global, "cant.apply.with.to.non.scriptobject");
    }

    /**
     * ECMA 11.6.1 - The addition operator (+) - generic implementation
     *
     * @param x  first term
     * @param y  second term
     *
     * @return result of addition
     */
    public static Object ADD(final Object x, final Object y) {
        // This prefix code to handle Number special is for optimization.
        final boolean xIsNumber = x instanceof Number;
        final boolean yIsNumber = y instanceof Number;

        if (xIsNumber && yIsNumber) {
             return ((Number)x).doubleValue() + ((Number)y).doubleValue();
        }

        final boolean xIsUndefined = x == UNDEFINED;
        final boolean yIsUndefined = y == UNDEFINED;

        if (xIsNumber && yIsUndefined || xIsUndefined && yIsNumber || xIsUndefined && yIsUndefined) {
            return Double.NaN;
        }

        // code below is as per the spec.
        final Object xPrim = JSType.toPrimitive(x);
        final Object yPrim = JSType.toPrimitive(y);

        if (isString(xPrim) || isString(yPrim)) {
            try {
                return new ConsString(JSType.toCharSequence(xPrim), JSType.toCharSequence(yPrim));
            } catch (final IllegalArgumentException iae) {
                throw rangeError(iae, "concat.string.too.big");
            }
        }

        return JSType.toNumber(xPrim) + JSType.toNumber(yPrim);
    }

    /**
     * Debugger hook.
     * TODO: currently unimplemented
     *
     * @return undefined
     */
    public static Object DEBUGGER() {
        return UNDEFINED;
    }

    /**
     * New hook
     *
     * @param clazz type for the clss
     * @param args  constructor arguments
     *
     * @return undefined
     */
    public static Object NEW(final Object clazz, final Object... args) {
        return UNDEFINED;
    }

    /**
     * ECMA 11.4.3 The typeof Operator - generic implementation
     *
     * @param object   the object from which to retrieve property to type check
     * @param property property in object to check
     *
     * @return type name
     */
    public static Object TYPEOF(final Object object, final Object property) {
        Object obj = object;

        if (property != null) {
            if (obj instanceof ScriptObject) {
                obj = ((ScriptObject)obj).get(property);
                if(Global.isLocationPropertyPlaceholder(obj)) {
                    if(CompilerConstants.__LINE__.name().equals(property)) {
                        obj = Integer.valueOf(0);
                    } else {
                        obj = "";
                    }
                }
            } else if (object instanceof Undefined) {
                obj = ((Undefined)obj).get(property);
            } else if (object == null) {
                throw typeError("cant.get.property", safeToString(property), "null");
            } else if (JSType.isPrimitive(obj)) {
                obj = ((ScriptObject)JSType.toScriptObject(obj)).get(property);
            } else if (obj instanceof JSObject) {
                obj = ((JSObject)obj).getMember(property.toString());
            } else {
                obj = UNDEFINED;
            }
        }

        return JSType.of(obj).typeName();
    }

    /**
     * Throw ReferenceError when LHS of assignment or increment/decrement
     * operator is not an assignable node (say a literal)
     *
     * @param lhs Evaluated LHS
     * @param rhs Evaluated RHS
     * @param msg Additional LHS info for error message
     * @return undefined
     */
    public static Object REFERENCE_ERROR(final Object lhs, final Object rhs, final Object msg) {
        throw referenceError("cant.be.used.as.lhs", Objects.toString(msg));
    }

    /**
     * ECMA 11.4.1 - delete operation, generic implementation
     *
     * @param obj       object with property to delete
     * @param property  property to delete
     * @param strict    are we in strict mode
     *
     * @return true if property was successfully found and deleted
     */
    public static boolean DELETE(final Object obj, final Object property, final Object strict) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).delete(property, Boolean.TRUE.equals(strict));
        }

        if (obj instanceof Undefined) {
            return ((Undefined)obj).delete(property, false);
        }

        if (obj == null) {
            throw typeError("cant.delete.property", safeToString(property), "null");
        }

        if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).delete(property);
        }

        if (JSType.isPrimitive(obj)) {
            return ((ScriptObject) JSType.toScriptObject(obj)).delete(property, Boolean.TRUE.equals(strict));
        }

        if (obj instanceof JSObject) {
            ((JSObject)obj).removeMember(Objects.toString(property));
            return true;
        }

        // if object is not reference type, vacuously delete is successful.
        return true;
    }

    /**
     * ECMA 11.4.1 - delete operator, implementation for slow scopes
     *
     * This implementation of 'delete' walks the scope chain to find the scope that contains the
     * property to be deleted, then invokes delete on it.
     *
     * @param obj       top scope object
     * @param property  property to delete
     * @param strict    are we in strict mode
     *
     * @return true if property was successfully found and deleted
     */
    public static boolean SLOW_DELETE(final Object obj, final Object property, final Object strict) {
        if (obj instanceof ScriptObject) {
            ScriptObject sobj = (ScriptObject) obj;
            final String key = property.toString();
            while (sobj != null && sobj.isScope()) {
                final FindProperty find = sobj.findProperty(key, false);
                if (find != null) {
                    return sobj.delete(key, Boolean.TRUE.equals(strict));
                }
                sobj = sobj.getProto();
            }
        }
        return DELETE(obj, property, strict);
    }

    /**
     * ECMA 11.4.1 - delete operator, special case
     *
     * This is 'delete' that always fails. We have to check strict mode and throw error.
     * That is why this is a runtime function. Or else we could have inlined 'false'.
     *
     * @param property  property to delete
     * @param strict    are we in strict mode
     *
     * @return false always
     */
    public static boolean FAIL_DELETE(final Object property, final Object strict) {
        if (Boolean.TRUE.equals(strict)) {
            throw syntaxError("strict.cant.delete", safeToString(property));
        }
        return false;
    }

    /**
     * ECMA 11.9.1 - The equals operator (==) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if type coerced versions of objects are equal
     */
    public static boolean EQ(final Object x, final Object y) {
        return equals(x, y);
    }

    /**
     * ECMA 11.9.2 - The does-not-equal operator (==) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if type coerced versions of objects are not equal
     */
    public static boolean NE(final Object x, final Object y) {
        return !EQ(x, y);
    }

    /** ECMA 11.9.3 The Abstract Equality Comparison Algorithm */
    private static boolean equals(final Object x, final Object y) {
        if (x == y) {
            return true;
        }
        if (x instanceof ScriptObject && y instanceof ScriptObject) {
            return false; // x != y
        }
        if (x instanceof ScriptObjectMirror || y instanceof ScriptObjectMirror) {
            return ScriptObjectMirror.identical(x, y);
        }
        return equalValues(x, y);
    }

    /**
     * Extracted portion of {@code equals()} that compares objects by value (or by reference, if no known value
     * comparison applies).
     * @param x one value
     * @param y another value
     * @return true if they're equal according to 11.9.3
     */
    private static boolean equalValues(final Object x, final Object y) {
        final JSType xType = JSType.ofNoFunction(x);
        final JSType yType = JSType.ofNoFunction(y);

        if (xType == yType) {
            return equalSameTypeValues(x, y, xType);
        }

        return equalDifferentTypeValues(x, y, xType, yType);
    }

    /**
     * Extracted portion of {@link #equals(Object, Object)} and {@link #strictEquals(Object, Object)} that compares
     * values belonging to the same JSType.
     * @param x one value
     * @param y another value
     * @param type the common type for the values
     * @return true if they're equal
     */
    private static boolean equalSameTypeValues(final Object x, final Object y, final JSType type) {
        if (type == JSType.UNDEFINED || type == JSType.NULL) {
            return true;
        }

        if (type == JSType.NUMBER) {
            return ((Number)x).doubleValue() == ((Number)y).doubleValue();
        }

        if (type == JSType.STRING) {
            // String may be represented by ConsString
            return x.toString().equals(y.toString());
        }

        if (type == JSType.BOOLEAN) {
            return ((Boolean)x).booleanValue() == ((Boolean)y).booleanValue();
        }

        return x == y;
    }

    /**
     * Extracted portion of {@link #equals(Object, Object)} that compares values belonging to different JSTypes.
     * @param x one value
     * @param y another value
     * @param xType the type for the value x
     * @param yType the type for the value y
     * @return true if they're equal
     */
    private static boolean equalDifferentTypeValues(final Object x, final Object y, final JSType xType, final JSType yType) {
        if (isUndefinedAndNull(xType, yType) || isUndefinedAndNull(yType, xType)) {
            return true;
        } else if (isNumberAndString(xType, yType)) {
            return equalNumberToString(x, y);
        } else if (isNumberAndString(yType, xType)) {
            // Can reverse order as both are primitives
            return equalNumberToString(y, x);
        } else if (xType == JSType.BOOLEAN) {
            return equalBooleanToAny(x, y);
        } else if (yType == JSType.BOOLEAN) {
            // Can reverse order as y is primitive
            return equalBooleanToAny(y, x);
        } else if (isNumberOrStringAndObject(xType, yType)) {
            return equalNumberOrStringToObject(x, y);
        } else if (isNumberOrStringAndObject(yType, xType)) {
            // Can reverse order as y is primitive
            return equalNumberOrStringToObject(y, x);
        }

        return false;
    }

    private static boolean isUndefinedAndNull(final JSType xType, final JSType yType) {
        return xType == JSType.UNDEFINED && yType == JSType.NULL;
    }

    private static boolean isNumberAndString(final JSType xType, final JSType yType) {
        return xType == JSType.NUMBER && yType == JSType.STRING;
    }

    private static boolean isNumberOrStringAndObject(final JSType xType, final JSType yType) {
        return (xType == JSType.NUMBER || xType == JSType.STRING) && yType == JSType.OBJECT;
    }

    private static boolean equalNumberToString(final Object num, final Object str) {
        // Specification says comparing a number to string should be done as "equals(num, JSType.toNumber(str))". We
        // can short circuit it to this as we know that "num" is a number, so it'll end up being a number-number
        // comparison.
        return ((Number)num).doubleValue() == JSType.toNumber(str.toString());
    }

    private static boolean equalBooleanToAny(final Object bool, final Object any) {
        return equals(JSType.toNumber((Boolean)bool), any);
    }

    private static boolean equalNumberOrStringToObject(final Object numOrStr, final Object any) {
        return equals(numOrStr, JSType.toPrimitive(any));
    }

    /**
     * ECMA 11.9.4 - The strict equal operator (===) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if objects are equal
     */
    public static boolean EQ_STRICT(final Object x, final Object y) {
        return strictEquals(x, y);
    }

    /**
     * ECMA 11.9.5 - The strict non equal operator (!==) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if objects are not equal
     */
    public static boolean NE_STRICT(final Object x, final Object y) {
        return !EQ_STRICT(x, y);
    }

    /** ECMA 11.9.6 The Strict Equality Comparison Algorithm */
    private static boolean strictEquals(final Object x, final Object y) {
        // NOTE: you might be tempted to do a quick x == y comparison. Remember, though, that any Double object having
        // NaN value is not equal to itself by value even though it is referentially.

        final JSType xType = JSType.ofNoFunction(x);
        final JSType yType = JSType.ofNoFunction(y);

        if (xType != yType) {
            return false;
        }

        return equalSameTypeValues(x, y, xType);
    }

    /**
     * ECMA 11.8.6 - The in operator - generic implementation
     *
     * @param property property to check for
     * @param obj object in which to check for property
     *
     * @return true if objects are equal
     */
    public static boolean IN(final Object property, final Object obj) {
        final JSType rvalType = JSType.ofNoFunction(obj);

        if (rvalType == JSType.OBJECT) {
            if (obj instanceof ScriptObject) {
                return ((ScriptObject)obj).has(property);
            }

            if (obj instanceof JSObject) {
                return ((JSObject)obj).hasMember(Objects.toString(property));
            }

            return false;
        }

        throw typeError("in.with.non.object", rvalType.toString().toLowerCase(Locale.ENGLISH));
    }

    /**
     * ECMA 11.8.6 - The strict instanceof operator - generic implementation
     *
     * @param obj first object to compare
     * @param clazz type to check against
     *
     * @return true if {@code obj} is an instanceof {@code clazz}
     */
    public static boolean INSTANCEOF(final Object obj, final Object clazz) {
        if (clazz instanceof ScriptFunction) {
            if (obj instanceof ScriptObject) {
                return ((ScriptObject)clazz).isInstance((ScriptObject)obj);
            }
            return false;
        }

        if (clazz instanceof StaticClass) {
            return ((StaticClass)clazz).getRepresentedClass().isInstance(obj);
        }

        if (clazz instanceof JSObject) {
            return ((JSObject)clazz).isInstance(obj);
        }

        // provide for reverse hook
        if (obj instanceof JSObject) {
            return ((JSObject)obj).isInstanceOf(clazz);
        }

        throw typeError("instanceof.on.non.object");
    }

    /**
     * ECMA 11.8.1 - The less than operator ({@literal <}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is less than y
     */
    public static boolean LT(final Object x, final Object y) {
        final Object px = JSType.toPrimitive(x, Number.class);
        final Object py = JSType.toPrimitive(y, Number.class);

        return areBothString(px, py) ? px.toString().compareTo(py.toString()) < 0 :
            JSType.toNumber(px) < JSType.toNumber(py);
    }

    private static boolean areBothString(final Object x, final Object y) {
        return isString(x) && isString(y);
    }

    /**
     * ECMA 11.8.2 - The greater than operator ({@literal >}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is greater than y
     */
    public static boolean GT(final Object x, final Object y) {
        final Object px = JSType.toPrimitive(x, Number.class);
        final Object py = JSType.toPrimitive(y, Number.class);

        return areBothString(px, py) ? px.toString().compareTo(py.toString()) > 0 :
            JSType.toNumber(px) > JSType.toNumber(py);
    }

    /**
     * ECMA 11.8.3 - The less than or equal operator ({@literal <=}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is less than or equal to y
     */
    public static boolean LE(final Object x, final Object y) {
        final Object px = JSType.toPrimitive(x, Number.class);
        final Object py = JSType.toPrimitive(y, Number.class);

        return areBothString(px, py) ? px.toString().compareTo(py.toString()) <= 0 :
            JSType.toNumber(px) <= JSType.toNumber(py);
    }

    /**
     * ECMA 11.8.4 - The greater than or equal operator ({@literal >=}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is greater than or equal to y
     */
    public static boolean GE(final Object x, final Object y) {
        final Object px = JSType.toPrimitive(x, Number.class);
        final Object py = JSType.toPrimitive(y, Number.class);

        return areBothString(px, py) ? px.toString().compareTo(py.toString()) >= 0 :
            JSType.toNumber(px) >= JSType.toNumber(py);
    }

    /**
     * Tag a reserved name as invalidated - used when someone writes
     * to a property with this name - overly conservative, but link time
     * is too late to apply e.g. apply-&gt;call specialization
     * @param name property name
     */
    public static void invalidateReservedBuiltinName(final String name) {
        final Context context = Context.getContextTrusted();
        final SwitchPoint sp = context.getBuiltinSwitchPoint(name);
        assert sp != null;
        context.getLogger(ApplySpecialization.class).info("Overwrote special name '" + name +"' - invalidating switchpoint");
        SwitchPoint.invalidateAll(new SwitchPoint[] { sp });
    }
}
