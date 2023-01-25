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

package jdk.nashorn.internal.ir;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;

/**
 * IR representation for a runtime call.
 */
@Immutable
public class RuntimeNode extends Expression {
    private static final long serialVersionUID = 1L;

    /**
     * Request enum used for meta-information about the runtime request
     */
    public enum Request {
        /** An addition with at least one object */
        ADD(TokenType.ADD, Type.OBJECT, 2, true),
        /** Request to enter debugger */
        DEBUGGER,
        /** New operator */
        NEW,
        /** Typeof operator */
        TYPEOF,
        /** Reference error type */
        REFERENCE_ERROR,
        /** Delete operator */
        DELETE(TokenType.DELETE, Type.BOOLEAN, 1),
        /** Delete operator for slow scopes */
        SLOW_DELETE(TokenType.DELETE, Type.BOOLEAN, 1, false),
        /** Delete operator that always fails -- see Lower */
        FAIL_DELETE(TokenType.DELETE, Type.BOOLEAN, 1, false),
        /** === operator with at least one object */
        EQ_STRICT(TokenType.EQ_STRICT, Type.BOOLEAN, 2, true),
        /** == operator with at least one object */
        EQ(TokenType.EQ, Type.BOOLEAN, 2, true),
        /** {@literal >=} operator with at least one object */
        GE(TokenType.GE, Type.BOOLEAN, 2, true),
        /** {@literal >} operator with at least one object */
        GT(TokenType.GT, Type.BOOLEAN, 2, true),
        /** in operator */
        IN(TokenType.IN, Type.BOOLEAN, 2),
        /** instanceof operator */
        INSTANCEOF(TokenType.INSTANCEOF, Type.BOOLEAN, 2),
        /** {@literal <=} operator with at least one object */
        LE(TokenType.LE, Type.BOOLEAN, 2, true),
        /** {@literal <} operator with at least one object */
        LT(TokenType.LT, Type.BOOLEAN, 2, true),
        /** !== operator with at least one object */
        NE_STRICT(TokenType.NE_STRICT, Type.BOOLEAN, 2, true),
        /** != operator with at least one object */
        NE(TokenType.NE, Type.BOOLEAN, 2, true),
        /** is undefined */
        IS_UNDEFINED(TokenType.EQ_STRICT, Type.BOOLEAN, 2),
        /** is not undefined */
        IS_NOT_UNDEFINED(TokenType.NE_STRICT, Type.BOOLEAN, 2);

        /** token type */
        private final TokenType tokenType;

        /** return type for request */
        private final Type returnType;

        /** arity of request */
        private final int arity;

        /** Can the specializer turn this into something that works with 1 or more primitives? */
        private final boolean canSpecialize;

        private Request() {
            this(TokenType.VOID, Type.OBJECT, 0);
        }

        private Request(final TokenType tokenType, final Type returnType, final int arity) {
            this(tokenType, returnType, arity, false);
        }

        private Request(final TokenType tokenType, final Type returnType, final int arity, final boolean canSpecialize) {
            this.tokenType     = tokenType;
            this.returnType    = returnType;
            this.arity         = arity;
            this.canSpecialize = canSpecialize;
        }

        /**
         * Can this request type be specialized?
         *
         * @return true if request can be specialized
         */
        public boolean canSpecialize() {
            return canSpecialize;
        }

        /**
         * Get arity
         *
         * @return the arity of the request
         */
        public int getArity() {
            return arity;
        }

        /**
         * Get the return type
         *
         * @return return type for request
         */
        public Type getReturnType() {
            return returnType;
        }

        /**
         * Get token type
         *
         * @return token type for request
         */
        public TokenType getTokenType() {
            return tokenType;
        }

        /**
         * Get the non-strict name for this request.
         *
         * @return the name without _STRICT suffix
         */
        public String nonStrictName() {
            switch(this) {
            case NE_STRICT:
                return NE.name();
            case EQ_STRICT:
                return EQ.name();
            default:
                return name();
            }
        }

        /**
         * Derive a runtime node request type for a node
         * @param node the node
         * @return request type
         */
        public static Request requestFor(final Expression node) {
            switch (node.tokenType()) {
            case TYPEOF:
                return Request.TYPEOF;
            case IN:
                return Request.IN;
            case INSTANCEOF:
                return Request.INSTANCEOF;
            case EQ_STRICT:
                return Request.EQ_STRICT;
            case NE_STRICT:
                return Request.NE_STRICT;
            case EQ:
                return Request.EQ;
            case NE:
                return Request.NE;
            case LT:
                return Request.LT;
            case LE:
                return Request.LE;
            case GT:
                return Request.GT;
            case GE:
                return Request.GE;
            default:
                assert false;
                return null;
            }
        }

        /**
         * Is this an undefined check?
         *
         * @param request request
         *
         * @return true if undefined check
         */
        public static boolean isUndefinedCheck(final Request request) {
            return request == IS_UNDEFINED || request == IS_NOT_UNDEFINED;
        }

        /**
         * Is this an EQ or EQ_STRICT?
         *
         * @param request a request
         *
         * @return true if EQ or EQ_STRICT
         */
        public static boolean isEQ(final Request request) {
            return request == EQ || request == EQ_STRICT;
        }

        /**
         * Is this an NE or NE_STRICT?
         *
         * @param request a request
         *
         * @return true if NE or NE_STRICT
         */
        public static boolean isNE(final Request request) {
            return request == NE || request == NE_STRICT;
        }

