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

package com.sun.xml.internal.ws.server.sei;

import com.oracle.webservices.internal.api.databinding.JavaCallInfo;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.databinding.EndpointCallBridge;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.MessageContextFactory;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.ws.message.jaxb.JAXBMessage;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import com.sun.xml.internal.ws.model.ParameterImpl;
import com.sun.xml.internal.ws.model.WrapperParameter;
import com.sun.xml.internal.ws.wsdl.DispatchException;

import javax.jws.WebParam.Mode;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.Holder;
import javax.xml.ws.ProtocolException;
import javax.xml.ws.WebServiceException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * <p>
 * This class mainly performs the following two tasks:
 * <ol>
 *  <li>Takes a {@link Message} that represents a request,
 *      and extracts the arguments (and updates {@link Holder}s.)
 *  <li>Accepts return value and {@link Holder} arguments for a Java method,
 *      and creates {@link JAXBMessage} that represents a response message.
 * </ol>
 *
 * <h2>Creating {@link JAXBMessage}</h2>
 * <p>
 * At the construction time, we prepare {@link EndpointArgumentsBuilder} that knows how to create endpoint {@link Method}
 * invocation arguments.
 * we also prepare {@link EndpointResponseMessageBuilder} and {@link MessageFiller}s
 * that know how to move arguments into a {@link Message}.
 * Some arguments go to the payload, some go to headers, still others go to attachments.
 *
 * @author Jitendra Kotamraju
 * @author shih-chang.chen@oracle.com
 *                 Refactored from EndpointMethodHandler
 */
final public class TieHandler implements EndpointCallBridge {

    private final SOAPVersion soapVersion;
    private final Method method;
    private final int noOfArgs;
    private final JavaMethodImpl javaMethodModel;

     private final Boolean isOneWay;

    // Converts {@link Message} --> Object[]
    private final EndpointArgumentsBuilder argumentsBuilder;

    // these objects together create a response message from method parameters
    private final EndpointResponseMessageBuilder bodyBuilder;
    private final MessageFiller[] outFillers;
    protected MessageContextFactory packetFactory;

    public TieHandler(JavaMethodImpl method, WSBinding binding, MessageContextFactory mcf) {
        this.soapVersion = binding.getSOAPVersion();
        this.method = method.getMethod();
        this.javaMethodModel = method;
        argumentsBuilder = createArgumentsBuilder();
        List<MessageFiller> fillers = new ArrayList<MessageFiller>();
        bodyBuilder = createResponseMessageBuilder(fillers);
        this.outFillers = fillers.toArray(new MessageFiller[fillers.size()]);
        this.isOneWay = method.getMEP().isOneWay();
        this.noOfArgs = this.method.getParameterTypes().length;
        packetFactory = mcf;
    }

    /**
     * It builds EndpointArgumentsBuilder which converts request {@link Message} to endpoint method's invocation
     * arguments Object[]
     *
     * @return EndpointArgumentsBuilder
     */
    private EndpointArgumentsBuilder createArgumentsBuilder() {
        EndpointArgumentsBuilder argsBuilder;
        List<ParameterImpl> rp = javaMethodModel.getRequestParameters();
        List<EndpointArgumentsBuilder> builders = new ArrayList<EndpointArgumentsBuilder>();

        for( ParameterImpl param : rp ) {
            EndpointValueSetter setter = EndpointValueSetter.get(param);
            switch(param.getInBinding().kind) {
            case BODY:
                if(param.isWrapperStyle()) {
                    if(param.getParent().getBinding().isRpcLit())
                        builders.add(new EndpointArgumentsBuilder.RpcLit((WrapperParameter)param));
                    else
                        builders.add(new EndpointArgumentsBuilder.DocLit((WrapperParameter)param, Mode.OUT));
                } else {
                    builders.add(new EndpointArgumentsBuilder.Body(param.getXMLBridge(),setter));
                }
                break;
            case HEADER:
                builders.add(new EndpointArgumentsBuilder.Header(soapVersion, param, setter));
                break;
            case ATTACHMENT:
                builders.add(EndpointArgumentsBuilder.AttachmentBuilder.createAttachmentBuilder(param, setter));
                break;
            case UNBOUND:
                builders.add(new EndpointArgumentsBuilder.NullSetter(setter,
                    EndpointArgumentsBuilder.getVMUninitializedValue(param.getTypeInfo().type)));
                break;
            default:
                throw new AssertionError();
            }
        }

        // creates {@link Holder} arguments for OUT parameters
        List<ParameterImpl> resp = javaMethodModel.getResponseParameters();
        for( ParameterImpl param : resp ) {
            if (param.isWrapperStyle()) {
                WrapperParameter wp = (WrapperParameter)param;
                List<ParameterImpl> children = wp.getWrapperChildren();
                for (ParameterImpl p : children) {
                    if (p.isOUT() && p.getIndex() != -1) {
                        EndpointValueSetter setter = EndpointValueSetter.get(p);
                        builders.add(new EndpointArgumentsBuilder.NullSetter(setter, null));
                    }
                }
            } else if (param.isOUT() && param.getIndex() != -1) {
                EndpointValueSetter setter = EndpointValueSetter.get(param);
                builders.add(new EndpointArgumentsBuilder.NullSetter(setter, null));
            }
        }

        switch(builders.size()) {
        case 0:
            argsBuilder = EndpointArgumentsBuilder.NONE;
            break;
        case 1:
            argsBuilder = builders.get(0);
            break;
        default:
            argsBuilder = new EndpointArgumentsBuilder.Composite(builders);
        }
        return argsBuilder;
    }

