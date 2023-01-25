/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.servicecontext;

import org.omg.CORBA.SystemException;
import org.omg.CORBA_2_3.portable.InputStream;
import org.omg.CORBA_2_3.portable.OutputStream;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.impl.encoding.MarshalInputStream ;
import com.sun.corba.se.impl.encoding.MarshalOutputStream ;
import com.sun.corba.se.impl.encoding.CodeSetComponentInfo  ;

public class CodeSetServiceContext extends ServiceContext {
    public CodeSetServiceContext( CodeSetComponentInfo.CodeSetContext csc )
    {
        this.csc = csc ;
    }

    public CodeSetServiceContext(InputStream is, GIOPVersion gv)
    {
        super(is, gv) ;
        csc = new CodeSetComponentInfo.CodeSetContext() ;
        csc.read( (MarshalInputStream)in ) ;
    }

    // Required SERVICE_CONTEXT_ID and getId definitions
    public static final int SERVICE_CONTEXT_ID = 1 ;
    public int getId() { return SERVICE_CONTEXT_ID ; }

    public void writeData( OutputStream os ) throws SystemException
    {
        csc.write( (MarshalOutputStream)os ) ;
    }

    public CodeSetComponentInfo.CodeSetContext getCodeSetContext()
    {
        return csc ;
    }

    private CodeSetComponentInfo.CodeSetContext csc ;

    public String toString()
    {
        return "CodeSetServiceContext[ csc=" + csc + " ]" ;
    }
}
