/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

/**
 * Access to the compiler's name table.  STandard names are defined,
 * as well as methods to create new names.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Names {

    public static final Context.Key<Names> namesKey = new Context.Key<Names>();

    public static Names instance(Context context) {
        Names instance = context.get(namesKey);
        if (instance == null) {
            instance = new Names(context);
            context.put(namesKey, instance);
        }
        return instance;
    }

    // operators and punctuation
    public final Name asterisk;
    public final Name comma;
    public final Name empty;
    public final Name hyphen;
    public final Name one;
    public final Name period;
    public final Name semicolon;
    public final Name slash;
    public final Name slashequals;

    // keywords
    public final Name _class;
    public final Name _default;
    public final Name _super;
    public final Name _this;

    // field and method names
    public final Name _name;
    public final Name addSuppressed;
    public final Name any;
    public final Name append;
    public final Name clinit;
    public final Name clone;
    public final Name close;
    public final Name compareTo;
    public final Name deserializeLambda;
    public final Name desiredAssertionStatus;
    public final Name equals;
    public final Name error;
    public final Name family;
    public final Name finalize;
    public final Name forName;
    public final Name getClass;
    public final Name getClassLoader;
    public final Name getComponentType;
    public final Name getDeclaringClass;
    public final Name getMessage;
    public final Name hasNext;
    public final Name hashCode;
    public final Name init;
    public final Name initCause;
    public final Name iterator;
    public final Name length;
    public final Name next;
    public final Name ordinal;
    public final Name serialVersionUID;
    public final Name toString;
    public final Name value;
    public final Name valueOf;
    public final Name values;

    // class names
    public final Name java_io_Serializable;
    public final Name java_lang_AutoCloseable;
    public final Name java_lang_Class;
    public final Name java_lang_Cloneable;
    public final Name java_lang_Enum;
    public final Name java_lang_Object;
    public final Name java_lang_invoke_MethodHandle;

    // names of builtin classes
    public final Name Array;
    public final Name Bound;
    public final Name Method;

    // package names
    public final Name java_lang;

    // attribute names
    public final Name Annotation;
    public final Name AnnotationDefault;
    public final Name BootstrapMethods;
    public final Name Bridge;
    public final Name CharacterRangeTable;
    public final Name Code;
    public final Name CompilationID;
    public final Name ConstantValue;
    public final Name Deprecated;
    public final Name EnclosingMethod;
    public final Name Enum;
    public final Name Exceptions;
    public final Name InnerClasses;
    public final Name LineNumberTable;
    public final Name LocalVariableTable;
    public final Name LocalVariableTypeTable;
    public final Name MethodParameters;
    public final Name RuntimeInvisibleAnnotations;
    public final Name RuntimeInvisibleParameterAnnotations;
    public final Name RuntimeInvisibleTypeAnnotations;
    public final Name RuntimeVisibleAnnotations;
    public final Name RuntimeVisibleParameterAnnotations;
    public final Name RuntimeVisibleTypeAnnotations;
    public final Name Signature;
    public final Name SourceFile;
    public final Name SourceID;
    public final Name StackMap;
    public final Name StackMapTable;
    public final Name Synthetic;
    public final Name Value;
    public final Name Varargs;

    // members of java.lang.annotation.ElementType
    public final Name ANNOTATION_TYPE;
    public final Name CONSTRUCTOR;
    public final Name FIELD;
    public final Name LOCAL_VARIABLE;
    public final Name METHOD;
    public final Name PACKAGE;
    public final Name PARAMETER;
    public final Name TYPE;
    public final Name TYPE_PARAMETER;
    public final Name TYPE_USE;

    // members of java.lang.annotation.RetentionPolicy
    public final Name CLASS;
    public final Name RUNTIME;
    public final Name SOURCE;

    // other identifiers
    public final Name T;
    public final Name deprecated;
    public final Name ex;
    public final Name package_info;

    //lambda-related
    public final Name lambda;
    public final Name metafactory;
    public final Name altMetafactory;
    public final Name dollarThis;

    public final Name.Table table;

    public Names(Context context) {
        Options options = Options.instance(context);
        table = createTable(options);

        // operators and punctuation
        asterisk = fromString("*");
        comma = fromString(",");
        empty = fromString("");
        hyphen = fromString("-");
        one = fromString("1");
        period = fromString(".");
        semicolon = fromString(";");
        slash = fromString("/");
        slashequals = fromString("/=");

        // keywords
        _class = fromString("class");
        _default = fromString("default");
        _super = fromString("super");
        _this = fromString("this");

        // field and method names
        _name = fromString("name");
        addSuppressed = fromString("addSuppressed");
        any = fromString("<any>");
        append = fromString("append");
        clinit = fromString("<clinit>");
        clone = fromString("clone");
        close = fromString("close");
        compareTo = fromString("compareTo");
        deserializeLambda = fromString("$deserializeLambda$");
        desiredAssertionStatus = fromString("desiredAssertionStatus");
        equals = fromString("equals");
        error = fromString("<error>");
        family = fromString("family");
        finalize = fromString("finalize");
        forName = fromString("forName");
        getClass = fromString("getClass");
        getClassLoader = fromString("getClassLoader");
        getComponentType = fromString("getComponentType");
        getDeclaringClass = fromString("getDeclaringClass");
        getMessage = fromString("getMessage");
        hasNext = fromString("hasNext");
        hashCode = fromString("hashCode");
        init = fromString("<init>");
        initCause = fromString("initCause");
        iterator = fromString("iterator");
        length = fromString("length");
        next = fromString("next");
        ordinal = fromString("ordinal");
        serialVersionUID = fromString("serialVersionUID");
        toString = fromString("toString");
        value = fromString("value");
        valueOf = fromString("valueOf");
        values = fromString("values");
        dollarThis = fromString("$this");

        // class names
        java_io_Serializable = fromString("java.io.Serializable");
        java_lang_AutoCloseable = fromString("java.lang.AutoCloseable");
        java_lang_Class = fromString("java.lang.Class");
        java_lang_Cloneable = fromString("java.lang.Cloneable");
        java_lang_Enum = fromString("java.lang.Enum");
        java_lang_Object = fromString("java.lang.Object");
        java_lang_invoke_MethodHandle = fromString("java.lang.invoke.MethodHandle");

        // names of builtin classes
        Array = fromString("Array");
        Bound = fromString("Bound");
        Method = fromString("Method");

        // package names
        java_lang = fromString("java.lang");

        // attribute names
        Annotation = fromString("Annotation");
        AnnotationDefault = fromString("AnnotationDefault");
        BootstrapMethods = fromString("BootstrapMethods");
        Bridge = fromString("Bridge");
        CharacterRangeTable = fromString("CharacterRangeTable");
        Code = fromString("Code");
        CompilationID = fromString("CompilationID");
        ConstantValue = fromString("ConstantValue");
        Deprecated = fromString("Deprecated");
        EnclosingMethod = fromString("EnclosingMethod");
        Enum = fromString("Enum");
        Exceptions = fromString("Exceptions");
        InnerClasses = fromString("InnerClasses");
        LineNumberTable = fromString("LineNumberTable");
        LocalVariableTable = fromString("LocalVariableTable");
        LocalVariableTypeTable = fromString("LocalVariableTypeTable");
        MethodParameters = fromString("MethodParameters");
        RuntimeInvisibleAnnotations = fromString("RuntimeInvisibleAnnotations");
        RuntimeInvisibleParameterAnnotations = fromString("RuntimeInvisibleParameterAnnotations");
        RuntimeInvisibleTypeAnnotations = fromString("RuntimeInvisibleTypeAnnotations");
        RuntimeVisibleAnnotations = fromString("RuntimeVisibleAnnotations");
        RuntimeVisibleParameterAnnotations = fromString("RuntimeVisibleParameterAnnotations");
        RuntimeVisibleTypeAnnotations = fromString("RuntimeVisibleTypeAnnotations");
        Signature = fromString("Signature");
        SourceFile = fromString("SourceFile");
        SourceID = fromString("SourceID");
        StackMap = fromString("StackMap");
        StackMapTable = fromString("StackMapTable");
        Synthetic = fromString("Synthetic");
        Value = fromString("Value");
        Varargs = fromString("Varargs");

        // members of java.lang.annotation.ElementType
        ANNOTATION_TYPE = fromString("ANNOTATION_TYPE");
        CONSTRUCTOR = fromString("CONSTRUCTOR");
        FIELD = fromString("FIELD");
        LOCAL_VARIABLE = fromString("LOCAL_VARIABLE");
        METHOD = fromString("METHOD");
        PACKAGE = fromString("PACKAGE");
        PARAMETER = fromString("PARAMETER");
        TYPE = fromString("TYPE");
        TYPE_PARAMETER = fromString("TYPE_PARAMETER");
        TYPE_USE = fromString("TYPE_USE");

        // members of java.lang.annotation.RetentionPolicy
        CLASS = fromString("CLASS");
        RUNTIME = fromString("RUNTIME");
        SOURCE = fromString("SOURCE");

        // other identifiers
        T = fromString("T");
        deprecated = fromString("deprecated");
        ex = fromString("ex");
        package_info = fromString("package-info");

        //lambda-related
        lambda = fromString("lambda$");
        metafactory = fromString("metafactory");
        altMetafactory = fromString("altMetafactory");
    }

    protected Name.Table createTable(Options options) {
        boolean useUnsharedTable = options.isSet("useUnsharedTable");
        if (useUnsharedTable)
            return new UnsharedNameTable(this);
        else
            return new SharedNameTable(this);
    }

    public void dispose() {
        table.dispose();
    }

    public Name fromChars(char[] cs, int start, int len) {
        return table.fromChars(cs, start, len);
    }

    public Name fromString(String s) {
        return table.fromString(s);
    }

    public Name fromUtf(byte[] cs) {
        return table.fromUtf(cs);
    }

    public Name fromUtf(byte[] cs, int start, int len) {
        return table.fromUtf(cs, start, len);
    }
}
