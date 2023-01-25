/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.management;

import java.lang.management.MonitorInfo;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import java.util.Set;

/**
 * A CompositeData for MonitorInfo for the local management support.
 * This class avoids the performance penalty paid to the
 * construction of a CompositeData use in the local case.
 */
public class MonitorInfoCompositeData extends LazyCompositeData {
    private final MonitorInfo lock;

    private MonitorInfoCompositeData(MonitorInfo mi) {
        this.lock = mi;
    }

    public MonitorInfo getMonitorInfo() {
        return lock;
    }

    public static CompositeData toCompositeData(MonitorInfo mi) {
        MonitorInfoCompositeData micd = new MonitorInfoCompositeData(mi);
        return micd.getCompositeData();
    }

    protected CompositeData getCompositeData() {
        // CONTENTS OF THIS ARRAY MUST BE SYNCHRONIZED WITH
        // monitorInfoItemNames!

        int len = monitorInfoItemNames.length;
        Object[] values = new Object[len];
        CompositeData li = LockInfoCompositeData.toCompositeData(lock);

        for (int i = 0; i < len; i++) {
            String item = monitorInfoItemNames[i];
            if (item.equals(LOCKED_STACK_FRAME)) {
                StackTraceElement ste = lock.getLockedStackFrame();
                values[i] = (ste != null ? StackTraceElementCompositeData.
                                               toCompositeData(ste)
                                         : null);
            } else if (item.equals(LOCKED_STACK_DEPTH)) {
                values[i] = new Integer(lock.getLockedStackDepth());
            } else {
                values[i] = li.get(item);
            }
        }

        try {
            return new CompositeDataSupport(monitorInfoCompositeType,
                                            monitorInfoItemNames,
                                            values);
        } catch (OpenDataException e) {
            // Should never reach here
            throw new AssertionError(e);
        }
    }

    private static final CompositeType monitorInfoCompositeType;
    private static final String[] monitorInfoItemNames;
    static {
        try {
            monitorInfoCompositeType = (CompositeType)
                MappedMXBeanType.toOpenType(MonitorInfo.class);
            Set<String> s = monitorInfoCompositeType.keySet();
            monitorInfoItemNames =  s.toArray(new String[0]);
        } catch (OpenDataException e) {
            // Should never reach here
            throw new AssertionError(e);
        }
    }

    static CompositeType getMonitorInfoCompositeType() {
        return monitorInfoCompositeType;
    }

    private static final String CLASS_NAME         = "className";
    private static final String IDENTITY_HASH_CODE = "identityHashCode";
    private static final String LOCKED_STACK_FRAME = "lockedStackFrame";
    private static final String LOCKED_STACK_DEPTH = "lockedStackDepth";

    public static String getClassName(CompositeData cd) {
        return getString(cd, CLASS_NAME);
    }

    public static int getIdentityHashCode(CompositeData cd) {
        return getInt(cd, IDENTITY_HASH_CODE);
    }

    public static StackTraceElement getLockedStackFrame(CompositeData cd) {
        CompositeData ste = (CompositeData) cd.get(LOCKED_STACK_FRAME);
        if (ste != null) {
            return StackTraceElementCompositeData.from(ste);
        } else {
            return null;
        }
    }

    public static int getLockedStackDepth(CompositeData cd) {
        return getInt(cd, LOCKED_STACK_DEPTH);
    }

    /** Validate if the input CompositeData has the expected
     * CompositeType (i.e. contain all attributes with expected
     * names and types).
     */
    public static void validateCompositeData(CompositeData cd) {
        if (cd == null) {
            throw new NullPointerException("Null CompositeData");
        }

        if (!isTypeMatched(monitorInfoCompositeType, cd.getCompositeType())) {
            throw new IllegalArgumentException(
                "Unexpected composite type for MonitorInfo");
        }
    }

    private static final long serialVersionUID = -5825215591822908529L;
}
