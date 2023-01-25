/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @bug 6263319
 * @summary test setNativeMethodPrefix
 * @author Robert Field, Sun Microsystems
 *
 * @run shell/timeout=240 MakeJAR2.sh NativeMethodPrefixAgent NativeMethodPrefixApp 'Can-Retransform-Classes: true' 'Can-Set-Native-Method-Prefix: true'
 * @run main/othervm -javaagent:NativeMethodPrefixAgent.jar NativeMethodPrefixApp
 */

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.io.*;

import ilib.*;

class NativeMethodPrefixAgent {

    static ClassFileTransformer t0, t1, t2;
    static Instrumentation inst;

    static class Tr implements ClassFileTransformer {
        final String trname;
        final int transformId;

        Tr(int transformId) {
            this.trname = "tr" + transformId;
            this.transformId = transformId;
        }

        public byte[]
        transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain    protectionDomain,
            byte[] classfileBuffer) {
            boolean redef = classBeingRedefined != null;
            System.out.println(trname + ": " +
                               (redef? "Retransforming " : "Loading ") + className);
            if (className != null) {
                Options opt = new Options();
                opt.shouldInstrumentNativeMethods = true;
                opt.trackerClassName = "bootreporter/StringIdCallbackReporter";
                opt.wrappedTrackerMethodName = "tracker";
                opt.fixedIndex = transformId;
                opt.wrappedPrefix = "wrapped_" + trname + "_";
                try {
                    byte[] newcf =  Inject.instrumentation(opt, loader, className, classfileBuffer);
                    return redef? null : newcf;
                } catch (Throwable ex) {
                    System.err.println("ERROR: Injection failure: " + ex);
                    ex.printStackTrace();
                    System.err.println("Returning bad class file, to cause test failure");
                    return new byte[0];
                }
            }
            return null;
        }

    }

    // for debugging
    static void write_buffer(String fname, byte[]buffer) {
        try {
            FileOutputStream outStream = new FileOutputStream(fname);
            outStream.write(buffer, 0, buffer.length);
            outStream.close();
        } catch (Exception ex) {
            System.err.println("EXCEPTION in write_buffer: " + ex);
        }
    }

    public static void
    premain (String agentArgs, Instrumentation instArg)
        throws IOException, IllegalClassFormatException,
        ClassNotFoundException, UnmodifiableClassException {
        inst = instArg;
        System.out.println("Premain");

        t1 = new Tr(1);
        t2 = new Tr(2);
        t0 = new Tr(0);
        inst.addTransformer(t1, true);
        inst.addTransformer(t2, false);
        inst.addTransformer(t0, true);
        instArg.setNativeMethodPrefix(t0, "wrapped_tr0_");
        instArg.setNativeMethodPrefix(t1, "wrapped_tr1_");
        instArg.setNativeMethodPrefix(t2, "wrapped_tr2_");

        // warm up: cause load of transformer classes before used during class load
        instArg.retransformClasses(Runtime.class);
    }
}
