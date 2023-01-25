/*
 * Copyright (c) 2004, 2006, Oracle and/or its affiliates. All rights reserved.
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

package org.omg.CORBA;

/**
 * The <code>ACTIVITY_REQUIRED</code> system exception may be raised on any
 * method for which an Activity context is required. It indicates that an
 * Activity context was necessary to perform the invoked operation, but one
 * was not found associated with the calling thread.
 *
 * @see <A href="../../../../technotes/guides/idl/jidlExceptions.html">documentation on
 *      Java&nbsp;IDL exceptions</A>
 * @since   J2SE 1.5
 */

public final class ACTIVITY_REQUIRED extends SystemException {

    /**
     * Constructs an <code>ACTIVITY_REQUIRED</code> exception with
     * minor code set to 0 and CompletionStatus set to COMPLETED_NO.
     */
    public ACTIVITY_REQUIRED() {
        this("");
    }

    /**
     * Constructs an <code>ACTIVITY_REQUIRED</code> exception with the
     * specified message.
     *
     * @param detailMessage string containing a detailed message.
     */
    public ACTIVITY_REQUIRED(String detailMessage) {
        this(detailMessage, 0, CompletionStatus.COMPLETED_NO);
    }

    /**
     * Constructs an <code>ACTIVITY_REQUIRED</code> exception with the
     * specified minor code and completion status.
     *
     * @param minorCode minor code.
     * @param completionStatus completion status.
     */
    public ACTIVITY_REQUIRED(int minorCode,
                             CompletionStatus completionStatus) {
        this("", minorCode, completionStatus);
    }

    /**
     * Constructs an <code>ACTIVITY_REQUIRED</code> exception with the
     * specified message, minor code, and completion status.
     *
     * @param detailMessage string containing a detailed message.
     * @param minorCode minor code.
     * @param completionStatus completion status.
     */
    public ACTIVITY_REQUIRED(String detailMessage,
                             int minorCode,
                             CompletionStatus completionStatus) {
        super(detailMessage, minorCode, completionStatus);
    }
}
