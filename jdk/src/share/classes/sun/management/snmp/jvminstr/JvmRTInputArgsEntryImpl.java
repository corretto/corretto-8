/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
package sun.management.snmp.jvminstr;

// java imports
//
import java.io.Serializable;

// jmx imports
//
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;

import sun.management.snmp.jvmmib.JvmRTInputArgsEntryMBean;

/**
 * The class is used for implementing the "JvmRTInputArgsEntry" group.
 */
public class JvmRTInputArgsEntryImpl implements JvmRTInputArgsEntryMBean,
                                                Serializable {

    static final long serialVersionUID = 1000306518436503395L;
    private final String item;
    private final int index;

    /**
     * Constructor for the "JvmRTInputArgsEntry" group.
     */
    public JvmRTInputArgsEntryImpl(String item, int index) {
        this.item = validArgValueTC(item);
        this.index = index;
    }

    private String validArgValueTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validArgValueTC(str);
    }

    /**
     * Getter for the "JvmRTInputArgsItem" variable.
     */
    public String getJvmRTInputArgsItem() throws SnmpStatusException {
        return item;
    }

    /**
     * Getter for the "JvmRTInputArgsIndex" variable.
     */
    public Integer getJvmRTInputArgsIndex() throws SnmpStatusException {
        return new Integer(index);
    }

}
