/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

import javax.print.attribute.PrintJobAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.event.PrintJobAttributeListener;
import javax.print.event.PrintJobListener;
import javax.print.PrintException;

/**
 *
 * This interface represents a print job that can print a specified
 * document with a set of job attributes.  An object implementing
 * this interface is obtained from a print service.
 *
 */

public interface DocPrintJob {

    /**
     * Determines the {@link PrintService} object to which this print job
     * object is bound.
     *
     * @return  <code>PrintService</code> object.
     *
     */
    public PrintService getPrintService();

    /**
     * Obtains this Print Job's set of printing attributes.
     * The returned attribute set object is unmodifiable.
     * The returned attribute set object is a "snapshot" of this Print Job's
     * attribute set at the time of the {@link #getAttributes()} method
     * call; that is, the returned attribute set's object's contents will
     * not be updated if this Print Job's attribute set's contents change
     * in the future. To detect changes in attribute values, call
     * <code>getAttributes()</code> again and compare the new attribute
     * set to the previous attribute set; alternatively, register a
     * listener for print job events.
     * The returned value may be an empty set but should not be null.
     * @return the print job attributes
     */
     public PrintJobAttributeSet getAttributes();

    /**
     * Registers a listener for event occurring during this print job.
     * If listener is null, no exception is thrown and no action is
     * performed.
     * If listener is already registered, it will be registered again.
     * @see #removePrintJobListener
     *
     * @param listener  The object implementing the listener interface
     *
     */
    public void addPrintJobListener(PrintJobListener listener);

    /**
     * Removes a listener from this print job.
     * This method performs no function, nor does it throw an exception,
     * if the listener specified by the argument was not previously added
     * to this component. If listener is null, no exception is thrown and
     * no action is performed. If a listener was registered more than once
     * only one of the registrations will be removed.
     * @see #addPrintJobListener
     *
     * @param listener  The object implementing the listener interface
     */
    public void removePrintJobListener(PrintJobListener listener);

    /**
     * Registers a listener for changes in the specified attributes.
     * If listener is null, no exception is thrown and no action is
     * performed.
     * To determine the attribute updates that may be reported by this job,
     * a client can call <code>getAttributes()</code> and identify the
     * subset that are interesting and likely to be reported to the
     * listener. Clients expecting to be updated about changes in a
     * specific job attribute should verify it is in that set, but
     * updates about an attribute will be made only if it changes and this
     * is detected by the job. Also updates may be subject to batching
     * by the job. To minimize overhead in print job processing it is
     * recommended to listen on only that subset of attributes which
     * are likely to change.
     * If the specified set is empty no attribute updates will be reported
     * to the listener.
     * If the attribute set is null, then this means to listen on all
     * dynamic attributes that the job supports. This may result in no
     * update notifications if a job can not report any attribute updates.
     *
     * If listener is already registered, it will be registered again.
     * @see #removePrintJobAttributeListener
     *
     * @param listener  The object implementing the listener interface
     * @param attributes The attributes to listen on, or null to mean
     * all attributes that can change, as determined by the job.
     */
    public void addPrintJobAttributeListener(
                                  PrintJobAttributeListener listener,
                                  PrintJobAttributeSet attributes);

    /**
     * Removes an attribute listener from this print job.
     * This method performs no function, nor does it throw an exception,
     * if the listener specified by the argument was not previously added
     * to this component. If the listener is null, no exception is thrown
     * and no action is performed.
     * If a listener is registered more than once, even for a different
     * set of attributes, no guarantee is made which listener is removed.
     * @see #addPrintJobAttributeListener
     *
     * @param listener  The object implementing the listener interface
     *
     */
    public void removePrintJobAttributeListener(
                                      PrintJobAttributeListener listener);

    /**
     * Prints a document with the specified job attributes.
     * This method should only be called once for a given print job.
     * Calling it again will not result in a new job being spooled to
     * the printer. The service implementation will define policy
     * for service interruption and recovery.
     * When the print method returns, printing may not yet have completed as
     * printing may happen asynchronously, perhaps in a different thread.
     * Application clients which  want to monitor the success or failure
     * should register a PrintJobListener.
     * <p>
     * Print service implementors should close any print data streams (ie
     * Reader or InputStream implementations) that they obtain
     * from the client doc. Robust clients may still wish to verify this.
     * An exception is always generated if a <code>DocFlavor</code> cannot
     * be printed.
     *
     * @param doc       The document to be printed. If must be a flavor
     *                                  supported by this PrintJob.
     *
     * @param attributes The job attributes to be applied to this print job.
     *        If this parameter is null then the default attributes are used.
     * @throws PrintException The exception additionally may implement
     * an interface that more precisely describes the cause of the
     * exception
     * <ul>
     * <li>FlavorException.
     *  If the document has a flavor not supported by this print job.
     * <li>AttributeException.
     *  If one or more of the attributes are not valid for this print job.
     * </ul>
     */
    public void print(Doc doc, PrintRequestAttributeSet attributes)
          throws PrintException;

}
