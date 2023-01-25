/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.jvm;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardJavaFileManager;

import static javax.tools.StandardLocation.*;

import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.file.BaseFileObject;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.code.TypeTag.TYPEVAR;
import static com.sun.tools.javac.jvm.ClassFile.*;
import static com.sun.tools.javac.jvm.ClassFile.Version.*;

import static com.sun.tools.javac.main.Option.*;

/** This class provides operations to read a classfile into an internal
 *  representation. The internal representation is anchored in a
 *  ClassSymbol which contains in its scope symbol representations
 *  for all other definitions in the classfile. Top-level Classes themselves
 *  appear as members of the scopes of PackageSymbols.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ClassReader {
    /** The context key for the class reader. */
    protected static final Context.Key<ClassReader> classReaderKey =
        new Context.Key<ClassReader>();

    public static final int INITIAL_BUFFER_SIZE = 0x0fff0;

    Annotate annotate;

    /** Switch: verbose output.
     */
    boolean verbose;

    /** Switch: check class file for correct minor version, unrecognized
     *  attributes.
     */
    boolean checkClassFile;

    /** Switch: read constant pool and code sections. This switch is initially
     *  set to false but can be turned on from outside.
     */
    public boolean readAllOfClassFile = false;

    /** Switch: read GJ signature information.
     */
    boolean allowGenerics;

    /** Switch: read varargs attribute.
     */
    boolean allowVarargs;

    /** Switch: allow annotations.
     */
    boolean allowAnnotations;

    /** Switch: allow simplified varargs.
     */
    boolean allowSimplifiedVarargs;

   /** Lint option: warn about classfile issues
     */
    boolean lintClassfile;

    /** Switch: preserve parameter names from the variable table.
     */
    public boolean saveParameterNames;

    /**
     * Switch: cache completion failures unless -XDdev is used
     */
    private boolean cacheCompletionFailure;

    /**
     * Switch: prefer source files instead of newer when both source
     * and class are available
     **/
    public boolean preferSource;

    /**
     * The currently selected profile.
     */
    public final Profile profile;

    /** The log to use for verbose output
     */
    final Log log;

    /** The symbol table. */
    Symtab syms;

    Types types;

    /** The name table. */
    final Names names;

    /** Force a completion failure on this name
     */
    final Name completionFailureName;

    /** Access to files
     */
    private final JavaFileManager fileManager;

    /** Factory for diagnostics
     */
    JCDiagnostic.Factory diagFactory;

    /** Can be reassigned from outside:
     *  the completer to be used for ".java" files. If this remains unassigned
     *  ".java" files will not be loaded.
     */
    public SourceCompleter sourceCompleter = null;

    /** A hashtable containing the encountered top-level and member classes,
     *  indexed by flat names. The table does not contain local classes.
     */
    private Map<Name,ClassSymbol> classes;

    /** A hashtable containing the encountered packages.
     */
    private Map<Name, PackageSymbol> packages;

    /** The current scope where type variables are entered.
     */
    protected Scope typevars;

    /** The path name of the class file currently being read.
     */
    protected JavaFileObject currentClassFile = null;

    /** The class or method currently being read.
     */
    protected Symbol currentOwner = null;

    /** The buffer containing the currently read class file.
     */
    byte[] buf = new byte[INITIAL_BUFFER_SIZE];

    /** The current input pointer.
     */
    protected int bp;

    /** The objects of the constant pool.
     */
    Object[] poolObj;

    /** For every constant pool entry, an index into buf where the
     *  defining section of the entry is found.
     */
    int[] poolIdx;

    /** The major version number of the class file being read. */
    int majorVersion;
    /** The minor version number of the class file being read. */
    int minorVersion;

    /** A table to hold the constant pool indices for method parameter
     * names, as given in LocalVariableTable attributes.
     */
    int[] parameterNameIndices;

    /**
     * Whether or not any parameter names have been found.
     */
    boolean haveParameterNameIndices;

    /** Set this to false every time we start reading a method
     * and are saving parameter names.  Set it to true when we see
     * MethodParameters, if it's set when we see a LocalVariableTable,
     * then we ignore the parameter names from the LVT.
     */
    boolean sawMethodParameters;

    /**
     * The set of attribute names for which warnings have been generated for the current class
     */
    Set<Name> warnedAttrs = new HashSet<Name>();

    /**
     * Completer that delegates to the complete-method of this class.
     */
    private final Completer thisCompleter = new Completer() {
        @Override
        public void complete(Symbol sym) throws CompletionFailure {
            ClassReader.this.complete(sym);
        }
    };


    /** Get the ClassReader instance for this invocation. */
    public static ClassReader instance(Context context) {
        ClassReader instance = context.get(classReaderKey);
        if (instance == null)
            instance = new ClassReader(context, true);
        return instance;
    }

    /** Initialize classes and packages, treating this as the definitive classreader. */
    public void init(Symtab syms) {
        init(syms, true);
    }

    /** Initialize classes and packages, optionally treating this as
     *  the definitive classreader.
     */
    private void init(Symtab syms, boolean definitive) {
        if (classes != null) return;

        if (definitive) {
            Assert.check(packages == null || packages == syms.packages);
            packages = syms.packages;
            Assert.check(classes == null || classes == syms.classes);
            classes = syms.classes;
        } else {
            packages = new HashMap<Name, PackageSymbol>();
            classes = new HashMap<Name, ClassSymbol>();
        }

        packages.put(names.empty, syms.rootPackage);
        syms.rootPackage.completer = thisCompleter;
        syms.unnamedPackage.completer = thisCompleter;
    }

    /** Construct a new class reader, optionally treated as the
     *  definitive classreader for this invocation.
     */
    protected ClassReader(Context context, boolean definitive) {
        if (definitive) context.put(classReaderKey, this);

        names = Names.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        fileManager = context.get(JavaFileManager.class);
        if (fileManager == null)
            throw new AssertionError("FileManager initialization error");
        diagFactory = JCDiagnostic.Factory.instance(context);

        init(syms, definitive);
        log = Log.instance(context);

        Options options = Options.instance(context);
        annotate = Annotate.instance(context);
        verbose        = options.isSet(VERBOSE);
        checkClassFile = options.isSet("-checkclassfile");

        Source source = Source.instance(context);
        allowGenerics    = source.allowGenerics();
        allowVarargs     = source.allowVarargs();
        allowAnnotations = source.allowAnnotations();
        allowSimplifiedVarargs = source.allowSimplifiedVarargs();

        saveParameterNames = options.isSet("save-parameter-names");
        cacheCompletionFailure = options.isUnset("dev");
        preferSource = "source".equals(options.get("-Xprefer"));

        profile = Profile.instance(context);

        completionFailureName =
            options.isSet("failcomplete")
            ? names.fromString(options.get("failcomplete"))
            : null;

        typevars = new Scope(syms.noSymbol);

        lintClassfile = Lint.instance(context).isEnabled(LintCategory.CLASSFILE);

        initAttributeReaders();
    }

    /** Add member to class unless it is synthetic.
     */
    private void enterMember(ClassSymbol c, Symbol sym) {
        // Synthetic members are not entered -- reason lost to history (optimization?).
        // Lambda methods must be entered because they may have inner classes (which reference them)
        if ((sym.flags_field & (SYNTHETIC|BRIDGE)) != SYNTHETIC || sym.name.startsWith(names.lambda))
            c.members_field.enter(sym);
    }

/************************************************************************
 * Error Diagnoses
 ***********************************************************************/


    public class BadClassFile extends CompletionFailure {
        private static final long serialVersionUID = 0;

        public BadClassFile(TypeSymbol sym, JavaFileObject file, JCDiagnostic diag) {
            super(sym, createBadClassFileDiagnostic(file, diag));
        }
    }
    // where
    private JCDiagnostic createBadClassFileDiagnostic(JavaFileObject file, JCDiagnostic diag) {
        String key = (file.getKind() == JavaFileObject.Kind.SOURCE
                    ? "bad.source.file.header" : "bad.class.file.header");
        return diagFactory.fragment(key, file, diag);
    }

    public BadClassFile badClassFile(String key, Object... args) {
        return new BadClassFile (
            currentOwner.enclClass(),
            currentClassFile,
            diagFactory.fragment(key, args));
    }

