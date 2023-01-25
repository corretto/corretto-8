/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import sun.hotspot.WhiteBox;
import sun.hotspot.code.NMethod;
import sun.management.ManagementFactoryHelper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Abstract class for WhiteBox testing of JIT.
 *
 * @author igor.ignatyev@oracle.com
 */
public abstract class CompilerWhiteBoxTest {
    /** {@code CompLevel::CompLevel_none} -- Interpreter */
    protected static final int COMP_LEVEL_NONE = 0;
    /** {@code CompLevel::CompLevel_any}, {@code CompLevel::CompLevel_all} */
    protected static final int COMP_LEVEL_ANY = -1;
    /** {@code CompLevel::CompLevel_simple} -- C1 */
    protected static final int COMP_LEVEL_SIMPLE = 1;
    /** {@code CompLevel::CompLevel_limited_profile} -- C1, invocation &amp; backedge counters */
    protected static final int COMP_LEVEL_LIMITED_PROFILE = 2;
    /** {@code CompLevel::CompLevel_full_profile} -- C1, invocation &amp; backedge counters + mdo */
    protected static final int COMP_LEVEL_FULL_PROFILE = 3;
    /** {@code CompLevel::CompLevel_full_optimization} -- C2 or Shark */
    protected static final int COMP_LEVEL_FULL_OPTIMIZATION = 4;
    /** Maximal value for CompLevel */
    protected static final int COMP_LEVEL_MAX = COMP_LEVEL_FULL_OPTIMIZATION;

    /** Instance of WhiteBox */
    protected static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    /** Value of {@code -XX:CompileThreshold} */
    protected static final int COMPILE_THRESHOLD
            = Integer.parseInt(getVMOption("CompileThreshold", "10000"));
    /** Value of {@code -XX:BackgroundCompilation} */
    protected static final boolean BACKGROUND_COMPILATION
            = Boolean.valueOf(getVMOption("BackgroundCompilation", "true"));
    /** Value of {@code -XX:TieredCompilation} */
    protected static final boolean TIERED_COMPILATION
            = Boolean.valueOf(getVMOption("TieredCompilation", "false"));
    /** Value of {@code -XX:TieredStopAtLevel} */
    protected static final int TIERED_STOP_AT_LEVEL
            = Integer.parseInt(getVMOption("TieredStopAtLevel", "0"));
    /** Flag for verbose output, true if {@code -Dverbose} specified */
    protected static final boolean IS_VERBOSE
            = System.getProperty("verbose") != null;
    /** invocation count to trigger compilation */
    protected static final int THRESHOLD;
    /** invocation count to trigger OSR compilation */
    protected static final long BACKEDGE_THRESHOLD;
    /** Value of {@code java.vm.info} (interpreted|mixed|comp mode) */
    protected static final String MODE = System.getProperty("java.vm.info");

    static {
        if (TIERED_COMPILATION) {
            BACKEDGE_THRESHOLD = THRESHOLD = 150000;
        } else {
            THRESHOLD = COMPILE_THRESHOLD;
            BACKEDGE_THRESHOLD = Math.max(10000, COMPILE_THRESHOLD *
                    Long.parseLong(getVMOption("OnStackReplacePercentage")));
        }
    }

    /**
     * Returns value of VM option.
     *
     * @param name option's name
     * @return value of option or {@code null}, if option doesn't exist
     * @throws NullPointerException if name is null
     */
    protected static String getVMOption(String name) {
        Objects.requireNonNull(name);
        HotSpotDiagnosticMXBean diagnostic
                = ManagementFactoryHelper.getDiagnosticMXBean();
        VMOption tmp;
        try {
            tmp = diagnostic.getVMOption(name);
        } catch (IllegalArgumentException e) {
            tmp = null;
        }
        return (tmp == null ? null : tmp.getValue());
    }

    /**
     * Returns value of VM option or default value.
     *
     * @param name         option's name
     * @param defaultValue default value
     * @return value of option or {@code defaultValue}, if option doesn't exist
     * @throws NullPointerException if name is null
     * @see #getVMOption(String)
     */
    protected static String getVMOption(String name, String defaultValue) {
        String result = getVMOption(name);
        return result == null ? defaultValue : result;
    }

    /** copy of is_c1_compile(int) from utilities/globalDefinitions.hpp */
    protected static boolean isC1Compile(int compLevel) {
        return (compLevel > COMP_LEVEL_NONE)
                && (compLevel < COMP_LEVEL_FULL_OPTIMIZATION);
    }

