/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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


package javax.print;

import java.util.ArrayList;
import java.util.Iterator;
import javax.print.attribute.AttributeSet;

import sun.awt.AppContext;
import java.util.ServiceLoader;
import java.util.ServiceConfigurationError;

/** Implementations of this class provide lookup services for
  * print services (typically equivalent to printers) of a particular type.
  * <p>
  * Multiple implementations may be installed concurrently.
  * All implementations must be able to describe the located printers
  * as instances of a PrintService.
  * Typically implementations of this service class are located
  * automatically in JAR files (see the SPI JAR file specification).
  * These classes must be instantiable using a default constructor.
  * Alternatively applications may explicitly register instances
  * at runtime.
  * <p>
  * Applications use only the static methods of this abstract class.
  * The instance methods are implemented by a service provider in a subclass
  * and the unification of the results from all installed lookup classes
  * are reported by the static methods of this class when called by
  * the application.
  * <p>
  * A PrintServiceLookup implementor is recommended to check for the
  * SecurityManager.checkPrintJobAccess() to deny access to untrusted code.
  * Following this recommended policy means that untrusted code may not
  * be able to locate any print services. Downloaded applets are the most
  * common example of untrusted code.
  * <p>
  * This check is made on a per lookup service basis to allow flexibility in
  * the policy to reflect the needs of different lookup services.
  * <p>
  * Services which are registered by registerService(PrintService)
  * will not be included in lookup results if a security manager is
  * installed and its checkPrintJobAccess() method denies access.
  */

public abstract class PrintServiceLookup {

    static class Services {
        private ArrayList listOfLookupServices = null;
        private ArrayList registeredServices = null;
    }

    private static Services getServicesForContext() {
        Services services =
            (Services)AppContext.getAppContext().get(Services.class);
        if (services == null) {
            services = new Services();
            AppContext.getAppContext().put(Services.class, services);
        }
        return services;
    }

    private static ArrayList getListOfLookupServices() {
        return getServicesForContext().listOfLookupServices;
    }

    private static ArrayList initListOfLookupServices() {
        ArrayList listOfLookupServices = new ArrayList();
        getServicesForContext().listOfLookupServices = listOfLookupServices;
        return listOfLookupServices;
    }


    private static ArrayList getRegisteredServices() {
        return getServicesForContext().registeredServices;
    }

    private static ArrayList initRegisteredServices() {
        ArrayList registeredServices = new ArrayList();
        getServicesForContext().registeredServices = registeredServices;
        return registeredServices;
    }

    /**
     * Locates print services capable of printing the specified
     * {@link DocFlavor}.
     *
     * @param flavor the flavor to print. If null, this constraint is not
     *        used.
     * @param attributes attributes that the print service must support.
     * If null this constraint is not used.
     *
     * @return array of matching <code>PrintService</code> objects
     * representing print services that support the specified flavor
     * attributes.  If no services match, the array is zero-length.
     */
    public static final PrintService[]
        lookupPrintServices(DocFlavor flavor,
                            AttributeSet attributes) {
        ArrayList list = getServices(flavor, attributes);
        return (PrintService[])(list.toArray(new PrintService[list.size()]));
    }


    /**
     * Locates MultiDoc print Services capable of printing MultiDocs
     * containing all the specified doc flavors.
     * <P> This method is useful to help locate a service that can print
     * a <code>MultiDoc</code> in which the elements may be different
     * flavors. An application could perform this itself by multiple lookups
     * on each <code>DocFlavor</code> in turn and collating the results,
     * but the lookup service may be able to do this more efficiently.
     *
     * @param flavors the flavors to print. If null or empty this
     *        constraint is not used.
     * Otherwise return only multidoc print services that can print all
     * specified doc flavors.
     * @param attributes attributes that the print service must
     * support.  If null this constraint is not used.
     *
     * @return array of matching {@link MultiDocPrintService} objects.
     * If no services match, the array is zero-length.
     *
     */
    public static final MultiDocPrintService[]
        lookupMultiDocPrintServices(DocFlavor[] flavors,
                                    AttributeSet attributes) {
        ArrayList list = getMultiDocServices(flavors, attributes);
        return (MultiDocPrintService[])
            list.toArray(new MultiDocPrintService[list.size()]);
    }


    /**
     * Locates the default print service for this environment.
     * This may return null.
     * If multiple lookup services each specify a default, the
     * chosen service is not precisely defined, but a
     * platform native service, rather than an installed service,
     * is usually returned as the default.  If there is no clearly
     * identifiable
     * platform native default print service, the default is the first
     * to be located in an implementation-dependent manner.
     * <p>
     * This may include making use of any preferences API that is available
     * as part of the Java or native platform.
     * This algorithm may be overridden by a user setting the property
     * javax.print.defaultPrinter.
     * A service specified must be discovered to be valid and currently
     * available to be returned as the default.
     *
     * @return the default PrintService.
     */

