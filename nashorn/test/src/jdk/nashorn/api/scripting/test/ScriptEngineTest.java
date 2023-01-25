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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.testng.annotations.Test;

/**
 * Tests for JSR-223 script engine for Nashorn.
 *
 * @test
 * @build jdk.nashorn.api.scripting.test.Window jdk.nashorn.api.scripting.test.WindowEventHandler jdk.nashorn.api.scripting.test.VariableArityTestInterface jdk.nashorn.api.scripting.test.ScriptEngineTest
 * @run testng/othervm jdk.nashorn.api.scripting.test.ScriptEngineTest
 */
@SuppressWarnings("javadoc")
public class ScriptEngineTest {

    private static void log(final String msg) {
        org.testng.Reporter.log(msg, true);
    }

    @Test
    public void argumentsTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        final String[] args = new String[] { "hello", "world" };
        try {
            e.put("arguments", args);
            final Object arg0 = e.eval("arguments[0]");
            final Object arg1 = e.eval("arguments[1]");
            assertEquals(args[0], arg0);
            assertEquals(args[1], arg1);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void argumentsWithTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        final String[] args = new String[] { "hello", "world" };
        try {
            e.put("arguments", args);
            final Object arg0 = e.eval("var imports = new JavaImporter(java.io); " +
                    " with(imports) { arguments[0] }");
            final Object arg1 = e.eval("var imports = new JavaImporter(java.util, java.io); " +
                    " with(imports) { arguments[1] }");
            assertEquals(args[0], arg0);
            assertEquals(args[1], arg1);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void argumentsEmptyTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            assertEquals(e.eval("arguments instanceof Array"), true);
            assertEquals(e.eval("arguments.length == 0"), true);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void factoryTests() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        assertNotNull(e);

        final ScriptEngineFactory fac = e.getFactory();

        assertEquals(fac.getLanguageName(), "ECMAScript");
        assertEquals(fac.getParameter(ScriptEngine.NAME), "javascript");
        assertEquals(fac.getLanguageVersion(), "ECMA - 262 Edition 5.1");
        assertEquals(fac.getEngineName(), "Oracle Nashorn");
        assertEquals(fac.getOutputStatement("context"), "print(context)");
        assertEquals(fac.getProgram("print('hello')", "print('world')"), "print('hello');print('world');");
        assertEquals(fac.getParameter(ScriptEngine.NAME), "javascript");

        boolean seenJS = false;
        for (final String ext : fac.getExtensions()) {
            if (ext.equals("js")) {
                seenJS = true;
            }
        }

        assertEquals(seenJS, true);
        final String str = fac.getMethodCallSyntax("obj", "foo", "x");
        assertEquals(str, "obj.foo(x)");

        boolean seenNashorn = false, seenJavaScript = false, seenECMAScript = false;
        for (final String name : fac.getNames()) {
            switch (name) {
                case "nashorn": seenNashorn = true; break;
                case "javascript": seenJavaScript = true; break;
                case "ECMAScript": seenECMAScript = true; break;
            default:
                break;
            }
        }

        assertTrue(seenNashorn);
        assertTrue(seenJavaScript);
        assertTrue(seenECMAScript);

        boolean seenAppJS = false, seenAppECMA = false, seenTextJS = false, seenTextECMA = false;
        for (final String mime : fac.getMimeTypes()) {
            switch (mime) {
                case "application/javascript": seenAppJS = true; break;
                case "application/ecmascript": seenAppECMA = true; break;
                case "text/javascript": seenTextJS = true; break;
                case "text/ecmascript": seenTextECMA = true; break;
            default:
                break;
            }
        }

        assertTrue(seenAppJS);
        assertTrue(seenAppECMA);
        assertTrue(seenTextJS);
        assertTrue(seenTextECMA);
    }

    @Test
    public void evalTests() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.put(ScriptEngine.FILENAME, "myfile.js");

        try {
            e.eval("print('hello')");
        } catch (final ScriptException se) {
            fail(se.getMessage());
        }
        try {
            e.eval("print('hello)");
            fail("script exception expected");
        } catch (final ScriptException se) {
            assertEquals(se.getLineNumber(), 1);
            assertEquals(se.getColumnNumber(), 13);
            assertEquals(se.getFileName(), "myfile.js");
            // se.printStackTrace();
        }

        try {
            Object obj = e.eval("34 + 41");
            assertTrue(34.0 + 41.0 == ((Number)obj).doubleValue());
            obj = e.eval("x = 5");
            assertTrue(5.0 == ((Number)obj).doubleValue());
        } catch (final ScriptException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }
    }

