/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import static java.lang.invoke.MethodHandleNatives.Constants.*;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;

/**
 * The JVM interface for the method handles package is all here.
 * This is an interface internal and private to an implementation of JSR 292.
 * <em>This class is not part of the JSR 292 standard.</em>
 * @author jrose
 */
class MethodHandleNatives {

    private MethodHandleNatives() { } // static only

    /// MemberName support

    static native void init(MemberName self, Object ref);
    static native void expand(MemberName self);
    static native MemberName resolve(MemberName self, Class<?> caller) throws LinkageError, ClassNotFoundException;
    static native int getMembers(Class<?> defc, String matchName, String matchSig,
            int matchFlags, Class<?> caller, int skip, MemberName[] results);

    /// Field layout queries parallel to sun.misc.Unsafe:
    static native long objectFieldOffset(MemberName self);  // e.g., returns vmindex
    static native long staticFieldOffset(MemberName self);  // e.g., returns vmindex
    static native Object staticFieldBase(MemberName self);  // e.g., returns clazz
    static native Object getMemberVMInfo(MemberName self);  // returns {vmindex,vmtarget}

    /// MethodHandle support

    /** Fetch MH-related JVM parameter.
     *  which=0 retrieves MethodHandlePushLimit
     *  which=1 retrieves stack slot push size (in address units)
     */
    static native int getConstant(int which);

    static final boolean COUNT_GWT;

    /// CallSite support

    /** Tell the JVM that we need to change the target of a CallSite. */
    static native void setCallSiteTargetNormal(CallSite site, MethodHandle target);
    static native void setCallSiteTargetVolatile(CallSite site, MethodHandle target);

    private static native void registerNatives();
    static {
        registerNatives();
        COUNT_GWT                   = getConstant(Constants.GC_COUNT_GWT) != 0;

        // The JVM calls MethodHandleNatives.<clinit>.  Cascade the <clinit> calls as needed:
        MethodHandleImpl.initStatics();
    }

    // All compile-time constants go here.
    // There is an opportunity to check them against the JVM's idea of them.
    static class Constants {
        Constants() { } // static only
        // MethodHandleImpl
        static final int // for getConstant
                GC_COUNT_GWT = 4,
                GC_LAMBDA_SUPPORT = 5;

        // MemberName
        // The JVM uses values of -2 and above for vtable indexes.
        // Field values are simple positive offsets.
        // Ref: src/share/vm/oops/methodOop.hpp
        // This value is negative enough to avoid such numbers,
        // but not too negative.
        static final int
                MN_IS_METHOD           = 0x00010000, // method (not constructor)
                MN_IS_CONSTRUCTOR      = 0x00020000, // constructor
                MN_IS_FIELD            = 0x00040000, // field
                MN_IS_TYPE             = 0x00080000, // nested type
                MN_CALLER_SENSITIVE    = 0x00100000, // @CallerSensitive annotation detected
                MN_REFERENCE_KIND_SHIFT = 24, // refKind
                MN_REFERENCE_KIND_MASK = 0x0F000000 >> MN_REFERENCE_KIND_SHIFT,
                // The SEARCH_* bits are not for MN.flags but for the matchFlags argument of MHN.getMembers:
                MN_SEARCH_SUPERCLASSES = 0x00100000,
                MN_SEARCH_INTERFACES   = 0x00200000;

        /**
         * Basic types as encoded in the JVM.  These code values are not
         * intended for use outside this class.  They are used as part of
         * a private interface between the JVM and this class.
         */
        static final int
            T_BOOLEAN  =  4,
            T_CHAR     =  5,
            T_FLOAT    =  6,
            T_DOUBLE   =  7,
            T_BYTE     =  8,
            T_SHORT    =  9,
            T_INT      = 10,
            T_LONG     = 11,
            T_OBJECT   = 12,
            //T_ARRAY    = 13
            T_VOID     = 14,
            //T_ADDRESS  = 15
            T_ILLEGAL  = 99;

