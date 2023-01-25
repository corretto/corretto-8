/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.impl;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.helpers.ValidationEventImpl;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;

import com.sun.istack.internal.ByteArrayDataSource;
import com.sun.xml.internal.bind.DatatypeConverterImpl;
import com.sun.xml.internal.bind.WhiteSpaceProcessor;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.TODO;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeBuiltinLeafInfo;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.Transducer;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.output.Pcdata;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Base64Data;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.internal.bind.v2.util.ByteArrayOutputStreamEx;
import com.sun.xml.internal.bind.v2.util.DataSourceSource;

import org.xml.sax.SAXException;

/**
 * {@link BuiltinLeafInfoImpl} with a support for runtime.
 *
 * <p>
 * In particular this class defines {@link Transducer}s for the built-in types.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class RuntimeBuiltinLeafInfoImpl<T> extends BuiltinLeafInfoImpl<Type,Class>
    implements RuntimeBuiltinLeafInfo, Transducer<T> {

    private RuntimeBuiltinLeafInfoImpl(Class type, QName... typeNames) {
        super(type, typeNames);
        LEAVES.put(type,this);
    }

    public final Class getClazz() {
        return (Class)getType();
    }


    public final Transducer getTransducer() {
        return this;
    }

    public boolean useNamespace() {
        return false;
    }

    public final boolean isDefault() {
        return true;
    }

    public void declareNamespace(T o, XMLSerializer w) throws AccessorException {
    }

    public QName getTypeName(T instance) {
        return null;
    }

    /**
     * Those built-in types that print to {@link String}.
     */
    private static abstract class StringImpl<T> extends RuntimeBuiltinLeafInfoImpl<T> {
        protected StringImpl(Class type, QName... typeNames) {
            super(type,typeNames);
        }

        public abstract String print(T o) throws AccessorException;

        public void writeText(XMLSerializer w, T o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
            w.text(print(o),fieldName);
        }

        public void writeLeafElement(XMLSerializer w, Name tagName, T o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
            w.leafElement(tagName,print(o),fieldName);
        }
    }

    /**
     * Those built-in types that print to {@link Pcdata}.
     */
    private static abstract class PcdataImpl<T> extends RuntimeBuiltinLeafInfoImpl<T> {
        protected PcdataImpl(Class type, QName... typeNames) {
            super(type,typeNames);
        }

        public abstract Pcdata print(T o) throws AccessorException;

        public final void writeText(XMLSerializer w, T o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
            w.text(print(o),fieldName);
        }

        public final void writeLeafElement(XMLSerializer w, Name tagName, T o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
            w.leafElement(tagName,print(o),fieldName);
        }

    }

    /**
     * All instances of {@link RuntimeBuiltinLeafInfoImpl}s keyed by their type.
     */
    public static final Map<Type,RuntimeBuiltinLeafInfoImpl<?>> LEAVES = new HashMap<Type, RuntimeBuiltinLeafInfoImpl<?>>();

    private static QName createXS(String typeName) {
        return new QName(WellKnownNamespace.XML_SCHEMA,typeName);
    }

    public static final RuntimeBuiltinLeafInfoImpl<String> STRING;

    private static final String DATE = "date";

    /**
     * List of all {@link RuntimeBuiltinLeafInfoImpl}s.
     *
     * <p>
     * This corresponds to the built-in Java classes that are specified to be
     * handled differently than ordinary classes. See table 8-2 "Mapping of Standard Java classes".
     */
    public static final List<RuntimeBuiltinLeafInfoImpl<?>> builtinBeanInfos;

    public static final String MAP_ANYURI_TO_URI = "mapAnyUriToUri";

    static {

        String MAP_ANYURI_TO_URI_VALUE = AccessController.doPrivileged(
                new PrivilegedAction<String>() {
                    @Override
                    public String run() {
                        return System.getProperty(MAP_ANYURI_TO_URI);
                    }
                }
        );
        QName[] qnames = (MAP_ANYURI_TO_URI_VALUE == null) ? new QName[] {
                                createXS("string"),
                                createXS("anySimpleType"),
                                createXS("normalizedString"),
                                createXS("anyURI"),
                                createXS("token"),
                                createXS("language"),
                                createXS("Name"),
                                createXS("NCName"),
                                createXS("NMTOKEN"),
                                createXS("ENTITY")}
                                    :
                         new QName[] {
                                createXS("string"),
                                createXS("anySimpleType"),
                                createXS("normalizedString"),
                                createXS("token"),
                                createXS("language"),
                                createXS("Name"),
                                createXS("NCName"),
                                createXS("NMTOKEN"),
                                createXS("ENTITY")};

        STRING = new StringImplImpl(String.class, qnames);

        ArrayList<RuntimeBuiltinLeafInfoImpl<?>> secondaryList = new ArrayList<RuntimeBuiltinLeafInfoImpl<?>>();
            /*
                There are cases where more than one Java classes map to the same XML type.
                But when we see the same XML type in an incoming document, we only pick
                one of those Java classes to unmarshal. This Java class is called 'primary'.
                The rest are called 'secondary'.

                Currently we lack the proper infrastructure to handle those nicely.
                For now, we rely on a hack.

                We define secondary mappings first, then primary ones later. GrammarInfo
                builds a map from type name to BeanInfo. By defining primary ones later,
                those primary bindings will overwrite the secondary ones.
            */

            /*
                secondary bindings
            */
        secondaryList.add(
            new StringImpl<Character>(Character.class, createXS("unsignedShort")) {
                public Character parse(CharSequence text) {
                    // TODO.checkSpec("default mapping for char is not defined yet");
                    return (char)DatatypeConverterImpl._parseInt(text);
                }
                public String print(Character v) {
                    return Integer.toString(v);
                }
            });
        secondaryList.add(
            new StringImpl<Calendar>(Calendar.class, DatatypeConstants.DATETIME) {
                public Calendar parse(CharSequence text) {
                    return DatatypeConverterImpl._parseDateTime(text.toString());
                }
                public String print(Calendar v) {
                    return DatatypeConverterImpl._printDateTime(v);
                }
            });
        secondaryList.add(
            new StringImpl<GregorianCalendar>(GregorianCalendar.class, DatatypeConstants.DATETIME) {
                public GregorianCalendar parse(CharSequence text) {
                    return DatatypeConverterImpl._parseDateTime(text.toString());
                }
                public String print(GregorianCalendar v) {
                    return DatatypeConverterImpl._printDateTime(v);
                }
            });
        secondaryList.add(
            new StringImpl<Date>(Date.class, DatatypeConstants.DATETIME) {
                public Date parse(CharSequence text) {
                    return DatatypeConverterImpl._parseDateTime(text.toString()).getTime();
                }
                public String print(Date v) {
                    XMLSerializer xs = XMLSerializer.getInstance();
                    QName type = xs.getSchemaType();
                    GregorianCalendar cal = new GregorianCalendar(0,0,0);
                    cal.setTime(v);
                    if ((type != null) && (WellKnownNamespace.XML_SCHEMA.equals(type.getNamespaceURI())) &&
                            DATE.equals(type.getLocalPart())) {
                        return DatatypeConverterImpl._printDate(cal);
                    } else {
                        return DatatypeConverterImpl._printDateTime(cal);
                    }
                }
            });
        secondaryList.add(
            new StringImpl<File>(File.class, createXS("string")) {
                public File parse(CharSequence text) {
                    return new File(WhiteSpaceProcessor.trim(text).toString());
                }
                public String print(File v) {
                    return v.getPath();
                }
            });
        secondaryList.add(
            new StringImpl<URL>(URL.class, createXS("anyURI")) {
                public URL parse(CharSequence text) throws SAXException {
                    TODO.checkSpec("JSR222 Issue #42");
                    try {
                        return new URL(WhiteSpaceProcessor.trim(text).toString());
                    } catch (MalformedURLException e) {
                        UnmarshallingContext.getInstance().handleError(e);
                        return null;
                    }
                }
                public String print(URL v) {
                    return v.toExternalForm();
                }
            });
        if (MAP_ANYURI_TO_URI_VALUE == null) {
            secondaryList.add(
                new StringImpl<URI>(URI.class, createXS("string")) {
                    public URI parse(CharSequence text) throws SAXException {
                        try {
                            return new URI(text.toString());
                        } catch (URISyntaxException e) {
                            UnmarshallingContext.getInstance().handleError(e);
                            return null;
                        }
                    }

                    public String print(URI v) {
                        return v.toString();
                    }
                });
        }
        secondaryList.add(
            new StringImpl<Class>(Class.class, createXS("string")) {
                public Class parse(CharSequence text) throws SAXException {
                    TODO.checkSpec("JSR222 Issue #42");
                    try {
                        String name = WhiteSpaceProcessor.trim(text).toString();
                        ClassLoader cl = UnmarshallingContext.getInstance().classLoader;
                        if(cl==null)
                            cl = Thread.currentThread().getContextClassLoader();

                        if(cl!=null)
                            return cl.loadClass(name);
                        else
                            return Class.forName(name);
                    } catch (ClassNotFoundException e) {
                        UnmarshallingContext.getInstance().handleError(e);
                        return null;
                    }
                }
                public String print(Class v) {
                    return v.getName();
                }
            });

            /*
                classes that map to base64Binary / MTOM related classes.
                a part of the secondary binding.
            */
        secondaryList.add(
            new PcdataImpl<Image>(Image.class, createXS("base64Binary")) {
                public Image parse(CharSequence text) throws SAXException  {
                    try {
                        InputStream is;
                        if(text instanceof Base64Data)
                            is = ((Base64Data)text).getInputStream();
                        else
                            is = new ByteArrayInputStream(decodeBase64(text)); // TODO: buffering is inefficient

                        // technically we should check the MIME type here, but
                        // normally images can be content-sniffed.
                        // so the MIME type check will only make us slower and draconian, both of which
                        // JAXB 2.0 isn't interested.
                        try {
                            return ImageIO.read(is);
                        } finally {
                            is.close();
                        }
                    } catch (IOException e) {
                        UnmarshallingContext.getInstance().handleError(e);
                        return null;
                    }
                }

                private BufferedImage convertToBufferedImage(Image image) throws IOException {
                    if (image instanceof BufferedImage) {
                        return (BufferedImage)image;

                    } else {
                        MediaTracker tracker = new MediaTracker(new Component(){}); // not sure if this is the right thing to do.
                        tracker.addImage(image, 0);
                        try {
                            tracker.waitForAll();
                        } catch (InterruptedException e) {
                            throw new IOException(e.getMessage());
                        }
                        BufferedImage bufImage = new BufferedImage(
                                image.getWidth(null),
                                image.getHeight(null),
                                BufferedImage.TYPE_INT_ARGB);

                        Graphics g = bufImage.createGraphics();
                        g.drawImage(image, 0, 0, null);
                        return bufImage;
                    }
                }

                public Base64Data print(Image v) {
                    ByteArrayOutputStreamEx imageData = new ByteArrayOutputStreamEx();
                    XMLSerializer xs = XMLSerializer.getInstance();

                    String mimeType = xs.getXMIMEContentType();
                    if(mimeType==null || mimeType.startsWith("image/*"))
                        // because PNG is lossless, it's a good default
                        //
                        // mime type can be a range, in which case we can't just pass that
                        // to ImageIO.getImageWritersByMIMEType, so here I'm just assuming
                        // the default of PNG. Not sure if this is complete.
                        mimeType = "image/png";

                    try {
                        Iterator<ImageWriter> itr = ImageIO.getImageWritersByMIMEType(mimeType);
                        if(itr.hasNext()) {
                            ImageWriter w = itr.next();
                            ImageOutputStream os = ImageIO.createImageOutputStream(imageData);
                            w.setOutput(os);
                            w.write(convertToBufferedImage(v));
                            os.close();
                            w.dispose();
                        } else {
                            // no encoder
                            xs.handleEvent(new ValidationEventImpl(
                                ValidationEvent.ERROR,
                                Messages.NO_IMAGE_WRITER.format(mimeType),
                                xs.getCurrentLocation(null) ));
                            // TODO: proper error reporting
                            throw new RuntimeException("no encoder for MIME type "+mimeType);
                        }
                    } catch (IOException e) {
                        xs.handleError(e);
                        // TODO: proper error reporting
                        throw new RuntimeException(e);
                    }
                    Base64Data bd = new Base64Data();
                    imageData.set(bd,mimeType);
                    return bd;
                }
            });
        secondaryList.add(
            new PcdataImpl<DataHandler>(DataHandler.class, createXS("base64Binary")) {
                public DataHandler parse(CharSequence text) {
                    if(text instanceof Base64Data)
                        return ((Base64Data)text).getDataHandler();
                    else
                        return new DataHandler(new ByteArrayDataSource(decodeBase64(text),
                            UnmarshallingContext.getInstance().getXMIMEContentType()));
                }

                public Base64Data print(DataHandler v) {
                    Base64Data bd = new Base64Data();
                    bd.set(v);
                    return bd;
                }
            });
        secondaryList.add(
            new PcdataImpl<Source>(Source.class, createXS("base64Binary")) {
                public Source parse(CharSequence text) throws SAXException  {
                    try {
                        if(text instanceof Base64Data)
                            return new DataSourceSource( ((Base64Data)text).getDataHandler() );
                        else
                            return new DataSourceSource(new ByteArrayDataSource(decodeBase64(text),
                                UnmarshallingContext.getInstance().getXMIMEContentType()));
                    } catch (MimeTypeParseException e) {
                        UnmarshallingContext.getInstance().handleError(e);
                        return null;
                    }
                }

                public Base64Data print(Source v) {
                    XMLSerializer xs = XMLSerializer.getInstance();
                    Base64Data bd = new Base64Data();

                    String contentType = xs.getXMIMEContentType();
                    MimeType mt = null;
                    if(contentType!=null)
                        try {
                            mt = new MimeType(contentType);
                        } catch (MimeTypeParseException e) {
                            xs.handleError(e);
                            // recover by ignoring the content type specification
                        }

                    if( v instanceof DataSourceSource ) {
                        // if so, we already have immutable DataSource so
                        // this can be done efficiently
                        DataSource ds = ((DataSourceSource)v).getDataSource();

                        String dsct = ds.getContentType();
                        if(dsct!=null && (contentType==null || contentType.equals(dsct))) {
                            bd.set(new DataHandler(ds));
                            return bd;
                        }
                    }

                    // general case. slower.

                    // find out the encoding
                    String charset=null;
                    if(mt!=null)
                        charset = mt.getParameter("charset");
                    if(charset==null)
                        charset = "UTF-8";

                    try {
                        ByteArrayOutputStreamEx baos = new ByteArrayOutputStreamEx();
                        Transformer tr = xs.getIdentityTransformer();
                        String defaultEncoding = tr.getOutputProperty(OutputKeys.ENCODING);
                        tr.setOutputProperty(OutputKeys.ENCODING, charset);
                        tr.transform(v, new StreamResult(new OutputStreamWriter(baos,charset)));
                        tr.setOutputProperty(OutputKeys.ENCODING, defaultEncoding);
                        baos.set(bd,"application/xml; charset="+charset);
                        return bd;
                    } catch (TransformerException e) {
                        // TODO: marshaller error handling
                        xs.handleError(e);
                    } catch (UnsupportedEncodingException e) {
                        xs.handleError(e);
                    }

                    // error recoverly
                    bd.set(new byte[0],"application/xml");
                    return bd;
                }
            });
        secondaryList.add(
            new StringImpl<XMLGregorianCalendar>(XMLGregorianCalendar.class,
                    createXS("anySimpleType"),
                    DatatypeConstants.DATE,
                    DatatypeConstants.DATETIME,
                    DatatypeConstants.TIME,
                    DatatypeConstants.GMONTH,
                    DatatypeConstants.GDAY,
                    DatatypeConstants.GYEAR,
                    DatatypeConstants.GYEARMONTH,
                    DatatypeConstants.GMONTHDAY
                ) {
                public String print(XMLGregorianCalendar cal) {
                    XMLSerializer xs = XMLSerializer.getInstance();

                    QName type = xs.getSchemaType();
                    if (type != null) {
                        try {
                            checkXmlGregorianCalendarFieldRef(type, cal);
                            String format = xmlGregorianCalendarFormatString.get(type);
                            if (format != null) {
                                return format(format, cal);
                            }
                        } catch (javax.xml.bind.MarshalException e) {
                            // see issue 649
                            xs.handleEvent(new ValidationEventImpl(ValidationEvent.WARNING, e.getMessage(),
                                xs.getCurrentLocation(null) ));
                            return "";
                        }
                    }
                    return cal.toXMLFormat();
                }

                public XMLGregorianCalendar parse(CharSequence lexical) throws SAXException {
                    try {
                        return DatatypeConverterImpl.getDatatypeFactory()
                                .newXMLGregorianCalendar(lexical.toString().trim()); // (.trim() - issue 396)
                    } catch (Exception e) {
                        UnmarshallingContext.getInstance().handleError(e);
                        return null;
                    }
                }

                // code duplicated from JAXP RI 1.3. See 6277586
                private String format( String format, XMLGregorianCalendar value ) {
                    StringBuilder buf = new StringBuilder();
                    int fidx=0,flen=format.length();

                    while(fidx<flen) {
                        char fch = format.charAt(fidx++);
                        if(fch!='%') {// not a meta char
                            buf.append(fch);
                            continue;
                        }

                        switch(format.charAt(fidx++)) {
                        case 'Y':
                            printNumber(buf,value.getEonAndYear(), 4);
                            break;
                        case 'M':
                            printNumber(buf,value.getMonth(),2);
                            break;
                        case 'D':
                            printNumber(buf,value.getDay(),2);
                            break;
                        case 'h':
                            printNumber(buf,value.getHour(),2);
                            break;
                        case 'm':
                            printNumber(buf,value.getMinute(),2);
                            break;
                        case 's':
                            printNumber(buf,value.getSecond(),2);
                    if (value.getFractionalSecond() != null) {
                        String frac = value.getFractionalSecond().toPlainString();
                        //skip leading zero.
                        buf.append(frac.substring(1, frac.length()));
                    }
                            break;
                        case 'z':
                    int offset = value.getTimezone();
                            if(offset == 0) {
                        buf.append('Z');
                    } else if (offset != DatatypeConstants.FIELD_UNDEFINED) {
                        if(offset<0) {
                        buf.append('-');
                        offset *= -1;
                        } else {
                        buf.append('+');
                        }
                        printNumber(buf,offset/60,2);
                                buf.append(':');
                                printNumber(buf,offset%60,2);
                            }
                            break;
                        default:
                            throw new InternalError();  // impossible
                        }
                    }

                    return buf.toString();
                }
                private void printNumber( StringBuilder out, BigInteger number, int nDigits) {
                    String s = number.toString();
                    for( int i=s.length(); i<nDigits; i++ )
                        out.append('0');
                    out.append(s);
                }
                private void printNumber( StringBuilder out, int number, int nDigits ) {
                    String s = String.valueOf(number);
                    for( int i=s.length(); i<nDigits; i++ )
                        out.append('0');
                    out.append(s);
                }
                @Override
                public QName getTypeName(XMLGregorianCalendar cal) {
                    return cal.getXMLSchemaType();
                }
            });

        ArrayList<RuntimeBuiltinLeafInfoImpl<?>> primaryList = new ArrayList<RuntimeBuiltinLeafInfoImpl<?>>();

        /*
            primary bindings
        */
        primaryList.add(STRING);
        primaryList.add(new StringImpl<Boolean>(Boolean.class,
                createXS("boolean")
                ) {
                public Boolean parse(CharSequence text) {
                    return DatatypeConverterImpl._parseBoolean(text);
                }

                public String print(Boolean v) {
                    return v.toString();
                }
            });
        primaryList.add(new PcdataImpl<byte[]>(byte[].class,
                createXS("base64Binary"),
                createXS("hexBinary")
                ) {
                public byte[] parse(CharSequence text) {
                    return decodeBase64(text);
                }

                public Base64Data print(byte[] v) {
                    XMLSerializer w = XMLSerializer.getInstance();
                    Base64Data bd = new Base64Data();
                    String mimeType = w.getXMIMEContentType();
                    bd.set(v,mimeType);
                    return bd;
                }
            });
        primaryList.add(new StringImpl<Byte>(Byte.class,
                createXS("byte")
                ) {
                public Byte parse(CharSequence text) {
                    return DatatypeConverterImpl._parseByte(text);
                }

                public String print(Byte v) {
                    return DatatypeConverterImpl._printByte(v);
                }
            });
        primaryList.add(new StringImpl<Short>(Short.class,
                createXS("short"),
                createXS("unsignedByte")
                ) {
                public Short parse(CharSequence text) {
                    return DatatypeConverterImpl._parseShort(text);
                }

                public String print(Short v) {
                    return DatatypeConverterImpl._printShort(v);
                }
            });
        primaryList.add(new StringImpl<Integer>(Integer.class,
                createXS("int"),
                createXS("unsignedShort")
                ) {
                public Integer parse(CharSequence text) {
                    return DatatypeConverterImpl._parseInt(text);
                }

                public String print(Integer v) {
                    return DatatypeConverterImpl._printInt(v);
                }
            });
        primaryList.add(
            new StringImpl<Long>(Long.class,
                createXS("long"),
                createXS("unsignedInt")
                ) {
                public Long parse(CharSequence text) {
                    return DatatypeConverterImpl._parseLong(text);
                }

                public String print(Long v) {
                    return DatatypeConverterImpl._printLong(v);
                }
            });
        primaryList.add(
            new StringImpl<Float>(Float.class,
                createXS("float")
                ) {
                public Float parse(CharSequence text) {
                    return DatatypeConverterImpl._parseFloat(text.toString());
                }

                public String print(Float v) {
                    return DatatypeConverterImpl._printFloat(v);
                }
            });
        primaryList.add(
            new StringImpl<Double>(Double.class,
                createXS("double")
                ) {
                public Double parse(CharSequence text) {
                    return DatatypeConverterImpl._parseDouble(text);
                }

                public String print(Double v) {
                    return DatatypeConverterImpl._printDouble(v);
                }
            });
        primaryList.add(
            new StringImpl<BigInteger>(BigInteger.class,
                createXS("integer"),
                createXS("positiveInteger"),
                createXS("negativeInteger"),
                createXS("nonPositiveInteger"),
                createXS("nonNegativeInteger"),
                createXS("unsignedLong")
                ) {
                public BigInteger parse(CharSequence text) {
                    return DatatypeConverterImpl._parseInteger(text);
                }

                public String print(BigInteger v) {
                    return DatatypeConverterImpl._printInteger(v);
                }
            });
        primaryList.add(
                new StringImpl<BigDecimal>(BigDecimal.class,
                        createXS("decimal")
                ) {
                    public BigDecimal parse(CharSequence text) {
                        return DatatypeConverterImpl._parseDecimal(text.toString());
                    }

                    public String print(BigDecimal v) {
                        return DatatypeConverterImpl._printDecimal(v);
                    }
                }
        );
        primaryList.add(
            new StringImpl<QName>(QName.class,
                createXS("QName")
                ) {
                public QName parse(CharSequence text) throws SAXException {
                    try {
                        return DatatypeConverterImpl._parseQName(text.toString(),UnmarshallingContext.getInstance());
                    } catch (IllegalArgumentException e) {
                        UnmarshallingContext.getInstance().handleError(e);
                        return null;
                    }
                }

                public String print(QName v) {
                    return DatatypeConverterImpl._printQName(v,XMLSerializer.getInstance().getNamespaceContext());
                }

                @Override
                public boolean useNamespace() {
                    return true;
                }

                @Override
                public void declareNamespace(QName v, XMLSerializer w) {
                    w.getNamespaceContext().declareNamespace(v.getNamespaceURI(),v.getPrefix(),false);
                }
            });
        if (MAP_ANYURI_TO_URI_VALUE != null) {
            primaryList.add(
                new StringImpl<URI>(URI.class, createXS("anyURI")) {
                    public URI parse(CharSequence text) throws SAXException {
                        try {
                            return new URI(text.toString());
                        } catch (URISyntaxException e) {
                            UnmarshallingContext.getInstance().handleError(e);
                            return null;
                        }
                    }

                    public String print(URI v) {
                        return v.toString();
                    }
                });
        }
        primaryList.add(
                new StringImpl<Duration>(Duration.class, createXS("duration")) {
                    public String print(Duration duration) {
                        return duration.toString();
                    }

                    public Duration parse(CharSequence lexical) {
                        TODO.checkSpec("JSR222 Issue #42");
                        return DatatypeConverterImpl.getDatatypeFactory().newDuration(lexical.toString());
                    }
                }
        );
        primaryList.add(
            new StringImpl<Void>(Void.class) {
                // 'void' binding isn't defined by the spec, but when the JAX-RPC processes user-defined
                // methods like "int actionFoo()", they need this pseudo-void property.

                public String print(Void value) {
                    return "";
                }

                public Void parse(CharSequence lexical) {
                    return null;
                }
            });

        List<RuntimeBuiltinLeafInfoImpl<?>> l = new ArrayList<RuntimeBuiltinLeafInfoImpl<?>>(secondaryList.size()+primaryList.size()+1);
        l.addAll(secondaryList);

        // UUID may fail to load if we are running on JDK 1.4. Handle gracefully
        try {
            l.add(new UUIDImpl());
        } catch (LinkageError e) {
            // ignore
        }

        l.addAll(primaryList);

        builtinBeanInfos = Collections.unmodifiableList(l);
    }

    private static byte[] decodeBase64(CharSequence text) {
        if (text instanceof Base64Data) {
            Base64Data base64Data = (Base64Data) text;
            return base64Data.getExact();
        } else {
            return DatatypeConverterImpl._parseBase64Binary(text.toString());
        }
    }

        private static void checkXmlGregorianCalendarFieldRef(QName type,
                XMLGregorianCalendar cal)throws javax.xml.bind.MarshalException{
                StringBuilder buf = new StringBuilder();
                int bitField = xmlGregorianCalendarFieldRef.get(type);
                final int l = 0x1;
                int pos = 0;
                while (bitField != 0x0){
                        int bit = bitField & l;
                        bitField >>>= 4;
                        pos++;

                        if (bit == 1) {
                                switch(pos){
                                        case 1:
                                                if (cal.getSecond() == DatatypeConstants.FIELD_UNDEFINED){
                                                        buf.append("  ").append(Messages.XMLGREGORIANCALENDAR_SEC);
                                                }
                                                break;
                                        case 2:
                                                if (cal.getMinute() == DatatypeConstants.FIELD_UNDEFINED){
                                                        buf.append("  ").append(Messages.XMLGREGORIANCALENDAR_MIN);
                                                }
                                                break;
                                        case 3:
                                                if (cal.getHour() == DatatypeConstants.FIELD_UNDEFINED){
                                                        buf.append("  ").append(Messages.XMLGREGORIANCALENDAR_HR);
                                                }
                                                break;
                                        case 4:
                                                if (cal.getDay() == DatatypeConstants.FIELD_UNDEFINED){
                                                        buf.append("  ").append(Messages.XMLGREGORIANCALENDAR_DAY);
                                                }
                                                break;
                                        case 5:
                                                if (cal.getMonth() == DatatypeConstants.FIELD_UNDEFINED){
                                                        buf.append("  ").append(Messages.XMLGREGORIANCALENDAR_MONTH);
                                                }
                                                break;
                                        case 6:
                                                if (cal.getYear() == DatatypeConstants.FIELD_UNDEFINED){
                                                        buf.append("  ").append(Messages.XMLGREGORIANCALENDAR_YEAR);
                                                }
                                                break;
                                        case 7:  // ignore timezone setting
                                                break;
                                }
                        }
                }
                if (buf.length() > 0){
                        throw new javax.xml.bind.MarshalException(
                         Messages.XMLGREGORIANCALENDAR_INVALID.format(type.getLocalPart())
                         + buf.toString());
                }
        }

    /**
     * Format string for the {@link XMLGregorianCalendar}.
     */
    private static final Map<QName,String> xmlGregorianCalendarFormatString = new HashMap<QName, String>();

    static {
        Map<QName,String> m = xmlGregorianCalendarFormatString;
        // See 4971612: be careful for SCCS substitution
        m.put(DatatypeConstants.DATETIME,   "%Y-%M-%DT%h:%m:%s"+ "%z");
        m.put(DatatypeConstants.DATE,       "%Y-%M-%D" +"%z");
        m.put(DatatypeConstants.TIME,       "%h:%m:%s"+ "%z");
        m.put(DatatypeConstants.GMONTH,     "--%M--%z");
        m.put(DatatypeConstants.GDAY,       "---%D" + "%z");
        m.put(DatatypeConstants.GYEAR,      "%Y" + "%z");
        m.put(DatatypeConstants.GYEARMONTH, "%Y-%M" + "%z");
        m.put(DatatypeConstants.GMONTHDAY,  "--%M-%D" +"%z");
    }

        /**
         * Field designations for XMLGregorianCalendar format string.
         * sec          0x0000001
         * min          0x0000010
         * hrs          0x0000100
         * day          0x0001000
         * month        0x0010000
         * year         0x0100000
         * timezone     0x1000000
         */
        private static final Map<QName, Integer> xmlGregorianCalendarFieldRef =
                new HashMap<QName, Integer>();
        static {
                Map<QName, Integer> f = xmlGregorianCalendarFieldRef;
                f.put(DatatypeConstants.DATETIME,   0x1111111);
                f.put(DatatypeConstants.DATE,       0x1111000);
                f.put(DatatypeConstants.TIME,       0x1000111);
                f.put(DatatypeConstants.GDAY,       0x1001000);
                f.put(DatatypeConstants.GMONTH,     0x1010000);
                f.put(DatatypeConstants.GYEAR,      0x1100000);
                f.put(DatatypeConstants.GYEARMONTH, 0x1110000);
                f.put(DatatypeConstants.GMONTHDAY,  0x1011000);
        }

    /**
     * {@link RuntimeBuiltinLeafInfoImpl} for {@link UUID}.
     *
     * This class is given a name so that failing to load this class won't cause a fatal problem.
     */
    private static class UUIDImpl extends StringImpl<UUID> {
        public UUIDImpl() {
            super(UUID.class, RuntimeBuiltinLeafInfoImpl.createXS("string"));
        }

        public UUID parse(CharSequence text) throws SAXException {
            TODO.checkSpec("JSR222 Issue #42");
            try {
                return UUID.fromString(WhiteSpaceProcessor.trim(text).toString());
            } catch (IllegalArgumentException e) {
                UnmarshallingContext.getInstance().handleError(e);
                return null;
            }
        }

        public String print(UUID v) {
            return v.toString();
        }
    }

    private static class StringImplImpl extends StringImpl<String> {

        public StringImplImpl(Class type, QName[] typeNames) {
            super(type, typeNames);
        }

        public String parse(CharSequence text) {
            return text.toString();
        }

        public String print(String s) {
            return s;
        }

        @Override
        public final void writeText(XMLSerializer w, String o, String fieldName) throws IOException, SAXException, XMLStreamException {
            w.text(o, fieldName);
        }

        @Override
        public final void writeLeafElement(XMLSerializer w, Name tagName, String o, String fieldName) throws IOException, SAXException, XMLStreamException {
            w.leafElement(tagName, o, fieldName);
        }
    }
}
