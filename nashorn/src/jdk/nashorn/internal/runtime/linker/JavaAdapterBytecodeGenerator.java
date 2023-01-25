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

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_FINAL;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_VARARGS;
import static jdk.internal.org.objectweb.asm.Opcodes.ACONST_NULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP;
import static jdk.internal.org.objectweb.asm.Opcodes.IFNONNULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ILOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ISTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.POP;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.linker.AdaptationResult.Outcome.ERROR_NO_ACCESSIBLE_CONSTRUCTOR;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.InstructionAdapter;
import jdk.nashorn.api.scripting.ScriptUtils;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.linker.AdaptationResult.Outcome;
import sun.reflect.CallerSensitive;

/**
 * Generates bytecode for a Java adapter class. Used by the {@link JavaAdapterFactory}.
 * </p><p>
 * For every protected or public constructor in the extended class, the adapter class will have either one or two
 * public constructors (visibility of protected constructors in the extended class is promoted to public).
 * <li>
 * <li>For adapter classes with instance-level overrides, a constructor taking a trailing ScriptObject argument preceded
 * by original constructor arguments is always created on the adapter class. When such a constructor is invoked, the
 * passed ScriptObject's member functions are used to implement and/or override methods on the original class,
 * dispatched by name. A single JavaScript function will act as the implementation for all overloaded methods of the
 * same name. When methods on an adapter instance are invoked, the functions are invoked having the ScriptObject passed
 * in the instance constructor as their "this". Subsequent changes to the ScriptObject (reassignment or removal of its
 * functions) are not reflected in the adapter instance; the method implementations are bound to functions at
 * constructor invocation time.
 * {@code java.lang.Object} methods {@code equals}, {@code hashCode}, and {@code toString} can also be overridden. The
 * only restriction is that since every JavaScript object already has a {@code toString} function through the
 * {@code Object.prototype}, the {@code toString} in the adapter is only overridden if the passed ScriptObject has a
 * {@code toString} function as its own property, and not inherited from a prototype. All other adapter methods can be
 * implemented or overridden through a prototype-inherited function of the ScriptObject passed to the constructor too.
 * </li>
 * <li>
 * If the original types collectively have only one abstract method, or have several of them, but all share the
 * same name, an additional constructor for instance-level override adapter is provided for every original constructor;
 * this one takes a ScriptFunction as its last argument preceded by original constructor arguments. This constructor
 * will use the passed function as the implementation for all abstract methods. For consistency, any concrete methods
 * sharing the single abstract method name will also be overridden by the function. When methods on the adapter instance
 * are invoked, the ScriptFunction is invoked with UNDEFINED or Global as its "this" depending whether the function is
 * strict or not.
 * </li>
 * <li>
 * If the adapter being generated can have class-level overrides, constructors taking same arguments as the superclass
 * constructors are created. These constructors simply delegate to the superclass constructor. They are simply used to
 * create instances of the adapter class, with no instance-level overrides, as they don't have them. If the original
 * class' constructor was variable arity, the adapter constructor will also be variable arity. Protected constructors
 * are exposed as public.
 * </li>
 * </ul>
 * </p><p>
 * For adapter methods that return values, all the JavaScript-to-Java conversions supported by Nashorn will be in effect
 * to coerce the JavaScript function return value to the expected Java return type.
 * </p><p>
 * Since we are adding a trailing argument to the generated constructors in the adapter class, they will never be
 * declared as variable arity, even if the original constructor in the superclass was declared as variable arity. The
 * reason we are passing the additional argument at the end of the argument list instead at the front is that the
 * source-level script expression <code>new X(a, b) { ... }</code> (which is a proprietary syntax extension Nashorn uses
 * to resemble Java anonymous classes) is actually equivalent to <code>new X(a, b, { ... })</code>.
 * </p><p>
 * It is possible to create two different adapter classes: those that can have class-level overrides, and those that can
 * have instance-level overrides. When {@link JavaAdapterFactory#getAdapterClassFor(Class[], ScriptObject)} is invoked
 * with non-null {@code classOverrides} parameter, an adapter class is created that can have class-level overrides, and
 * the passed script object will be used as the implementations for its methods, just as in the above case of the
 * constructor taking a script object. Note that in the case of class-level overrides, a new adapter class is created on
 * every invocation, and the implementation object is bound to the class, not to any instance. All created instances
 * will share these functions. If it is required to have both class-level overrides and instance-level overrides, the
 * class-level override adapter class should be subclassed with an instance-override adapter. Since adapters delegate to
 * super class when an overriding method handle is not specified, this will behave as expected. It is not possible to
 * have both class-level and instance-level overrides in the same class for security reasons: adapter classes are
 * defined with a protection domain of their creator code, and an adapter class that has both class and instance level
 * overrides would need to have two potentially different protection domains: one for class-based behavior and one for
 * instance-based behavior; since Java classes can only belong to a single protection domain, this could not be
 * implemented securely.
 */
final class JavaAdapterBytecodeGenerator {
    private static final Type SCRIPTUTILS_TYPE = Type.getType(ScriptUtils.class);
    private static final Type OBJECT_TYPE = Type.getType(Object.class);
    private static final Type CLASS_TYPE  = Type.getType(Class.class);

    static final String OBJECT_TYPE_NAME  = OBJECT_TYPE.getInternalName();
    static final String SCRIPTUTILS_TYPE_NAME  = SCRIPTUTILS_TYPE.getInternalName();

    static final String INIT = "<init>";

    static final String GLOBAL_FIELD_NAME = "global";

    // "global" is declared as Object instead of Global - avoid static references to internal Nashorn classes when possible.
    static final String GLOBAL_TYPE_DESCRIPTOR = OBJECT_TYPE.getDescriptor();

