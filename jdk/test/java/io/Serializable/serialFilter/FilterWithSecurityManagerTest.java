/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.security.AccessControlException;

import sun.misc.ObjectInputFilter;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/* @test
 * @build FilterWithSecurityManagerTest SerialFilterTest
 * @run testng/othervm FilterWithSecurityManagerTest
 * @run testng/othervm/policy=security.policy.without.globalFilter
 *          -Djava.security.manager=default FilterWithSecurityManagerTest
 * @run testng/othervm/policy=security.policy
 *          -Djava.security.manager=default
 *          -Djdk.serialFilter=java.lang.Integer FilterWithSecurityManagerTest
 *
 * @summary Test that setting specific filter is checked by security manager,
 *          setting process-wide filter is checked by security manager.
 */

@Test
public class FilterWithSecurityManagerTest {

    byte[] bytes;
    boolean setSecurityManager;
    ObjectInputFilter filter;

    @BeforeClass
    public void setup() throws Exception {
        setSecurityManager = System.getSecurityManager() != null;
        Object toDeserialized = Long.MAX_VALUE;
        bytes = SerialFilterTest.writeObjects(toDeserialized);
        filter = ObjectInputFilter.Config.createFilter("java.lang.Long");
    }

    /**
     * Test that setting process-wide filter is checked by security manager.
     */
    @Test
    public void testGlobalFilter() throws Exception {
        if (ObjectInputFilter.Config.getSerialFilter() == null) {
            return;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bais)) {
            ObjectInputFilter.Config.setSerialFilter(filter);
            assertFalse(setSecurityManager,
                    "When SecurityManager exists, without "
                    + "java.security.SerializablePermission(serialFilter) Exception should be thrown");
            Object o = ois.readObject();
        } catch (AccessControlException ex) {
            assertTrue(setSecurityManager);
            assertTrue(ex.getMessage().contains("java.io.SerializablePermission"));
            assertTrue(ex.getMessage().contains("serialFilter"));
        }
    }

    /**
     * Test that setting specific filter is checked by security manager.
     */
    @Test(dependsOnMethods = { "testGlobalFilter" })
    public void testSpecificFilter() throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bais)) {
            ObjectInputFilter.Config.setObjectInputFilter(ois, filter);
            Object o = ois.readObject();
        } catch (AccessControlException ex) {
            assertTrue(setSecurityManager);
            assertTrue(ex.getMessage().contains("java.io.SerializablePermission"));
            assertTrue(ex.getMessage().contains("serialFilter"));
        }
    }
}