/************************************************************************
 * Buffer Access
 ***********************************************************************/

    /** Read a character.
     */
    char nextChar() {
        return (char)(((buf[bp++] & 0xFF) << 8) + (buf[bp++] & 0xFF));
    }

    /** Read a byte.
     */
    int nextByte() {
        return buf[bp++] & 0xFF;
    }

    /** Read an integer.
     */
    int nextInt() {
        return
            ((buf[bp++] & 0xFF) << 24) +
            ((buf[bp++] & 0xFF) << 16) +
            ((buf[bp++] & 0xFF) << 8) +
            (buf[bp++] & 0xFF);
    }

    /** Extract a character at position bp from buf.
     */
    char getChar(int bp) {
        return
            (char)(((buf[bp] & 0xFF) << 8) + (buf[bp+1] & 0xFF));
    }

    /** Extract an integer at position bp from buf.
     */
    int getInt(int bp) {
        return
            ((buf[bp] & 0xFF) << 24) +
            ((buf[bp+1] & 0xFF) << 16) +
            ((buf[bp+2] & 0xFF) << 8) +
            (buf[bp+3] & 0xFF);
    }


    /** Extract a long integer at position bp from buf.
     */
    long getLong(int bp) {
        DataInputStream bufin =
            new DataInputStream(new ByteArrayInputStream(buf, bp, 8));
        try {
            return bufin.readLong();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Extract a float at position bp from buf.
     */
    float getFloat(int bp) {
        DataInputStream bufin =
            new DataInputStream(new ByteArrayInputStream(buf, bp, 4));
        try {
            return bufin.readFloat();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Extract a double at position bp from buf.
     */
    double getDouble(int bp) {
        DataInputStream bufin =
            new DataInputStream(new ByteArrayInputStream(buf, bp, 8));
        try {
            return bufin.readDouble();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

/************************************************************************
 * Constant Pool Access
 ***********************************************************************/

    /** Index all constant pool entries, writing their start addresses into
     *  poolIdx.
     */
    void indexPool() {
        poolIdx = new int[nextChar()];
        poolObj = new Object[poolIdx.length];
        int i = 1;
        while (i < poolIdx.length) {
            poolIdx[i++] = bp;
            byte tag = buf[bp++];
            switch (tag) {
            case CONSTANT_Utf8: case CONSTANT_Unicode: {
                int len = nextChar();
                bp = bp + len;
                break;
            }
            case CONSTANT_Class:
            case CONSTANT_String:
            case CONSTANT_MethodType:
                bp = bp + 2;
                break;
            case CONSTANT_MethodHandle:
                bp = bp + 3;
                break;
            case CONSTANT_Fieldref:
            case CONSTANT_Methodref:
            case CONSTANT_InterfaceMethodref:
            case CONSTANT_NameandType:
            case CONSTANT_Integer:
            case CONSTANT_Float:
            case CONSTANT_InvokeDynamic:
                bp = bp + 4;
                break;
            case CONSTANT_Long:
            case CONSTANT_Double:
                bp = bp + 8;
                i++;
                break;
            default:
                throw badClassFile("bad.const.pool.tag.at",
                                   Byte.toString(tag),
                                   Integer.toString(bp -1));
            }
        }
    }

    /** Read constant pool entry at start address i, use pool as a cache.
     */
    Object readPool(int i) {
        Object result = poolObj[i];
        if (result != null) return result;

        int index = poolIdx[i];
        if (index == 0) return null;

        byte tag = buf[index];
        switch (tag) {
        case CONSTANT_Utf8:
            poolObj[i] = names.fromUtf(buf, index + 3, getChar(index + 1));
            break;
        case CONSTANT_Unicode:
            throw badClassFile("unicode.str.not.supported");
        case CONSTANT_Class:
            poolObj[i] = readClassOrType(getChar(index + 1));
            break;
        case CONSTANT_String:
            // FIXME: (footprint) do not use toString here
            poolObj[i] = readName(getChar(index + 1)).toString();
            break;
        case CONSTANT_Fieldref: {
            ClassSymbol owner = readClassSymbol(getChar(index + 1));
            NameAndType nt = readNameAndType(getChar(index + 3));
            poolObj[i] = new VarSymbol(0, nt.name, nt.uniqueType.type, owner);
            break;
        }
        case CONSTANT_Methodref:
        case CONSTANT_InterfaceMethodref: {
            ClassSymbol owner = readClassSymbol(getChar(index + 1));
            NameAndType nt = readNameAndType(getChar(index + 3));
            poolObj[i] = new MethodSymbol(0, nt.name, nt.uniqueType.type, owner);
            break;
        }
        case CONSTANT_NameandType:
            poolObj[i] = new NameAndType(
                readName(getChar(index + 1)),
                readType(getChar(index + 3)), types);
            break;
        case CONSTANT_Integer:
            poolObj[i] = getInt(index + 1);
            break;
        case CONSTANT_Float:
            poolObj[i] = new Float(getFloat(index + 1));
            break;
        case CONSTANT_Long:
            poolObj[i] = new Long(getLong(index + 1));
            break;
        case CONSTANT_Double:
            poolObj[i] = new Double(getDouble(index + 1));
            break;
        case CONSTANT_MethodHandle:
            skipBytes(4);
            break;
        case CONSTANT_MethodType:
            skipBytes(3);
            break;
        case CONSTANT_InvokeDynamic:
            skipBytes(5);
            break;
        default:
            throw badClassFile("bad.const.pool.tag", Byte.toString(tag));
        }
        return poolObj[i];
    }

    /** Read signature and convert to type.
     */
    Type readType(int i) {
        int index = poolIdx[i];
        return sigToType(buf, index + 3, getChar(index + 1));
    }

    /** If name is an array type or class signature, return the
     *  corresponding type; otherwise return a ClassSymbol with given name.
     */
    Object readClassOrType(int i) {
        int index =  poolIdx[i];
        int len = getChar(index + 1);
        int start = index + 3;
        Assert.check(buf[start] == '[' || buf[start + len - 1] != ';');
        // by the above assertion, the following test can be
        // simplified to (buf[start] == '[')
        return (buf[start] == '[' || buf[start + len - 1] == ';')
            ? (Object)sigToType(buf, start, len)
            : (Object)enterClass(names.fromUtf(internalize(buf, start,
                                                           len)));
    }

    /** Read signature and convert to type parameters.
     */
    List<Type> readTypeParams(int i) {
        int index = poolIdx[i];
        return sigToTypeParams(buf, index + 3, getChar(index + 1));
    }

    /** Read class entry.
     */
    ClassSymbol readClassSymbol(int i) {
        Object obj = readPool(i);
        if (obj != null && !(obj instanceof ClassSymbol))
            throw badClassFile("bad.const.pool.entry",
                               currentClassFile.toString(),
                               "CONSTANT_Class_info", i);
        return (ClassSymbol)obj;
    }

    /** Read name.
     */
    Name readName(int i) {
        Object obj = readPool(i);
        if (obj != null && !(obj instanceof Name))
            throw badClassFile("bad.const.pool.entry",
                               currentClassFile.toString(),
                               "CONSTANT_Utf8_info or CONSTANT_String_info", i);
        return (Name)obj;
    }

    /** Read name and type.
     */
    NameAndType readNameAndType(int i) {
        Object obj = readPool(i);
        if (obj != null && !(obj instanceof NameAndType))
            throw badClassFile("bad.const.pool.entry",
                               currentClassFile.toString(),
                               "CONSTANT_NameAndType_info", i);
        return (NameAndType)obj;
    }

/************************************************************************
 * Reading Types
 ***********************************************************************/

    /** The unread portion of the currently read type is
     *  signature[sigp..siglimit-1].
     */
    byte[] signature;
    int sigp;
    int siglimit;
    boolean sigEnterPhase = false;

    /** Convert signature to type, where signature is a byte array segment.
     */
    Type sigToType(byte[] sig, int offset, int len) {
        signature = sig;
        sigp = offset;
        siglimit = offset + len;
        return sigToType();
    }

    /** Convert signature to type, where signature is implicit.
     */
    Type sigToType() {
        switch ((char) signature[sigp]) {
        case 'T':
            sigp++;
            int start = sigp;
            while (signature[sigp] != ';') sigp++;
            sigp++;
            return sigEnterPhase
                ? Type.noType
                : findTypeVar(names.fromUtf(signature, start, sigp - 1 - start));
        case '+': {
            sigp++;
            Type t = sigToType();
            return new WildcardType(t, BoundKind.EXTENDS,
                                    syms.boundClass);
        }
        case '*':
            sigp++;
            return new WildcardType(syms.objectType, BoundKind.UNBOUND,
                                    syms.boundClass);
        case '-': {
            sigp++;
            Type t = sigToType();
            return new WildcardType(t, BoundKind.SUPER,
                                    syms.boundClass);
        }
        case 'B':
            sigp++;
            return syms.byteType;
        case 'C':
            sigp++;
            return syms.charType;
        case 'D':
            sigp++;
            return syms.doubleType;
        case 'F':
            sigp++;
            return syms.floatType;
        case 'I':
            sigp++;
            return syms.intType;
        case 'J':
            sigp++;
            return syms.longType;
        case 'L':
            {
                // int oldsigp = sigp;
                Type t = classSigToType();
                if (sigp < siglimit && signature[sigp] == '.')
                    throw badClassFile("deprecated inner class signature syntax " +
                                       "(please recompile from source)");
                /*
                System.err.println(" decoded " +
                                   new String(signature, oldsigp, sigp-oldsigp) +
                                   " => " + t + " outer " + t.outer());
                */
                return t;
            }
        case 'S':
            sigp++;
            return syms.shortType;
        case 'V':
            sigp++;
            return syms.voidType;
        case 'Z':
            sigp++;
            return syms.booleanType;
        case '[':
            sigp++;
            return new ArrayType(sigToType(), syms.arrayClass);
        case '(':
            sigp++;
            List<Type> argtypes = sigToTypes(')');
            Type restype = sigToType();
            List<Type> thrown = List.nil();
            while (signature[sigp] == '^') {
                sigp++;
                thrown = thrown.prepend(sigToType());
            }
            // if there is a typevar in the throws clause we should state it.
            for (List<Type> l = thrown; l.nonEmpty(); l = l.tail) {
                if (l.head.hasTag(TYPEVAR)) {
                    l.head.tsym.flags_field |= THROWS;
                }
            }
            return new MethodType(argtypes,
                                  restype,
                                  thrown.reverse(),
                                  syms.methodClass);
        case '<':
            typevars = typevars.dup(currentOwner);
            Type poly = new ForAll(sigToTypeParams(), sigToType());
            typevars = typevars.leave();
            return poly;
        default:
            throw badClassFile("bad.signature",
                               Convert.utf2string(signature, sigp, 10));
        }
    }

    byte[] signatureBuffer = new byte[0];
    int sbp = 0;
    /** Convert class signature to type, where signature is implicit.
     */
    Type classSigToType() {
        if (signature[sigp] != 'L')
            throw badClassFile("bad.class.signature",
                               Convert.utf2string(signature, sigp, 10));
        sigp++;
        Type outer = Type.noType;
        int startSbp = sbp;

        while (true) {
            final byte c = signature[sigp++];
            switch (c) {

            case ';': {         // end
                ClassSymbol t = enterClass(names.fromUtf(signatureBuffer,
                                                         startSbp,
                                                         sbp - startSbp));

                try {
                    return (outer == Type.noType) ?
                            t.erasure(types) :
                            new ClassType(outer, List.<Type>nil(), t);
                } finally {
                    sbp = startSbp;
                }
            }

            case '<':           // generic arguments
                ClassSymbol t = enterClass(names.fromUtf(signatureBuffer,
                                                         startSbp,
                                                         sbp - startSbp));
                outer = new ClassType(outer, sigToTypes('>'), t) {
                        boolean completed = false;
                        @Override
                        public Type getEnclosingType() {
                            if (!completed) {
                                completed = true;
                                tsym.complete();
                                Type enclosingType = tsym.type.getEnclosingType();
                                if (enclosingType != Type.noType) {
                                    List<Type> typeArgs =
                                        super.getEnclosingType().allparams();
                                    List<Type> typeParams =
                                        enclosingType.allparams();
                                    if (typeParams.length() != typeArgs.length()) {
                                        // no "rare" types
                                        super.setEnclosingType(types.erasure(enclosingType));
                                    } else {
                                        super.setEnclosingType(types.subst(enclosingType,
                                                                           typeParams,
                                                                           typeArgs));
                                    }
                                } else {
                                    super.setEnclosingType(Type.noType);
                                }
                            }
                            return super.getEnclosingType();
                        }
                        @Override
                        public void setEnclosingType(Type outer) {
                            throw new UnsupportedOperationException();
                        }
                    };
                switch (signature[sigp++]) {
                case ';':
                    if (sigp < signature.length && signature[sigp] == '.') {
                        // support old-style GJC signatures
                        // The signature produced was
                        // Lfoo/Outer<Lfoo/X;>;.Lfoo/Outer$Inner<Lfoo/Y;>;
                        // rather than say
                        // Lfoo/Outer<Lfoo/X;>.Inner<Lfoo/Y;>;
                        // so we skip past ".Lfoo/Outer$"
                        sigp += (sbp - startSbp) + // "foo/Outer"
                            3;  // ".L" and "$"
                        signatureBuffer[sbp++] = (byte)'$';
                        break;
                    } else {
                        sbp = startSbp;
                        return outer;
                    }
                case '.':
                    signatureBuffer[sbp++] = (byte)'$';
                    break;
                default:
                    throw new AssertionError(signature[sigp-1]);
                }
                continue;

            case '.':
                //we have seen an enclosing non-generic class
                if (outer != Type.noType) {
                    t = enterClass(names.fromUtf(signatureBuffer,
                                                 startSbp,
                                                 sbp - startSbp));
                    outer = new ClassType(outer, List.<Type>nil(), t);
                }
                signatureBuffer[sbp++] = (byte)'$';
                continue;
            case '/':
                signatureBuffer[sbp++] = (byte)'.';
                continue;
            default:
                signatureBuffer[sbp++] = c;
                continue;
            }
        }
    }

    /** Convert (implicit) signature to list of types
     *  until `terminator' is encountered.
     */
    List<Type> sigToTypes(char terminator) {
        List<Type> head = List.of(null);
        List<Type> tail = head;
        while (signature[sigp] != terminator)
            tail = tail.setTail(List.of(sigToType()));
        sigp++;
        return head.tail;
    }

    /** Convert signature to type parameters, where signature is a byte
     *  array segment.
     */
    List<Type> sigToTypeParams(byte[] sig, int offset, int len) {
        signature = sig;
        sigp = offset;
        siglimit = offset + len;
        return sigToTypeParams();
    }

    /** Convert signature to type parameters, where signature is implicit.
     */
    List<Type> sigToTypeParams() {
        List<Type> tvars = List.nil();
        if (signature[sigp] == '<') {
            sigp++;
            int start = sigp;
            sigEnterPhase = true;
            while (signature[sigp] != '>')
                tvars = tvars.prepend(sigToTypeParam());
            sigEnterPhase = false;
            sigp = start;
            while (signature[sigp] != '>')
                sigToTypeParam();
            sigp++;
        }
        return tvars.reverse();
    }

    /** Convert (implicit) signature to type parameter.
     */
    Type sigToTypeParam() {
        int start = sigp;
        while (signature[sigp] != ':') sigp++;
        Name name = names.fromUtf(signature, start, sigp - start);
        TypeVar tvar;
        if (sigEnterPhase) {
            tvar = new TypeVar(name, currentOwner, syms.botType);
            typevars.enter(tvar.tsym);
        } else {
            tvar = (TypeVar)findTypeVar(name);
        }
        List<Type> bounds = List.nil();
        boolean allInterfaces = false;
        if (signature[sigp] == ':' && signature[sigp+1] == ':') {
            sigp++;
            allInterfaces = true;
        }
        while (signature[sigp] == ':') {
            sigp++;
            bounds = bounds.prepend(sigToType());
        }
        if (!sigEnterPhase) {
            types.setBounds(tvar, bounds.reverse(), allInterfaces);
        }
        return tvar;
    }

    /** Find type variable with given name in `typevars' scope.
     */
    Type findTypeVar(Name name) {
        Scope.Entry e = typevars.lookup(name);
        if (e.scope != null) {
            return e.sym.type;
        } else {
            if (readingClassAttr) {
                // While reading the class attribute, the supertypes
                // might refer to a type variable from an enclosing element
                // (method or class).
                // If the type variable is defined in the enclosing class,
                // we can actually find it in
                // currentOwner.owner.type.getTypeArguments()
                // However, until we have read the enclosing method attribute
                // we don't know for sure if this owner is correct.  It could
                // be a method and there is no way to tell before reading the
                // enclosing method attribute.
                TypeVar t = new TypeVar(name, currentOwner, syms.botType);
                missingTypeVariables = missingTypeVariables.prepend(t);
                // System.err.println("Missing type var " + name);
                return t;
            }
            throw badClassFile("undecl.type.var", name);
        }
    }

/************************************************************************
 * Reading Attributes
 ***********************************************************************/

    protected enum AttributeKind { CLASS, MEMBER };
    protected abstract class AttributeReader {
        protected AttributeReader(Name name, ClassFile.Version version, Set<AttributeKind> kinds) {
            this.name = name;
            this.version = version;
            this.kinds = kinds;
        }

        protected boolean accepts(AttributeKind kind) {
            if (kinds.contains(kind)) {
                if (majorVersion > version.major || (majorVersion == version.major && minorVersion >= version.minor))
                    return true;

                if (lintClassfile && !warnedAttrs.contains(name)) {
                    JavaFileObject prev = log.useSource(currentClassFile);
                    try {
                        log.warning(LintCategory.CLASSFILE, (DiagnosticPosition) null, "future.attr",
                                name, version.major, version.minor, majorVersion, minorVersion);
                    } finally {
                        log.useSource(prev);
                    }
                    warnedAttrs.add(name);
                }
            }
            return false;
        }

        protected abstract void read(Symbol sym, int attrLen);

        protected final Name name;
        protected final ClassFile.Version version;
        protected final Set<AttributeKind> kinds;
    }

    protected Set<AttributeKind> CLASS_ATTRIBUTE =
            EnumSet.of(AttributeKind.CLASS);
    protected Set<AttributeKind> MEMBER_ATTRIBUTE =
            EnumSet.of(AttributeKind.MEMBER);
    protected Set<AttributeKind> CLASS_OR_MEMBER_ATTRIBUTE =
            EnumSet.of(AttributeKind.CLASS, AttributeKind.MEMBER);

    protected Map<Name, AttributeReader> attributeReaders = new HashMap<Name, AttributeReader>();

    private void initAttributeReaders() {
        AttributeReader[] readers = {
            // v45.3 attributes

            new AttributeReader(names.Code, V45_3, MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    if (readAllOfClassFile || saveParameterNames)
                        ((MethodSymbol)sym).code = readCode(sym);
                    else
                        bp = bp + attrLen;
                }
            },

            new AttributeReader(names.ConstantValue, V45_3, MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    Object v = readPool(nextChar());
                    // Ignore ConstantValue attribute if field not final.
                    if ((sym.flags() & FINAL) != 0)
                        ((VarSymbol) sym).setData(v);
                }
            },

            new AttributeReader(names.Deprecated, V45_3, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    sym.flags_field |= DEPRECATED;
                }
            },

            new AttributeReader(names.Exceptions, V45_3, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    int nexceptions = nextChar();
                    List<Type> thrown = List.nil();
                    for (int j = 0; j < nexceptions; j++)
                        thrown = thrown.prepend(readClassSymbol(nextChar()).type);
                    if (sym.type.getThrownTypes().isEmpty())
                        sym.type.asMethodType().thrown = thrown.reverse();
                }
            },

            new AttributeReader(names.InnerClasses, V45_3, CLASS_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    ClassSymbol c = (ClassSymbol) sym;
                    readInnerClasses(c);
                }
            },

            new AttributeReader(names.LocalVariableTable, V45_3, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    int newbp = bp + attrLen;
                    if (saveParameterNames && !sawMethodParameters) {
                        // Pick up parameter names from the variable table.
                        // Parameter names are not explicitly identified as such,
                        // but all parameter name entries in the LocalVariableTable
                        // have a start_pc of 0.  Therefore, we record the name
                        // indicies of all slots with a start_pc of zero in the
                        // parameterNameIndicies array.
                        // Note that this implicitly honors the JVMS spec that
                        // there may be more than one LocalVariableTable, and that
                        // there is no specified ordering for the entries.
                        int numEntries = nextChar();
                        for (int i = 0; i < numEntries; i++) {
                            int start_pc = nextChar();
                            int length = nextChar();
                            int nameIndex = nextChar();
                            int sigIndex = nextChar();
                            int register = nextChar();
                            if (start_pc == 0) {
                                // ensure array large enough
                                if (register >= parameterNameIndices.length) {
                                    int newSize = Math.max(register, parameterNameIndices.length + 8);
                                    parameterNameIndices =
                                            Arrays.copyOf(parameterNameIndices, newSize);
                                }
                                parameterNameIndices[register] = nameIndex;
                                haveParameterNameIndices = true;
                            }
                        }
                    }
                    bp = newbp;
                }
            },

            new AttributeReader(names.MethodParameters, V52, MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrlen) {
                    int newbp = bp + attrlen;
                    if (saveParameterNames) {
                        sawMethodParameters = true;
                        int numEntries = nextByte();
                        parameterNameIndices = new int[numEntries];
                        haveParameterNameIndices = true;
                        for (int i = 0; i < numEntries; i++) {
                            int nameIndex = nextChar();
                            int flags = nextChar();
                            parameterNameIndices[i] = nameIndex;
                        }
                    }
                    bp = newbp;
                }
            },


            new AttributeReader(names.SourceFile, V45_3, CLASS_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    ClassSymbol c = (ClassSymbol) sym;
                    Name n = readName(nextChar());
                    c.sourcefile = new SourceFileObject(n, c.flatname);
                    // If the class is a toplevel class, originating from a Java source file,
                    // but the class name does not match the file name, then it is
                    // an auxiliary class.
                    String sn = n.toString();
                    if (c.owner.kind == Kinds.PCK &&
                        sn.endsWith(".java") &&
                        !sn.equals(c.name.toString()+".java")) {
                        c.flags_field |= AUXILIARY;
                    }
                }
            },

            new AttributeReader(names.Synthetic, V45_3, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    // bridge methods are visible when generics not enabled
                    if (allowGenerics || (sym.flags_field & BRIDGE) == 0)
                        sym.flags_field |= SYNTHETIC;
                }
            },

            // standard v49 attributes

            new AttributeReader(names.EnclosingMethod, V49, CLASS_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    int newbp = bp + attrLen;
                    readEnclosingMethodAttr(sym);
                    bp = newbp;
                }
            },

            new AttributeReader(names.Signature, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                @Override
                protected boolean accepts(AttributeKind kind) {
                    return super.accepts(kind) && allowGenerics;
                }

                protected void read(Symbol sym, int attrLen) {
                    if (sym.kind == TYP) {
                        ClassSymbol c = (ClassSymbol) sym;
                        readingClassAttr = true;
                        try {
                            ClassType ct1 = (ClassType)c.type;
                            Assert.check(c == currentOwner);
                            ct1.typarams_field = readTypeParams(nextChar());
                            ct1.supertype_field = sigToType();
                            ListBuffer<Type> is = new ListBuffer<Type>();
                            while (sigp != siglimit) is.append(sigToType());
                            ct1.interfaces_field = is.toList();
                        } finally {
                            readingClassAttr = false;
                        }
                    } else {
                        List<Type> thrown = sym.type.getThrownTypes();
                        sym.type = readType(nextChar());
                        //- System.err.println(" # " + sym.type);
                        if (sym.kind == MTH && sym.type.getThrownTypes().isEmpty())
                            sym.type.asMethodType().thrown = thrown;

                    }
                }
            },

            // v49 annotation attributes

            new AttributeReader(names.AnnotationDefault, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    attachAnnotationDefault(sym);
                }
            },

            new AttributeReader(names.RuntimeInvisibleAnnotations, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    attachAnnotations(sym);
                }
            },

            new AttributeReader(names.RuntimeInvisibleParameterAnnotations, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    attachParameterAnnotations(sym);
                }
            },

            new AttributeReader(names.RuntimeVisibleAnnotations, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    attachAnnotations(sym);
                }
            },

            new AttributeReader(names.RuntimeVisibleParameterAnnotations, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    attachParameterAnnotations(sym);
                }
            },

            // additional "legacy" v49 attributes, superceded by flags

            new AttributeReader(names.Annotation, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    if (allowAnnotations)
                        sym.flags_field |= ANNOTATION;
                }
            },

            new AttributeReader(names.Bridge, V49, MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    sym.flags_field |= BRIDGE;
                    if (!allowGenerics)
                        sym.flags_field &= ~SYNTHETIC;
                }
            },

            new AttributeReader(names.Enum, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    sym.flags_field |= ENUM;
                }
            },

            new AttributeReader(names.Varargs, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    if (allowVarargs)
                        sym.flags_field |= VARARGS;
                }
            },

            new AttributeReader(names.RuntimeVisibleTypeAnnotations, V52, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    attachTypeAnnotations(sym);
                }
            },

            new AttributeReader(names.RuntimeInvisibleTypeAnnotations, V52, CLASS_OR_MEMBER_ATTRIBUTE) {
                protected void read(Symbol sym, int attrLen) {
                    attachTypeAnnotations(sym);
                }
            },


            // The following attributes for a Code attribute are not currently handled
            // StackMapTable
            // SourceDebugExtension
            // LineNumberTable
            // LocalVariableTypeTable
        };

        for (AttributeReader r: readers)
            attributeReaders.put(r.name, r);
    }

    /** Report unrecognized attribute.
     */
    void unrecognized(Name attrName) {
        if (checkClassFile)
            printCCF("ccf.unrecognized.attribute", attrName);
    }



    protected void readEnclosingMethodAttr(Symbol sym) {
        // sym is a nested class with an "Enclosing Method" attribute
        // remove sym from it's current owners scope and place it in
        // the scope specified by the attribute
        sym.owner.members().remove(sym);
        ClassSymbol self = (ClassSymbol)sym;
        ClassSymbol c = readClassSymbol(nextChar());
        NameAndType nt = readNameAndType(nextChar());

        if (c.members_field == null)
            throw badClassFile("bad.enclosing.class", self, c);

        MethodSymbol m = findMethod(nt, c.members_field, self.flags());
        if (nt != null && m == null)
            throw badClassFile("bad.enclosing.method", self);

        self.name = simpleBinaryName(self.flatname, c.flatname) ;
        self.owner = m != null ? m : c;
        if (self.name.isEmpty())
            self.fullname = names.empty;
        else
            self.fullname = ClassSymbol.formFullName(self.name, self.owner);

        if (m != null) {
            ((ClassType)sym.type).setEnclosingType(m.type);
        } else if ((self.flags_field & STATIC) == 0) {
            ((ClassType)sym.type).setEnclosingType(c.type);
        } else {
            ((ClassType)sym.type).setEnclosingType(Type.noType);
        }
        enterTypevars(self);
        if (!missingTypeVariables.isEmpty()) {
            ListBuffer<Type> typeVars =  new ListBuffer<Type>();
            for (Type typevar : missingTypeVariables) {
                typeVars.append(findTypeVar(typevar.tsym.name));
            }
            foundTypeVariables = typeVars.toList();
        } else {
            foundTypeVariables = List.nil();
        }
    }

    // See java.lang.Class
    private Name simpleBinaryName(Name self, Name enclosing) {
        String simpleBinaryName = self.toString().substring(enclosing.toString().length());
        if (simpleBinaryName.length() < 1 || simpleBinaryName.charAt(0) != '$')
            throw badClassFile("bad.enclosing.method", self);
        int index = 1;
        while (index < simpleBinaryName.length() &&
               isAsciiDigit(simpleBinaryName.charAt(index)))
            index++;
        return names.fromString(simpleBinaryName.substring(index));
    }

    private MethodSymbol findMethod(NameAndType nt, Scope scope, long flags) {
        if (nt == null)
            return null;

        MethodType type = nt.uniqueType.type.asMethodType();

        for (Scope.Entry e = scope.lookup(nt.name); e.scope != null; e = e.next())
            if (e.sym.kind == MTH && isSameBinaryType(e.sym.type.asMethodType(), type))
                return (MethodSymbol)e.sym;

        if (nt.name != names.init)
            // not a constructor
            return null;
        if ((flags & INTERFACE) != 0)
            // no enclosing instance
            return null;
        if (nt.uniqueType.type.getParameterTypes().isEmpty())
            // no parameters
            return null;

        // A constructor of an inner class.
        // Remove the first argument (the enclosing instance)
        nt.setType(new MethodType(nt.uniqueType.type.getParameterTypes().tail,
                                 nt.uniqueType.type.getReturnType(),
                                 nt.uniqueType.type.getThrownTypes(),
                                 syms.methodClass));
        // Try searching again
        return findMethod(nt, scope, flags);
    }

    /** Similar to Types.isSameType but avoids completion */
    private boolean isSameBinaryType(MethodType mt1, MethodType mt2) {
        List<Type> types1 = types.erasure(mt1.getParameterTypes())
            .prepend(types.erasure(mt1.getReturnType()));
        List<Type> types2 = mt2.getParameterTypes().prepend(mt2.getReturnType());
        while (!types1.isEmpty() && !types2.isEmpty()) {
            if (types1.head.tsym != types2.head.tsym)
                return false;
            types1 = types1.tail;
            types2 = types2.tail;
        }
        return types1.isEmpty() && types2.isEmpty();
    }

    /**
     * Character.isDigit answers <tt>true</tt> to some non-ascii
     * digits.  This one does not.  <b>copied from java.lang.Class</b>
     */
    private static boolean isAsciiDigit(char c) {
        return '0' <= c && c <= '9';
    }

    /** Read member attributes.
     */
    void readMemberAttrs(Symbol sym) {
        readAttrs(sym, AttributeKind.MEMBER);
    }

    void readAttrs(Symbol sym, AttributeKind kind) {
        char ac = nextChar();
        for (int i = 0; i < ac; i++) {
            Name attrName = readName(nextChar());
            int attrLen = nextInt();
            AttributeReader r = attributeReaders.get(attrName);
            if (r != null && r.accepts(kind))
                r.read(sym, attrLen);
            else  {
                unrecognized(attrName);
                bp = bp + attrLen;
            }
        }
    }

    private boolean readingClassAttr = false;
    private List<Type> missingTypeVariables = List.nil();
    private List<Type> foundTypeVariables = List.nil();

    /** Read class attributes.
     */
    void readClassAttrs(ClassSymbol c) {
        readAttrs(c, AttributeKind.CLASS);
    }

    /** Read code block.
     */
    Code readCode(Symbol owner) {
        nextChar(); // max_stack
        nextChar(); // max_locals
        final int  code_length = nextInt();
        bp += code_length;
        final char exception_table_length = nextChar();
        bp += exception_table_length * 8;
        readMemberAttrs(owner);
        return null;
    }