    /** copy of is_c2_compile(int) from utilities/globalDefinitions.hpp */
    protected static boolean isC2Compile(int compLevel) {
        return compLevel == COMP_LEVEL_FULL_OPTIMIZATION;
    }

    protected static void main(
            Function<TestCase, CompilerWhiteBoxTest> constructor,
            String[] args) {
        if (args.length == 0) {
            for (TestCase test : SimpleTestCase.values()) {
                constructor.apply(test).runTest();
            }
        } else {
            for (String name : args) {
                constructor.apply(SimpleTestCase.valueOf(name)).runTest();
            }
        }
    }

    /** tested method */
    protected final Executable method;
    protected final TestCase testCase;

    /**
     * Constructor.
     *
     * @param testCase object, that contains tested method and way to invoke it.
     */
    protected CompilerWhiteBoxTest(TestCase testCase) {
        Objects.requireNonNull(testCase);
        System.out.println("TEST CASE:" + testCase.name());
        method = testCase.getExecutable();
        this.testCase = testCase;
    }

    /**
     * Template method for testing. Prints tested method's info before
     * {@linkplain #test()} and after {@linkplain #test()} or on thrown
     * exception.
     *
     * @throws RuntimeException if method {@linkplain #test()} throws any
     *                          exception
     * @see #test()
     */
    protected final void runTest() {
        if (ManagementFactoryHelper.getCompilationMXBean() == null) {
            System.err.println(
                    "Warning: test is not applicable in interpreted mode");
            return;
        }
        System.out.println("at test's start:");
        printInfo();
        try {
            test();
        } catch (Exception e) {
            System.out.printf("on exception '%s':", e.getMessage());
            printInfo();
            e.printStackTrace();
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
        System.out.println("at test's end:");
        printInfo();
    }

    /**
     * Checks, that {@linkplain #method} is not compiled at the given compilation
     * level or above.
     *
     * @param compLevel
     *
     * @throws RuntimeException if {@linkplain #method} is in compiler queue or
     *                          is compiled, or if {@linkplain #method} has zero
     *                          compilation level.
     */
    protected final void checkNotCompiled(int compLevel) {
        if (WHITE_BOX.isMethodQueuedForCompilation(method)) {
            throw new RuntimeException(method + " must not be in queue");
        }
        if (WHITE_BOX.getMethodCompilationLevel(method, false) >= compLevel) {
            throw new RuntimeException(method + " comp_level must be >= maxCompLevel");
        }
        if (WHITE_BOX.getMethodCompilationLevel(method, true) >= compLevel) {
            throw new RuntimeException(method + " osr_comp_level must be >= maxCompLevel");
        }
    }

    /**
     * Checks, that {@linkplain #method} is not compiled.
     *
     * @throws RuntimeException if {@linkplain #method} is in compiler queue or
     *                          is compiled, or if {@linkplain #method} has zero
     *                          compilation level.
     */
    protected final void checkNotCompiled() {
        checkNotCompiled(true);
        checkNotCompiled(false);
    }

    /**
     * Checks, that {@linkplain #method} is not (OSR-)compiled.
     *
     * @param isOsr Check for OSR compilation if true
     * @throws RuntimeException if {@linkplain #method} is in compiler queue or
     *                          is compiled, or if {@linkplain #method} has zero
     *                          compilation level.
     */
    protected final void checkNotCompiled(boolean isOsr) {
        waitBackgroundCompilation();
        if (WHITE_BOX.isMethodQueuedForCompilation(method)) {
            throw new RuntimeException(method + " must not be in queue");
        }
        if (WHITE_BOX.isMethodCompiled(method, isOsr)) {
            throw new RuntimeException(method + " must not be " +
                                       (isOsr ? "osr_" : "") + "compiled");
        }
        if (WHITE_BOX.getMethodCompilationLevel(method, isOsr) != 0) {
            throw new RuntimeException(method + (isOsr ? " osr_" : " ") +
                                       "comp_level must be == 0");
        }
    }

    /**
     * Checks, that {@linkplain #method} is compiled.
     *
     * @throws RuntimeException if {@linkplain #method} isn't in compiler queue
     *                          and isn't compiled, or if {@linkplain #method}
     *                          has nonzero compilation level
     */
    protected final void checkCompiled() {
        final long start = System.currentTimeMillis();
        waitBackgroundCompilation();
        if (WHITE_BOX.isMethodQueuedForCompilation(method)) {
            System.err.printf("Warning: %s is still in queue after %dms%n",
                    method, System.currentTimeMillis() - start);
            return;
        }
        if (!WHITE_BOX.isMethodCompiled(method, testCase.isOsr())) {
            throw new RuntimeException(method + " must be "
                    + (testCase.isOsr() ? "osr_" : "") + "compiled");
        }
        if (WHITE_BOX.getMethodCompilationLevel(method, testCase.isOsr())
                == 0) {
            throw new RuntimeException(method
                    + (testCase.isOsr() ? " osr_" : " ")
                    + "comp_level must be != 0");
        }
    }

    protected final void deoptimize() {
        WHITE_BOX.deoptimizeMethod(method, testCase.isOsr());
        if (testCase.isOsr()) {
            WHITE_BOX.deoptimizeMethod(method, false);
        }
    }

    protected final int getCompLevel() {
        NMethod nm = NMethod.get(method, testCase.isOsr());
        return nm == null ? COMP_LEVEL_NONE : nm.comp_level;
    }

    protected final boolean isCompilable() {
        return WHITE_BOX.isMethodCompilable(method, COMP_LEVEL_ANY,
                testCase.isOsr());
    }

    protected final boolean isCompilable(int compLevel) {
        return WHITE_BOX
                .isMethodCompilable(method, compLevel, testCase.isOsr());
    }

    protected final void makeNotCompilable() {
        WHITE_BOX.makeMethodNotCompilable(method, COMP_LEVEL_ANY,
                testCase.isOsr());
    }

    protected final void makeNotCompilable(int compLevel) {
        WHITE_BOX.makeMethodNotCompilable(method, compLevel, testCase.isOsr());
    }

    /**
     * Waits for completion of background compilation of {@linkplain #method}.
     */
    protected final void waitBackgroundCompilation() {
        waitBackgroundCompilation(method);
    }

    /**
     * Waits for completion of background compilation of the given executable.
     *
     * @param executable Executable
     */
    protected static final void waitBackgroundCompilation(Executable executable) {
        if (!BACKGROUND_COMPILATION) {
            return;
        }
        final Object obj = new Object();
        for (int i = 0; i < 100
                && WHITE_BOX.isMethodQueuedForCompilation(executable); ++i) {
            synchronized (obj) {
                try {
                    obj.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Prints information about {@linkplain #method}.
     */
    protected final void printInfo() {
        System.out.printf("%n%s:%n", method);
        System.out.printf("\tcompilable:\t%b%n",
                WHITE_BOX.isMethodCompilable(method, COMP_LEVEL_ANY, false));
        boolean isCompiled = WHITE_BOX.isMethodCompiled(method, false);
        System.out.printf("\tcompiled:\t%b%n", isCompiled);
        if (isCompiled) {
            System.out.printf("\tcompile_id:\t%d%n",
                    NMethod.get(method, false).compile_id);
        }
        System.out.printf("\tcomp_level:\t%d%n",
                WHITE_BOX.getMethodCompilationLevel(method, false));
        System.out.printf("\tosr_compilable:\t%b%n",
                WHITE_BOX.isMethodCompilable(method, COMP_LEVEL_ANY, true));
        isCompiled = WHITE_BOX.isMethodCompiled(method, true);
        System.out.printf("\tosr_compiled:\t%b%n", isCompiled);
        if (isCompiled) {
            System.out.printf("\tosr_compile_id:\t%d%n",
                    NMethod.get(method, true).compile_id);
        }
        System.out.printf("\tosr_comp_level:\t%d%n",
                WHITE_BOX.getMethodCompilationLevel(method, true));
        System.out.printf("\tin_queue:\t%b%n",
                WHITE_BOX.isMethodQueuedForCompilation(method));
        System.out.printf("compile_queues_size:\t%d%n%n",
                WHITE_BOX.getCompileQueuesSize());
    }

    /**
     * Executes testing.
     */
    protected abstract void test() throws Exception;

    /**
     * Tries to trigger compilation of {@linkplain #method} by call
     * {@linkplain TestCase#getCallable()} enough times.
     *
     * @return accumulated result
     * @see #compile(int)
     */
    protected final int compile() {
        if (testCase.isOsr()) {
            return compile(1);
        } else {
            return compile(THRESHOLD);
        }
    }

    /**
     * Tries to trigger compilation of {@linkplain #method} by call
     * {@linkplain TestCase#getCallable()} specified times.
     *
     * @param count invocation count
     * @return accumulated result
     */
    protected final int compile(int count) {
        int result = 0;
        Integer tmp;
        for (int i = 0; i < count; ++i) {
            try {
                tmp = testCase.getCallable().call();
            } catch (Exception e) {
                tmp = null;
            }
            result += tmp == null ? 0 : tmp;
        }
        if (IS_VERBOSE) {
            System.out.println("method was invoked " + count + " times");
        }
        return result;
    }

    /**
     * Utility interface provides tested method and object to invoke it.
     */
    public interface TestCase {
        /** the name of test case */
        String name();

        /** tested method */
        Executable getExecutable();

        /** object to invoke {@linkplain #getExecutable()} */
        Callable<Integer> getCallable();

        /** flag for OSR test case */
        boolean isOsr();
    }

    /**
     * @return {@code true} if the current test case is OSR and the mode is
     *          Xcomp, otherwise {@code false}
     */
    protected boolean skipXcompOSR() {
        boolean result =  testCase.isOsr()
                && CompilerWhiteBoxTest.MODE.startsWith("compiled ");
        if (result && IS_VERBOSE) {
            System.err.printf("Warning: %s is not applicable in %s%n",
                    testCase.name(), CompilerWhiteBoxTest.MODE);
        }
        return result;
    }

    /**
     * Skip the test for the specified value of Tiered Compilation
     * @param value of TieredCompilation the test should not run with
     * @return {@code true} if the test should be skipped,
     *         {@code false} otherwise
     */
    protected static boolean skipOnTieredCompilation(boolean value) {
        if (value == CompilerWhiteBoxTest.TIERED_COMPILATION) {
            System.err.println("Test isn't applicable w/ "
                    + (value ? "enabled" : "disabled")
                    + "TieredCompilation. Skip test.");
            return true;
        }
        return false;
    }
}

enum SimpleTestCase implements CompilerWhiteBoxTest.TestCase {
    /** constructor test case */
    CONSTRUCTOR_TEST(Helper.CONSTRUCTOR, Helper.CONSTRUCTOR_CALLABLE, false),
    /** method test case */
    METHOD_TEST(Helper.METHOD, Helper.METHOD_CALLABLE, false),
    /** static method test case */
    STATIC_TEST(Helper.STATIC, Helper.STATIC_CALLABLE, false),
    /** OSR constructor test case */
    OSR_CONSTRUCTOR_TEST(Helper.OSR_CONSTRUCTOR,
            Helper.OSR_CONSTRUCTOR_CALLABLE, true),
    /** OSR method test case */
    OSR_METHOD_TEST(Helper.OSR_METHOD, Helper.OSR_METHOD_CALLABLE, true),
    /** OSR static method test case */
    OSR_STATIC_TEST(Helper.OSR_STATIC, Helper.OSR_STATIC_CALLABLE, true);

    private final Executable executable;
    private final Callable<Integer> callable;
    private final boolean isOsr;

    private SimpleTestCase(Executable executable, Callable<Integer> callable,
            boolean isOsr) {
        this.executable = executable;
        this.callable = callable;
        this.isOsr = isOsr;
    }

    @Override
    public Executable getExecutable() {
        return executable;
    }

    @Override
    public Callable<Integer> getCallable() {
        return callable;
    }

    @Override
    public boolean isOsr() {
        return isOsr;
    }

    private static class Helper {

        private static final Callable<Integer> CONSTRUCTOR_CALLABLE
                = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return new Helper(1337).hashCode();
            }
        };

        private static final Callable<Integer> METHOD_CALLABLE
                = new Callable<Integer>() {
            private final Helper helper = new Helper();

            @Override
            public Integer call() throws Exception {
                return helper.method();
            }
        };

        private static final Callable<Integer> STATIC_CALLABLE
                = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return staticMethod();
            }
        };

        private static final Callable<Integer> OSR_CONSTRUCTOR_CALLABLE
                = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return new Helper(null, CompilerWhiteBoxTest.BACKEDGE_THRESHOLD).hashCode();
            }
        };

        private static final Callable<Integer> OSR_METHOD_CALLABLE
                = new Callable<Integer>() {
            private final Helper helper = new Helper();

            @Override
            public Integer call() throws Exception {
                return helper.osrMethod(CompilerWhiteBoxTest.BACKEDGE_THRESHOLD);
            }
        };

        private static final Callable<Integer> OSR_STATIC_CALLABLE
                = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return osrStaticMethod(CompilerWhiteBoxTest.BACKEDGE_THRESHOLD);
            }
        };

        private static final Constructor CONSTRUCTOR;
        private static final Constructor OSR_CONSTRUCTOR;
        private static final Method METHOD;
        private static final Method STATIC;
        private static final Method OSR_METHOD;
        private static final Method OSR_STATIC;

        static {
            try {
                CONSTRUCTOR = Helper.class.getDeclaredConstructor(int.class);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new RuntimeException(
                        "exception on getting method Helper.<init>(int)", e);
            }
            try {
                OSR_CONSTRUCTOR = Helper.class.getDeclaredConstructor(
                        Object.class, long.class);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new RuntimeException(
                        "exception on getting method Helper.<init>(Object, long)", e);
            }
            METHOD = getMethod("method");
            STATIC = getMethod("staticMethod");
            OSR_METHOD = getMethod("osrMethod", long.class);
            OSR_STATIC = getMethod("osrStaticMethod", long.class);
        }

        private static Method getMethod(String name, Class<?>... parameterTypes) {
            try {
                return Helper.class.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new RuntimeException(
                        "exception on getting method Helper." + name, e);
            }
        }

        private static int staticMethod() {
            return 1138;
        }

        private int method() {
            return 42;
        }

        /**
         * Deoptimizes all non-osr versions of the given executable after
         * compilation finished.
         *
         * @param e Executable
         * @throws Exception
         */
        private static void waitAndDeoptimize(Executable e) {
            CompilerWhiteBoxTest.waitBackgroundCompilation(e);
            if (WhiteBox.getWhiteBox().isMethodQueuedForCompilation(e)) {
                throw new RuntimeException(e + " must not be in queue");
            }
            // Deoptimize non-osr versions of executable
            WhiteBox.getWhiteBox().deoptimizeMethod(e, false);
        }

        /**
         * Executes the method multiple times to make sure we have
         * enough profiling information before triggering an OSR
         * compilation. Otherwise the C2 compiler may add uncommon traps.
         *
         * @param m Method to be executed
         * @return Number of times the method was executed
         * @throws Exception
         */
        private static int warmup(Method m) throws Exception {
            waitAndDeoptimize(m);
            Helper helper = new Helper();
            int result = 0;
            for (long i = 0; i < CompilerWhiteBoxTest.THRESHOLD; ++i) {
                result += (int)m.invoke(helper, 1);
            }
            // Wait to make sure OSR compilation is not blocked by
            // non-OSR compilation in the compile queue
            CompilerWhiteBoxTest.waitBackgroundCompilation(m);
            return result;
        }

        /**
         * Executes the constructor multiple times to make sure we
         * have enough profiling information before triggering an OSR
         * compilation. Otherwise the C2 compiler may add uncommon traps.
         *
         * @param c Constructor to be executed
         * @return Number of times the constructor was executed
         * @throws Exception
         */
        private static int warmup(Constructor c) throws Exception {
            waitAndDeoptimize(c);
            int result = 0;
            for (long i = 0; i < CompilerWhiteBoxTest.THRESHOLD; ++i) {
                result += c.newInstance(null, 1).hashCode();
            }
            // Wait to make sure OSR compilation is not blocked by
            // non-OSR compilation in the compile queue
            CompilerWhiteBoxTest.waitBackgroundCompilation(c);
            return result;
        }

        private static int osrStaticMethod(long limit) throws Exception {
            int result = 0;
            if (limit != 1) {
                result = warmup(OSR_STATIC);
            }
            // Trigger osr compilation
            for (long i = 0; i < limit; ++i) {
                result += staticMethod();
            }
            return result;
        }

        private int osrMethod(long limit) throws Exception {
            int result = 0;
            if (limit != 1) {
                result = warmup(OSR_METHOD);
            }
            // Trigger osr compilation
            for (long i = 0; i < limit; ++i) {
                result += method();
            }
            return result;
        }

        private final int x;

        // for method and OSR method test case
        public Helper() {
            x = 0;
        }

        // for OSR constructor test case
        private Helper(Object o, long limit) throws Exception {
            int result = 0;
            if (limit != 1) {
                result = warmup(OSR_CONSTRUCTOR);
            }
            // Trigger osr compilation
            for (long i = 0; i < limit; ++i) {
                result += method();
            }
            x = result;
        }

        // for constructor test case
        private Helper(int x) {
            this.x = x;
        }

        @Override
        public int hashCode() {
            return x;
        }
    }
}