    @Test
    public void compileTests() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        CompiledScript script = null;

        try {
            script = ((Compilable)e).compile("print('hello')");
        } catch (final ScriptException se) {
            fail(se.getMessage());
        }

        try {
            script.eval();
        } catch (final ScriptException | NullPointerException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }

        // try to compile from a Reader
        try {
            script = ((Compilable)e).compile(new StringReader("print('world')"));
        } catch (final ScriptException se) {
            fail(se.getMessage());
        }

        try {
            script.eval();
        } catch (final ScriptException | NullPointerException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }
    }

    @Test
    public void compileAndEvalInDiffContextTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine = m.getEngineByName("js");
        final Compilable compilable = (Compilable) engine;
        final CompiledScript compiledScript = compilable.compile("foo");
        final ScriptContext ctxt = new SimpleScriptContext();
        ctxt.setAttribute("foo", "hello", ScriptContext.ENGINE_SCOPE);
        assertEquals(compiledScript.eval(ctxt), "hello");
    }

    @Test
    public void accessGlobalTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            e.eval("var x = 'hello'");
            assertEquals(e.get("x"), "hello");
        } catch (final ScriptException exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void exposeGlobalTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            e.put("y", "foo");
            e.eval("print(y)");
        } catch (final ScriptException exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void putGlobalFunctionTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        e.put("callable", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "callable was called";
            }
        });

        try {
            e.eval("print(callable.call())");
        } catch (final ScriptException exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowAlertTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            e.put("window", window);
            e.eval("print(window.alert)");
            e.eval("window.alert('calling window.alert...')");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowLocationTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            e.put("window", window);
            e.eval("print(window.location)");
            final Object locationValue = e.eval("window.getLocation()");
            assertEquals(locationValue, "http://localhost:8080/window");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowItemTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            e.put("window", window);
            final String item1 = (String)e.eval("window.item(65535)");
            assertEquals(item1, "ffff");
            final String item2 = (String)e.eval("window.item(255)");
            assertEquals(item2, "ff");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowEventTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            e.put("window", window);
            e.eval("window.onload = function() { print('window load event fired'); return true }");
            assertTrue((Boolean)e.eval("window.onload.loaded()"));
            final WindowEventHandler handler = window.getOnload();
            assertNotNull(handler);
            assertTrue(handler.loaded());
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void throwTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.put(ScriptEngine.FILENAME, "throwtest.js");

        try {
            e.eval("throw 'foo'");
        } catch (final ScriptException exp) {
            log(exp.getMessage());
            assertEquals(exp.getMessage(), "foo in throwtest.js at line number 1 at column number 0");
            assertEquals(exp.getFileName(), "throwtest.js");
            assertEquals(exp.getLineNumber(), 1);
        }
    }

    @Test
    public void setTimeoutTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            final Class<?> setTimeoutParamTypes[] = { Window.class, String.class, int.class };
            final Method setTimeout = Window.class.getDeclaredMethod("setTimeout", setTimeoutParamTypes);
            assertNotNull(setTimeout);
            e.put("window", window);
            e.eval("window.setTimeout('foo()', 100)");

            // try to make setTimeout global
            e.put("setTimeout", setTimeout);
            // TODO: java.lang.ClassCastException: required class
            // java.lang.Integer but encountered class java.lang.Double
            // e.eval("setTimeout('foo2()', 200)");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void setWriterTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final StringWriter sw = new StringWriter();
        e.getContext().setWriter(sw);

        try {
            e.eval("print('hello world')");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
        assertEquals(sw.toString(), println("hello world"));
    }

    @Test
    public void redefineEchoTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            e.eval("var echo = {}; if (typeof echo !== 'object') { throw 'echo is a '+typeof echo; }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }
    @Test
    public void noEnumerablePropertiesTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("for (i in this) { throw 'found property: ' + i }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void noRefErrorForGlobalThisAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("this.foo");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void refErrorForUndeclaredAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("try { print(foo); throw 'no ref error' } catch (e) { if (!(e instanceof ReferenceError)) throw e; }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void typeErrorForGlobalThisCallTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("try { this.foo() } catch(e) { if (! (e instanceof TypeError)) throw 'no type error' }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void refErrorForUndeclaredCallTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("try { foo() } catch(e) { if (! (e instanceof ReferenceError)) throw 'no ref error' }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    // check that print function prints arg followed by newline char
    public void printTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final StringWriter sw = new StringWriter();
        e.getContext().setWriter(sw);
        try {
            e.eval("print('hello')");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        assertEquals(sw.toString(), println("hello"));
    }

    @Test
    // check that print prints all arguments (more than one)
    public void printManyTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final StringWriter sw = new StringWriter();
        e.getContext().setWriter(sw);
        try {
            e.eval("print(34, true, 'hello')");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        assertEquals(sw.toString(), println("34 true hello"));
    }

    @Test
    public void scriptObjectAutoConversionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.eval("obj = { foo: 'hello' }");
        e.put("Window", e.eval("Packages.jdk.nashorn.api.scripting.test.Window"));
        assertEquals(e.eval("Window.funcJSObject(obj)"), "hello");
        assertEquals(e.eval("Window.funcScriptObjectMirror(obj)"), "hello");
        assertEquals(e.eval("Window.funcMap(obj)"), "hello");
        assertEquals(e.eval("Window.funcJSObject(obj)"), "hello");
    }

    // @bug 8032948: Nashorn linkages awry
    @Test
    public void checkProxyAccess() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final boolean[] reached = new boolean[1];
        final Runnable r = (Runnable)Proxy.newProxyInstance(
            ScriptEngineTest.class.getClassLoader(),
            new Class[] { Runnable.class },
            new InvocationHandler() {
                @Override
                public Object invoke(final Object p, final Method mtd, final Object[] a) {
                    reached[0] = true;
                    return null;
                }
            });

        e.put("r", r);
        e.eval("r.run()");

        assertTrue(reached[0]);
    }

    // properties that can be read by any code
    private static String[] propNames = {
        "java.version",
        "java.vendor",
        "java.vendor.url",
        "java.class.version",
        "os.name",
        "os.version",
        "os.arch",
        "file.separator",
        "path.separator",
        "line.separator",
        "java.specification.version",
        "java.specification.vendor",
        "java.specification.name",
        "java.vm.specification.version",
        "java.vm.specification.vendor",
        "java.vm.specification.name",
        "java.vm.version",
        "java.vm.vendor",
        "java.vm.name"
    };

    // @bug 8033924: Default permissions are not given for eval code
    @Test
    public void checkPropertyReadPermissions() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        for (final String name : propNames) {
            checkProperty(e, name);
        }
    }

    // @bug 8046013: TypeError: Cannot apply "with" to non script object
    @Test
    public void withOnMirrorTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        final Object obj = e.eval("({ foo: 'hello'})");
        final Object[] arr = new Object[1];
        arr[0] = obj;
        e.put("arr", arr);
        final Object res = e.eval("var res; with(arr[0]) { res = foo; }; res");
        assertEquals(res, "hello");
    }

    // @bug 8054223: Nashorn: AssertionError when use __DIR__ and ScriptEngine.eval()
    @Test
    public void check__DIR__Test() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.eval("__DIR__");
    }

    // @bug 8050432:javax.script.filename variable should not be enumerable
    // with nashorn engine's ENGINE_SCOPE bindings
    @Test
    public void enumerableGlobalsTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        e.put(ScriptEngine.FILENAME, "test");
        final Object enumerable = e.eval(
            "Object.getOwnPropertyDescriptor(this, " +
            " 'javax.script.filename').enumerable");
        assertEquals(enumerable, Boolean.FALSE);
    }

    public static class Context {
        private Object myobj;

        public void set(final Object o) {
            myobj = o;
        }

        public Object get() {
            return myobj;
        }
    }

    // @bug 8050977: Java8 Javascript Nashorn exception:
    // no current Global instance for nashorn
    @Test
    public void currentGlobalMissingTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName("nashorn");

        final Context ctx = new Context();
        e.put("ctx", ctx);
        e.eval("var obj = { foo: function(str) { return str.toUpperCase() } }");
        e.eval("ctx.set(obj)");
        final Invocable inv = (Invocable)e;
        assertEquals("HELLO", inv.invokeMethod(ctx.get(), "foo", "hello"));
        // try object literal
        e.eval("ctx.set({ bar: function(str) { return str.toLowerCase() } })");
        assertEquals("hello", inv.invokeMethod(ctx.get(), "bar", "HELLO"));
        // try array literal
        e.eval("var arr = [ 'hello', 'world' ]");
        e.eval("ctx.set(arr)");
        assertEquals("helloworld", inv.invokeMethod(ctx.get(), "join", ""));
    }

    // @bug 8068524: NashornScriptEngineFactory.getParameter() throws IAE
    // for an unknown key, doesn't conform to the general spec
    @Test
    public void getParameterInvalidKeyTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName("nashorn");
        // no exception expected here!
        Object value = e.getFactory().getParameter("no value assigned to this key");
        assertNull(value);
    }

    // @bug JDK-8068889: ConsString arguments to a functional interface wasn't converted to string.
    @Test
    public void functionalInterfaceStringTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName("nashorn");
        final AtomicBoolean invoked = new AtomicBoolean(false);
        e.put("f", new Function<String, String>() {
            @Override
            public String apply(String t) {
                invoked.set(true);
                return t;
            }
        });
        assertEquals(e.eval("var x = 'a'; x += 'b'; f(x)"), "ab");
        assertTrue(invoked.get());
    }

    // @bug JDK-8068889: ScriptObject arguments to a functional interface wasn't converted to a mirror.
    @Test
    public void functionalInterfaceObjectTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName("nashorn");
        final AtomicBoolean invoked = new AtomicBoolean(false);
        e.put("c", new Consumer<Object>() {
            @Override
            public void accept(Object t) {
                assertTrue(t instanceof ScriptObjectMirror);
                assertEquals(((ScriptObjectMirror)t).get("a"), "xyz");
                invoked.set(true);
            }
        });
        e.eval("var x = 'xy'; x += 'z';c({a:x})");
        assertTrue(invoked.get());
    }

    @Test
    public void testLengthOnArrayLikeObjects() throws Exception {
        final ScriptEngine e = new ScriptEngineManager().getEngineByName("nashorn");
        final Object val = e.eval("var arr = { length: 1, 0: 1}; arr.length");

        assertTrue(Number.class.isAssignableFrom(val.getClass()));
        assertTrue(((Number)val).intValue() == 1);
    }

    // @bug JDK-8068603: NashornScriptEngine.put/get() impls don't conform to NPE, IAE spec assertions
    @Test
    public void illegalBindingsValuesTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName("nashorn");

        try {
            e.put(null, "null-value");
            fail();
        } catch (NullPointerException x) {
            // expected
        }

        try {
            e.put("", "empty-value");
            fail();
        } catch (IllegalArgumentException x) {
            // expected
        }

        final Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        assertTrue(b instanceof ScriptObjectMirror);

        try {
            b.put(null, "null-value");
            fail();
        } catch (NullPointerException x) {
            // expected
        }

        try {
            b.put("", "empty-value");
            fail();
        } catch (IllegalArgumentException x) {
            // expected
        }

        try {
            b.get(null);
            fail();
        } catch (NullPointerException x) {
            // expected
        }

        try {
            b.get("");
            fail();
        } catch (IllegalArgumentException x) {
            // expected
        }

        try {
            b.get(1);
            fail();
        } catch (ClassCastException x) {
            // expected
        }

        try {
            b.remove(null);
            fail();
        } catch (NullPointerException x) {
            // expected
        }

        try {
            b.remove("");
            fail();
        } catch (IllegalArgumentException x) {
            // expected
        }

        try {
            b.remove(1);
            fail();
        } catch (ClassCastException x) {
            // expected
        }

        try {
            b.containsKey(null);
            fail();
        } catch (NullPointerException x) {
            // expected
        }

        try {
            b.containsKey("");
            fail();
        } catch (IllegalArgumentException x) {
            // expected
        }

        try {
            b.containsKey(1);
            fail();
        } catch (ClassCastException x) {
            // expected
        }

        try {
            b.putAll(null);
            fail();
        } catch (NullPointerException x) {
            // expected
        }

        try {
            b.putAll(Collections.singletonMap((String)null, "null-value"));
            fail();
        } catch (NullPointerException x) {
            // expected
        }

        try {
            b.putAll(Collections.singletonMap("", "empty-value"));
            fail();
        } catch (IllegalArgumentException x) {
            // expected
        }
    }

    // @bug 8071989: NashornScriptEngine returns javax.script.ScriptContext instance
    // with insonsistent get/remove methods behavior for undefined attributes
    @Test
    public void testScriptContextGetRemoveUndefined() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName("nashorn");
        final ScriptContext ctx = e.getContext();
        assertNull(ctx.getAttribute("undefinedname", ScriptContext.ENGINE_SCOPE));
        assertNull(ctx.removeAttribute("undefinedname", ScriptContext.ENGINE_SCOPE));
    }

    private static void checkProperty(final ScriptEngine e, final String name)
        throws ScriptException {
        final String value = System.getProperty(name);
        e.put("name", name);
        assertEquals(value, e.eval("java.lang.System.getProperty(name)"));
    }

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    // Returns String that would be the result of calling PrintWriter.println
    // of the given String. (This is to handle platform specific newline).
    private static String println(final String str) {
        return str + LINE_SEPARATOR;
    }
}
