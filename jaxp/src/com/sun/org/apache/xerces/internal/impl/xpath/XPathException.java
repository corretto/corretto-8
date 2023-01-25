/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001, 2002,2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xerces.internal.impl.xpath;

/**
 * XPath exception.
 *
 * @xerces.internal
 *
 * @author Andy Clark, IBM
 *
 */
public class XPathException
    extends Exception {

    /** Serialization version. */
    static final long serialVersionUID = -948482312169512085L;

    // Data

    // hold the value of the key this Exception refers to.
    private String fKey;
    //
    // Constructors
    //

    /** Constructs an exception. */
    public XPathException() {
        super();
        fKey = "c-general-xpath";
    } // <init>()

    /** Constructs an exception with the specified key. */
    public XPathException(String key) {
        super();
        fKey = key;
    } // <init>(String)

    public String getKey() {
        return fKey;
    } // getKey():  String

} // class XPathException
