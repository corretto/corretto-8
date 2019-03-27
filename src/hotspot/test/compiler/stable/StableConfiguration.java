/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.util.Properties;
import sun.hotspot.WhiteBox;

public class StableConfiguration {
    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static final boolean isStableEnabled;
    static final boolean isServerWithStable;

    static {
        Boolean value = WB.getBooleanVMFlag("FoldStableValues");
        isStableEnabled = (value == null ? false : value);
        isServerWithStable = isStableEnabled && get();
        System.out.println("@Stable:         " + (isStableEnabled ? "enabled" : "disabled"));
        System.out.println("Server Compiler: " + get());
    }

    // ::get() is among immediately compiled methods.
    static boolean get() {
        try {
            Method m = StableConfiguration.class.getDeclaredMethod("get");
            int level = WB.getMethodCompilationLevel(m);
            if (level > 0) {
              return (level == 4);
            } else {
              String javaVM = System.getProperty("java.vm.name", "");
              if (javaVM.contains("Server")) return true;
              if (javaVM.contains("Client")) return false;
              throw new Error("Unknown VM type: "+javaVM);
            }
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
    }

}
