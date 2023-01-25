/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.WebServiceRef;
import javax.xml.ws.WebServiceRefs;
import javax.xml.ws.RespectBinding;
import javax.xml.ws.soap.Addressing;
import javax.xml.ws.soap.MTOM;

/**
 * Annotation used to identify other annotations
 * as a <code>WebServiceFeature</code>.
 * <p>
 * Each <code>WebServiceFeature</code> annotation annotated with
 * this annotation MUST contain an
 * <code>enabled</code> property of type
 * <code>boolean</code> with a default value of <code>true</code>.
 * <p>
 * JAX-WS defines the following
 * <code>WebServiceFeature</code> annotations (<code>Addressing</code>,
 * <code>MTOM</code>, <code>RespectBinding</code>), however, an implementation
 * may define vendors specific annotations for other features.
 * <p>
 * Annotations annotated with <code>WebServiceFeatureAnnotation</code> MUST
 * have the same @Target of {@link WebServiceRef} annotation, so that the resulting
 * feature annotation can be used in conjunction with the {@link WebServiceRef}
 * annotation if necessary.
 * <p>
 * If a JAX-WS implementation encounters an annotation annotated
 * with the <code>WebServiceFeatureAnnotation</code> that it does not
 * recognize/support an error MUST be given.
 * <p>
 *
 * @see Addressing
 * @see MTOM
 * @see RespectBinding
 *
 * @since JAX-WS 2.1
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebServiceFeatureAnnotation {
    /**
     * Unique identifier for the WebServiceFeature.  This
     * identifier MUST be unique across all implementations
     * of JAX-WS.
     */
    String id();

    /**
     * The <code>WebServiceFeature</code> bean that is associated
     * with the <code>WebServiceFeature</code> annotation
     */
    Class<? extends WebServiceFeature> bean();
}