        /**
         * Is this strict?
         *
         * @param request a request
         *
         * @return true if script
         */
        public static boolean isStrict(final Request request) {
            return request == EQ_STRICT || request == NE_STRICT;
        }

        /**
         * If this request can be reversed, return the reverse request
         * Eq EQ {@literal ->} NE.
         *
         * @param request request to reverse
         *
         * @return reversed request or null if not applicable
         */
        public static Request reverse(final Request request) {
            switch (request) {
            case EQ:
            case EQ_STRICT:
            case NE:
            case NE_STRICT:
                return request;
            case LE:
                return GE;
            case LT:
                return GT;
            case GE:
                return LE;
            case GT:
                return LT;
            default:
                return null;
            }
        }

        /**
         * Invert the request, only for non equals comparisons.
         *
         * @param request a request
         *
         * @return the inverted request, or null if not applicable
         */
        public static Request invert(final Request request) {
            switch (request) {
            case EQ:
                return NE;
            case EQ_STRICT:
                return NE_STRICT;
            case NE:
                return EQ;
            case NE_STRICT:
                return EQ_STRICT;
            case LE:
                return GT;
            case LT:
                return GE;
            case GE:
                return LT;
            case GT:
                return LE;
            default:
                return null;
            }
        }

        /**
         * Check if this is a comparison
         *
         * @param request a request
         *
         * @return true if this is a comparison, null otherwise
         */
        public static boolean isComparison(final Request request) {
            switch (request) {
            case EQ:
            case EQ_STRICT:
            case NE:
            case NE_STRICT:
            case LE:
            case LT:
            case GE:
            case GT:
            case IS_UNDEFINED:
            case IS_NOT_UNDEFINED:
                return true;
            default:
                return false;
            }
        }
    }

    /** Runtime request. */
    private final Request request;

    /** Call arguments. */
    private final List<Expression> args;

    /**
     * Constructor
     *
     * @param token   token
     * @param finish  finish
     * @param request the request
     * @param args    arguments to request
     */
    public RuntimeNode(final long token, final int finish, final Request request, final List<Expression> args) {
        super(token, finish);

        this.request      = request;
        this.args         = args;
    }

    private RuntimeNode(final RuntimeNode runtimeNode, final Request request, final List<Expression> args) {
        super(runtimeNode);

        this.request      = request;
        this.args         = args;
    }

    /**
     * Constructor
     *
     * @param token   token
     * @param finish  finish
     * @param request the request
     * @param args    arguments to request
     */
    public RuntimeNode(final long token, final int finish, final Request request, final Expression... args) {
        this(token, finish, request, Arrays.asList(args));
    }

    /**
     * Constructor
     *
     * @param parent  parent node from which to inherit source, token, finish
     * @param request the request
     * @param args    arguments to request
     */
    public RuntimeNode(final Expression parent, final Request request, final Expression... args) {
        this(parent, request, Arrays.asList(args));
    }

    /**
     * Constructor
     *
     * @param parent  parent node from which to inherit source, token, finish
     * @param request the request
     * @param args    arguments to request
     */
    public RuntimeNode(final Expression parent, final Request request, final List<Expression> args) {
        super(parent);

        this.request      = request;
        this.args         = args;
    }

    /**
     * Constructor
     *
     * @param parent  parent node from which to inherit source, token, finish and arguments
     * @param request the request
     */
    public RuntimeNode(final UnaryNode parent, final Request request) {
        this(parent, request, parent.getExpression());
    }

    /**
     * Constructor used to replace a binary node with a runtime request.
     *
     * @param parent  parent node from which to inherit source, token, finish and arguments
     */
    public RuntimeNode(final BinaryNode parent) {
        this(parent, Request.requestFor(parent), parent.lhs(), parent.rhs());
    }

    /**
     * Reset the request for this runtime node
     * @param request request
     * @return new runtime node or same if same request
     */
    public RuntimeNode setRequest(final Request request) {
        if (this.request == request) {
            return this;
        }
        return new RuntimeNode(this, request, args);
   }

    /**
     * Return type for the ReferenceNode
     */
    @Override
    public Type getType() {
        return request.getReturnType();
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterRuntimeNode(this)) {
            return visitor.leaveRuntimeNode(setArgs(Node.accept(visitor, args)));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append("ScriptRuntime.");
        sb.append(request);
        sb.append('(');

        boolean first = true;

        for (final Node arg : args) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }

            arg.toString(sb, printType);
        }

        sb.append(')');
    }

    /**
     * Get the arguments for this runtime node
     * @return argument list
     */
    public List<Expression> getArgs() {
        return Collections.unmodifiableList(args);
    }

    /**
     * Set the arguments of this runtime node
     * @param args new arguments
     * @return new runtime node, or identical if no change
     */
    public RuntimeNode setArgs(final List<Expression> args) {
        if (this.args == args) {
            return this;
        }
        return new RuntimeNode(this, request, args);
    }

    /**
     * Get the request that this runtime node implements
     * @return the request
     */
    public Request getRequest() {
        return request;
    }

    /**
     * Is this runtime node, engineered to handle the "at least one object" case of the defined
     * requests and specialize on demand, really primitive. This can happen e.g. after AccessSpecializer
     * In that case it can be turned into a simpler primitive form in CodeGenerator
     *
     * @return true if all arguments now are primitive
     */
    public boolean isPrimitive() {
        for (final Expression arg : args) {
            if (arg.getType().isObject()) {
                return false;
            }
        }
        return true;
    }
}