/************************************************************************
 * Reading Java-language annotations
 ***********************************************************************/

    /** Attach annotations.
     */
    void attachAnnotations(final Symbol sym) {
        int numAttributes = nextChar();
        if (numAttributes != 0) {
            ListBuffer<CompoundAnnotationProxy> proxies =
                new ListBuffer<CompoundAnnotationProxy>();
            for (int i = 0; i<numAttributes; i++) {
                CompoundAnnotationProxy proxy = readCompoundAnnotation();
                if (proxy.type.tsym == syms.proprietaryType.tsym)
                    sym.flags_field |= PROPRIETARY;
                else if (proxy.type.tsym == syms.profileType.tsym) {
                    if (profile != Profile.DEFAULT) {
                        for (Pair<Name,Attribute> v: proxy.values) {
                            if (v.fst == names.value && v.snd instanceof Attribute.Constant) {
                                Attribute.Constant c = (Attribute.Constant) v.snd;
                                if (c.type == syms.intType && ((Integer) c.value) > profile.value) {
                                    sym.flags_field |= NOT_IN_PROFILE;
                                }
                            }
                        }
                    }
                } else
                    proxies.append(proxy);
            }
            annotate.normal(new AnnotationCompleter(sym, proxies.toList()));
        }
    }

    /** Attach parameter annotations.
     */
    void attachParameterAnnotations(final Symbol method) {
        final MethodSymbol meth = (MethodSymbol)method;
        int numParameters = buf[bp++] & 0xFF;
        List<VarSymbol> parameters = meth.params();
        int pnum = 0;
        while (parameters.tail != null) {
            attachAnnotations(parameters.head);
            parameters = parameters.tail;
            pnum++;
        }
        if (pnum != numParameters) {
            throw badClassFile("bad.runtime.invisible.param.annotations", meth);
        }
    }

    void attachTypeAnnotations(final Symbol sym) {
        int numAttributes = nextChar();
        if (numAttributes != 0) {
            ListBuffer<TypeAnnotationProxy> proxies = new ListBuffer<>();
            for (int i = 0; i < numAttributes; i++)
                proxies.append(readTypeAnnotation());
            annotate.normal(new TypeAnnotationCompleter(sym, proxies.toList()));
        }
    }

    /** Attach the default value for an annotation element.
     */
    void attachAnnotationDefault(final Symbol sym) {
        final MethodSymbol meth = (MethodSymbol)sym; // only on methods
        final Attribute value = readAttributeValue();

        // The default value is set later during annotation. It might
        // be the case that the Symbol sym is annotated _after_ the
        // repeating instances that depend on this default value,
        // because of this we set an interim value that tells us this
        // element (most likely) has a default.
        //
        // Set interim value for now, reset just before we do this
        // properly at annotate time.
        meth.defaultValue = value;
        annotate.normal(new AnnotationDefaultCompleter(meth, value));
    }

    Type readTypeOrClassSymbol(int i) {
        // support preliminary jsr175-format class files
        if (buf[poolIdx[i]] == CONSTANT_Class)
            return readClassSymbol(i).type;
        return readType(i);
    }
    Type readEnumType(int i) {
        // support preliminary jsr175-format class files
        int index = poolIdx[i];
        int length = getChar(index + 1);
        if (buf[index + length + 2] != ';')
            return enterClass(readName(i)).type;
        return readType(i);
    }

    CompoundAnnotationProxy readCompoundAnnotation() {
        Type t = readTypeOrClassSymbol(nextChar());
        int numFields = nextChar();
        ListBuffer<Pair<Name,Attribute>> pairs =
            new ListBuffer<Pair<Name,Attribute>>();
        for (int i=0; i<numFields; i++) {
            Name name = readName(nextChar());
            Attribute value = readAttributeValue();
            pairs.append(new Pair<Name,Attribute>(name, value));
        }
        return new CompoundAnnotationProxy(t, pairs.toList());
    }

    TypeAnnotationProxy readTypeAnnotation() {
        TypeAnnotationPosition position = readPosition();
        CompoundAnnotationProxy proxy = readCompoundAnnotation();

        return new TypeAnnotationProxy(proxy, position);
    }

    TypeAnnotationPosition readPosition() {
        int tag = nextByte(); // TargetType tag is a byte

        if (!TargetType.isValidTargetTypeValue(tag))
            throw this.badClassFile("bad.type.annotation.value", String.format("0x%02X", tag));

        TypeAnnotationPosition position = new TypeAnnotationPosition();
        TargetType type = TargetType.fromTargetTypeValue(tag);

        position.type = type;

        switch (type) {
        // instanceof
        case INSTANCEOF:
        // new expression
        case NEW:
        // constructor/method reference receiver
        case CONSTRUCTOR_REFERENCE:
        case METHOD_REFERENCE:
            position.offset = nextChar();
            break;
        // local variable
        case LOCAL_VARIABLE:
        // resource variable
        case RESOURCE_VARIABLE:
            int table_length = nextChar();
            position.lvarOffset = new int[table_length];
            position.lvarLength = new int[table_length];
            position.lvarIndex = new int[table_length];

            for (int i = 0; i < table_length; ++i) {
                position.lvarOffset[i] = nextChar();
                position.lvarLength[i] = nextChar();
                position.lvarIndex[i] = nextChar();
            }
            break;
        // exception parameter
        case EXCEPTION_PARAMETER:
            position.exception_index = nextChar();
            break;
        // method receiver
        case METHOD_RECEIVER:
            // Do nothing
            break;
        // type parameter
        case CLASS_TYPE_PARAMETER:
        case METHOD_TYPE_PARAMETER:
            position.parameter_index = nextByte();
            break;
        // type parameter bound
        case CLASS_TYPE_PARAMETER_BOUND:
        case METHOD_TYPE_PARAMETER_BOUND:
            position.parameter_index = nextByte();
            position.bound_index = nextByte();
            break;
        // class extends or implements clause
        case CLASS_EXTENDS:
            position.type_index = nextChar();
            break;
        // throws
        case THROWS:
            position.type_index = nextChar();
            break;
        // method parameter
        case METHOD_FORMAL_PARAMETER:
            position.parameter_index = nextByte();
            break;
        // type cast
        case CAST:
        // method/constructor/reference type argument
        case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
        case METHOD_INVOCATION_TYPE_ARGUMENT:
        case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
        case METHOD_REFERENCE_TYPE_ARGUMENT:
            position.offset = nextChar();
            position.type_index = nextByte();
            break;
        // We don't need to worry about these
        case METHOD_RETURN:
        case FIELD:
            break;
        case UNKNOWN:
            throw new AssertionError("jvm.ClassReader: UNKNOWN target type should never occur!");
        default:
            throw new AssertionError("jvm.ClassReader: Unknown target type for position: " + position);
        }

        { // See whether there is location info and read it
            int len = nextByte();
            ListBuffer<Integer> loc = new ListBuffer<>();
            for (int i = 0; i < len * TypeAnnotationPosition.TypePathEntry.bytesPerEntry; ++i)
                loc = loc.append(nextByte());
            position.location = TypeAnnotationPosition.getTypePathFromBinary(loc.toList());
        }

        return position;
    }

    Attribute readAttributeValue() {
        char c = (char) buf[bp++];
        switch (c) {
        case 'B':
            return new Attribute.Constant(syms.byteType, readPool(nextChar()));
        case 'C':
            return new Attribute.Constant(syms.charType, readPool(nextChar()));
        case 'D':
            return new Attribute.Constant(syms.doubleType, readPool(nextChar()));
        case 'F':
            return new Attribute.Constant(syms.floatType, readPool(nextChar()));
        case 'I':
            return new Attribute.Constant(syms.intType, readPool(nextChar()));
        case 'J':
            return new Attribute.Constant(syms.longType, readPool(nextChar()));
        case 'S':
            return new Attribute.Constant(syms.shortType, readPool(nextChar()));
        case 'Z':
            return new Attribute.Constant(syms.booleanType, readPool(nextChar()));
        case 's':
            return new Attribute.Constant(syms.stringType, readPool(nextChar()).toString());
        case 'e':
            return new EnumAttributeProxy(readEnumType(nextChar()), readName(nextChar()));
        case 'c':
            return new Attribute.Class(types, readTypeOrClassSymbol(nextChar()));
        case '[': {
            int n = nextChar();
            ListBuffer<Attribute> l = new ListBuffer<Attribute>();
            for (int i=0; i<n; i++)
                l.append(readAttributeValue());
            return new ArrayAttributeProxy(l.toList());
        }
        case '@':
            return readCompoundAnnotation();
        default:
            throw new AssertionError("unknown annotation tag '" + c + "'");
        }
    }

    interface ProxyVisitor extends Attribute.Visitor {
        void visitEnumAttributeProxy(EnumAttributeProxy proxy);
        void visitArrayAttributeProxy(ArrayAttributeProxy proxy);
        void visitCompoundAnnotationProxy(CompoundAnnotationProxy proxy);
    }

    static class EnumAttributeProxy extends Attribute {
        Type enumType;
        Name enumerator;
        public EnumAttributeProxy(Type enumType, Name enumerator) {
            super(null);
            this.enumType = enumType;
            this.enumerator = enumerator;
        }
        public void accept(Visitor v) { ((ProxyVisitor)v).visitEnumAttributeProxy(this); }
        @Override
        public String toString() {
            return "/*proxy enum*/" + enumType + "." + enumerator;
        }
    }

    static class ArrayAttributeProxy extends Attribute {
        List<Attribute> values;
        ArrayAttributeProxy(List<Attribute> values) {
            super(null);
            this.values = values;
        }
        public void accept(Visitor v) { ((ProxyVisitor)v).visitArrayAttributeProxy(this); }
        @Override
        public String toString() {
            return "{" + values + "}";
        }
    }

    /** A temporary proxy representing a compound attribute.
     */
    static class CompoundAnnotationProxy extends Attribute {
        final List<Pair<Name,Attribute>> values;
        public CompoundAnnotationProxy(Type type,
                                      List<Pair<Name,Attribute>> values) {
            super(type);
            this.values = values;
        }
        public void accept(Visitor v) { ((ProxyVisitor)v).visitCompoundAnnotationProxy(this); }
        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("@");
            buf.append(type.tsym.getQualifiedName());
            buf.append("/*proxy*/{");
            boolean first = true;
            for (List<Pair<Name,Attribute>> v = values;
                 v.nonEmpty(); v = v.tail) {
                Pair<Name,Attribute> value = v.head;
                if (!first) buf.append(",");
                first = false;
                buf.append(value.fst);
                buf.append("=");
                buf.append(value.snd);
            }
            buf.append("}");
            return buf.toString();
        }
    }

    /** A temporary proxy representing a type annotation.
     */
    static class TypeAnnotationProxy {
        final CompoundAnnotationProxy compound;
        final TypeAnnotationPosition position;
        public TypeAnnotationProxy(CompoundAnnotationProxy compound,
                TypeAnnotationPosition position) {
            this.compound = compound;
            this.position = position;
        }
    }

    class AnnotationDeproxy implements ProxyVisitor {
        private ClassSymbol requestingOwner = currentOwner.kind == MTH
            ? currentOwner.enclClass() : (ClassSymbol)currentOwner;

        List<Attribute.Compound> deproxyCompoundList(List<CompoundAnnotationProxy> pl) {
            // also must fill in types!!!!
            ListBuffer<Attribute.Compound> buf =
                new ListBuffer<Attribute.Compound>();
            for (List<CompoundAnnotationProxy> l = pl; l.nonEmpty(); l=l.tail) {
                buf.append(deproxyCompound(l.head));
            }
            return buf.toList();
        }

        Attribute.Compound deproxyCompound(CompoundAnnotationProxy a) {
            ListBuffer<Pair<Symbol.MethodSymbol,Attribute>> buf =
                new ListBuffer<Pair<Symbol.MethodSymbol,Attribute>>();
            for (List<Pair<Name,Attribute>> l = a.values;
                 l.nonEmpty();
                 l = l.tail) {
                MethodSymbol meth = findAccessMethod(a.type, l.head.fst);
                buf.append(new Pair<Symbol.MethodSymbol,Attribute>
                           (meth, deproxy(meth.type.getReturnType(), l.head.snd)));
            }
            return new Attribute.Compound(a.type, buf.toList());
        }

        MethodSymbol findAccessMethod(Type container, Name name) {
            CompletionFailure failure = null;
            try {
                for (Scope.Entry e = container.tsym.members().lookup(name);
                     e.scope != null;
                     e = e.next()) {
                    Symbol sym = e.sym;
                    if (sym.kind == MTH && sym.type.getParameterTypes().length() == 0)
                        return (MethodSymbol) sym;
                }
            } catch (CompletionFailure ex) {
                failure = ex;
            }
            // The method wasn't found: emit a warning and recover
            JavaFileObject prevSource = log.useSource(requestingOwner.classfile);
            try {
                if (lintClassfile) {
                    if (failure == null) {
                        log.warning("annotation.method.not.found",
                                    container,
                                    name);
                    } else {
                        log.warning("annotation.method.not.found.reason",
                                    container,
                                    name,
                                    failure.getDetailValue()); //diagnostic, if present
                    }
                }
            } finally {
                log.useSource(prevSource);
            }
            // Construct a new method type and symbol.  Use bottom
            // type (typeof null) as return type because this type is
            // a subtype of all reference types and can be converted
            // to primitive types by unboxing.
            MethodType mt = new MethodType(List.<Type>nil(),
                                           syms.botType,
                                           List.<Type>nil(),
                                           syms.methodClass);
            return new MethodSymbol(PUBLIC | ABSTRACT, name, mt, container.tsym);
        }

        Attribute result;
        Type type;
        Attribute deproxy(Type t, Attribute a) {
            Type oldType = type;
            try {
                type = t;
                a.accept(this);
                return result;
            } finally {
                type = oldType;
            }
        }

        // implement Attribute.Visitor below

        public void visitConstant(Attribute.Constant value) {
            // assert value.type == type;
            result = value;
        }

        public void visitClass(Attribute.Class clazz) {
            result = clazz;
        }

        public void visitEnum(Attribute.Enum e) {
            throw new AssertionError(); // shouldn't happen
        }

        public void visitCompound(Attribute.Compound compound) {
            throw new AssertionError(); // shouldn't happen
        }

        public void visitArray(Attribute.Array array) {
            throw new AssertionError(); // shouldn't happen
        }

        public void visitError(Attribute.Error e) {
            throw new AssertionError(); // shouldn't happen
        }

        public void visitEnumAttributeProxy(EnumAttributeProxy proxy) {
            // type.tsym.flatName() should == proxy.enumFlatName
            TypeSymbol enumTypeSym = proxy.enumType.tsym;
            VarSymbol enumerator = null;
            CompletionFailure failure = null;
            try {
                for (Scope.Entry e = enumTypeSym.members().lookup(proxy.enumerator);
                     e.scope != null;
                     e = e.next()) {
                    if (e.sym.kind == VAR) {
                        enumerator = (VarSymbol)e.sym;
                        break;
                    }
                }
            }
            catch (CompletionFailure ex) {
                failure = ex;
            }
            if (enumerator == null) {
                if (failure != null) {
                    log.warning("unknown.enum.constant.reason",
                              currentClassFile, enumTypeSym, proxy.enumerator,
                              failure.getDiagnostic());
                } else {
                    log.warning("unknown.enum.constant",
                              currentClassFile, enumTypeSym, proxy.enumerator);
                }
                result = new Attribute.Enum(enumTypeSym.type,
                        new VarSymbol(0, proxy.enumerator, syms.botType, enumTypeSym));
            } else {
                result = new Attribute.Enum(enumTypeSym.type, enumerator);
            }
        }

        public void visitArrayAttributeProxy(ArrayAttributeProxy proxy) {
            int length = proxy.values.length();
            Attribute[] ats = new Attribute[length];
            Type elemtype = types.elemtype(type);
            int i = 0;
            for (List<Attribute> p = proxy.values; p.nonEmpty(); p = p.tail) {
                ats[i++] = deproxy(elemtype, p.head);
            }
            result = new Attribute.Array(type, ats);
        }

        public void visitCompoundAnnotationProxy(CompoundAnnotationProxy proxy) {
            result = deproxyCompound(proxy);
        }
    }

    class AnnotationDefaultCompleter extends AnnotationDeproxy implements Annotate.Worker {
        final MethodSymbol sym;
        final Attribute value;
        final JavaFileObject classFile = currentClassFile;
        @Override
        public String toString() {
            return " ClassReader store default for " + sym.owner + "." + sym + " is " + value;
        }
        AnnotationDefaultCompleter(MethodSymbol sym, Attribute value) {
            this.sym = sym;
            this.value = value;
        }
        // implement Annotate.Worker.run()
        public void run() {
            JavaFileObject previousClassFile = currentClassFile;
            try {
                // Reset the interim value set earlier in
                // attachAnnotationDefault().
                sym.defaultValue = null;
                currentClassFile = classFile;
                sym.defaultValue = deproxy(sym.type.getReturnType(), value);
            } finally {
                currentClassFile = previousClassFile;
            }
        }
    }

    class AnnotationCompleter extends AnnotationDeproxy implements Annotate.Worker {
        final Symbol sym;
        final List<CompoundAnnotationProxy> l;
        final JavaFileObject classFile;
        @Override
        public String toString() {
            return " ClassReader annotate " + sym.owner + "." + sym + " with " + l;
        }
        AnnotationCompleter(Symbol sym, List<CompoundAnnotationProxy> l) {
            this.sym = sym;
            this.l = l;
            this.classFile = currentClassFile;
        }
        // implement Annotate.Worker.run()
        public void run() {
            JavaFileObject previousClassFile = currentClassFile;
            try {
                currentClassFile = classFile;
                List<Attribute.Compound> newList = deproxyCompoundList(l);
                if (sym.annotationsPendingCompletion()) {
                    sym.setDeclarationAttributes(newList);
                } else {
                    sym.appendAttributes(newList);
                }
            } finally {
                currentClassFile = previousClassFile;
            }
        }
    }

    class TypeAnnotationCompleter extends AnnotationCompleter {

        List<TypeAnnotationProxy> proxies;

        TypeAnnotationCompleter(Symbol sym,
                List<TypeAnnotationProxy> proxies) {
            super(sym, List.<CompoundAnnotationProxy>nil());
            this.proxies = proxies;
        }

        List<Attribute.TypeCompound> deproxyTypeCompoundList(List<TypeAnnotationProxy> proxies) {
            ListBuffer<Attribute.TypeCompound> buf = new ListBuffer<>();
            for (TypeAnnotationProxy proxy: proxies) {
                Attribute.Compound compound = deproxyCompound(proxy.compound);
                Attribute.TypeCompound typeCompound = new Attribute.TypeCompound(compound, proxy.position);
                buf.add(typeCompound);
            }
            return buf.toList();
        }

        @Override
        public void run() {
            JavaFileObject previousClassFile = currentClassFile;
            try {
                currentClassFile = classFile;
                List<Attribute.TypeCompound> newList = deproxyTypeCompoundList(proxies);
                sym.setTypeAttributes(newList.prependList(sym.getRawTypeAttributes()));
            } finally {
                currentClassFile = previousClassFile;
            }
        }
    }