        /**
         * Constant pool entry types.
         */
        static final byte
            CONSTANT_Utf8                = 1,
            CONSTANT_Integer             = 3,
            CONSTANT_Float               = 4,
            CONSTANT_Long                = 5,
            CONSTANT_Double              = 6,
            CONSTANT_Class               = 7,
            CONSTANT_String              = 8,
            CONSTANT_Fieldref            = 9,
            CONSTANT_Methodref           = 10,
            CONSTANT_InterfaceMethodref  = 11,
            CONSTANT_NameAndType         = 12,
            CONSTANT_MethodHandle        = 15,  // JSR 292
            CONSTANT_MethodType          = 16,  // JSR 292
            CONSTANT_InvokeDynamic       = 18,
            CONSTANT_LIMIT               = 19;   // Limit to tags found in classfiles

        /**
         * Access modifier flags.
         */
        static final char
            ACC_PUBLIC                 = 0x0001,
            ACC_PRIVATE                = 0x0002,
            ACC_PROTECTED              = 0x0004,
            ACC_STATIC                 = 0x0008,
            ACC_FINAL                  = 0x0010,
            ACC_SYNCHRONIZED           = 0x0020,
            ACC_VOLATILE               = 0x0040,
            ACC_TRANSIENT              = 0x0080,
            ACC_NATIVE                 = 0x0100,
            ACC_INTERFACE              = 0x0200,
            ACC_ABSTRACT               = 0x0400,
            ACC_STRICT                 = 0x0800,
            ACC_SYNTHETIC              = 0x1000,
            ACC_ANNOTATION             = 0x2000,
            ACC_ENUM                   = 0x4000,
            // aliases:
            ACC_SUPER                  = ACC_SYNCHRONIZED,
            ACC_BRIDGE                 = ACC_VOLATILE,
            ACC_VARARGS                = ACC_TRANSIENT;

        /**
         * Constant pool reference-kind codes, as used by CONSTANT_MethodHandle CP entries.
         */
        static final byte
            REF_NONE                    = 0,  // null value
            REF_getField                = 1,
            REF_getStatic               = 2,
            REF_putField                = 3,
            REF_putStatic               = 4,
            REF_invokeVirtual           = 5,
            REF_invokeStatic            = 6,
            REF_invokeSpecial           = 7,
            REF_newInvokeSpecial        = 8,
            REF_invokeInterface         = 9,
            REF_LIMIT                  = 10;
    }

    static boolean refKindIsValid(int refKind) {
        return (refKind > REF_NONE && refKind < REF_LIMIT);
    }
    static boolean refKindIsField(byte refKind) {
        assert(refKindIsValid(refKind));
        return (refKind <= REF_putStatic);
    }
    static boolean refKindIsGetter(byte refKind) {
        assert(refKindIsValid(refKind));
        return (refKind <= REF_getStatic);
    }
    static boolean refKindIsSetter(byte refKind) {
        return refKindIsField(refKind) && !refKindIsGetter(refKind);
    }
    static boolean refKindIsMethod(byte refKind) {
        return !refKindIsField(refKind) && (refKind != REF_newInvokeSpecial);
    }
    static boolean refKindIsConstructor(byte refKind) {
        return (refKind == REF_newInvokeSpecial);
    }
    static boolean refKindHasReceiver(byte refKind) {
        assert(refKindIsValid(refKind));
        return (refKind & 1) != 0;
    }
    static boolean refKindIsStatic(byte refKind) {
        return !refKindHasReceiver(refKind) && (refKind != REF_newInvokeSpecial);
    }
    static boolean refKindDoesDispatch(byte refKind) {
        assert(refKindIsValid(refKind));
        return (refKind == REF_invokeVirtual ||
                refKind == REF_invokeInterface);
    }
    static {
        final int HR_MASK = ((1 << REF_getField) |
                             (1 << REF_putField) |
                             (1 << REF_invokeVirtual) |
                             (1 << REF_invokeSpecial) |
                             (1 << REF_invokeInterface)
                            );
        for (byte refKind = REF_NONE+1; refKind < REF_LIMIT; refKind++) {
            assert(refKindHasReceiver(refKind) == (((1<<refKind) & HR_MASK) != 0)) : refKind;
        }
    }
    static String refKindName(byte refKind) {
        assert(refKindIsValid(refKind));
        switch (refKind) {
        case REF_getField:          return "getField";
        case REF_getStatic:         return "getStatic";
        case REF_putField:          return "putField";
        case REF_putStatic:         return "putStatic";
        case REF_invokeVirtual:     return "invokeVirtual";
        case REF_invokeStatic:      return "invokeStatic";
        case REF_invokeSpecial:     return "invokeSpecial";
        case REF_newInvokeSpecial:  return "newInvokeSpecial";
        case REF_invokeInterface:   return "invokeInterface";
        default:                    return "REF_???";
        }
    }

