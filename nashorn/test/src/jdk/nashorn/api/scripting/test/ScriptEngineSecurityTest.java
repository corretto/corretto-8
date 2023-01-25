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

package jdk.nashorn.api.scripting.test;

import static org.testng.Assert.fail;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.Test;

/**
 * jsr223 tests for security access checks.
 */
@SuppressWarnings("javadoc")
public class ScriptEngineSecurityTest {

    private static void log(final String msg) {
        org.testng.Reporter.log(msg, true);
    }

    @Test
    public void securityPackagesTest() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("var v = Packages.sun.misc.Unsafe;");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void securityJavaTypeTest() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("var v = Java.type('sun.misc.Unsafe');");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void securityClassForNameTest() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("var v = java.lang.Class.forName('sun.misc.Unsafe');");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void securitySystemExit() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("java.lang.System.exit(0);");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
        }
    }


    @Test
    public void securitySystemExitFromFinalizerThread() throws ScriptException {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.eval("var o = Java.extend(Java.type('javax.imageio.spi.ServiceRegistry'), { deregisterAll: this.exit.bind(null, 1234)});\n" +
                "new o(new java.util.ArrayList().iterator())");
        System.gc();
        System.runFinalization();
        // NOTE: this test just exits the VM if it fails.
    }

    @Test
    public void securitySystemLoadLibrary() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("java.lang.System.loadLibrary('foo');");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
        }
    }

    // @bug 8032948: Nashorn linkages awry
    @SuppressWarnings("serial")
    public static class FakeProxy extends Proxy {
        public FakeProxy(final InvocationHandler ih) {
            super(ih);
        }

        public static Class<?> makeProxyClass(final ClassLoader cl, final Class<?>... ifaces) {
            return Proxy.getProxyClass(cl, ifaces);
        }
    }

    @Test
    public void fakeProxySubclassAccessCheckTest() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        e.put("name", ScriptEngineSecurityTest.class.getName());
        e.put("cl", ScriptEngineSecurityTest.class.getClassLoader());
        e.put("intfs", new Class[] { Runnable.class });

        final String getClass = "Java.type(name + '$FakeProxy').getProxyClass(cl, intfs);";

        // Should not be able to call static methods of Proxy via fake subclass
        try {
            e.eval(getClass);
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (! (exp instanceof SecurityException)) {
                fail("SecurityException expected, got " + exp);
            }
        }
    }

    @Test
    public void fakeProxySubclassAccessCheckTest2() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        e.put("name", ScriptEngineSecurityTest.class.getName());
        e.put("cl", ScriptEngineSecurityTest.class.getClassLoader());
        e.put("intfs", new Class[] { Runnable.class });

        final String getClass = "Java.type(name + '$FakeProxy').makeProxyClass(cl, intfs);";

        // Should not be able to call static methods of Proxy via fake subclass
        try {
            e.eval(getClass);
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (! (exp instanceof SecurityException)) {
                fail("SecurityException expected, got " + exp);
            }
        }
    }

    @Test
    public static void proxyStaticAccessCheckTest() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Runnable r = (Runnable)Proxy.newProxyInstance(
            ScriptEngineTest.class.getClassLoader(),
            new Class[] { Runnable.class },
            new InvocationHandler() {
                @Override
                public Object invoke(final Object p, final Method mtd, final Object[] a) {
                    return null;
                }
            });

        e.put("rc", r.getClass());
        e.put("cl", ScriptEngineSecurityTest.class.getClassLoader());
        e.put("intfs", new Class[] { Runnable.class });

        // make sure static methods of Proxy is not accessible via subclass
        try {
            e.eval("rc.static.getProxyClass(cl, intfs)");
            fail("Should have thrown SecurityException");
        } catch (final Exception exp) {
            if (! (exp instanceof SecurityException)) {
                fail("SecurityException expected, got " + exp);
            }
        }
    }


    @Test
    public void nashornConfigSecurityTest() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final NashornScriptEngineFactory fac = new NashornScriptEngineFactory();
        try {
            fac.getScriptEngine(new ClassFilter() {
               @Override
               public boolean exposeToScripts(final String name) {
                   return true;
               }
            });
            fail("SecurityException should have been thrown");
        } catch (final SecurityException e) {
            //empty
        }
    }

    @Test
    public void nashornConfigSecurityTest2() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
            return;
        }

        final NashornScriptEngineFactory fac = new NashornScriptEngineFactory();
        try {
            fac.getScriptEngine(new String[0], null, new ClassFilter() {
               @Override
               public boolean exposeToScripts(final String name) {
                   return true;
               }
            });
            fail("SecurityException should have been thrown");
        } catch (final SecurityException e) {
            //empty
        }
    }
}