    public static final PrintService lookupDefaultPrintService() {

        Iterator psIterator = getAllLookupServices().iterator();
        while (psIterator.hasNext()) {
            try {
                PrintServiceLookup lus = (PrintServiceLookup)psIterator.next();
                PrintService service = lus.getDefaultPrintService();
                if (service != null) {
                    return service;
                }
            } catch (Exception e) {
            }
        }
        return null;
    }


    /**
     * Allows an application to explicitly register a class that
     * implements lookup services. The registration will not persist
     * across VM invocations.
     * This is useful if an application needs to make a new service
     * available that is not part of the installation.
     * If the lookup service is already registered, or cannot be registered,
     * the method returns false.
     * <p>
     *
     * @param sp an implementation of a lookup service.
     * @return <code>true</code> if the new lookup service is newly
     *         registered; <code>false</code> otherwise.
     */
    public static boolean registerServiceProvider(PrintServiceLookup sp) {
        synchronized (PrintServiceLookup.class) {
            Iterator psIterator = getAllLookupServices().iterator();
            while (psIterator.hasNext()) {
                try {
                    Object lus = psIterator.next();
                    if (lus.getClass() == sp.getClass()) {
                        return false;
                    }
                } catch (Exception e) {
                }
            }
            getListOfLookupServices().add(sp);
            return true;
        }

    }


    /**
     * Allows an application to directly register an instance of a
     * class which implements a print service.
     * The lookup operations for this service will be
     * performed by the PrintServiceLookup class using the attribute
     * values and classes reported by the service.
     * This may be less efficient than a lookup
     * service tuned for that service.
     * Therefore registering a <code>PrintServiceLookup</code> instance
     * instead is recommended.
     * The method returns true if this service is not previously
     * registered and is now successfully registered.
     * This method should not be called with StreamPrintService instances.
     * They will always fail to register and the method will return false.
     * @param service an implementation of a print service.
     * @return <code>true</code> if the service is newly
     *         registered; <code>false</code> otherwise.
     */

    public static boolean registerService(PrintService service) {
        synchronized (PrintServiceLookup.class) {
            if (service instanceof StreamPrintService) {
                return false;
            }
            ArrayList registeredServices = getRegisteredServices();
            if (registeredServices == null) {
                registeredServices = initRegisteredServices();
            }
            else {
              if (registeredServices.contains(service)) {
                return false;
              }
            }
            registeredServices.add(service);
            return true;
        }
    }


   /**
    * Locates services that can be positively confirmed to support
    * the combination of attributes and DocFlavors specified.
    * This method is not called directly by applications.
    * <p>
    * Implemented by a service provider, used by the static methods
    * of this class.
    * <p>
    * The results should be the same as obtaining all the PrintServices
    * and querying each one individually on its support for the
    * specified attributes and flavors, but the process can be more
    * efficient by taking advantage of the capabilities of lookup services
    * for the print services.
    *
    * @param flavor of document required.  If null it is ignored.
    * @param attributes required to be supported. If null this
    * constraint is not used.
    * @return array of matching PrintServices. If no services match, the
    * array is zero-length.
    */
    public abstract PrintService[] getPrintServices(DocFlavor flavor,
                                                    AttributeSet attributes);

    /**
     * Not called directly by applications.
     * Implemented by a service provider, used by the static methods
     * of this class.
     * @return array of all PrintServices known to this lookup service
     * class. If none are found, the array is zero-length.
     */
    public abstract PrintService[] getPrintServices() ;


   /**
    * Not called directly by applications.
    * <p>
    * Implemented by a service provider, used by the static methods
    * of this class.
    * <p>
    * Locates MultiDoc print services which can be positively confirmed
    * to support the combination of attributes and DocFlavors specified.
    * <p>
    *
    * @param flavors of documents required. If null or empty it is ignored.
    * @param attributes required to be supported. If null this
     * constraint is not used.
    * @return array of matching PrintServices. If no services match, the
    * array is zero-length.
    */
    public abstract MultiDocPrintService[]
        getMultiDocPrintServices(DocFlavor[] flavors,
                                 AttributeSet attributes);

    /**
     * Not called directly by applications.
     * Implemented by a service provider, and called by the print lookup
     * service
     * @return the default PrintService for this lookup service.
     * If there is no default, returns null.
     */
    public abstract PrintService getDefaultPrintService();

