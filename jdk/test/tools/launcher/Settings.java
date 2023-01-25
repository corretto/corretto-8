/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @bug 6994753 7123582
 * @summary tests -XshowSettings options
 * @compile -XDignore.symbol.file Settings.java
 * @run main Settings
 * @author ksrini
 */
public class Settings extends TestHelper {
    private static File testJar = null;

    static void init() throws IOException {
        if  (testJar != null) {
            return;
        }
        testJar = new File("test.jar");
        StringBuilder tsrc = new StringBuilder();
        tsrc.append("public static void main(String... args) {\n");
        tsrc.append("   for (String x : args) {\n");
        tsrc.append("        System.out.println(x);\n");
        tsrc.append("   }\n");
        tsrc.append("}\n");
        createJar(testJar, tsrc.toString());
    }

    static void checkContains(TestResult tr, String str) {
        if (!tr.contains(str)) {
            System.out.println(tr);
            throw new RuntimeException(str + " not found");
        }
    }

    static void checkNoContains(TestResult tr, String str) {
        if (tr.contains(str)) {
            System.out.println(tr.status);
            throw new RuntimeException(str + " found");
        }
    }

    private static final String VM_SETTINGS = "VM settings:";
    private static final String PROP_SETTINGS = "Property settings:";
    private static final String LOCALE_SETTINGS = "Locale settings:";
    private static final String STACKSIZE_SETTINGS = "Stack Size:";
    private static final String SYSTEM_SETTINGS = "Operating System Metrics:";

    static void containsAllOptions(TestResult tr) {
        checkContains(tr, VM_SETTINGS);
        checkContains(tr, PROP_SETTINGS);
        checkContains(tr, LOCALE_SETTINGS);
        if (System.getProperty("os.name").contains("Linux")) {
            checkContains(tr, SYSTEM_SETTINGS);
        }
    }

    static void runTestOptionDefault() throws IOException {
        String stackSize = "256"; // in kb
        if (getArch().equals("ppc64") || getArch().equals("ppc64le")) {
            stackSize = "800";
        } else if (getArch().equals("aarch64")) {
            /*
             * The max value of minimum stack size allowed for aarch64 can be estimated as
             * such: suppose the vm page size is 64KB and the test runs with a debug build,
             * the initial _java_thread_min_stack_allowed defined in os_linux_aarch64.cpp is
             * 72K, stack guard zones could take 192KB, and the shadow zone needs 128KB,
             * after aligning up all parts to the page size, the final size would be 448KB.
             * See details in JDK-8163363
             */
            stackSize = "448";
        }
        TestResult tr = null;
        tr = doExec(javaCmd, "-Xms64m", "-Xmx512m",
                "-Xss" + stackSize + "k", "-XshowSettings", "-jar", testJar.getAbsolutePath());
        // Check the stack size logs printed by -XshowSettings to verify -Xss meaningfully.
        checkContains(tr, STACKSIZE_SETTINGS);
        containsAllOptions(tr);
        if (!tr.isOK()) {
            System.out.println(tr.status);
            throw new RuntimeException("test fails");
        }
        tr = doExec(javaCmd, "-Xms65536k", "-Xmx712m",
                "-Xss" + stackSize + "000", "-XshowSettings", "-jar", testJar.getAbsolutePath());
        checkContains(tr, STACKSIZE_SETTINGS);
        containsAllOptions(tr);
        if (!tr.isOK()) {
            System.out.println(tr.status);
            throw new RuntimeException("test fails");
        }
    }

    static void runTestOptionSystem() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:system");
        if (System.getProperty("os.name").contains("Linux")) {
            checkNoContains(tr, VM_SETTINGS);
            checkNoContains(tr, PROP_SETTINGS);
            checkNoContains(tr, LOCALE_SETTINGS);
            checkContains(tr, SYSTEM_SETTINGS);
        } else {
            // -XshowSettings prints all available settings when
            // settings argument is not recognized.
            containsAllOptions(tr);
        }
    }

    static void runTestOptionAll() throws IOException {
        init();
        TestResult tr = null;
        tr = doExec(javaCmd, "-XshowSettings:all");
        containsAllOptions(tr);
    }

    static void runTestOptionVM() throws IOException {
        TestResult tr = null;
        tr = doExec(javaCmd, "-XshowSettings:vm");
        checkContains(tr, VM_SETTINGS);
        checkNoContains(tr, PROP_SETTINGS);
        checkNoContains(tr, LOCALE_SETTINGS);
    }

    static void runTestOptionProperty() throws IOException {
        TestResult tr = null;
        tr = doExec(javaCmd, "-XshowSettings:properties");
        checkNoContains(tr, VM_SETTINGS);
        checkContains(tr, PROP_SETTINGS);
        checkNoContains(tr, LOCALE_SETTINGS);
    }

    static void runTestOptionLocale() throws IOException {
        TestResult tr = null;
        tr = doExec(javaCmd, "-XshowSettings:locale");
        checkNoContains(tr, VM_SETTINGS);
        checkNoContains(tr, PROP_SETTINGS);
        checkContains(tr, LOCALE_SETTINGS);
    }

    static void runTestBadOptions() throws IOException {
        TestResult tr = null;
        tr = doExec(javaCmd, "-XshowSettingsBadOption");
        checkNoContains(tr, VM_SETTINGS);
        checkNoContains(tr, PROP_SETTINGS);
        checkNoContains(tr, LOCALE_SETTINGS);
        checkContains(tr, "Unrecognized option: -XshowSettingsBadOption");
    }

    static void runTest7123582() throws IOException {
        TestResult tr = null;
        tr = doExec(javaCmd, "-XshowSettings", "-version");
        if (!tr.isOK()) {
            System.out.println(tr.status);
            throw new RuntimeException("test fails");
        }
        containsAllOptions(tr);
    }

    public static void main(String... args) {
        try {
            runTestOptionAll();
            runTestOptionDefault();
            runTestOptionVM();
            runTestOptionProperty();
            runTestOptionLocale();
            runTestOptionSystem();
            runTestBadOptions();
            runTest7123582();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
}
