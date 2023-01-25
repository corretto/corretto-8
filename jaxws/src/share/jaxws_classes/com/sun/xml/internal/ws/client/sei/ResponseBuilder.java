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

package com.sun.xml.internal.ws.client.sei;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.message.AttachmentUnmarshallerImpl;
import com.sun.xml.internal.ws.model.ParameterImpl;
import com.sun.xml.internal.ws.model.WrapperParameter;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.spi.db.RepeatedElementBridge;
import com.sun.xml.internal.ws.spi.db.XMLBridge;
import com.sun.xml.internal.ws.spi.db.DatabindingException;
import com.sun.xml.internal.ws.spi.db.PropertyAccessor;
import com.sun.xml.internal.ws.spi.db.WrapperComposite;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.encoding.StringDataContentHandler;
import com.sun.xml.internal.ws.encoding.DataHandlerDataSource;

import javax.activation.DataHandler;
import javax.imageio.ImageIO;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.transform.Source;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Reads a response {@link Message}, disassembles it, and moves obtained Java values
 * to the expected places.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public abstract class ResponseBuilder {
    /**
     * Reads a response {@link Message}, disassembles it, and moves obtained Java values
     * to the expected places.
     *
     * @param reply
     *      The reply {@link Message} to be de-composed.
     * @param args
     *      The Java arguments given to the SEI method invocation.
     *      Some parts of the reply message may be set to {@link Holder}s in the arguments.
     * @return
     *      If a part of the reply message is returned as a return value from
     *      the SEI method, this method returns that value. Otherwise null.
     * @throws JAXBException
     *      if there's an error during unmarshalling the reply message.
     * @throws XMLStreamException
     *      if there's an error during unmarshalling the reply message.
     */
    public abstract Object readResponse(Message reply, Object[] args) throws JAXBException, XMLStreamException;

    static final class WrappedPartBuilder {
        private final XMLBridge bridge;
        private final ValueSetter setter;
        public WrappedPartBuilder(XMLBridge bridge, ValueSetter setter) {
            this.bridge = bridge;
            this.setter = setter;
        }
        final Object readResponse(Object[] args, XMLStreamReader r, AttachmentSet att) throws JAXBException {
            Object obj;
            AttachmentUnmarshallerImpl au = (att != null)?new AttachmentUnmarshallerImpl(att):null;
            if (bridge instanceof RepeatedElementBridge) {
                RepeatedElementBridge rbridge = (RepeatedElementBridge)bridge;
                ArrayList list = new ArrayList();
                QName name = r.getName();
                while (r.getEventType()==XMLStreamReader.START_ELEMENT && name.equals(r.getName())) {
                    list.add(rbridge.unmarshal(r, au));
                    XMLStreamReaderUtil.toNextTag(r, name);
                }
                obj = rbridge.collectionHandler().convert(list);
            } else {
                obj = bridge.unmarshal(r, au);
            }
            return setter.put(obj,args);
        }
    }
    /**
     * {@link ResponseBuilder.PartBuilder} keyed by the element name (inside the wrapper element.)
     */
    protected Map<QName,WrappedPartBuilder> wrappedParts = null;
    protected QName wrapperName;

    protected Object readWrappedResponse(Message msg, Object[] args) throws JAXBException, XMLStreamException {
        Object retVal = null;

        if (!msg.hasPayload()) {
            throw new WebServiceException("No payload. Expecting payload with "+wrapperName+" element");
        }
        XMLStreamReader reader = msg.readPayload();
        XMLStreamReaderUtil.verifyTag(reader,wrapperName);
        reader.nextTag();

        while(reader.getEventType()==XMLStreamReader.START_ELEMENT) {
            // TODO: QName has a performance issue
            WrappedPartBuilder part = wrappedParts.get(reader.getName());
            if(part==null) {
                // no corresponding part found. ignore
                XMLStreamReaderUtil.skipElement(reader);
                reader.nextTag();
            } else {
                Object o = part.readResponse(args,reader, msg.getAttachments());
                // there's only at most one ResponseBuilder that returns a value.
                if(o!=null) {
                    assert retVal==null;
                    retVal = o;
                }
            }
            // skip any whitespace
            if (reader.getEventType() != XMLStreamConstants.START_ELEMENT &&
                    reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
                XMLStreamReaderUtil.nextElementContent(reader);
            }
        }

        // we are done with the body
        reader.close();
        XMLStreamReaderFactory.recycle(reader);

        return retVal;
    }

    static final class None extends ResponseBuilder {
        private None(){
        }
        @Override
        public Object readResponse(Message msg, Object[] args) {
            msg.consume();
            return null;
        }
    }

    /**
     * The singleton instance that produces null return value.
     * Used for operations that doesn't have any output.
     */
    public final static ResponseBuilder NONE = new None();

    /**
     * Returns the 'uninitialized' value for the given type.
     *
     * <p>
     * For primitive types, it's '0', and for reference types, it's null.
     */
    @SuppressWarnings("element-type-mismatch")
    public static Object getVMUninitializedValue(Type type) {
        // if this map returns null, that means the 'type' is a reference type,
        // in which case 'null' is the correct null value, so this code is correct.
        return primitiveUninitializedValues.get(type);
    }

    private static final Map<Class,Object> primitiveUninitializedValues = new HashMap<Class, Object>();

    static {
        Map<Class, Object> m = primitiveUninitializedValues;
        m.put(int.class,(int)0);
        m.put(char.class,(char)0);
        m.put(byte.class,(byte)0);
        m.put(short.class,(short)0);
        m.put(long.class,(long)0);
        m.put(float.class,(float)0);
        m.put(double.class,(double)0);
    }

    /**
     * {@link ResponseBuilder} that sets the VM uninitialized value to the type.
     */
    public static final class NullSetter extends ResponseBuilder {
        private final ValueSetter setter;
        private final Object nullValue;

        public NullSetter(ValueSetter setter, Object nullValue){
            assert setter!=null;
            this.nullValue = nullValue;
            this.setter = setter;
        }
        @Override
        public Object readResponse(Message msg, Object[] args) {
            return setter.put(nullValue, args);
        }
    }

    /**
     * {@link ResponseBuilder} that is a composition of multiple
     * {@link ResponseBuilder}s.
     *
     * <p>
     * Sometimes we need to look at multiple parts of the reply message
     * (say, two header params, one body param, and three attachments, etc.)
     * and that's when this object is used to combine multiple {@link ResponseBuilder}s
     * (that each responsible for handling one part).
     *
     * <p>
     * The model guarantees that only at most one {@link ResponseBuilder} will
     * return a value as a return value (and everything else has to go to
     * {@link Holder}s.)
     */
    public static final class Composite extends ResponseBuilder {
        private final ResponseBuilder[] builders;

        public Composite(ResponseBuilder... builders) {
            this.builders = builders;
        }

        public Composite(Collection<? extends ResponseBuilder> builders) {
            this(builders.toArray(new ResponseBuilder[builders.size()]));
        }

        @Override
        public Object readResponse(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            Object retVal = null;
            for (ResponseBuilder builder : builders) {
                Object r = builder.readResponse(msg,args);
                // there's only at most one ResponseBuilder that returns a value.
                if(r!=null) {
                    assert retVal==null;
                    retVal = r;
                }
            }
            return retVal;
        }
    }

    /**
     * Reads an Attachment into a Java parameter.
     */
    public static abstract class AttachmentBuilder extends ResponseBuilder {
        protected final ValueSetter setter;
        protected final ParameterImpl param;
        private final String pname;
        private final String pname1;

        AttachmentBuilder(ParameterImpl param, ValueSetter setter) {
            this.setter = setter;
            this.param = param;
            this.pname = param.getPartName();
            this.pname1 = "<"+pname;
        }

        /**
         * Creates an AttachmentBuilder based on the parameter type
         *
         * @param param
         *      runtime Parameter that abstracts the annotated java parameter
         * @param setter
         *      specifies how the obtained value is set into the argument. Takes
         *      care of Holder arguments.
         */
        public static ResponseBuilder createAttachmentBuilder(ParameterImpl param, ValueSetter setter) {
            Class type = (Class)param.getTypeInfo().type;
            if (DataHandler.class.isAssignableFrom(type)) {
                return new DataHandlerBuilder(param, setter);
            } else if (byte[].class==type) {
                return new ByteArrayBuilder(param, setter);
            } else if(Source.class.isAssignableFrom(type)) {
                return new SourceBuilder(param, setter);
            } else if(Image.class.isAssignableFrom(type)) {
                return new ImageBuilder(param, setter);
            } else if(InputStream.class==type) {
                return new InputStreamBuilder(param, setter);
            } else if(isXMLMimeType(param.getBinding().getMimeType())) {
                return new JAXBBuilder(param, setter);
            } else if(String.class.isAssignableFrom(type)) {
                return new StringBuilder(param, setter);
            } else {
                throw new UnsupportedOperationException("Unexpected Attachment type ="+type);
            }
        }

        @Override
        public Object readResponse(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            // TODO not to loop
            for (Attachment att : msg.getAttachments()) {
                String part = getWSDLPartName(att);
                if (part == null) {
                    continue;
                }
                if(part.equals(pname) || part.equals(pname1)){
                    return mapAttachment(att, args);
                }
            }
            return null;
        }

        abstract Object mapAttachment(Attachment att, Object[] args) throws JAXBException;
    }

    private static final class DataHandlerBuilder extends AttachmentBuilder {
        DataHandlerBuilder(ParameterImpl param, ValueSetter setter) {
            super(param, setter);
        }

        @Override
        Object mapAttachment(Attachment att, Object[] args) {
            return setter.put(att.asDataHandler(), args);
        }
    }

    private static final class StringBuilder extends AttachmentBuilder {
        StringBuilder(ParameterImpl param, ValueSetter setter) {
            super(param, setter);
        }

        @Override
        Object mapAttachment(Attachment att, Object[] args) {
            att.getContentType();
            StringDataContentHandler sdh = new StringDataContentHandler();
            try {
                String str = (String)sdh.getContent(new DataHandlerDataSource(att.asDataHandler()));
                return setter.put(str, args);
            } catch(Exception e) {
                throw new WebServiceException(e);
            }

        }
    }

    private static final class ByteArrayBuilder extends AttachmentBuilder {
        ByteArrayBuilder(ParameterImpl param, ValueSetter setter) {
            super(param, setter);
        }

        @Override
        Object mapAttachment(Attachment att, Object[] args) {
            return setter.put(att.asByteArray(), args);
        }
    }

    private static final class SourceBuilder extends AttachmentBuilder {
        SourceBuilder(ParameterImpl param, ValueSetter setter) {
            super(param, setter);
        }

        @Override
        Object mapAttachment(Attachment att, Object[] args) {
            return setter.put(att.asSource(), args);
        }
    }

    private static final class ImageBuilder extends AttachmentBuilder {
        ImageBuilder(ParameterImpl param, ValueSetter setter) {
            super(param, setter);
        }

        @Override
        Object mapAttachment(Attachment att, Object[] args) {
            Image image;
            InputStream is = null;
            try {
                is = att.asInputStream();
                image = ImageIO.read(is);
            } catch(IOException ioe) {
                throw new WebServiceException(ioe);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch(IOException ioe) {
                        throw new WebServiceException(ioe);
                    }
                }
            }
            return setter.put(image, args);
        }
    }

    private static final class InputStreamBuilder extends AttachmentBuilder {
        InputStreamBuilder(ParameterImpl param, ValueSetter setter) {
            super(param, setter);
        }

        @Override
        Object mapAttachment(Attachment att, Object[] args) {
            return setter.put(att.asInputStream(), args);
        }
    }

    private static final class JAXBBuilder extends AttachmentBuilder {
        JAXBBuilder(ParameterImpl param, ValueSetter setter) {
            super(param, setter);
        }

        @Override
        Object mapAttachment(Attachment att, Object[] args) throws JAXBException {
            Object obj = param.getXMLBridge().unmarshal(att.asInputStream());
            return setter.put(obj, args);
        }
    }

    /**
     * Gets the WSDL part name of this attachment.
     *
     * <p>
     * According to WSI AP 1.0
     * <PRE>
     * 3.8 Value-space of Content-Id Header
     *   Definition: content-id part encoding
     *   The "content-id part encoding" consists of the concatenation of:
     * The value of the name attribute of the wsdl:part element referenced by the mime:content, in which characters disallowed in content-id headers (non-ASCII characters as represented by code points above 0x7F) are escaped as follows:
     *     o Each disallowed character is converted to UTF-8 as one or more bytes.
     *     o Any bytes corresponding to a disallowed character are escaped with the URI escaping mechanism (that is, converted to %HH, where HH is the hexadecimal notation of the byte value).
     *     o The original character is replaced by the resulting character sequence.
     * The character '=' (0x3D).
     * A globally unique value such as a UUID.
     * The character '@' (0x40).
     * A valid domain name under the authority of the entity constructing the message.
     * </PRE>
     *
     * So a wsdl:part fooPart will be encoded as:
     *      <fooPart=somereallybignumberlikeauuid@example.com>
     *
     * @return null
     *      if the parsing fails.
     */
    @SuppressWarnings("FinalStaticMethod")
    public static final String getWSDLPartName(com.sun.xml.internal.ws.api.message.Attachment att){
        String cId = att.getContentId();

        int index = cId.lastIndexOf('@', cId.length());
        if(index == -1){
            return null;
        }
        String localPart = cId.substring(0, index);
        index = localPart.lastIndexOf('=', localPart.length());
        if(index == -1){
            return null;
        }
        try {
            return java.net.URLDecoder.decode(localPart.substring(0, index), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new WebServiceException(e);
        }
    }


    /**
     * Reads a header into a JAXB object.
     */
    public static final class Header extends ResponseBuilder {
        private final XMLBridge<?> bridge;
        private final ValueSetter setter;
        private final QName headerName;
        private final SOAPVersion soapVersion;

        /**
         * @param soapVersion
         *      SOAP1.1 or 1.2
         * @param name
         *      The name of the header element.
         * @param bridge
         *      specifies how to unmarshal a header into a JAXB object.
         * @param setter
         *      specifies how the obtained value is returned to the client.
         */
        public Header(SOAPVersion soapVersion, QName name, XMLBridge<?> bridge, ValueSetter setter) {
            this.soapVersion = soapVersion;
            this.headerName = name;
            this.bridge = bridge;
            this.setter = setter;
        }

        public Header(SOAPVersion soapVersion, ParameterImpl param, ValueSetter setter) {
            this(soapVersion,
                param.getTypeInfo().tagName,
                param.getXMLBridge(),
                setter);
            assert param.getOutBinding()== ParameterBinding.HEADER;
        }

        private SOAPFaultException createDuplicateHeaderException() {
            try {
                SOAPFault fault = soapVersion.getSOAPFactory().createFault();
                fault.setFaultCode(soapVersion.faultCodeServer);
                fault.setFaultString(ServerMessages.DUPLICATE_PORT_KNOWN_HEADER(headerName));
                return new SOAPFaultException(fault);
            } catch(SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        @Override
        public Object readResponse(Message msg, Object[] args) throws JAXBException {
            com.sun.xml.internal.ws.api.message.Header header = null;
            Iterator<com.sun.xml.internal.ws.api.message.Header> it =
                msg.getHeaders().getHeaders(headerName,true);
            if (it.hasNext()) {
                header = it.next();
                if (it.hasNext()) {
                    throw createDuplicateHeaderException();
                }
            }

            if(header!=null)
                return setter.put( header.readAsJAXB(bridge), args );
            else
                // header not found.
                return null;
        }
    }

    /**
     * Reads the whole payload into a single JAXB bean.
     */
    public static final class Body extends ResponseBuilder {
        private final XMLBridge<?> bridge;
        private final ValueSetter setter;

        /**
         * @param bridge
         *      specifies how to unmarshal the payload into a JAXB object.
         * @param setter
         *      specifies how the obtained value is returned to the client.
         */
        public Body(XMLBridge<?> bridge, ValueSetter setter) {
            this.bridge = bridge;
            this.setter = setter;
        }

        @Override
        public Object readResponse(Message msg, Object[] args) throws JAXBException {
            return setter.put( msg.readPayloadAsJAXB(bridge), args );
        }
    }

    /**
     * Treats a payload as multiple parts wrapped into one element,
     * and processes all such wrapped parts.
     */
    public static final class DocLit extends ResponseBuilder {
        /**
         * {@link PartBuilder} keyed by the element name (inside the wrapper element.)
         */
        private final PartBuilder[] parts;

        private final XMLBridge wrapper;

        private boolean dynamicWrapper;

        public DocLit(WrapperParameter wp, ValueSetterFactory setterFactory) {
            wrapperName = wp.getName();
            wrapper = wp.getXMLBridge();
            Class wrapperType = (Class) wrapper.getTypeInfo().type;
            dynamicWrapper = WrapperComposite.class.equals(wrapperType);

            List<PartBuilder> tempParts = new ArrayList<PartBuilder>();

            List<ParameterImpl> children = wp.getWrapperChildren();
            for (ParameterImpl p : children) {
                if(p.isIN())
                    continue;
                QName name = p.getName();
                if (dynamicWrapper) {
                    if (wrappedParts == null) wrappedParts = new HashMap<QName,WrappedPartBuilder>();
                    XMLBridge xmlBridge = p.getInlinedRepeatedElementBridge();
                    if (xmlBridge == null) xmlBridge = p.getXMLBridge();
                    wrappedParts.put( p.getName(), new WrappedPartBuilder(xmlBridge, setterFactory.get(p)));
                } else {
                    try {
                        tempParts.add(new PartBuilder(
                                    wp.getOwner().getBindingContext().getElementPropertyAccessor(
                                        wrapperType,
                                        name.getNamespaceURI(),
                                        p.getName().getLocalPart()),
                                    setterFactory.get(p)
                                ));
                        // wrapper parameter itself always bind to body, and
                        // so do all its children
                        assert p.getBinding()== ParameterBinding.BODY;
                    } catch (JAXBException e) {
                        throw new WebServiceException(  // TODO: i18n
                            wrapperType+" do not have a property of the name "+name,e);
                    }
                }
            }
            this.parts = tempParts.toArray(new PartBuilder[tempParts.size()]);
        }

        @Override
        public Object readResponse(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            if (dynamicWrapper) return readWrappedResponse(msg, args);
            Object retVal = null;

            if (parts.length>0) {
                if (!msg.hasPayload()) {
                    throw new WebServiceException("No payload. Expecting payload with "+wrapperName+" element");
                }
                XMLStreamReader reader = msg.readPayload();
                XMLStreamReaderUtil.verifyTag(reader,wrapperName);
                Object wrapperBean = wrapper.unmarshal(reader, (msg.getAttachments() != null) ?
                    new AttachmentUnmarshallerImpl(msg.getAttachments()): null);

                try {
                    for (PartBuilder part : parts) {
                        Object o = part.readResponse(args,wrapperBean);
                        // there's only at most one ResponseBuilder that returns a value.
                        // TODO: reorder parts so that the return value comes at the end.
                        if(o!=null) {
                            assert retVal==null;
                            retVal = o;
                        }
                    }
                } catch (DatabindingException e) {
                    // this can happen when the set method throw a checked exception or something like that
                    throw new WebServiceException(e);    // TODO:i18n
                }

                // we are done with the body
                reader.close();
                XMLStreamReaderFactory.recycle(reader);
            } else {
                msg.consume();
            }

            return retVal;
        }

        /**
         * Unmarshals each wrapped part into a JAXB object and moves it
         * to the expected place.
         */
        static final class PartBuilder {
            private final PropertyAccessor accessor;
            private final ValueSetter setter;

            /**
             * @param accessor
             *      specifies which portion of the wrapper bean to obtain the value from.
             * @param setter
             *      specifies how the obtained value is returned to the client.
             */
            public PartBuilder(PropertyAccessor accessor, ValueSetter setter) {
                this.accessor = accessor;
                this.setter = setter;
                assert accessor!=null && setter!=null;
            }

            final Object readResponse( Object[] args, Object wrapperBean ) {
                Object obj = accessor.get(wrapperBean);
                return setter.put(obj,args);
            }


        }
    }

    /**
     * Treats a payload as multiple parts wrapped into one element,
     * and processes all such wrapped parts.
     */
    public static final class RpcLit extends ResponseBuilder {
        public RpcLit(WrapperParameter wp, ValueSetterFactory setterFactory) {
            assert wp.getTypeInfo().type== WrapperComposite.class;
            wrapperName = wp.getName();
            wrappedParts = new HashMap<QName,WrappedPartBuilder>();
            List<ParameterImpl> children = wp.getWrapperChildren();
            for (ParameterImpl p : children) {
                wrappedParts.put( p.getName(), new WrappedPartBuilder(
                    p.getXMLBridge(), setterFactory.get(p)
                ));
                // wrapper parameter itself always bind to body, and
                // so do all its children
                assert p.getBinding()== ParameterBinding.BODY;
            }
        }

        @Override
        public Object readResponse(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            return readWrappedResponse(msg, args);
        }
    }

    private static boolean isXMLMimeType(String mimeType){
        return mimeType.equals("text/xml") || mimeType.equals("application/xml");
    }
}
