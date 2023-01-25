/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This package contains Oracle Corporation's platform extension to
 * the implementation of the
 * <a href="{@docRoot}/../../../../api/java/lang/management/package-summary.html">
 * java.lang.management</a> API and also defines the management
 * interface for some other components for the platform.
 *
 * <p>
 * All platform MBeans are registered in the <em>platform MBeanServer</em>
 * which can be obtained via the
 * <a href="{@docRoot}/../../../../api/java/lang/management/ManagementFactory.html#getPlatformMBeanServer()">
 * java.lang.management.ManagementFactory.getPlatformMBeanServer</a>
 *
 * @author  Mandy Chung
 * @since   1.5
 */

@jdk.Exported
package com.sun.management;
