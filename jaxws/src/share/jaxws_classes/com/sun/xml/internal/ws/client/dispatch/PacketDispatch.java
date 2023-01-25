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

package com.sun.xml.internal.ws.client.dispatch;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.client.ThrowableInPacketCompletionFeature;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.WSServiceDelegate;

import javax.xml.namespace.QName;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service.Mode;

/**
 * {@link Dispatch} implementation for {@link Packet}.
 *
 * @since 2.2.6
 */
public class PacketDispatch extends DispatchImpl<Packet> {
    private final boolean isDeliverThrowableInPacket;

    @Deprecated
    public PacketDispatch(QName port, WSServiceDelegate owner, Tube pipe, BindingImpl binding, @Nullable WSEndpointReference epr) {
        super(port, Mode.MESSAGE, owner, pipe, binding, epr);
        isDeliverThrowableInPacket = calculateIsDeliverThrowableInPacket(binding);
    }


    public PacketDispatch(WSPortInfo portInfo, Tube pipe, BindingImpl binding, WSEndpointReference epr) {
        this(portInfo, pipe, binding, epr, true);
    }

    public PacketDispatch(WSPortInfo portInfo, Tube pipe, BindingImpl binding, WSEndpointReference epr, boolean allowFaultResponseMsg) {
        super(portInfo, Mode.MESSAGE, pipe, binding, epr, allowFaultResponseMsg);
        isDeliverThrowableInPacket = calculateIsDeliverThrowableInPacket(binding);
    }

    public PacketDispatch(WSPortInfo portInfo, BindingImpl binding, WSEndpointReference epr) {
        super(portInfo, Mode.MESSAGE, binding, epr, true);
        isDeliverThrowableInPacket = calculateIsDeliverThrowableInPacket(binding);
    }

    private boolean calculateIsDeliverThrowableInPacket(BindingImpl binding) {
        return binding.isFeatureEnabled(ThrowableInPacketCompletionFeature.class);
    }

    @Override
    protected void configureFiber(Fiber fiber) {
        fiber.setDeliverThrowableInPacket(isDeliverThrowableInPacket);
    }

    @Override
    Packet toReturnValue(Packet response) {
        return response;
    }

    @Override
    Packet createPacket(Packet request) {
        return request;
    }


}
