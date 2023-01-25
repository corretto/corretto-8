/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.orbutil.fsm ;

/**
 * An FSM is used to represent an instance of a finite state machine
 * which has a transition function represented by an instance of
 * StateEngine.  An instance of an FSM may be created either by calling
 * StateEngine.makeFSM( startState ) on a state engine, or by extending FSMImpl and
 * using a constructor.  Using FSMImpl as a base class is convenient if
 * additional state is associated with the FSM beyond that encoded
 * by the current state.  This is especially convenient if an action
 * needs some additional information.  For example, counters are best
 * handled by special actions rather than encoding a bounded counter
 * in a state machine.  It is also possible to create a class that
 * implements the FSM interface by delegating to an FSM instance
 * created by StateEngine.makeFSM.
 *
 * @author Ken Cavanaugh
 */
public interface FSM
{
    /** Get the current state of this FSM.
    */
    public State getState() ;

    /** Perform the action and transition to the next state based
    * on the current state of the FSM and the input.
    */
    public void doIt( Input in ) ;
}

// end of FSM.java