    static final String SET_GLOBAL_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE);
    static final String VOID_NOARG_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE);

    private static final Type SCRIPT_OBJECT_TYPE = Type.getType(ScriptObject.class);
    private static final Type SCRIPT_FUNCTION_TYPE = Type.getType(ScriptFunction.class);
    private static final Type STRING_TYPE = Type.getType(String.class);
    private static final Type METHOD_TYPE_TYPE = Type.getType(MethodType.class);
    private static final Type METHOD_HANDLE_TYPE = Type.getType(MethodHandle.class);
    private static final String GET_HANDLE_OBJECT_DESCRIPTOR = Type.getMethodDescriptor(METHOD_HANDLE_TYPE,
            OBJECT_TYPE, STRING_TYPE, METHOD_TYPE_TYPE);
    private static final String GET_HANDLE_FUNCTION_DESCRIPTOR = Type.getMethodDescriptor(METHOD_HANDLE_TYPE,
            SCRIPT_FUNCTION_TYPE, METHOD_TYPE_TYPE);
    private static final String GET_CLASS_INITIALIZER_DESCRIPTOR = Type.getMethodDescriptor(OBJECT_TYPE);
    private static final Type RUNTIME_EXCEPTION_TYPE = Type.getType(RuntimeException.class);
    private static final Type THROWABLE_TYPE = Type.getType(Throwable.class);
    private static final Type UNSUPPORTED_OPERATION_TYPE = Type.getType(UnsupportedOperationException.class);

    private static final String SERVICES_CLASS_TYPE_NAME = Type.getInternalName(JavaAdapterServices.class);
    private static final String RUNTIME_EXCEPTION_TYPE_NAME = RUNTIME_EXCEPTION_TYPE.getInternalName();
    private static final String ERROR_TYPE_NAME = Type.getInternalName(Error.class);
    private static final String THROWABLE_TYPE_NAME = THROWABLE_TYPE.getInternalName();
    private static final String UNSUPPORTED_OPERATION_TYPE_NAME = UNSUPPORTED_OPERATION_TYPE.getInternalName();

    private static final String METHOD_HANDLE_TYPE_DESCRIPTOR = METHOD_HANDLE_TYPE.getDescriptor();
    private static final String GET_GLOBAL_METHOD_DESCRIPTOR = Type.getMethodDescriptor(OBJECT_TYPE);
    private static final String GET_CLASS_METHOD_DESCRIPTOR = Type.getMethodDescriptor(CLASS_TYPE);
    private static final String EXPORT_RETURN_VALUE_METHOD_DESCRIPTOR = Type.getMethodDescriptor(OBJECT_TYPE, OBJECT_TYPE);
    private static final String UNWRAP_METHOD_DESCRIPTOR = Type.getMethodDescriptor(OBJECT_TYPE, OBJECT_TYPE);
    private static final String GET_CONVERTER_METHOD_DESCRIPTOR = Type.getMethodDescriptor(METHOD_HANDLE_TYPE, CLASS_TYPE);
    private static final String TO_CHAR_PRIMITIVE_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.CHAR_TYPE, OBJECT_TYPE);
    private static final String TO_STRING_METHOD_DESCRIPTOR = Type.getMethodDescriptor(STRING_TYPE, OBJECT_TYPE);

    // Package used when the adapter can't be defined in the adaptee's package (either because it's sealed, or because
    // it's a java.* package.
    private static final String ADAPTER_PACKAGE_PREFIX = "jdk/nashorn/javaadapters/";
    // Class name suffix used to append to the adaptee class name, when it can be defined in the adaptee's package.
    private static final String ADAPTER_CLASS_NAME_SUFFIX = "$$NashornJavaAdapter";
    private static final String JAVA_PACKAGE_PREFIX = "java/";
    private static final int MAX_GENERATED_TYPE_NAME_LENGTH = 255;

    private static final String CLASS_INIT = "<clinit>";

    // Method name prefix for invoking super-methods
    static final String SUPER_PREFIX = "super$";

    /**
     * Collection of methods we never override: Object.clone(), Object.finalize().
     */
    private static final Collection<MethodInfo> EXCLUDED = getExcludedMethods();

    // This is the superclass for our generated adapter.
    private final Class<?> superClass;
    // Interfaces implemented by our generated adapter.
    private final List<Class<?>> interfaces;
    // Class loader used as the parent for the class loader we'll create to load the generated class. It will be a class
    // loader that has the visibility of all original types (class to extend and interfaces to implement) and of the
    // Nashorn classes.
    private final ClassLoader commonLoader;
    // Is this a generator for the version of the class that can have overrides on the class level?
    private final boolean classOverride;
    // Binary name of the superClass
    private final String superClassName;
    // Binary name of the generated class.
    private final String generatedClassName;
    private final Set<String> usedFieldNames = new HashSet<>();
    private final Set<String> abstractMethodNames = new HashSet<>();
    private final String samName;
    private final Set<MethodInfo> finalMethods = new HashSet<>(EXCLUDED);
    private final Set<MethodInfo> methodInfos = new HashSet<>();
    private boolean autoConvertibleFromFunction = false;
    private boolean hasExplicitFinalizer = false;

    /**
     * Names of static fields holding type converter method handles for return value conversion. We are emitting code
     * for invoking these explicitly after the delegate handle is invoked, instead of doing an asType or
     * filterReturnValue on the delegate handle, as that would create a new converter handle wrapping the function's
     * handle for every instance of the adapter, causing the handle.invokeExact() call sites to become megamorphic.
     */
    private final Map<Class<?>, String> converterFields = new LinkedHashMap<>();

    /**
     * Subset of possible return types for all methods; namely, all possible return types of the SAM methods (we
     * identify SAM types by having all of their abstract methods share a single name, so there can be multiple
     * overloads with multiple return types. We use this set when emitting the constructor taking a ScriptFunction (the
     * SAM initializer) to avoid populating converter fields that will never be used by SAM methods.
     */
    private final Set<Class<?>> samReturnTypes = new HashSet<>();

    private final ClassWriter cw;

    /**
     * Creates a generator for the bytecode for the adapter for the specified superclass and interfaces.
     * @param superClass the superclass the adapter will extend.
     * @param interfaces the interfaces the adapter will implement.
     * @param commonLoader the class loader that can see all of superClass, interfaces, and Nashorn classes.
     * @param classOverride true to generate the bytecode for the adapter that has class-level overrides, false to
     * generate the bytecode for the adapter that has instance-level overrides.
     * @throws AdaptationException if the adapter can not be generated for some reason.
     */
    JavaAdapterBytecodeGenerator(final Class<?> superClass, final List<Class<?>> interfaces,
            final ClassLoader commonLoader, final boolean classOverride) throws AdaptationException {
        assert superClass != null && !superClass.isInterface();
        assert interfaces != null;

        this.superClass = superClass;
        this.interfaces = interfaces;
        this.classOverride = classOverride;
        this.commonLoader = commonLoader;
        cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                // We need to override ClassWriter.getCommonSuperClass to use this factory's commonLoader as a class
                // loader to find the common superclass of two types when needed.
                return JavaAdapterBytecodeGenerator.this.getCommonSuperClass(type1, type2);
            }
        };
        superClassName = Type.getInternalName(superClass);
        generatedClassName = getGeneratedClassName(superClass, interfaces);

        cw.visit(Opcodes.V1_7, ACC_PUBLIC | ACC_SUPER, generatedClassName, null, superClassName, getInternalTypeNames(interfaces));
        generateGlobalFields();

        gatherMethods(superClass);
        gatherMethods(interfaces);
        samName = abstractMethodNames.size() == 1 ? abstractMethodNames.iterator().next() : null;
        generateHandleFields();
        generateConverterFields();
        if(classOverride) {
            generateClassInit();
        }
        generateConstructors();
        generateMethods();
        generateSuperMethods();
        if (hasExplicitFinalizer) {
            generateFinalizerMethods();
        }
        // }
        cw.visitEnd();
    }

    private void generateGlobalFields() {
        cw.visitField(ACC_PRIVATE | ACC_FINAL | (classOverride ? ACC_STATIC : 0), GLOBAL_FIELD_NAME, GLOBAL_TYPE_DESCRIPTOR, null, null).visitEnd();
        usedFieldNames.add(GLOBAL_FIELD_NAME);
    }

    JavaAdapterClassLoader createAdapterClassLoader() {
        return new JavaAdapterClassLoader(generatedClassName, cw.toByteArray());
    }

    boolean isAutoConvertibleFromFunction() {
        return autoConvertibleFromFunction;
    }

    private static String getGeneratedClassName(final Class<?> superType, final List<Class<?>> interfaces) {
        // The class we use to primarily name our adapter is either the superclass, or if it is Object (meaning we're
        // just implementing interfaces or extending Object), then the first implemented interface or Object.
        final Class<?> namingType = superType == Object.class ? (interfaces.isEmpty()? Object.class : interfaces.get(0)) : superType;
        final Package pkg = namingType.getPackage();
        final String namingTypeName = Type.getInternalName(namingType);
        final StringBuilder buf = new StringBuilder();
        if (namingTypeName.startsWith(JAVA_PACKAGE_PREFIX) || pkg == null || pkg.isSealed()) {
            // Can't define new classes in java.* packages
            buf.append(ADAPTER_PACKAGE_PREFIX).append(namingTypeName);
        } else {
            buf.append(namingTypeName).append(ADAPTER_CLASS_NAME_SUFFIX);
        }
        final Iterator<Class<?>> it = interfaces.iterator();
        if(superType == Object.class && it.hasNext()) {
            it.next(); // Skip first interface, it was used to primarily name the adapter
        }
        // Append interface names to the adapter name
        while(it.hasNext()) {
            buf.append("$$").append(it.next().getSimpleName());
        }
        return buf.toString().substring(0, Math.min(MAX_GENERATED_TYPE_NAME_LENGTH, buf.length()));
    }

    /**
     * Given a list of class objects, return an array with their binary names. Used to generate the array of interface
     * names to implement.
     * @param classes the classes
     * @return an array of names
     */
    private static String[] getInternalTypeNames(final List<Class<?>> classes) {
        final int interfaceCount = classes.size();
        final String[] interfaceNames = new String[interfaceCount];
        for(int i = 0; i < interfaceCount; ++i) {
            interfaceNames[i] = Type.getInternalName(classes.get(i));
        }
        return interfaceNames;
    }

    private void generateHandleFields() {
        final int flags = ACC_PRIVATE | ACC_FINAL | (classOverride ? ACC_STATIC : 0);
        for (final MethodInfo mi: methodInfos) {
            cw.visitField(flags, mi.methodHandleFieldName, METHOD_HANDLE_TYPE_DESCRIPTOR, null, null).visitEnd();
        }
    }

    private void generateConverterFields() {
        final int flags = ACC_PRIVATE | ACC_FINAL | (classOverride ? ACC_STATIC : 0);
        for (final MethodInfo mi: methodInfos) {
            final Class<?> returnType = mi.type.returnType();
            // Handle primitive types, Object, and String specially
            if(!returnType.isPrimitive() && returnType != Object.class && returnType != String.class) {
                if(!converterFields.containsKey(returnType)) {
                    final String name = nextName("convert");
                    converterFields.put(returnType, name);
                    if(mi.getName().equals(samName)) {
                        samReturnTypes.add(returnType);
                    }
                    cw.visitField(flags, name, METHOD_HANDLE_TYPE_DESCRIPTOR, null, null).visitEnd();
                }
            }
        }
    }

    private void generateClassInit() {
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_STATIC, CLASS_INIT,
                Type.getMethodDescriptor(Type.VOID_TYPE), null, null));

        mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "getClassOverrides", GET_CLASS_INITIALIZER_DESCRIPTOR, false);
        final Label initGlobal;
        if(samName != null) {
            // If the class is a SAM, allow having a ScriptFunction passed as class overrides
            final Label notAFunction = new Label();
            mv.dup();
            mv.instanceOf(SCRIPT_FUNCTION_TYPE);
            mv.ifeq(notAFunction);
            mv.checkcast(SCRIPT_FUNCTION_TYPE);

            // Assign MethodHandle fields through invoking getHandle() for a ScriptFunction, only assigning the SAM
            // method(s).
            for (final MethodInfo mi : methodInfos) {
                if(mi.getName().equals(samName)) {
                    mv.dup();
                    loadMethodTypeAndGetHandle(mv, mi, GET_HANDLE_FUNCTION_DESCRIPTOR);
                } else {
                    mv.visitInsn(ACONST_NULL);
                }
                mv.putstatic(generatedClassName, mi.methodHandleFieldName, METHOD_HANDLE_TYPE_DESCRIPTOR);
            }
            initGlobal = new Label();
            mv.goTo(initGlobal);
            mv.visitLabel(notAFunction);
        } else {
            initGlobal = null;
        }
        // Assign MethodHandle fields through invoking getHandle() for a ScriptObject
        for (final MethodInfo mi : methodInfos) {
            mv.dup();
            mv.aconst(mi.getName());
            loadMethodTypeAndGetHandle(mv, mi, GET_HANDLE_OBJECT_DESCRIPTOR);
            mv.putstatic(generatedClassName, mi.methodHandleFieldName, METHOD_HANDLE_TYPE_DESCRIPTOR);
        }

        if(initGlobal != null) {
            mv.visitLabel(initGlobal);
        }
        // Assign "global = Context.getGlobal()"
        invokeGetGlobalWithNullCheck(mv);
        mv.putstatic(generatedClassName, GLOBAL_FIELD_NAME, GLOBAL_TYPE_DESCRIPTOR);

        generateConverterInit(mv, false);
        endInitMethod(mv);
    }

    private void generateConverterInit(final InstructionAdapter mv, final boolean samOnly) {
        assert !samOnly || !classOverride;
        for(final Map.Entry<Class<?>, String> converterField: converterFields.entrySet()) {
            final Class<?> returnType = converterField.getKey();
            if(!classOverride) {
                mv.visitVarInsn(ALOAD, 0);
            }

            if(samOnly && !samReturnTypes.contains(returnType)) {
                mv.visitInsn(ACONST_NULL);
            } else {
                mv.aconst(Type.getType(converterField.getKey()));
                mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "getObjectConverter", GET_CONVERTER_METHOD_DESCRIPTOR, false);
            }

            if(classOverride) {
                mv.putstatic(generatedClassName, converterField.getValue(), METHOD_HANDLE_TYPE_DESCRIPTOR);
            } else {
                mv.putfield(generatedClassName, converterField.getValue(), METHOD_HANDLE_TYPE_DESCRIPTOR);
            }
        }
    }

    private static void loadMethodTypeAndGetHandle(final InstructionAdapter mv, final MethodInfo mi, final String getHandleDescriptor) {
        // NOTE: we're using generic() here because we'll be linking to the "generic" invoker version of
        // the functions anyway, so we cut down on megamorphism in the invokeExact() calls in adapter
        // bodies. Once we start linking to type-specializing invokers, this should be changed.
        mv.aconst(Type.getMethodType(mi.type.generic().toMethodDescriptorString()));
        mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "getHandle", getHandleDescriptor, false);
    }

    private static void invokeGetGlobalWithNullCheck(final InstructionAdapter mv) {
        invokeGetGlobal(mv);
        mv.dup();
        mv.invokevirtual(OBJECT_TYPE_NAME, "getClass", GET_CLASS_METHOD_DESCRIPTOR, false); // check against null Context
        mv.pop();
    }

    private void generateConstructors() throws AdaptationException {
        boolean gotCtor = false;
        for (final Constructor<?> ctor: superClass.getDeclaredConstructors()) {
            final int modifier = ctor.getModifiers();
            if((modifier & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0 && !isCallerSensitive(ctor)) {
                generateConstructors(ctor);
                gotCtor = true;
            }
        }
        if(!gotCtor) {
            throw new AdaptationException(ERROR_NO_ACCESSIBLE_CONSTRUCTOR, superClass.getCanonicalName());
        }
    }

    private void generateConstructors(final Constructor<?> ctor) {
        if(classOverride) {
            // Generate a constructor that just delegates to ctor. This is used with class-level overrides, when we want
            // to create instances without further per-instance overrides.
            generateDelegatingConstructor(ctor);
        } else {
            // Generate a constructor that delegates to ctor, but takes an additional ScriptObject parameter at the
            // beginning of its parameter list.
            generateOverridingConstructor(ctor, false);

            if (samName != null) {
                if (!autoConvertibleFromFunction && ctor.getParameterTypes().length == 0) {
                    // If the original type only has a single abstract method name, as well as a default ctor, then it can
                    // be automatically converted from JS function.
                    autoConvertibleFromFunction = true;
                }
                // If all our abstract methods have a single name, generate an additional constructor, one that takes a
                // ScriptFunction as its first parameter and assigns it as the implementation for all abstract methods.
                generateOverridingConstructor(ctor, true);
            }
        }
    }

    private void generateDelegatingConstructor(final Constructor<?> ctor) {
        final Type originalCtorType = Type.getType(ctor);
        final Type[] argTypes = originalCtorType.getArgumentTypes();

        // All constructors must be public, even if in the superclass they were protected.
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC |
                (ctor.isVarArgs() ? ACC_VARARGS : 0), INIT,
                Type.getMethodDescriptor(originalCtorType.getReturnType(), argTypes), null, null));

        mv.visitCode();
        // Invoke super constructor with the same arguments.
        mv.visitVarInsn(ALOAD, 0);
        int offset = 1; // First arg is at position 1, after this.
        for (final Type argType: argTypes) {
            mv.load(offset, argType);
            offset += argType.getSize();
        }
        mv.invokespecial(superClassName, INIT, originalCtorType.getDescriptor(), false);

        endInitMethod(mv);
    }

    /**
     * Generates a constructor for the instance adapter class. This constructor will take the same arguments as the supertype
     * constructor passed as the argument here, and delegate to it. However, it will take an additional argument of
     * either ScriptObject or ScriptFunction type (based on the value of the "fromFunction" parameter), and initialize
     * all the method handle fields of the adapter instance with functions from the script object (or the script
     * function itself, if that's what's passed). There is one method handle field in the adapter class for every method
     * that can be implemented or overridden; the name of every field is same as the name of the method, with a number
     * suffix that makes it unique in case of overloaded methods. The generated constructor will invoke
     * {@link #getHandle(ScriptFunction, MethodType, boolean)} or {@link #getHandle(Object, String, MethodType,
     * boolean)} to obtain the method handles; these methods make sure to add the necessary conversions and arity
     * adjustments so that the resulting method handles can be invoked from generated methods using {@code invokeExact}.
     * The constructor that takes a script function will only initialize the methods with the same name as the single
     * abstract method. The constructor will also store the Nashorn global that was current at the constructor
     * invocation time in a field named "global". The generated constructor will be public, regardless of whether the
     * supertype constructor was public or protected. The generated constructor will not be variable arity, even if the
     * supertype constructor was.
     * @param ctor the supertype constructor that is serving as the base for the generated constructor.
     * @param fromFunction true if we're generating a constructor that initializes SAM types from a single
     * ScriptFunction passed to it, false if we're generating a constructor that initializes an arbitrary type from a
     * ScriptObject passed to it.
     */
    private void generateOverridingConstructor(final Constructor<?> ctor, final boolean fromFunction) {
        final Type originalCtorType = Type.getType(ctor);
        final Type[] originalArgTypes = originalCtorType.getArgumentTypes();
        final int argLen = originalArgTypes.length;
        final Type[] newArgTypes = new Type[argLen + 1];

        // Insert ScriptFunction|ScriptObject as the last argument to the constructor
        final Type extraArgumentType = fromFunction ? SCRIPT_FUNCTION_TYPE : SCRIPT_OBJECT_TYPE;
        newArgTypes[argLen] = extraArgumentType;
        System.arraycopy(originalArgTypes, 0, newArgTypes, 0, argLen);

        // All constructors must be public, even if in the superclass they were protected.
        // Existing super constructor <init>(this, args...) triggers generating <init>(this, args..., scriptObj).
        // Any variable arity constructors become fixed-arity with explicit array arguments.
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC, INIT,
                Type.getMethodDescriptor(originalCtorType.getReturnType(), newArgTypes), null, null));

        mv.visitCode();
        // First, invoke super constructor with original arguments. If the form of the constructor we're generating is
        // <init>(this, args..., scriptFn), then we're invoking super.<init>(this, args...).
        mv.visitVarInsn(ALOAD, 0);
        final Class<?>[] argTypes = ctor.getParameterTypes();
        int offset = 1; // First arg is at position 1, after this.
        for (int i = 0; i < argLen; ++i) {
            final Type argType = Type.getType(argTypes[i]);
            mv.load(offset, argType);
            offset += argType.getSize();
        }
        mv.invokespecial(superClassName, INIT, originalCtorType.getDescriptor(), false);

        // Get a descriptor to the appropriate "JavaAdapterFactory.getHandle" method.
        final String getHandleDescriptor = fromFunction ? GET_HANDLE_FUNCTION_DESCRIPTOR : GET_HANDLE_OBJECT_DESCRIPTOR;

        // Assign MethodHandle fields through invoking getHandle()
        for (final MethodInfo mi : methodInfos) {
            mv.visitVarInsn(ALOAD, 0);
            if (fromFunction && !mi.getName().equals(samName)) {
                // Constructors initializing from a ScriptFunction only initialize methods with the SAM name.
                // NOTE: if there's a concrete overloaded method sharing the SAM name, it'll be overridden too. This
                // is a deliberate design choice. All other method handles are initialized to null.
                mv.visitInsn(ACONST_NULL);
            } else {
                mv.visitVarInsn(ALOAD, offset);
                if(!fromFunction) {
                    mv.aconst(mi.getName());
                }
                loadMethodTypeAndGetHandle(mv, mi, getHandleDescriptor);
            }
            mv.putfield(generatedClassName, mi.methodHandleFieldName, METHOD_HANDLE_TYPE_DESCRIPTOR);
        }

        // Assign "this.global = Context.getGlobal()"
        mv.visitVarInsn(ALOAD, 0);
        invokeGetGlobalWithNullCheck(mv);
        mv.putfield(generatedClassName, GLOBAL_FIELD_NAME, GLOBAL_TYPE_DESCRIPTOR);

        // Initialize converters
        generateConverterInit(mv, fromFunction);
        endInitMethod(mv);

        if (! fromFunction) {
            newArgTypes[argLen] = OBJECT_TYPE;
            final InstructionAdapter mv2 = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC, INIT,
                    Type.getMethodDescriptor(originalCtorType.getReturnType(), newArgTypes), null, null));
            generateOverridingConstructorWithObjectParam(mv2, ctor, originalCtorType.getDescriptor());
        }
    }

    // Object additional param accepting constructor - generated to handle null and undefined value
    // for script adapters. This is effectively to throw TypeError on such script adapters. See
    // JavaAdapterServices.getHandle as well.
    private void generateOverridingConstructorWithObjectParam(final InstructionAdapter mv, final Constructor<?> ctor, final String ctorDescriptor) {
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        final Class<?>[] argTypes = ctor.getParameterTypes();
        int offset = 1; // First arg is at position 1, after this.
        for (int i = 0; i < argTypes.length; ++i) {
            final Type argType = Type.getType(argTypes[i]);
            mv.load(offset, argType);
            offset += argType.getSize();
        }
        mv.invokespecial(superClassName, INIT, ctorDescriptor, false);
        mv.visitVarInsn(ALOAD, offset);
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ACONST_NULL);
        mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "getHandle", GET_HANDLE_OBJECT_DESCRIPTOR, false);
        endInitMethod(mv);
    }

    private static void endInitMethod(final InstructionAdapter mv) {
        mv.visitInsn(RETURN);
        endMethod(mv);
    }

    private static void endMethod(final InstructionAdapter mv) {
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void invokeGetGlobal(final InstructionAdapter mv) {
        mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "getGlobal", GET_GLOBAL_METHOD_DESCRIPTOR, false);
    }

    private static void invokeSetGlobal(final InstructionAdapter mv) {
        mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "setGlobal", SET_GLOBAL_METHOD_DESCRIPTOR, false);
    }

    /**
     * Encapsulation of the information used to generate methods in the adapter classes. Basically, a wrapper around the
     * reflective Method object, a cached MethodType, and the name of the field in the adapter class that will hold the
     * method handle serving as the implementation of this method in adapter instances.
     *
     */
    private static class MethodInfo {
        private final Method method;
        private final MethodType type;
        private String methodHandleFieldName;

        private MethodInfo(final Class<?> clazz, final String name, final Class<?>... argTypes) throws NoSuchMethodException {
            this(clazz.getDeclaredMethod(name, argTypes));
        }

        private MethodInfo(final Method method) {
            this.method = method;
            this.type   = MH.type(method.getReturnType(), method.getParameterTypes());
        }

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof MethodInfo && equals((MethodInfo)obj);
        }

        private boolean equals(final MethodInfo other) {
            // Only method name and type are used for comparison; method handle field name is not.
            return getName().equals(other.getName()) && type.equals(other.type);
        }

        String getName() {
            return method.getName();
        }

        @Override
        public int hashCode() {
            return getName().hashCode() ^ type.hashCode();
        }

        void setIsCanonical(final JavaAdapterBytecodeGenerator self) {
            methodHandleFieldName = self.nextName(getName());
        }
    }

    private String nextName(final String name) {
        int i = 0;
        String nextName = name;
        while (!usedFieldNames.add(nextName)) {
            final String ordinal = String.valueOf(i++);
            final int maxNameLen = 255 - ordinal.length();
            nextName = (name.length() <= maxNameLen ? name : name.substring(0, maxNameLen)).concat(ordinal);
        }
        return nextName;
    }

    private void generateMethods() {
        for(final MethodInfo mi: methodInfos) {
            generateMethod(mi);
        }
    }

    /**
     * Generates a method in the adapter class that adapts a method from the original class. The generated methods will
     * inspect the method handle field assigned to them. If it is null (the JS object doesn't provide an implementation
     * for the method) then it will either invoke its version in the supertype, or if it is abstract, throw an
     * {@link UnsupportedOperationException}. Otherwise, if the method handle field's value is not null, the handle is
     * invoked using invokeExact (signature polymorphic invocation as per JLS 15.12.3). Before the invocation, the
     * current Nashorn {@link Context} is checked, and if it is different than the global used to create the adapter
     * instance, the creating global is set to be the current global. In this case, the previously current global is
     * restored after the invocation. If invokeExact results in a Throwable that is not one of the method's declared
     * exceptions, and is not an unchecked throwable, then it is wrapped into a {@link RuntimeException} and the runtime
     * exception is thrown. The method handle retrieved from the field is guaranteed to exactly match the signature of
     * the method; this is guaranteed by the way constructors of the adapter class obtain them using
     * {@link #getHandle(Object, String, MethodType, boolean)}.
     * @param mi the method info describing the method to be generated.
     */
    private void generateMethod(final MethodInfo mi) {
        final Method method = mi.method;
        final Class<?>[] exceptions = method.getExceptionTypes();
        final String[] exceptionNames = getExceptionNames(exceptions);
        final MethodType type = mi.type;
        final String methodDesc = type.toMethodDescriptorString();
        final String name = mi.getName();

        final Type asmType = Type.getMethodType(methodDesc);
        final Type[] asmArgTypes = asmType.getArgumentTypes();

        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(getAccessModifiers(method), name,
                methodDesc, null, exceptionNames));
        mv.visitCode();

        final Label handleDefined = new Label();

        final Class<?> returnType = type.returnType();
        final Type asmReturnType = Type.getType(returnType);

        // See if we have overriding method handle defined
        if(classOverride) {
            mv.getstatic(generatedClassName, mi.methodHandleFieldName, METHOD_HANDLE_TYPE_DESCRIPTOR);
        } else {
            mv.visitVarInsn(ALOAD, 0);
            mv.getfield(generatedClassName, mi.methodHandleFieldName, METHOD_HANDLE_TYPE_DESCRIPTOR);
        }
        // stack: [handle]
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, handleDefined);

        // No handle is available, fall back to default behavior
        if(Modifier.isAbstract(method.getModifiers())) {
            // If the super method is abstract, throw an exception
            mv.anew(UNSUPPORTED_OPERATION_TYPE);
            mv.dup();
            mv.invokespecial(UNSUPPORTED_OPERATION_TYPE_NAME, INIT, VOID_NOARG_METHOD_DESCRIPTOR, false);
            mv.athrow();
        } else {
            mv.visitInsn(POP);
            // If the super method is not abstract, delegate to it.
            emitSuperCall(mv, method.getDeclaringClass(), name, methodDesc);
        }

        mv.visitLabel(handleDefined);
        // Load the creatingGlobal object
        if(classOverride) {
            // If class handle is defined, load the static defining global
            mv.getstatic(generatedClassName, GLOBAL_FIELD_NAME, GLOBAL_TYPE_DESCRIPTOR);
        } else {
            mv.visitVarInsn(ALOAD, 0);
            mv.getfield(generatedClassName, GLOBAL_FIELD_NAME, GLOBAL_TYPE_DESCRIPTOR);
        }
        // stack: [creatingGlobal, handle]
        final Label setupGlobal = new Label();
        mv.visitLabel(setupGlobal);

        // Determine the first index for a local variable
        int nextLocalVar = 1; // "this" is at 0
        for(final Type t: asmArgTypes) {
            nextLocalVar += t.getSize();
        }
        // Set our local variable indices
        final int currentGlobalVar  = nextLocalVar++;
        final int globalsDifferVar  = nextLocalVar++;

        mv.dup();
        // stack: [creatingGlobal, creatingGlobal, handle]

        // Emit code for switching to the creating global
        // Global currentGlobal = Context.getGlobal();
        invokeGetGlobal(mv);
        mv.dup();

        mv.visitVarInsn(ASTORE, currentGlobalVar);
        // stack: [currentGlobal, creatingGlobal, creatingGlobal, handle]
        // if(definingGlobal == currentGlobal) {
        final Label globalsDiffer = new Label();
        mv.ifacmpne(globalsDiffer);
        // stack: [creatingGlobal, handle]
        //     globalsDiffer = false
        mv.pop();
        // stack: [handle]
        mv.iconst(0); // false
        // stack: [false, handle]
        final Label invokeHandle = new Label();
        mv.goTo(invokeHandle);
        mv.visitLabel(globalsDiffer);
        // } else {
        //     Context.setGlobal(definingGlobal);
        // stack: [creatingGlobal, handle]
        invokeSetGlobal(mv);
        // stack: [handle]
        //     globalsDiffer = true
        mv.iconst(1);
        // stack: [true, handle]

        mv.visitLabel(invokeHandle);
        mv.visitVarInsn(ISTORE, globalsDifferVar);
        // stack: [handle]

        // Load all parameters back on stack for dynamic invocation. NOTE: since we're using a generic
        // Object(Object, Object, ...) type signature for the method, we must box all arguments here.
        int varOffset = 1;
        for (final Type t : asmArgTypes) {
            mv.load(varOffset, t);
            boxStackTop(mv, t);
            varOffset += t.getSize();
        }

        // Invoke the target method handle
        final Label tryBlockStart = new Label();
        mv.visitLabel(tryBlockStart);
        emitInvokeExact(mv, type.generic());
        convertReturnValue(mv, returnType, asmReturnType);
        final Label tryBlockEnd = new Label();
        mv.visitLabel(tryBlockEnd);
        emitFinally(mv, currentGlobalVar, globalsDifferVar);
        mv.areturn(asmReturnType);

        // If Throwable is not declared, we need an adapter from Throwable to RuntimeException
        final boolean throwableDeclared = isThrowableDeclared(exceptions);
        final Label throwableHandler;
        if (!throwableDeclared) {
            // Add "throw new RuntimeException(Throwable)" handler for Throwable
            throwableHandler = new Label();
            mv.visitLabel(throwableHandler);
            mv.anew(RUNTIME_EXCEPTION_TYPE);
            mv.dupX1();
            mv.swap();
            mv.invokespecial(RUNTIME_EXCEPTION_TYPE_NAME, INIT, Type.getMethodDescriptor(Type.VOID_TYPE, THROWABLE_TYPE), false);
            // Fall through to rethrow handler
        } else {
            throwableHandler = null;
        }
        final Label rethrowHandler = new Label();
        mv.visitLabel(rethrowHandler);
        // Rethrow handler for RuntimeException, Error, and all declared exception types
        emitFinally(mv, currentGlobalVar, globalsDifferVar);
        mv.athrow();
        final Label methodEnd = new Label();
        mv.visitLabel(methodEnd);

        mv.visitLocalVariable("currentGlobal", GLOBAL_TYPE_DESCRIPTOR, null, setupGlobal, methodEnd, currentGlobalVar);
        mv.visitLocalVariable("globalsDiffer", Type.BOOLEAN_TYPE.getDescriptor(), null, setupGlobal, methodEnd, globalsDifferVar);

        if(throwableDeclared) {
            mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, rethrowHandler, THROWABLE_TYPE_NAME);
            assert throwableHandler == null;
        } else {
            mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, rethrowHandler, RUNTIME_EXCEPTION_TYPE_NAME);
            mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, rethrowHandler, ERROR_TYPE_NAME);
            for(final String excName: exceptionNames) {
                mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, rethrowHandler, excName);
            }
            mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, throwableHandler, THROWABLE_TYPE_NAME);
        }
        endMethod(mv);
    }

    private void convertReturnValue(final InstructionAdapter mv, final Class<?> returnType, final Type asmReturnType) {
        switch(asmReturnType.getSort()) {
        case Type.VOID:
            mv.pop();
            break;
        case Type.BOOLEAN:
            JSType.TO_BOOLEAN.invoke(mv);
            break;
        case Type.BYTE:
            JSType.TO_INT32.invoke(mv);
            mv.visitInsn(Opcodes.I2B);
            break;
        case Type.SHORT:
            JSType.TO_INT32.invoke(mv);
            mv.visitInsn(Opcodes.I2S);
            break;
        case Type.CHAR:
            // JSType doesn't have a TO_CHAR, so we have services supply us one.
            mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "toCharPrimitive", TO_CHAR_PRIMITIVE_METHOD_DESCRIPTOR, false);
            break;
        case Type.INT:
            JSType.TO_INT32.invoke(mv);
            break;
        case Type.LONG:
            JSType.TO_LONG.invoke(mv);
            break;
        case Type.FLOAT:
            JSType.TO_NUMBER.invoke(mv);
            mv.visitInsn(Opcodes.D2F);
            break;
        case Type.DOUBLE:
            JSType.TO_NUMBER.invoke(mv);
            break;
        default:
            if(asmReturnType.equals(OBJECT_TYPE)) {
                // Must hide ConsString (and potentially other internal Nashorn types) from callers
                mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "exportReturnValue", EXPORT_RETURN_VALUE_METHOD_DESCRIPTOR, false);
            } else if(asmReturnType.equals(STRING_TYPE)){
                // Well-known conversion to String. Not using the JSType one as we want to preserve null as null instead
                // of the string "n,u,l,l".
                mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "toString", TO_STRING_METHOD_DESCRIPTOR, false);
            } else {
                // Invoke converter method handle for everything else. Note that we could have just added an asType or
                // filterReturnValue to the invoked handle instead, but then every instance would have the function
                // method handle wrapped in a separate converter method handle, making handle.invokeExact() megamorphic.
                if(classOverride) {
                    mv.getstatic(generatedClassName, converterFields.get(returnType), METHOD_HANDLE_TYPE_DESCRIPTOR);
                } else {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.getfield(generatedClassName, converterFields.get(returnType), METHOD_HANDLE_TYPE_DESCRIPTOR);
                }
                mv.swap();
                emitInvokeExact(mv, MethodType.methodType(returnType, Object.class));
            }
        }
    }

    private static void emitInvokeExact(final InstructionAdapter mv, final MethodType type) {
        mv.invokevirtual(METHOD_HANDLE_TYPE.getInternalName(), "invokeExact", type.toMethodDescriptorString(), false);
    }

    private static void boxStackTop(final InstructionAdapter mv, final Type t) {
        switch(t.getSort()) {
        case Type.BOOLEAN:
            invokeValueOf(mv, "Boolean", 'Z');
            break;
        case Type.BYTE:
        case Type.SHORT:
        case Type.INT:
            // bytes and shorts get boxed as integers
            invokeValueOf(mv, "Integer", 'I');
            break;
        case Type.CHAR:
            invokeValueOf(mv, "Character", 'C');
            break;
        case Type.FLOAT:
            // floats get boxed as doubles
            mv.visitInsn(Opcodes.F2D);
            invokeValueOf(mv, "Double", 'D');
            break;
        case Type.LONG:
            invokeValueOf(mv, "Long", 'J');
            break;
        case Type.DOUBLE:
            invokeValueOf(mv, "Double", 'D');
            break;
        case Type.ARRAY:
        case Type.METHOD:
            // Already boxed
            break;
        case Type.OBJECT:
            if(t.equals(OBJECT_TYPE)) {
                mv.invokestatic(SCRIPTUTILS_TYPE_NAME, "unwrap", UNWRAP_METHOD_DESCRIPTOR, false);
            }
            break;
        default:
            // Not expecting anything else (e.g. VOID)
            assert false;
            break;
        }
    }

    private static void invokeValueOf(final InstructionAdapter mv, final String boxedType, final char unboxedType) {
        mv.invokestatic("java/lang/" + boxedType, "valueOf", "(" + unboxedType + ")Ljava/lang/" + boxedType + ";", false);
    }

    /**
     * Emit code to restore the previous Nashorn Context when needed.
     * @param mv the instruction adapter
     * @param currentGlobalVar index of the local variable holding the reference to the current global at method
     * entry.
     * @param globalsDifferVar index of the boolean local variable that is true if the global needs to be restored.
     */
    private static void emitFinally(final InstructionAdapter mv, final int currentGlobalVar, final int globalsDifferVar) {
        // Emit code to restore the previous Nashorn global if needed
        mv.visitVarInsn(ILOAD, globalsDifferVar);
        final Label skip = new Label();
        mv.ifeq(skip);
        mv.visitVarInsn(ALOAD, currentGlobalVar);
        invokeSetGlobal(mv);
        mv.visitLabel(skip);
    }

    private static boolean isThrowableDeclared(final Class<?>[] exceptions) {
        for (final Class<?> exception : exceptions) {
            if (exception == Throwable.class) {
                return true;
            }
        }
        return false;
    }

    private void generateSuperMethods() {
        for(final MethodInfo mi: methodInfos) {
            if(!Modifier.isAbstract(mi.method.getModifiers())) {
                generateSuperMethod(mi);
            }
        }
    }

    private void generateSuperMethod(final MethodInfo mi) {
        final Method method = mi.method;

        final String methodDesc = mi.type.toMethodDescriptorString();
        final String name = mi.getName();

        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(getAccessModifiers(method),
                SUPER_PREFIX + name, methodDesc, null, getExceptionNames(method.getExceptionTypes())));
        mv.visitCode();

        emitSuperCall(mv, method.getDeclaringClass(), name, methodDesc);

        endMethod(mv);
    }

    // find the appropriate super type to use for invokespecial on the given interface
    private Class<?> findInvokespecialOwnerFor(final Class<?> cl) {
        assert Modifier.isInterface(cl.getModifiers()) : cl + " is not an interface";

        if (cl.isAssignableFrom(superClass)) {
            return superClass;
        }

        for (final Class<?> iface : interfaces) {
             if (cl.isAssignableFrom(iface)) {
                 return iface;
             }
        }

        // we better that interface that extends the given interface!
        throw new AssertionError("can't find the class/interface that extends " + cl);
    }

    private void emitSuperCall(final InstructionAdapter mv, final Class<?> owner, final String name, final String methodDesc) {
        mv.visitVarInsn(ALOAD, 0);
        int nextParam = 1;
        final Type methodType = Type.getMethodType(methodDesc);
        for(final Type t: methodType.getArgumentTypes()) {
            mv.load(nextParam, t);
            nextParam += t.getSize();
        }

        // default method - non-abstract, interface method
        if (Modifier.isInterface(owner.getModifiers())) {
            // we should call default method on the immediate "super" type - not on (possibly)
            // the indirectly inherited interface class!
            mv.invokespecial(Type.getInternalName(findInvokespecialOwnerFor(owner)), name, methodDesc, false);
        } else {
            mv.invokespecial(superClassName, name, methodDesc, false);
        }
        mv.areturn(methodType.getReturnType());
    }

    private void generateFinalizerMethods() {
        final String finalizerDelegateName = nextName("access$");
        generateFinalizerDelegate(finalizerDelegateName);
        generateFinalizerOverride(finalizerDelegateName);
    }

    private void generateFinalizerDelegate(final String finalizerDelegateName) {
        // Generate a delegate that will be invoked from the no-permission trampoline. Note it can be private, as we'll
        // refer to it with a MethodHandle constant pool entry in the overridden finalize() method (see
        // generateFinalizerOverride()).
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PRIVATE | ACC_STATIC,
                finalizerDelegateName, Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE), null, null));

        // Simply invoke super.finalize()
        mv.visitVarInsn(ALOAD, 0);
        mv.checkcast(Type.getType(generatedClassName));
        mv.invokespecial(superClassName, "finalize", Type.getMethodDescriptor(Type.VOID_TYPE), false);

        mv.visitInsn(RETURN);
        endMethod(mv);
    }

    private void generateFinalizerOverride(final String finalizerDelegateName) {
        final InstructionAdapter mv = new InstructionAdapter(cw.visitMethod(ACC_PUBLIC, "finalize",
                VOID_NOARG_METHOD_DESCRIPTOR, null, null));
        // Overridden finalizer will take a MethodHandle to the finalizer delegating method, ...
        mv.aconst(new Handle(Opcodes.H_INVOKESTATIC, generatedClassName, finalizerDelegateName,
                Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE)));
        mv.visitVarInsn(ALOAD, 0);
        // ...and invoke it through JavaAdapterServices.invokeNoPermissions
        mv.invokestatic(SERVICES_CLASS_TYPE_NAME, "invokeNoPermissions",
                Type.getMethodDescriptor(METHOD_HANDLE_TYPE, OBJECT_TYPE), false);
        mv.visitInsn(RETURN);
        endMethod(mv);
    }

    private static String[] getExceptionNames(final Class<?>[] exceptions) {
        final String[] exceptionNames = new String[exceptions.length];
        for (int i = 0; i < exceptions.length; ++i) {
            exceptionNames[i] = Type.getInternalName(exceptions[i]);
        }
        return exceptionNames;
    }

    private static int getAccessModifiers(final Method method) {
        return ACC_PUBLIC | (method.isVarArgs() ? ACC_VARARGS : 0);
    }

    /**
     * Gathers methods that can be implemented or overridden from the specified type into this factory's
     * {@link #methodInfos} set. It will add all non-final, non-static methods that are either public or protected from
     * the type if the type itself is public. If the type is a class, the method will recursively invoke itself for its
     * superclass and the interfaces it implements, and add further methods that were not directly declared on the
     * class.
     * @param type the type defining the methods.
     */
    private void gatherMethods(final Class<?> type) throws AdaptationException {
        if (Modifier.isPublic(type.getModifiers())) {
            final Method[] typeMethods = type.isInterface() ? type.getMethods() : type.getDeclaredMethods();

            for (final Method typeMethod: typeMethods) {
                final String name = typeMethod.getName();
                if(name.startsWith(SUPER_PREFIX)) {
                    continue;
                }
                final int m = typeMethod.getModifiers();
                if (Modifier.isStatic(m)) {
                    continue;
                }
                if (Modifier.isPublic(m) || Modifier.isProtected(m)) {
                    // Is it a "finalize()"?
                    if(name.equals("finalize") && typeMethod.getParameterCount() == 0) {
                        if(type != Object.class) {
                            hasExplicitFinalizer = true;
                            if(Modifier.isFinal(m)) {
                                // Must be able to override an explicit finalizer
                                throw new AdaptationException(Outcome.ERROR_FINAL_FINALIZER, type.getCanonicalName());
                            }
                        }
                        continue;
                    }

                    final MethodInfo mi = new MethodInfo(typeMethod);
                    if (Modifier.isFinal(m) || isCallerSensitive(typeMethod)) {
                        finalMethods.add(mi);
                    } else if (!finalMethods.contains(mi) && methodInfos.add(mi)) {
                        if (Modifier.isAbstract(m)) {
                            abstractMethodNames.add(mi.getName());
                        }
                        mi.setIsCanonical(this);
                    }
                }
            }
        }
        // If the type is a class, visit its superclasses and declared interfaces. If it's an interface, we're done.
        // Needing to invoke the method recursively for a non-interface Class object is the consequence of needing to
        // see all declared protected methods, and Class.getDeclaredMethods() doesn't provide those declared in a
        // superclass. For interfaces, we used Class.getMethods(), as we're only interested in public ones there, and
        // getMethods() does provide those declared in a superinterface.
        if (!type.isInterface()) {
            final Class<?> superType = type.getSuperclass();
            if (superType != null) {
                gatherMethods(superType);
            }
            for (final Class<?> itf: type.getInterfaces()) {
                gatherMethods(itf);
            }
        }
    }

    private void gatherMethods(final List<Class<?>> classes) throws AdaptationException {
        for(final Class<?> c: classes) {
            gatherMethods(c);
        }
    }

    private static final AccessControlContext GET_DECLARED_MEMBERS_ACC_CTXT = ClassAndLoader.createPermAccCtxt("accessDeclaredMembers");

    /**
     * Creates a collection of methods that are not final, but we still never allow them to be overridden in adapters,
     * as explicitly declaring them automatically is a bad idea. Currently, this means {@code Object.finalize()} and
     * {@code Object.clone()}.
     * @return a collection of method infos representing those methods that we never override in adapter classes.
     */
    private static Collection<MethodInfo> getExcludedMethods() {
        return AccessController.doPrivileged(new PrivilegedAction<Collection<MethodInfo>>() {
            @Override
            public Collection<MethodInfo> run() {
                try {
                    return Arrays.asList(
                            new MethodInfo(Object.class, "finalize"),
                            new MethodInfo(Object.class, "clone"));
                } catch (final NoSuchMethodException e) {
                    throw new AssertionError(e);
                }
            }
        }, GET_DECLARED_MEMBERS_ACC_CTXT);
    }

    private String getCommonSuperClass(final String type1, final String type2) {
        try {
            final Class<?> c1 = Class.forName(type1.replace('/', '.'), false, commonLoader);
            final Class<?> c2 = Class.forName(type2.replace('/', '.'), false, commonLoader);
            if (c1.isAssignableFrom(c2)) {
                return type1;
            }
            if (c2.isAssignableFrom(c1)) {
                return type2;
            }
            if (c1.isInterface() || c2.isInterface()) {
                return OBJECT_TYPE_NAME;
            }
            return assignableSuperClass(c1, c2).getName().replace('.', '/');
        } catch(final ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> assignableSuperClass(final Class<?> c1, final Class<?> c2) {
        final Class<?> superClass = c1.getSuperclass();
        return superClass.isAssignableFrom(c2) ? superClass : assignableSuperClass(superClass, c2);
    }

    private static boolean isCallerSensitive(final AccessibleObject e) {
        return e.isAnnotationPresent(CallerSensitive.class);
    }
}