    private static native int getNamedCon(int which, Object[] name);
    static boolean verifyConstants() {
        Object[] box = { null };
        for (int i = 0; ; i++) {
            box[0] = null;
            int vmval = getNamedCon(i, box);
            if (box[0] == null)  break;
            String name = (String) box[0];
            try {
                Field con = Constants.class.getDeclaredField(name);
                int jval = con.getInt(null);
                if (jval == vmval)  continue;
                String err = (name+": JVM has "+vmval+" while Java has "+jval);
                if (name.equals("CONV_OP_LIMIT")) {
                    System.err.println("warning: "+err);
                    continue;
                }
                throw new InternalError(err);
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                String err = (name+": JVM has "+vmval+" which Java does not define");
                // ignore exotic ops the JVM cares about; we just wont issue them
                //System.err.println("warning: "+err);
                continue;
            }
        }
        return true;
    }
    static {
        assert(verifyConstants());
    }

    // Up-calls from the JVM.
    // These must NOT be public.

    /**
     * The JVM is linking an invokedynamic instruction.  Create a reified call site for it.
     */
    static MemberName linkCallSite(Object callerObj,
                                   Object bootstrapMethodObj,
                                   Object nameObj, Object typeObj,
                                   Object staticArguments,
                                   Object[] appendixResult) {
        MethodHandle bootstrapMethod = (MethodHandle)bootstrapMethodObj;
        Class<?> caller = (Class<?>)callerObj;
        String name = nameObj.toString().intern();
        MethodType type = (MethodType)typeObj;
        if (!TRACE_METHOD_LINKAGE)
            return linkCallSiteImpl(caller, bootstrapMethod, name, type,
                                    staticArguments, appendixResult);
        return linkCallSiteTracing(caller, bootstrapMethod, name, type,
                                   staticArguments, appendixResult);
    }
    static MemberName linkCallSiteImpl(Class<?> caller,
                                       MethodHandle bootstrapMethod,
                                       String name, MethodType type,
                                       Object staticArguments,
                                       Object[] appendixResult) {
        CallSite callSite = CallSite.makeSite(bootstrapMethod,
                                              name,
                                              type,
                                              staticArguments,
                                              caller);
        if (callSite instanceof ConstantCallSite) {
            appendixResult[0] = callSite.dynamicInvoker();
            return Invokers.linkToTargetMethod(type);
        } else {
            appendixResult[0] = callSite;
            return Invokers.linkToCallSiteMethod(type);
        }
    }
    // Tracing logic:
    static MemberName linkCallSiteTracing(Class<?> caller,
                                          MethodHandle bootstrapMethod,
                                          String name, MethodType type,
                                          Object staticArguments,
                                          Object[] appendixResult) {
        Object bsmReference = bootstrapMethod.internalMemberName();
        if (bsmReference == null)  bsmReference = bootstrapMethod;
        Object staticArglist = (staticArguments instanceof Object[] ?
                                java.util.Arrays.asList((Object[]) staticArguments) :
                                staticArguments);
        System.out.println("linkCallSite "+caller.getName()+" "+
                           bsmReference+" "+
                           name+type+"/"+staticArglist);
        try {
            MemberName res = linkCallSiteImpl(caller, bootstrapMethod, name, type,
                                              staticArguments, appendixResult);
            System.out.println("linkCallSite => "+res+" + "+appendixResult[0]);
            return res;
        } catch (Throwable ex) {
            System.out.println("linkCallSite => throw "+ex);
            throw ex;
        }
    }