/************************************************************************
 * Reading Symbols
 ***********************************************************************/

    /** Read a field.
     */
    VarSymbol readField() {
        long flags = adjustFieldFlags(nextChar());
        Name name = readName(nextChar());
        Type type = readType(nextChar());
        VarSymbol v = new VarSymbol(flags, name, type, currentOwner);
        readMemberAttrs(v);
        return v;
    }

    /** Read a method.
     */
    MethodSymbol readMethod() {
        long flags = adjustMethodFlags(nextChar());
        Name name = readName(nextChar());
        Type type = readType(nextChar());
        if (currentOwner.isInterface() &&
                (flags & ABSTRACT) == 0 && !name.equals(names.clinit)) {
            if (majorVersion > Target.JDK1_8.majorVersion ||
                    (majorVersion == Target.JDK1_8.majorVersion && minorVersion >= Target.JDK1_8.minorVersion)) {
                if ((flags & STATIC) == 0) {
                    currentOwner.flags_field |= DEFAULT;
                    flags |= DEFAULT | ABSTRACT;
                }
            } else {
                //protect against ill-formed classfiles
                throw badClassFile((flags & STATIC) == 0 ? "invalid.default.interface" : "invalid.static.interface",
                                   Integer.toString(majorVersion),
                                   Integer.toString(minorVersion));
            }
        }
        if (name == names.init && currentOwner.hasOuterInstance()) {
            // Sometimes anonymous classes don't have an outer
            // instance, however, there is no reliable way to tell so
            // we never strip this$n
            if (!currentOwner.name.isEmpty())
                type = new MethodType(adjustMethodParams(flags, type.getParameterTypes()),
                                      type.getReturnType(),
                                      type.getThrownTypes(),
                                      syms.methodClass);
        }
        MethodSymbol m = new MethodSymbol(flags, name, type, currentOwner);
        if (types.isSignaturePolymorphic(m)) {
            m.flags_field |= SIGNATURE_POLYMORPHIC;
        }
        if (saveParameterNames)
            initParameterNames(m);
        Symbol prevOwner = currentOwner;
        currentOwner = m;
        try {
            readMemberAttrs(m);
        } finally {
            currentOwner = prevOwner;
        }
        if (saveParameterNames)
            setParameterNames(m, type);
        return m;
    }

    private List<Type> adjustMethodParams(long flags, List<Type> args) {
        boolean isVarargs = (flags & VARARGS) != 0;
        if (isVarargs) {
            Type varargsElem = args.last();
            ListBuffer<Type> adjustedArgs = new ListBuffer<>();
            for (Type t : args) {
                adjustedArgs.append(t != varargsElem ?
                    t :
                    ((ArrayType)t).makeVarargs());
            }
            args = adjustedArgs.toList();
        }
        return args.tail;
    }

    /**
     * Init the parameter names array.
     * Parameter names are currently inferred from the names in the
     * LocalVariableTable attributes of a Code attribute.
     * (Note: this means parameter names are currently not available for
     * methods without a Code attribute.)
     * This method initializes an array in which to store the name indexes
     * of parameter names found in LocalVariableTable attributes. It is
     * slightly supersized to allow for additional slots with a start_pc of 0.
     */
    void initParameterNames(MethodSymbol sym) {
        // make allowance for synthetic parameters.
        final int excessSlots = 4;
        int expectedParameterSlots =
                Code.width(sym.type.getParameterTypes()) + excessSlots;
        if (parameterNameIndices == null
                || parameterNameIndices.length < expectedParameterSlots) {
            parameterNameIndices = new int[expectedParameterSlots];
        } else
            Arrays.fill(parameterNameIndices, 0);
        haveParameterNameIndices = false;
        sawMethodParameters = false;
    }

    /**
     * Set the parameter names for a symbol from the name index in the
     * parameterNameIndicies array. The type of the symbol may have changed
     * while reading the method attributes (see the Signature attribute).
     * This may be because of generic information or because anonymous
     * synthetic parameters were added.   The original type (as read from
     * the method descriptor) is used to help guess the existence of
     * anonymous synthetic parameters.
     * On completion, sym.savedParameter names will either be null (if
     * no parameter names were found in the class file) or will be set to a
     * list of names, one per entry in sym.type.getParameterTypes, with
     * any missing names represented by the empty name.
     */
    void setParameterNames(MethodSymbol sym, Type jvmType) {
        // if no names were found in the class file, there's nothing more to do
        if (!haveParameterNameIndices)
            return;
        // If we get parameter names from MethodParameters, then we
        // don't need to skip.
        int firstParam = 0;
        if (!sawMethodParameters) {
            firstParam = ((sym.flags() & STATIC) == 0) ? 1 : 0;
            // the code in readMethod may have skipped the first
            // parameter when setting up the MethodType. If so, we
            // make a corresponding allowance here for the position of
            // the first parameter.  Note that this assumes the
            // skipped parameter has a width of 1 -- i.e. it is not
        // a double width type (long or double.)
        if (sym.name == names.init && currentOwner.hasOuterInstance()) {
            // Sometimes anonymous classes don't have an outer
            // instance, however, there is no reliable way to tell so
            // we never strip this$n
            if (!currentOwner.name.isEmpty())
                firstParam += 1;
        }

        if (sym.type != jvmType) {
                // reading the method attributes has caused the
                // symbol's type to be changed. (i.e. the Signature
                // attribute.)  This may happen if there are hidden
                // (synthetic) parameters in the descriptor, but not
                // in the Signature.  The position of these hidden
                // parameters is unspecified; for now, assume they are
                // at the beginning, and so skip over them. The
                // primary case for this is two hidden parameters
                // passed into Enum constructors.
            int skip = Code.width(jvmType.getParameterTypes())
                    - Code.width(sym.type.getParameterTypes());
            firstParam += skip;
        }
        }
        List<Name> paramNames = List.nil();
        int index = firstParam;
        for (Type t: sym.type.getParameterTypes()) {
            int nameIdx = (index < parameterNameIndices.length
                    ? parameterNameIndices[index] : 0);
            Name name = nameIdx == 0 ? names.empty : readName(nameIdx);
            paramNames = paramNames.prepend(name);
            index += Code.width(t);
        }
        sym.savedParameterNames = paramNames.reverse();
    }

    /**
     * skip n bytes
     */
    void skipBytes(int n) {
        bp = bp + n;
    }

    /** Skip a field or method
     */
    void skipMember() {
        bp = bp + 6;
        char ac = nextChar();
        for (int i = 0; i < ac; i++) {
            bp = bp + 2;
            int attrLen = nextInt();
            bp = bp + attrLen;
        }
    }

    /** Enter type variables of this classtype and all enclosing ones in
     *  `typevars'.
     */
    protected void enterTypevars(Type t) {
        if (t.getEnclosingType() != null && t.getEnclosingType().hasTag(CLASS))
            enterTypevars(t.getEnclosingType());
        for (List<Type> xs = t.getTypeArguments(); xs.nonEmpty(); xs = xs.tail)
            typevars.enter(xs.head.tsym);
    }

    protected void enterTypevars(Symbol sym) {
        if (sym.owner.kind == MTH) {
            enterTypevars(sym.owner);
            enterTypevars(sym.owner.owner);
        }
        enterTypevars(sym.type);
    }

    /** Read contents of a given class symbol `c'. Both external and internal
     *  versions of an inner class are read.
     */
    void readClass(ClassSymbol c) {
        ClassType ct = (ClassType)c.type;

        // allocate scope for members
        c.members_field = new Scope(c);

        // prepare type variable table
        typevars = typevars.dup(currentOwner);
        if (ct.getEnclosingType().hasTag(CLASS))
            enterTypevars(ct.getEnclosingType());

        // read flags, or skip if this is an inner class
        long flags = adjustClassFlags(nextChar());
        if (c.owner.kind == PCK) c.flags_field = flags;

        // read own class name and check that it matches
        ClassSymbol self = readClassSymbol(nextChar());
        if (c != self)
            throw badClassFile("class.file.wrong.class",
                               self.flatname);

        // class attributes must be read before class
        // skip ahead to read class attributes
        int startbp = bp;
        nextChar();
        char interfaceCount = nextChar();
        bp += interfaceCount * 2;
        char fieldCount = nextChar();
        for (int i = 0; i < fieldCount; i++) skipMember();
        char methodCount = nextChar();
        for (int i = 0; i < methodCount; i++) skipMember();
        readClassAttrs(c);

        if (readAllOfClassFile) {
            for (int i = 1; i < poolObj.length; i++) readPool(i);
            c.pool = new Pool(poolObj.length, poolObj, types);
        }

        // reset and read rest of classinfo
        bp = startbp;
        int n = nextChar();
        if (ct.supertype_field == null)
            ct.supertype_field = (n == 0)
                ? Type.noType
                : readClassSymbol(n).erasure(types);
        n = nextChar();
        List<Type> is = List.nil();
        for (int i = 0; i < n; i++) {
            Type _inter = readClassSymbol(nextChar()).erasure(types);
            is = is.prepend(_inter);
        }
        if (ct.interfaces_field == null)
            ct.interfaces_field = is.reverse();

        Assert.check(fieldCount == nextChar());
        for (int i = 0; i < fieldCount; i++) enterMember(c, readField());
        Assert.check(methodCount == nextChar());
        for (int i = 0; i < methodCount; i++) enterMember(c, readMethod());

        typevars = typevars.leave();
    }

    /** Read inner class info. For each inner/outer pair allocate a
     *  member class.
     */
    void readInnerClasses(ClassSymbol c) {
        int n = nextChar();
        for (int i = 0; i < n; i++) {
            nextChar(); // skip inner class symbol
            ClassSymbol outer = readClassSymbol(nextChar());
            Name name = readName(nextChar());
            if (name == null) name = names.empty;
            long flags = adjustClassFlags(nextChar());
            if (outer != null) { // we have a member class
                if (name == names.empty)
                    name = names.one;
                ClassSymbol member = enterClass(name, outer);
                if ((flags & STATIC) == 0) {
                    ((ClassType)member.type).setEnclosingType(outer.type);
                    if (member.erasure_field != null)
                        ((ClassType)member.erasure_field).setEnclosingType(types.erasure(outer.type));
                }
                if (c == outer) {
                    member.flags_field = flags;
                    enterMember(c, member);
                }
            }
        }
    }

    /** Read a class file.
     */
    private void readClassFile(ClassSymbol c) throws IOException {
        int magic = nextInt();
        if (magic != JAVA_MAGIC)
            throw badClassFile("illegal.start.of.class.file");

        minorVersion = nextChar();
        majorVersion = nextChar();
        int maxMajor = Target.MAX().majorVersion;
        int maxMinor = Target.MAX().minorVersion;
        if (majorVersion > maxMajor ||
            majorVersion * 1000 + minorVersion <
            Target.MIN().majorVersion * 1000 + Target.MIN().minorVersion)
        {
            if (majorVersion == (maxMajor + 1))
                log.warning("big.major.version",
                            currentClassFile,
                            majorVersion,
                            maxMajor);
            else
                throw badClassFile("wrong.version",
                                   Integer.toString(majorVersion),
                                   Integer.toString(minorVersion),
                                   Integer.toString(maxMajor),
                                   Integer.toString(maxMinor));
        }
        else if (checkClassFile &&
                 majorVersion == maxMajor &&
                 minorVersion > maxMinor)
        {
            printCCF("found.later.version",
                     Integer.toString(minorVersion));
        }
        indexPool();
        if (signatureBuffer.length < bp) {
            int ns = Integer.highestOneBit(bp) << 1;
            signatureBuffer = new byte[ns];
        }
        readClass(c);
    }

