/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta annotation for defining new types of event metadata.
 * <p>
 * In the following example, a transaction event is defined with two
 * user-defined annotations, {@code @Severity} and {@code @TransactionId}.
 *
 * <pre>
 * <code>
 *{@literal @}MetadataDefinition
 *{@literal @}Label("Severity")
 *{@literal @}Description("Value between 0 and 100 that indicates severity. 100 is most severe.")
 *{@literal @}Retention(RetentionPolicy.RUNTIME)
 *{@literal @}Target({ ElementType.TYPE })
 * public {@literal @}interface {@literal @}Severity {
 *   int value() default 50;
 * }
 *
 *{@literal @}MetadataDefinition
 *{@literal @}Label("Transaction Id")
 *{@literal @}Relational
 *{@literal @}Retention(RetentionPolicy.RUNTIME)
 *{@literal @}Target({ ElementType.FIELD })
 * public {@literal @}interface {@literal @}Severity {
 * }
 *
 *{@literal @}Severity(80)
 *{@literal @}Label("Transaction Blocked");
 * class TransactionBlocked extends Event {
 *  {@literal @}TransactionId
 *  {@literal @}Label("Transaction");
 *   long transactionId;
 *
 *  {@literal @}TransactionId
 *  {@literal @}Label("Transaction Blocker");
 *   long transactionId;
 * }
 *
 * </code>
 * </pre>
 *
 * Adding {@code @MetadataDefinition} to the declaration of {@code @Severity} and {@code @TransactionId}
 * ensures the information is saved by Flight Recorder.
 *
 * @since 8
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface MetadataDefinition {
}
