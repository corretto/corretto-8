/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.util;


import org.xml.sax.SAXException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;


/**
 * Pool of SAXParser objects
 */
public class ParserPool {
    private final BlockingQueue queue;
    private SAXParserFactory factory;
    private int capacity;

    public ParserPool(int capacity) {
        this.capacity = capacity;
        queue = new ArrayBlockingQueue(capacity);
        //factory = SAXParserFactory.newInstance();
        factory = new com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl();
        factory.setNamespaceAware(true);
        for (int i=0; i < capacity; i++) {
           try {
                queue.put(factory.newSAXParser());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            } catch (ParserConfigurationException ex) {
                throw new RuntimeException(ex);
            } catch (SAXException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public SAXParser get() throws ParserConfigurationException,
                SAXException {

        try {
            return (SAXParser) queue.take();
        } catch (InterruptedException ex) {
            throw new SAXException(ex);
        }

    }

    public void put(SAXParser parser) {
        queue.offer(parser);
    }

    public void returnParser(SAXParser saxParser) {
        saxParser.reset();
        resetSaxParser(saxParser);
        put(saxParser);
    }


    /**
     * SAAJ Issue 46 :https://saaj.dev.java.net/issues/show_bug.cgi?id=46
     * Xerces does not provide a way to reset the SymbolTable
     * So we are trying to reset it using the proprietary code below.
     * Temporary Until the bug : https://jaxp.dev.java.net/issues/show_bug.cgi?id=59
     * is fixed.
     * @param parser the parser from the pool whose Symbol Table needs to be reset.
     */
     private void resetSaxParser(SAXParser parser) {
        try {
            //Object obj = parser.getProperty("http://apache.org/xml/properties/internal/symbol-table");
            com.sun.org.apache.xerces.internal.util.SymbolTable table = new com.sun.org.apache.xerces.internal.util.SymbolTable();
            parser.setProperty("http://apache.org/xml/properties/internal/symbol-table", table);
            //obj = parser.getProperty("http://apache.org/xml/properties/internal/symbol-table");
        } catch (SAXNotRecognizedException ex) {
            //nothing to do
        } catch (SAXNotSupportedException ex) {
            //nothing to do
        }
    }

}
