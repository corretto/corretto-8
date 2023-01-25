/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.applet;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.FileDescriptor;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.SocketPermission;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.security.*;
import java.lang.reflect.*;
import sun.awt.AWTSecurityManager;
import sun.awt.AppContext;
import sun.security.provider.*;
import sun.security.util.SecurityConstants;


/**
 * This class defines an applet security policy
 *
 */
public
class AppletSecurity extends AWTSecurityManager {

    //URLClassLoader.acc
    private static Field facc = null;

    //AccessControlContext.context;
    private static Field fcontext = null;

    static {
        try {
            facc = URLClassLoader.class.getDeclaredField("acc");
            facc.setAccessible(true);
            fcontext = AccessControlContext.class.getDeclaredField("context");
            fcontext.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    /**
     * Construct and initialize.
     */
    public AppletSecurity() {
        reset();
    }

    // Cache to store known restricted packages
    private HashSet restrictedPackages = new HashSet();

    /**
     * Reset from Properties
     */
    public void reset()
    {
        // Clear cache
        restrictedPackages.clear();

        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run()
            {
                // Enumerate system properties
                Enumeration e = System.getProperties().propertyNames();

                while (e.hasMoreElements())
                {
                    String name = (String) e.nextElement();

                    if (name != null && name.startsWith("package.restrict.access."))
                    {
                        String value = System.getProperty(name);

                        if (value != null && value.equalsIgnoreCase("true"))
                        {
                            String pkg = name.substring(24);

                            // Cache restricted packages
                            restrictedPackages.add(pkg);
                        }
                    }
                }
                return null;
            }
        });
    }

    /**
     * get the current (first) instance of an AppletClassLoader on the stack.
     */
    private AppletClassLoader currentAppletClassLoader()
    {
        // try currentClassLoader first
        ClassLoader loader = currentClassLoader();

        if ((loader == null) || (loader instanceof AppletClassLoader))
            return (AppletClassLoader)loader;

        // if that fails, get all the classes on the stack and check them.
        Class[] context = getClassContext();
        for (int i = 0; i < context.length; i++) {
            loader = context[i].getClassLoader();
            if (loader instanceof AppletClassLoader)
                return (AppletClassLoader)loader;
        }

        /*
         * fix bug # 6433620 the logic here is : try to find URLClassLoader from
         * class context, check its AccessControlContext to see if
         * AppletClassLoader is in stack when it's created. for this kind of
         * URLClassLoader, return the AppContext associated with the
         * AppletClassLoader.
         */
        for (int i = 0; i < context.length; i++) {
            final ClassLoader currentLoader = context[i].getClassLoader();

            if (currentLoader instanceof URLClassLoader) {
                loader = (ClassLoader) AccessController.doPrivileged(new PrivilegedAction() {
                    public Object run() {

                        AccessControlContext acc = null;
                        ProtectionDomain[] pds = null;

                        try {
                            acc = (AccessControlContext) facc.get(currentLoader);
                            if (acc == null) {
                                return null;
                            }

                            pds = (ProtectionDomain[]) fcontext.get(acc);
                            if (pds == null) {
                                return null;
                            }
                        } catch (Exception e) {
                            throw new UnsupportedOperationException(e);
                        }

                        for (int i=0; i<pds.length; i++) {
                            ClassLoader cl = pds[i].getClassLoader();

                            if (cl instanceof AppletClassLoader) {
                                    return cl;
                            }
                        }

                        return null;
                    }
                });

                if (loader != null) {
                    return (AppletClassLoader) loader;
                }
            }
        }

        // if that fails, try the context class loader
        loader = Thread.currentThread().getContextClassLoader();
        if (loader instanceof AppletClassLoader)
            return (AppletClassLoader)loader;

        // no AppletClassLoaders on the stack
        return (AppletClassLoader)null;
    }

    /**
     * Returns true if this threadgroup is in the applet's own thread
     * group. This will return false if there is no current class
     * loader.
     */
    protected boolean inThreadGroup(ThreadGroup g) {
        if (currentAppletClassLoader() == null)
            return false;
        else
            return getThreadGroup().parentOf(g);
    }

    /**
     * Returns true of the threadgroup of thread is in the applet's
     * own threadgroup.
     */
    protected boolean inThreadGroup(Thread thread) {
        return inThreadGroup(thread.getThreadGroup());
    }

    /**
     * Applets are not allowed to manipulate threads outside
     * applet thread groups. However a terminated thread no longer belongs
     * to any group.
     */
    public void checkAccess(Thread t) {
        /* When multiple applets is reloaded simultaneously, there will be
         * multiple invocations to this method from plugin's SecurityManager.
         * This method should not be synchronized to avoid deadlock when
         * a page with multiple applets is reloaded
         */
        if ((t.getState() != Thread.State.TERMINATED) && !inThreadGroup(t)) {
            checkPermission(SecurityConstants.MODIFY_THREAD_PERMISSION);
        }
    }

