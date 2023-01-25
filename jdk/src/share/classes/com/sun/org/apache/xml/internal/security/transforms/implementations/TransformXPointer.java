/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.transforms.implementations;

import java.io.OutputStream;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.transforms.Transform;
import com.sun.org.apache.xml.internal.security.transforms.TransformSpi;
import com.sun.org.apache.xml.internal.security.transforms.TransformationException;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;

/**
 * Class TransformXPointer
 *
 */
public class TransformXPointer extends TransformSpi {

    /** Field implementedTransformURI */
    public static final String implementedTransformURI =
        Transforms.TRANSFORM_XPOINTER;


    /** {@inheritDoc} */
    protected String engineGetURI() {
        return implementedTransformURI;
    }

    /**
     * Method enginePerformTransform
     *
     * @param input
     * @return  {@link XMLSignatureInput} as the result of transformation
     * @throws TransformationException
     */
    protected XMLSignatureInput enginePerformTransform(
        XMLSignatureInput input, OutputStream os, Transform transformObject
    ) throws TransformationException {

        Object exArgs[] = { implementedTransformURI };

        throw new TransformationException("signature.Transform.NotYetImplemented", exArgs);
    }
}
