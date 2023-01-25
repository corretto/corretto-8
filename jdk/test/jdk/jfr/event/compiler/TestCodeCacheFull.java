/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.compiler;

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import sun.hotspot.WhiteBox;
import sun.hotspot.code.BlobType;

/**
 * @test TestCodeCacheFull
 *
 *
 * @library /lib
 *
 * @build sun.hotspot.WhiteBox
 * @run main ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 *
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:-UseLargePages jdk.jfr.event.compiler.TestCodeCacheFull
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     jdk.jfr.event.compiler.TestCodeCacheFull
 */
public class TestCodeCacheFull {

    public static void main(String[] args) throws Exception {
        for (BlobType btype : BlobType.getAvailable()) {
            testWithBlobType(btype, calculateAvailableSize(btype));
        }
    }

    private static void testWithBlobType(BlobType btype, long availableSize) throws Exception {
        Recording r = new Recording();
        r.enable(EventNames.CodeCacheFull);
        r.start();
        WhiteBox.getWhiteBox().allocateCodeBlob(availableSize, btype.id);
        r.stop();

        List<RecordedEvent> events = Events.fromRecording(r);
        Events.hasEvents(events);
        RecordedEvent event = events.get(0);

        String codeBlobType = Events.assertField(event, "codeBlobType").notNull().getValue();
        BlobType blobType = blobTypeFromName(codeBlobType);
        Asserts.assertTrue(blobType.allowTypeWhenOverflow(blobType), "Unexpected overflow BlobType " + blobType.id);
        Events.assertField(event, "entryCount").atLeast(0);
        Events.assertField(event, "methodCount").atLeast(0);
        Events.assertEventThread(event);
        Events.assertField(event, "fullCount").atLeast(0);
        Events.assertField(event, "startAddress").notEqual(0L);
        Events.assertField(event, "commitedTopAddress").notEqual(0L);
        Events.assertField(event, "reservedTopAddress").notEqual(0L);
    }

    private static BlobType blobTypeFromName(String codeBlobTypeName) throws Exception {
        for (BlobType t : BlobType.getAvailable()) {
            if (t.name.equals(codeBlobTypeName)) {
                return t;
            }
        }
        throw new Exception("Unexpected event " + codeBlobTypeName);
    }

    // Compute the available size for this BlobType by taking into account
    // that it may be stored in a different code heap in case it does not fit
    // into the current one.
    private static long calculateAvailableSize(BlobType btype) {
        long availableSize = btype.getSize();
        for (BlobType alternative : BlobType.getAvailable()) {
            if (btype.allowTypeWhenOverflow(alternative)) {
                availableSize = Math.max(availableSize, alternative.getSize());
            }
        }
        return availableSize;
    }
}