/************************************************************************
 * Adjusting flags
 ***********************************************************************/

    long adjustFieldFlags(long flags) {
        return flags;
    }
    long adjustMethodFlags(long flags) {
        if ((flags & ACC_BRIDGE) != 0) {
            flags &= ~ACC_BRIDGE;
            flags |= BRIDGE;
            if (!allowGenerics)
                flags &= ~SYNTHETIC;
        }
        if ((flags & ACC_VARARGS) != 0) {
            flags &= ~ACC_VARARGS;
            flags |= VARARGS;
        }
        return flags;
    }
    long adjustClassFlags(long flags) {
        return flags & ~ACC_SUPER; // SUPER and SYNCHRONIZED bits overloaded
    }

/************************************************************************
 * Loading Classes
 ***********************************************************************/

    /** Define a new class given its name and owner.
     */
    public ClassSymbol defineClass(Name name, Symbol owner) {
        ClassSymbol c = new ClassSymbol(0, name, owner);
        if (owner.kind == PCK)
            Assert.checkNull(classes.get(c.flatname), c);
        c.completer = thisCompleter;
        return c;
    }

    /** Create a new toplevel or member class symbol with given name
     *  and owner and enter in `classes' unless already there.
     */
    public ClassSymbol enterClass(Name name, TypeSymbol owner) {
        Name flatname = TypeSymbol.formFlatName(name, owner);
        ClassSymbol c = classes.get(flatname);
        if (c == null) {
            c = defineClass(name, owner);
            classes.put(flatname, c);
        } else if ((c.name != name || c.owner != owner) && owner.kind == TYP && c.owner.kind == PCK) {
            // reassign fields of classes that might have been loaded with
            // their flat names.
            c.owner.members().remove(c);
            c.name = name;
            c.owner = owner;
            c.fullname = ClassSymbol.formFullName(name, owner);
        }
        return c;
    }

    /**
     * Creates a new toplevel class symbol with given flat name and
     * given class (or source) file.
     *
     * @param flatName a fully qualified binary class name
     * @param classFile the class file or compilation unit defining
     * the class (may be {@code null})
     * @return a newly created class symbol
     * @throws AssertionError if the class symbol already exists
     */
    public ClassSymbol enterClass(Name flatName, JavaFileObject classFile) {
        ClassSymbol cs = classes.get(flatName);
        if (cs != null) {
            String msg = Log.format("%s: completer = %s; class file = %s; source file = %s",
                                    cs.fullname,
                                    cs.completer,
                                    cs.classfile,
                                    cs.sourcefile);
            throw new AssertionError(msg);
        }
        Name packageName = Convert.packagePart(flatName);
        PackageSymbol owner = packageName.isEmpty()
                                ? syms.unnamedPackage
                                : enterPackage(packageName);
        cs = defineClass(Convert.shortName(flatName), owner);
        cs.classfile = classFile;
        classes.put(flatName, cs);
        return cs;
    }

    /** Create a new member or toplevel class symbol with given flat name
     *  and enter in `classes' unless already there.
     */
    public ClassSymbol enterClass(Name flatname) {
        ClassSymbol c = classes.get(flatname);
        if (c == null)
            return enterClass(flatname, (JavaFileObject)null);
        else
            return c;
    }

    /** Completion for classes to be loaded. Before a class is loaded
     *  we make sure its enclosing class (if any) is loaded.
     */
    private void complete(Symbol sym) throws CompletionFailure {
        if (sym.kind == TYP) {
            ClassSymbol c = (ClassSymbol)sym;
            c.members_field = new Scope.ErrorScope(c); // make sure it's always defined
            annotate.enterStart();
            try {
                completeOwners(c.owner);
                completeEnclosing(c);
            } finally {
                // The flush needs to happen only after annotations
                // are filled in.
                annotate.enterDoneWithoutFlush();
            }
            fillIn(c);
        } else if (sym.kind == PCK) {
            PackageSymbol p = (PackageSymbol)sym;
            try {
                fillIn(p);
            } catch (IOException ex) {
                throw new CompletionFailure(sym, ex.getLocalizedMessage()).initCause(ex);
            }
        }
        if (!filling)
            annotate.flush(); // finish attaching annotations
    }

    /** complete up through the enclosing package. */
    private void completeOwners(Symbol o) {
        if (o.kind != PCK) completeOwners(o.owner);
        o.complete();
    }

    /**
     * Tries to complete lexically enclosing classes if c looks like a
     * nested class.  This is similar to completeOwners but handles
     * the situation when a nested class is accessed directly as it is
     * possible with the Tree API or javax.lang.model.*.
     */
    private void completeEnclosing(ClassSymbol c) {
        if (c.owner.kind == PCK) {
            Symbol owner = c.owner;
            for (Name name : Convert.enclosingCandidates(Convert.shortName(c.name))) {
                Symbol encl = owner.members().lookup(name).sym;
                if (encl == null)
                    encl = classes.get(TypeSymbol.formFlatName(name, owner));
                if (encl != null)
                    encl.complete();
            }
        }
    }

    /** We can only read a single class file at a time; this
     *  flag keeps track of when we are currently reading a class
     *  file.
     */
    private boolean filling = false;

    /** Fill in definition of class `c' from corresponding class or
     *  source file.
     */
    private void fillIn(ClassSymbol c) {
        if (completionFailureName == c.fullname) {
            throw new CompletionFailure(c, "user-selected completion failure by class name");
        }
        currentOwner = c;
        warnedAttrs.clear();
        JavaFileObject classfile = c.classfile;
        if (classfile != null) {
            JavaFileObject previousClassFile = currentClassFile;
            try {
                if (filling) {
                    Assert.error("Filling " + classfile.toUri() + " during " + previousClassFile);
                }
                currentClassFile = classfile;
                if (verbose) {
                    log.printVerbose("loading", currentClassFile.toString());
                }
                if (classfile.getKind() == JavaFileObject.Kind.CLASS) {
                    filling = true;
                    try {
                        bp = 0;
                        buf = readInputStream(buf, classfile.openInputStream());
                        readClassFile(c);
                        if (!missingTypeVariables.isEmpty() && !foundTypeVariables.isEmpty()) {
                            List<Type> missing = missingTypeVariables;
                            List<Type> found = foundTypeVariables;
                            missingTypeVariables = List.nil();
                            foundTypeVariables = List.nil();
                            filling = false;
                            ClassType ct = (ClassType)currentOwner.type;
                            ct.supertype_field =
                                types.subst(ct.supertype_field, missing, found);
                            ct.interfaces_field =
                                types.subst(ct.interfaces_field, missing, found);
                        } else if (missingTypeVariables.isEmpty() !=
                                   foundTypeVariables.isEmpty()) {
                            Name name = missingTypeVariables.head.tsym.name;
                            throw badClassFile("undecl.type.var", name);
                        }
                    } finally {
                        missingTypeVariables = List.nil();
                        foundTypeVariables = List.nil();
                        filling = false;
                    }
                } else {
                    if (sourceCompleter != null) {
                        sourceCompleter.complete(c);
                    } else {
                        throw new IllegalStateException("Source completer required to read "
                                                        + classfile.toUri());
                    }
                }
                return;
            } catch (IOException ex) {
                throw badClassFile("unable.to.access.file", ex.getMessage());
            } finally {
                currentClassFile = previousClassFile;
            }
        } else {
            JCDiagnostic diag =
                diagFactory.fragment("class.file.not.found", c.flatname);
            throw
                newCompletionFailure(c, diag);
        }
    }
    // where
        private static byte[] readInputStream(byte[] buf, InputStream s) throws IOException {
            try {
                buf = ensureCapacity(buf, s.available());
                int r = s.read(buf);
                int bp = 0;
                while (r != -1) {
                    bp += r;
                    buf = ensureCapacity(buf, bp);
                    r = s.read(buf, bp, buf.length - bp);
                }
                return buf;
            } finally {
                try {
                    s.close();
                } catch (IOException e) {
                    /* Ignore any errors, as this stream may have already
                     * thrown a related exception which is the one that
                     * should be reported.
                     */
                }
            }
        }
        /*
         * ensureCapacity will increase the buffer as needed, taking note that
         * the new buffer will always be greater than the needed and never
         * exactly equal to the needed size or bp. If equal then the read (above)
         * will infinitely loop as buf.length - bp == 0.
         */
        private static byte[] ensureCapacity(byte[] buf, int needed) {
            if (buf.length <= needed) {
                byte[] old = buf;
                buf = new byte[Integer.highestOneBit(needed) << 1];
                System.arraycopy(old, 0, buf, 0, old.length);
            }
            return buf;
        }
        /** Static factory for CompletionFailure objects.
         *  In practice, only one can be used at a time, so we share one
         *  to reduce the expense of allocating new exception objects.
         */
        private CompletionFailure newCompletionFailure(TypeSymbol c,
                                                       JCDiagnostic diag) {
            if (!cacheCompletionFailure) {
                // log.warning("proc.messager",
                //             Log.getLocalizedString("class.file.not.found", c.flatname));
                // c.debug.printStackTrace();
                return new CompletionFailure(c, diag);
            } else {
                CompletionFailure result = cachedCompletionFailure;
                result.sym = c;
                result.diag = diag;
                return result;
            }
        }
        private CompletionFailure cachedCompletionFailure =
            new CompletionFailure(null, (JCDiagnostic) null);
        {
            cachedCompletionFailure.setStackTrace(new StackTraceElement[0]);
        }

    /** Load a toplevel class with given fully qualified name
     *  The class is entered into `classes' only if load was successful.
     */
    public ClassSymbol loadClass(Name flatname) throws CompletionFailure {
        boolean absent = classes.get(flatname) == null;
        ClassSymbol c = enterClass(flatname);
        if (c.members_field == null && c.completer != null) {
            try {
                c.complete();
            } catch (CompletionFailure ex) {
                if (absent) classes.remove(flatname);
                throw ex;
            }
        }
        return c;
    }

