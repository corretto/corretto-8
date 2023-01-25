/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.model.wsdl;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.model.ParameterBinding;

import javax.jws.WebParam.Mode;
import javax.jws.soap.SOAPBinding;
import javax.xml.namespace.QName;

/**
 * {@link WSDLPortType} bound with a specific binding.
 *
 * @author Vivek Pandey
 */
public interface WSDLBoundPortType extends WSDLFeaturedObject, WSDLExtensible {
    /**
     * Gets the name of the wsdl:binding@name attribute value as local name and wsdl:definitions@targetNamespace
     * as the namespace uri.
     */
    QName getName();

    /**
     * Gets the {@link WSDLModel} that owns this port type.
     */
    @NotNull WSDLModel getOwner();

    /**
     * Gets the {@link WSDLBoundOperation} for a given operation name
     *
     * @param operationName non-null operationName
     * @return null if a {@link WSDLBoundOperation} is not found
     */
    public WSDLBoundOperation get(QName operationName);

    /**
     * Gets the wsdl:binding@type value, same as {@link WSDLPortType#getName()}
     */
    QName getPortTypeName();

    /**
     * Gets the {@link WSDLPortType} associated with the wsdl:binding
     */
    WSDLPortType getPortType();

    /**
     * Gets the {@link WSDLBoundOperation}s
     */
    Iterable<? extends WSDLBoundOperation> getBindingOperations();

    /**
     * Is this a document style or RPC style?
     *
     * Since we only support literal and not encoding, this means
     * either doc/lit or rpc/lit.
     */
    @NotNull SOAPBinding.Style getStyle();

    /**
     * Returns the binding ID.
     * This would typically determined by the binding extension elements in wsdl:binding.
     */
    BindingID getBindingId();

    /**
     * Gets the bound operation in this port for a tag name. Here the operation would be the one whose
     * input part descriptor bound to soap:body is same as the tag name except for rpclit where the tag
     * name would be {@link WSDLBoundOperation#getName()}.
     *
     * <p>
     * If you have a {@link Message} and trying to figure out which operation it belongs to,
     * always use {@link Message#getOperation}, as that performs better.
     *
     * <p>
     * For example this can be used in the case when a message receipient can get the
     * {@link WSDLBoundOperation} from the payload tag name.
     *
     * <p>
     * namespaceUri and the local name both can be null to get the WSDLBoundOperation that has empty body -
     * there is no payload. According to BP 1.1 in a port there can be at MOST one operation with empty body.
     * Its an error to have namespace URI non-null but local name as null.
     *
     * @param namespaceUri namespace of the payload element.
     * @param localName local name of the payload
     * @throws NullPointerException if localName is null and namespaceUri is not.
     * @return
     *      null if no operation with the given tag name is found.
     */
    @Nullable WSDLBoundOperation getOperation(String namespaceUri, String localName);

    /**
     * Gets the {@link ParameterBinding} for a given operation, part name and the direction - IN/OUT
     *
     * @param operation wsdl:operation@name value. Must be non-null.
     * @param part      wsdl:part@name such as value of soap:header@part. Must be non-null.
     * @param mode      {@link Mode#IN} or {@link Mode#OUT}. Must be non-null.
     * @return null if the binding could not be resolved for the part.
     */
    ParameterBinding getBinding(QName operation, String part, Mode mode);
}