    /**
     * The JVM wants a pointer to a MethodType.  Oblige it by finding or creating one.
     */
    static MethodType findMethodHandleType(Class<?> rtype, Class<?>[] ptypes) {
        return MethodType.makeImpl(rtype, ptypes, true);
    }

    /**
     * The JVM wants to link a call site that requires a dynamic type check.
     * Name is a type-checking invoker, invokeExact or invoke.
     * Return a JVM method (MemberName) to handle the invoking.
     * The method assumes the following arguments on the stack:
     * 0: the method handle being invoked
     * 1-N: the arguments to the method handle invocation
     * N+1: an optional, implicitly added argument (typically the given MethodType)
     * <p>
     * The nominal method at such a call site is an instance of
     * a signature-polymorphic method (see @PolymorphicSignature).
     * Such method instances are user-visible entities which are
     * "split" from the generic placeholder method in {@code MethodHandle}.
     * (Note that the placeholder method is not identical with any of
     * its instances.  If invoked reflectively, is guaranteed to throw an
     * {@code UnsupportedOperationException}.)
     * If the signature-polymorphic method instance is ever reified,
     * it appears as a "copy" of the original placeholder
     * (a native final member of {@code MethodHandle}) except
     * that its type descriptor has shape required by the instance,
     * and the method instance is <em>not</em> varargs.
     * The method instance is also marked synthetic, since the
     * method (by definition) does not appear in Java source code.
     * <p>
     * The JVM is allowed to reify this method as instance metadata.
     * For example, {@code invokeBasic} is always reified.
     * But the JVM may instead call {@code linkMethod}.
     * If the result is an * ordered pair of a {@code (method, appendix)},
     * the method gets all the arguments (0..N inclusive)
     * plus the appendix (N+1), and uses the appendix to complete the call.
     * In this way, one reusable method (called a "linker method")
     * can perform the function of any number of polymorphic instance
     * methods.
     * <p>
     * Linker methods are allowed to be weakly typed, with any or
     * all references rewritten to {@code Object} and any primitives
     * (except {@code long}/{@code float}/{@code double})
     * rewritten to {@code int}.
     * A linker method is trusted to return a strongly typed result,
     * according to the specific method type descriptor of the
     * signature-polymorphic instance it is emulating.
     * This can involve (as necessary) a dynamic check using
     * data extracted from the appendix argument.
     * <p>
     * The JVM does not inspect the appendix, other than to pass
     * it verbatim to the linker method at every call.
     * This means that the JDK runtime has wide latitude
     * for choosing the shape of each linker method and its
     * corresponding appendix.
     * Linker methods should be generated from {@code LambdaForm}s
     * so that they do not become visible on stack traces.
     * <p>
     * The {@code linkMethod} call is free to omit the appendix
     * (returning null) and instead emulate the required function
     * completely in the linker method.
     * As a corner case, if N==255, no appendix is possible.
     * In this case, the method returned must be custom-generated to
     * to perform any needed type checking.
     * <p>
     * If the JVM does not reify a method at a call site, but instead
     * calls {@code linkMethod}, the corresponding call represented
     * in the bytecodes may mention a valid method which is not
     * representable with a {@code MemberName}.
     * Therefore, use cases for {@code linkMethod} tend to correspond to
     * special cases in reflective code such as {@code findVirtual}
     * or {@code revealDirect}.
     */
    static MemberName linkMethod(Class<?> callerClass, int refKind,
                                 Class<?> defc, String name, Object type,
                                 Object[] appendixResult) {
        if (!TRACE_METHOD_LINKAGE)
            return linkMethodImpl(callerClass, refKind, defc, name, type, appendixResult);
        return linkMethodTracing(callerClass, refKind, defc, name, type, appendixResult);
    }
    static MemberName linkMethodImpl(Class<?> callerClass, int refKind,
                                     Class<?> defc, String name, Object type,
                                     Object[] appendixResult) {
        try {
            if (defc == MethodHandle.class && refKind == REF_invokeVirtual) {
                return Invokers.methodHandleInvokeLinkerMethod(name, fixMethodType(callerClass, type), appendixResult);
            }
        } catch (Throwable ex) {
            if (ex instanceof LinkageError)
                throw (LinkageError) ex;
            else
                throw new LinkageError(ex.getMessage(), ex);
        }
        throw new LinkageError("no such method "+defc.getName()+"."+name+type);
    }
    private static MethodType fixMethodType(Class<?> callerClass, Object type) {
        if (type instanceof MethodType)
            return (MethodType) type;
        else
            return MethodType.fromMethodDescriptorString((String)type, callerClass.getClassLoader());
    }
    // Tracing logic:
    static MemberName linkMethodTracing(Class<?> callerClass, int refKind,
                                        Class<?> defc, String name, Object type,
                                        Object[] appendixResult) {
        System.out.println("linkMethod "+defc.getName()+"."+
                           name+type+"/"+Integer.toHexString(refKind));
        try {
            MemberName res = linkMethodImpl(callerClass, refKind, defc, name, type, appendixResult);
            System.out.println("linkMethod => "+res+" + "+appendixResult[0]);
            return res;
        } catch (Throwable ex) {
            System.out.println("linkMethod => throw "+ex);
            throw ex;
        }
    }