/************************************************************************
 * Loading Packages
 ***********************************************************************/

    /** Check to see if a package exists, given its fully qualified name.
     */
    public boolean packageExists(Name fullname) {
        return enterPackage(fullname).exists();
    }

    /** Make a package, given its fully qualified name.
     */
    public PackageSymbol enterPackage(Name fullname) {
        PackageSymbol p = packages.get(fullname);
        if (p == null) {
            Assert.check(!fullname.isEmpty(), "rootPackage missing!");
            p = new PackageSymbol(
                Convert.shortName(fullname),
                enterPackage(Convert.packagePart(fullname)));
            p.completer = thisCompleter;
            packages.put(fullname, p);
        }
        return p;
    }

    /** Make a package, given its unqualified name and enclosing package.
     */
    public PackageSymbol enterPackage(Name name, PackageSymbol owner) {
        return enterPackage(TypeSymbol.formFullName(name, owner));
    }

    /** Include class corresponding to given class file in package,
     *  unless (1) we already have one the same kind (.class or .java), or
     *         (2) we have one of the other kind, and the given class file
     *             is older.
     */
    protected void includeClassFile(PackageSymbol p, JavaFileObject file) {
        if ((p.flags_field & EXISTS) == 0)
            for (Symbol q = p; q != null && q.kind == PCK; q = q.owner)
                q.flags_field |= EXISTS;
        JavaFileObject.Kind kind = file.getKind();
        int seen;
        if (kind == JavaFileObject.Kind.CLASS)
            seen = CLASS_SEEN;
        else
            seen = SOURCE_SEEN;
        String binaryName = fileManager.inferBinaryName(currentLoc, file);
        int lastDot = binaryName.lastIndexOf(".");
        Name classname = names.fromString(binaryName.substring(lastDot + 1));
        boolean isPkgInfo = classname == names.package_info;
        ClassSymbol c = isPkgInfo
            ? p.package_info
            : (ClassSymbol) p.members_field.lookup(classname).sym;
        if (c == null) {
            c = enterClass(classname, p);
            if (c.classfile == null) // only update the file if's it's newly created
                c.classfile = file;
            if (isPkgInfo) {
                p.package_info = c;
            } else {
                if (c.owner == p)  // it might be an inner class
                    p.members_field.enter(c);
            }
        } else if (c.classfile != null && (c.flags_field & seen) == 0) {
            // if c.classfile == null, we are currently compiling this class
            // and no further action is necessary.
            // if (c.flags_field & seen) != 0, we have already encountered
            // a file of the same kind; again no further action is necessary.
            if ((c.flags_field & (CLASS_SEEN | SOURCE_SEEN)) != 0)
                c.classfile = preferredFileObject(file, c.classfile);
        }
        c.flags_field |= seen;
    }

    /** Implement policy to choose to derive information from a source
     *  file or a class file when both are present.  May be overridden
     *  by subclasses.
     */
    protected JavaFileObject preferredFileObject(JavaFileObject a,
                                           JavaFileObject b) {

        if (preferSource)
            return (a.getKind() == JavaFileObject.Kind.SOURCE) ? a : b;
        else {
            long adate = a.getLastModified();
            long bdate = b.getLastModified();
            // 6449326: policy for bad lastModifiedTime in ClassReader
            //assert adate >= 0 && bdate >= 0;
            return (adate > bdate) ? a : b;
        }
    }

    /**
     * specifies types of files to be read when filling in a package symbol
     */
    protected EnumSet<JavaFileObject.Kind> getPackageFileKinds() {
        return EnumSet.of(JavaFileObject.Kind.CLASS, JavaFileObject.Kind.SOURCE);
    }

    /**
     * this is used to support javadoc
     */
    protected void extraFileActions(PackageSymbol pack, JavaFileObject fe) {
    }

    protected Location currentLoc; // FIXME

    private boolean verbosePath = true;

    /** Load directory of package into members scope.
     */
    private void fillIn(PackageSymbol p) throws IOException {
        if (p.members_field == null) p.members_field = new Scope(p);
        String packageName = p.fullname.toString();

        Set<JavaFileObject.Kind> kinds = getPackageFileKinds();

        fillIn(p, PLATFORM_CLASS_PATH,
               fileManager.list(PLATFORM_CLASS_PATH,
                                packageName,
                                EnumSet.of(JavaFileObject.Kind.CLASS),
                                false));

        Set<JavaFileObject.Kind> classKinds = EnumSet.copyOf(kinds);
        classKinds.remove(JavaFileObject.Kind.SOURCE);
        boolean wantClassFiles = !classKinds.isEmpty();

        Set<JavaFileObject.Kind> sourceKinds = EnumSet.copyOf(kinds);
        sourceKinds.remove(JavaFileObject.Kind.CLASS);
        boolean wantSourceFiles = !sourceKinds.isEmpty();

        boolean haveSourcePath = fileManager.hasLocation(SOURCE_PATH);

        if (verbose && verbosePath) {
            if (fileManager instanceof StandardJavaFileManager) {
                StandardJavaFileManager fm = (StandardJavaFileManager)fileManager;
                if (haveSourcePath && wantSourceFiles) {
                    List<File> path = List.nil();
                    for (File file : fm.getLocation(SOURCE_PATH)) {
                        path = path.prepend(file);
                    }
                    log.printVerbose("sourcepath", path.reverse().toString());
                } else if (wantSourceFiles) {
                    List<File> path = List.nil();
                    for (File file : fm.getLocation(CLASS_PATH)) {
                        path = path.prepend(file);
                    }
                    log.printVerbose("sourcepath", path.reverse().toString());
                }
                if (wantClassFiles) {
                    List<File> path = List.nil();
                    for (File file : fm.getLocation(PLATFORM_CLASS_PATH)) {
                        path = path.prepend(file);
                    }
                    for (File file : fm.getLocation(CLASS_PATH)) {
                        path = path.prepend(file);
                    }
                    log.printVerbose("classpath",  path.reverse().toString());
                }
            }
        }

        if (wantSourceFiles && !haveSourcePath) {
            fillIn(p, CLASS_PATH,
                   fileManager.list(CLASS_PATH,
                                    packageName,
                                    kinds,
                                    false));
        } else {
            if (wantClassFiles)
                fillIn(p, CLASS_PATH,
                       fileManager.list(CLASS_PATH,
                                        packageName,
                                        classKinds,
                                        false));
            if (wantSourceFiles)
                fillIn(p, SOURCE_PATH,
                       fileManager.list(SOURCE_PATH,
                                        packageName,
                                        sourceKinds,
                                        false));
        }
        verbosePath = false;
    }
    // where
        private void fillIn(PackageSymbol p,
                            Location location,
                            Iterable<JavaFileObject> files)
        {
            currentLoc = location;
            for (JavaFileObject fo : files) {
                switch (fo.getKind()) {
                case CLASS:
                case SOURCE: {
                    // TODO pass binaryName to includeClassFile
                    String binaryName = fileManager.inferBinaryName(currentLoc, fo);
                    String simpleName = binaryName.substring(binaryName.lastIndexOf(".") + 1);
                    if (SourceVersion.isIdentifier(simpleName) ||
                        simpleName.equals("package-info"))
                        includeClassFile(p, fo);
                    break;
                }
                default:
                    extraFileActions(p, fo);
                }
            }
        }

    /** Output for "-checkclassfile" option.
     *  @param key The key to look up the correct internationalized string.
     *  @param arg An argument for substitution into the output string.
     */
    private void printCCF(String key, Object arg) {
        log.printLines(key, arg);
    }


    public interface SourceCompleter {
        void complete(ClassSymbol sym)
            throws CompletionFailure;
    }

    /**
     * A subclass of JavaFileObject for the sourcefile attribute found in a classfile.
     * The attribute is only the last component of the original filename, so is unlikely
     * to be valid as is, so operations other than those to access the name throw
     * UnsupportedOperationException
     */
    private static class SourceFileObject extends BaseFileObject {

        /** The file's name.
         */
        private Name name;
        private Name flatname;

        public SourceFileObject(Name name, Name flatname) {
            super(null); // no file manager; never referenced for this file object
            this.name = name;
            this.flatname = flatname;
        }

        @Override
        public URI toUri() {
            try {
                return new URI(null, name.toString(), null);
            } catch (URISyntaxException e) {
                throw new CannotCreateUriError(name.toString(), e);
            }
        }

        @Override
        public String getName() {
            return name.toString();
        }

        @Override
        public String getShortName() {
            return getName();
        }

        @Override
        public JavaFileObject.Kind getKind() {
            return getKind(getName());
        }

        @Override
        public InputStream openInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public OutputStream openOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Writer openWriter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLastModified() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected String inferBinaryName(Iterable<? extends File> path) {
            return flatname.toString();
        }

        @Override
        public boolean isNameCompatible(String simpleName, JavaFileObject.Kind kind) {
            return true; // fail-safe mode
        }

        /**
         * Check if two file objects are equal.
         * SourceFileObjects are just placeholder objects for the value of a
         * SourceFile attribute, and do not directly represent specific files.
         * Two SourceFileObjects are equal if their names are equal.
         */
        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;

            if (!(other instanceof SourceFileObject))
                return false;

            SourceFileObject o = (SourceFileObject) other;
            return name.equals(o.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