    private static ArrayList getAllLookupServices() {
        synchronized (PrintServiceLookup.class) {
            ArrayList listOfLookupServices = getListOfLookupServices();
            if (listOfLookupServices != null) {
                return listOfLookupServices;
            } else {
                listOfLookupServices = initListOfLookupServices();
            }
            try {
                java.security.AccessController.doPrivileged(
                     new java.security.PrivilegedExceptionAction() {
                        public Object run() {
                            Iterator<PrintServiceLookup> iterator =
                                ServiceLoader.load(PrintServiceLookup.class).
                                iterator();
                            ArrayList los = getListOfLookupServices();
                            while (iterator.hasNext()) {
                                try {
                                    los.add(iterator.next());
                                }  catch (ServiceConfigurationError err) {
                                    /* In the applet case, we continue */
                                    if (System.getSecurityManager() != null) {
                                        err.printStackTrace();
                                    } else {
                                        throw err;
                                    }
                                }
                            }
                            return null;
                        }
                });
            } catch (java.security.PrivilegedActionException e) {
            }

            return listOfLookupServices;
        }
    }

    private static ArrayList getServices(DocFlavor flavor,
                                         AttributeSet attributes) {

        ArrayList listOfServices = new ArrayList();
        Iterator psIterator = getAllLookupServices().iterator();
        while (psIterator.hasNext()) {
            try {
                PrintServiceLookup lus = (PrintServiceLookup)psIterator.next();
                PrintService[] services=null;
                if (flavor == null && attributes == null) {
                    try {
                    services = lus.getPrintServices();
                    } catch (Throwable tr) {
                    }
                } else {
                    services = lus.getPrintServices(flavor, attributes);
                }
                if (services == null) {
                    continue;
                }
                for (int i=0; i<services.length; i++) {
                    listOfServices.add(services[i]);
                }
            } catch (Exception e) {
            }
        }
        /* add any directly registered services */
        ArrayList registeredServices = null;
        try {
          SecurityManager security = System.getSecurityManager();
          if (security != null) {
            security.checkPrintJobAccess();
          }
          registeredServices = getRegisteredServices();
        } catch (SecurityException se) {
        }
        if (registeredServices != null) {
            PrintService[] services = (PrintService[])
                registeredServices.toArray(
                           new PrintService[registeredServices.size()]);
            for (int i=0; i<services.length; i++) {
                if (!listOfServices.contains(services[i])) {
                    if (flavor == null && attributes == null) {
                        listOfServices.add(services[i]);
                    } else if (((flavor != null &&
                                 services[i].isDocFlavorSupported(flavor)) ||
                                flavor == null) &&
                               null == services[i].getUnsupportedAttributes(
                                                      flavor, attributes)) {
                        listOfServices.add(services[i]);
                    }
                }
            }
        }
        return listOfServices;
    }

    private static ArrayList getMultiDocServices(DocFlavor[] flavors,
                                                 AttributeSet attributes) {


        ArrayList listOfServices = new ArrayList();
        Iterator psIterator = getAllLookupServices().iterator();
        while (psIterator.hasNext()) {
            try {
                PrintServiceLookup lus = (PrintServiceLookup)psIterator.next();
                MultiDocPrintService[] services  =
                    lus.getMultiDocPrintServices(flavors, attributes);
                if (services == null) {
                    continue;
                }
                for (int i=0; i<services.length; i++) {
                    listOfServices.add(services[i]);
                }
            } catch (Exception e) {
            }
        }
        /* add any directly registered services */
        ArrayList registeredServices = null;
        try {
          SecurityManager security = System.getSecurityManager();
          if (security != null) {
            security.checkPrintJobAccess();
          }
          registeredServices = getRegisteredServices();
        } catch (Exception e) {
        }
        if (registeredServices != null) {
            PrintService[] services = (PrintService[])
                registeredServices.toArray(
                           new PrintService[registeredServices.size()]);
            for (int i=0; i<services.length; i++) {
                if (services[i] instanceof MultiDocPrintService &&
                    !listOfServices.contains(services[i])) {
                    if (flavors == null || flavors.length == 0) {
                        listOfServices.add(services[i]);
                    } else {
                        boolean supported = true;
                        for (int f=0; f<flavors.length; f++) {
                            if (services[i].isDocFlavorSupported(flavors[f])) {

                                if (services[i].getUnsupportedAttributes(
                                     flavors[f], attributes) != null) {
                                        supported = false;
                                        break;
                                }
                            } else {
                                supported = false;
                                break;
                            }
                        }
                        if (supported) {
                            listOfServices.add(services[i]);
                        }
                    }
                }
            }
        }
        return listOfServices;
    }

}
