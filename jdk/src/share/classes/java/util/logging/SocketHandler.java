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


package java.util.logging;

import java.io.*;
import java.net.*;

/**
 * Simple network logging <tt>Handler</tt>.
 * <p>
 * <tt>LogRecords</tt> are published to a network stream connection.  By default
 * the <tt>XMLFormatter</tt> class is used for formatting.
 * <p>
 * <b>Configuration:</b>
 * By default each <tt>SocketHandler</tt> is initialized using the following
 * <tt>LogManager</tt> configuration properties where <tt>&lt;handler-name&gt;</tt>
 * refers to the fully-qualified class name of the handler.
 * If properties are not defined
 * (or have invalid values) then the specified default values are used.
 * <ul>
 * <li>   &lt;handler-name&gt;.level
 *        specifies the default level for the <tt>Handler</tt>
 *        (defaults to <tt>Level.ALL</tt>). </li>
 * <li>   &lt;handler-name&gt;.filter
 *        specifies the name of a <tt>Filter</tt> class to use
 *        (defaults to no <tt>Filter</tt>). </li>
 * <li>   &lt;handler-name&gt;.formatter
 *        specifies the name of a <tt>Formatter</tt> class to use
 *        (defaults to <tt>java.util.logging.XMLFormatter</tt>). </li>
 * <li>   &lt;handler-name&gt;.encoding
 *        the name of the character set encoding to use (defaults to
 *        the default platform encoding). </li>
 * <li>   &lt;handler-name&gt;.host
 *        specifies the target host name to connect to (no default). </li>
 * <li>   &lt;handler-name&gt;.port
 *        specifies the target TCP port to use (no default). </li>
 * </ul>
 * <p>
 * For example, the properties for {@code SocketHandler} would be:
 * <ul>
 * <li>   java.util.logging.SocketHandler.level=INFO </li>
 * <li>   java.util.logging.SocketHandler.formatter=java.util.logging.SimpleFormatter </li>
 * </ul>
 * <p>
 * For a custom handler, e.g. com.foo.MyHandler, the properties would be:
 * <ul>
 * <li>   com.foo.MyHandler.level=INFO </li>
 * <li>   com.foo.MyHandler.formatter=java.util.logging.SimpleFormatter </li>
 * </ul>
 * <p>
 * The output IO stream is buffered, but is flushed after each
 * <tt>LogRecord</tt> is written.
 *
 * @since 1.4
 */

public class SocketHandler extends StreamHandler {
    private Socket sock;
    private String host;
    private int port;

    // Private method to configure a SocketHandler from LogManager
    // properties and/or default values as specified in the class
    // javadoc.
    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String cname = getClass().getName();

        setLevel(manager.getLevelProperty(cname +".level", Level.ALL));
        setFilter(manager.getFilterProperty(cname +".filter", null));
        setFormatter(manager.getFormatterProperty(cname +".formatter", new XMLFormatter()));
        try {
            setEncoding(manager.getStringProperty(cname +".encoding", null));
        } catch (Exception ex) {
            try {
                setEncoding(null);
            } catch (Exception ex2) {
                // doing a setEncoding with null should always work.
                // assert false;
            }
        }
        port = manager.getIntProperty(cname + ".port", 0);
        host = manager.getStringProperty(cname + ".host", null);
    }


    /**
     * Create a <tt>SocketHandler</tt>, using only <tt>LogManager</tt> properties
     * (or their defaults).
     * @throws IllegalArgumentException if the host or port are invalid or
     *          are not specified as LogManager properties.
     * @throws IOException if we are unable to connect to the target
     *         host and port.
     */
    public SocketHandler() throws IOException {
        // We are going to use the logging defaults.
        sealed = false;
        configure();

        try {
            connect();
        } catch (IOException ix) {
            System.err.println("SocketHandler: connect failed to " + host + ":" + port);
            throw ix;
        }
        sealed = true;
    }

    /**
     * Construct a <tt>SocketHandler</tt> using a specified host and port.
     *
     * The <tt>SocketHandler</tt> is configured based on <tt>LogManager</tt>
     * properties (or their default values) except that the given target host
     * and port arguments are used. If the host argument is empty, but not
     * null String then the localhost is used.
     *
     * @param host target host.
     * @param port target port.
     *
     * @throws IllegalArgumentException if the host or port are invalid.
     * @throws IOException if we are unable to connect to the target
     *         host and port.
     */
    public SocketHandler(String host, int port) throws IOException {
        sealed = false;
        configure();
        sealed = true;
        this.port = port;
        this.host = host;
        connect();
    }

    private void connect() throws IOException {
        // Check the arguments are valid.
        if (port == 0) {
            throw new IllegalArgumentException("Bad port: " + port);
        }
        if (host == null) {
            throw new IllegalArgumentException("Null host name: " + host);
        }

        // Try to open a new socket.
        sock = new Socket(host, port);
        OutputStream out = sock.getOutputStream();
        BufferedOutputStream bout = new BufferedOutputStream(out);
        setOutputStream(bout);
    }

    /**
     * Close this output stream.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    @Override
    public synchronized void close() throws SecurityException {
        super.close();
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException ix) {
                // drop through.
            }
        }
        sock = null;
    }

    /**
     * Format and publish a <tt>LogRecord</tt>.
     *
     * @param  record  description of the log event. A null record is
     *                 silently ignored and is not published
     */
    @Override
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        super.publish(record);
        flush();
    }
}