    /**
     * The JVM is resolving a CONSTANT_MethodHandle CP entry.  And it wants our help.
     * It will make an up-call to this method.  (Do not change the name or signature.)
     * The type argument is a Class for field requests and a MethodType for non-fields.
     * <p>
     * Recent versions of the JVM may also pass a resolved MemberName for the type.
     * In that case, the name is ignored and may be null.
     */
    static MethodHandle linkMethodHandleConstant(Class<?> callerClass, int refKind,
                                                 Class<?> defc, String name, Object type) {
        try {
            Lookup lookup = IMPL_LOOKUP.in(callerClass);
            assert(refKindIsValid(refKind));
            return lookup.linkMethodHandleConstant((byte) refKind, defc, name, type);
        } catch (IllegalAccessException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof AbstractMethodError) {
                throw (AbstractMethodError) cause;
            } else {
                Error err = new IllegalAccessError(ex.getMessage());
                throw initCauseFrom(err, ex);
            }
        } catch (NoSuchMethodException ex) {
            Error err = new NoSuchMethodError(ex.getMessage());
            throw initCauseFrom(err, ex);
        } catch (NoSuchFieldException ex) {
            Error err = new NoSuchFieldError(ex.getMessage());
            throw initCauseFrom(err, ex);
        } catch (ReflectiveOperationException ex) {
            Error err = new IncompatibleClassChangeError();
            throw initCauseFrom(err, ex);
        }
    }

    /**
     * Use best possible cause for err.initCause(), substituting the
     * cause for err itself if the cause has the same (or better) type.
     */
    static private Error initCauseFrom(Error err, Exception ex) {
        Throwable th = ex.getCause();
        if (err.getClass().isInstance(th))
           return (Error) th;
        err.initCause(th == null ? ex : th);
        return err;
    }

    /**
     * Is this method a caller-sensitive method?
     * I.e., does it call Reflection.getCallerClass or a similer method
     * to ask about the identity of its caller?
     */
    static boolean isCallerSensitive(MemberName mem) {
        if (!mem.isInvocable())  return false;  // fields are not caller sensitive

        return mem.isCallerSensitive() || canBeCalledVirtual(mem);
    }

    static boolean canBeCalledVirtual(MemberName mem) {
        assert(mem.isInvocable());
        Class<?> defc = mem.getDeclaringClass();
        switch (mem.getName()) {
        case "checkMemberAccess":
            return canBeCalledVirtual(mem, java.lang.SecurityManager.class);
        case "getContextClassLoader":
            return canBeCalledVirtual(mem, java.lang.Thread.class);
        }
        return false;
    }

    static boolean canBeCalledVirtual(MemberName symbolicRef, Class<?> definingClass) {
        Class<?> symbolicRefClass = symbolicRef.getDeclaringClass();
        if (symbolicRefClass == definingClass)  return true;
        if (symbolicRef.isStatic() || symbolicRef.isPrivate())  return false;
        return (definingClass.isAssignableFrom(symbolicRefClass) ||  // Msym overrides Mdef
                symbolicRefClass.isInterface());                     // Mdef implements Msym
    }
}