    private boolean inThreadGroupCheck = false;

    /**
     * Applets are not allowed to manipulate thread groups outside
     * applet thread groups.
     */
    public synchronized void checkAccess(ThreadGroup g) {
        if (inThreadGroupCheck) {
            // if we are in a recursive check, it is because
            // inThreadGroup is calling appletLoader.getThreadGroup
            // in that case, only do the super check, as appletLoader
            // has a begin/endPrivileged
            checkPermission(SecurityConstants.MODIFY_THREADGROUP_PERMISSION);
        } else {
            try {
                inThreadGroupCheck = true;
                if (!inThreadGroup(g)) {
                    checkPermission(SecurityConstants.MODIFY_THREADGROUP_PERMISSION);
                }
            } finally {
                inThreadGroupCheck = false;
            }
        }
    }


    /**
     * Throws a <code>SecurityException</code> if the
     * calling thread is not allowed to access the package specified by
     * the argument.
     * <p>
     * This method is used by the <code>loadClass</code> method of class
     * loaders.
     * <p>
     * The <code>checkPackageAccess</code> method for class
     * <code>SecurityManager</code>  calls
     * <code>checkPermission</code> with the
     * <code>RuntimePermission("accessClassInPackage."+pkg)</code>
     * permission.
     *
     * @param      pkg   the package name.
     * @exception  SecurityException  if the caller does not have
     *             permission to access the specified package.
     * @see        java.lang.ClassLoader#loadClass(java.lang.String, boolean)
     */
    public void checkPackageAccess(final String pkgname) {

        // first see if the VM-wide policy allows access to this package
        super.checkPackageAccess(pkgname);

        // now check the list of restricted packages
        for (Iterator iter = restrictedPackages.iterator(); iter.hasNext();)
        {
            String pkg = (String) iter.next();

            // Prevent matching "sun" and "sunir" even if they
            // starts with similar beginning characters
            //
            if (pkgname.equals(pkg) || pkgname.startsWith(pkg + "."))
            {
                checkPermission(new java.lang.RuntimePermission
                            ("accessClassInPackage." + pkgname));
            }
        }
    }

    /**
     * Tests if a client can get access to the AWT event queue.
     * <p>
     * This method calls <code>checkPermission</code> with the
     * <code>AWTPermission("accessEventQueue")</code> permission.
     *
     * @since   JDK1.1
     * @exception  SecurityException  if the caller does not have
     *             permission to access the AWT event queue.
     */
    public void checkAwtEventQueueAccess() {
        AppContext appContext = AppContext.getAppContext();
        AppletClassLoader appletClassLoader = currentAppletClassLoader();

        if (AppContext.isMainContext(appContext) && (appletClassLoader != null)) {
            // If we're about to allow access to the main EventQueue,
            // and anything untrusted is on the class context stack,
            // disallow access.
            super.checkPermission(SecurityConstants.AWT.CHECK_AWT_EVENTQUEUE_PERMISSION);
        }
    } // checkAwtEventQueueAccess()

    /**
     * Returns the thread group of the applet. We consult the classloader
     * if there is one.
     */
    public ThreadGroup getThreadGroup() {
        /* If any applet code is on the execution stack, we return
           that applet's ThreadGroup.  Otherwise, we use the default
           behavior. */
        AppletClassLoader appletLoader = currentAppletClassLoader();
        ThreadGroup loaderGroup = (appletLoader == null) ? null
                                          : appletLoader.getThreadGroup();
        if (loaderGroup != null) {
            return loaderGroup;
        } else {
            return super.getThreadGroup();
        }
    } // getThreadGroup()

    /**
      * Get the AppContext corresponding to the current context.
      * The default implementation returns null, but this method
      * may be overridden by various SecurityManagers
      * (e.g. AppletSecurity) to index AppContext objects by the
      * calling context.
      *
      * @return  the AppContext corresponding to the current context.
      * @see     sun.awt.AppContext
      * @see     java.lang.SecurityManager
      * @since   JDK1.2.1
      */
    public AppContext getAppContext() {
        AppletClassLoader appletLoader = currentAppletClassLoader();

        if (appletLoader == null) {
            return null;
        } else {
            AppContext context =  appletLoader.getAppContext();

            // context == null when some thread in applet thread group
            // has not been destroyed in AppContext.dispose()
            if (context == null) {
                throw new SecurityException("Applet classloader has invalid AppContext");
            }

            return context;
        }
    }

} // class AppletSecurity