    /**
    * prepare objects for creating response {@link Message}
    */
    private EndpointResponseMessageBuilder createResponseMessageBuilder(List<MessageFiller> fillers) {

        EndpointResponseMessageBuilder tmpBodyBuilder = null;
        List<ParameterImpl> rp = javaMethodModel.getResponseParameters();

        for (ParameterImpl param : rp) {
            ValueGetter getter = ValueGetter.get(param);

            switch(param.getOutBinding().kind) {
            case BODY:
                if(param.isWrapperStyle()) {
                    if(param.getParent().getBinding().isRpcLit()) {
                        tmpBodyBuilder = new EndpointResponseMessageBuilder.RpcLit((WrapperParameter)param,
                            soapVersion);
                    } else {
                        tmpBodyBuilder = new EndpointResponseMessageBuilder.DocLit((WrapperParameter)param,
                            soapVersion);
                    }
                } else {
                    tmpBodyBuilder = new EndpointResponseMessageBuilder.Bare(param, soapVersion);
                }
                break;
            case HEADER:
                fillers.add(new MessageFiller.Header(param.getIndex(), param.getXMLBridge(), getter ));
                break;
            case ATTACHMENT:
                fillers.add(MessageFiller.AttachmentFiller.createAttachmentFiller(param, getter));
                break;
            case UNBOUND:
                break;
            default:
                throw new AssertionError(); // impossible
            }
        }

        if (tmpBodyBuilder == null) {
            // no parameter binds to body. we create an empty message
            switch(soapVersion) {
            case SOAP_11:
                tmpBodyBuilder = EndpointResponseMessageBuilder.EMPTY_SOAP11;
                break;
            case SOAP_12:
                tmpBodyBuilder = EndpointResponseMessageBuilder.EMPTY_SOAP12;
                break;
            default:
                throw new AssertionError();
            }
        }
        return tmpBodyBuilder;
    }

    public Object[] readRequest(Message reqMsg) {
        Object[] args = new Object[noOfArgs];
        try {
            argumentsBuilder.readRequest(reqMsg,args);
        } catch (JAXBException e) {
            throw new WebServiceException(e);
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        }
        return args;
    }

    public Message createResponse(JavaCallInfo call) {
        Message responseMessage;
        if (call.getException() == null) {
            responseMessage = isOneWay ? null : createResponseMessage(call.getParameters(), call.getReturnValue());
        } else {
            Throwable e = call.getException();
            Throwable serviceException = getServiceException(e);
            if (e instanceof InvocationTargetException || serviceException != null) {
//              Throwable cause = e.getCause();
              //if (!(cause instanceof RuntimeException) && cause instanceof Exception) {
                if (serviceException != null) {
                    // Service specific exception
                    LOGGER.log(Level.FINE, serviceException.getMessage(), serviceException);
                    responseMessage = SOAPFaultBuilder.createSOAPFaultMessage(soapVersion,
                            javaMethodModel.getCheckedException(serviceException.getClass()), serviceException);
                } else {
                    Throwable cause = e.getCause();
                    if (cause instanceof ProtocolException) {
                        // Application code may be throwing it intentionally
                        LOGGER.log(Level.FINE, cause.getMessage(), cause);
                    } else {
                        // Probably some bug in application code
                        LOGGER.log(Level.SEVERE, cause.getMessage(), cause);
                    }
                    responseMessage = SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, cause);
                }
            } else if (e instanceof DispatchException) {
                responseMessage = ((DispatchException)e).fault;
            } else {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                responseMessage = SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, e);
            }
        }
//        return req.createServerResponse(responseMessage, req.endpoint.getPort(), javaMethodModel.getOwner(), req.endpoint.getBinding());

        return responseMessage;
    }

    Throwable getServiceException(Throwable throwable) {
        if (javaMethodModel.getCheckedException(throwable.getClass()) != null) return throwable;
        if (throwable.getCause() != null) {
            Throwable cause = throwable.getCause();
//            if (!(cause instanceof RuntimeException) && cause instanceof Exception) {
             if (javaMethodModel.getCheckedException(cause.getClass()) != null) return cause;
//            }
//            if (javaMethodModel.getCheckedException(cause.getClass()) != null) return cause;
        }
        return null;
    }

    /**
     * Creates a response {@link JAXBMessage} from method arguments, return value
     *
     * @return response message
     */
    private Message createResponseMessage(Object[] args, Object returnValue) {
        Message msg = bodyBuilder.createMessage(args, returnValue);

        for (MessageFiller filler : outFillers)
            filler.fillIn(args, returnValue, msg);

        return msg;
    }

    public Method getMethod() {
        return method;
    }

    private static final Logger LOGGER = Logger.getLogger(TieHandler.class.getName());

    @Override
        public JavaCallInfo deserializeRequest(Packet req) {
        com.sun.xml.internal.ws.api.databinding.JavaCallInfo call = new com.sun.xml.internal.ws.api.databinding.JavaCallInfo();
                call.setMethod(this.getMethod());
        Object[] args = this.readRequest(req.getMessage());
                call.setParameters(args);
                return call;
        }

    @Override
        public Packet serializeResponse(JavaCallInfo call) {
            Message msg = this.createResponse(call);
            Packet p = (msg == null) ? (Packet)packetFactory.createContext() : (Packet)packetFactory.createContext(msg);
            p.setState(Packet.State.ServerResponse);
            return p;
        }

    @Override
    public JavaMethod getOperationModel() {
        return javaMethodModel;
    }
}
