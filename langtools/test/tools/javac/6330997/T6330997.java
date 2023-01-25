/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @bug     6330997 7025789
 * @summary javac should accept class files with major version of the next release
 * @author  Wei Tao
 * @clean T1 T2
 * @compile -target 8 T1.java
 * @compile -target 8 T2.java
 * @run main/othervm T6330997
 */

import java.nio.*;
import java.io.*;
import java.nio.channels.*;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.jvm.ClassReader.BadClassFile;
import com.sun.tools.javac.main.JavaCompiler;
import javax.tools.ToolProvider;

public class T6330997 {
    public static void main(String... args) {
        increaseMajor("T1.class", 1);
        increaseMajor("T2.class", 2);
        javax.tools.JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavacTaskImpl task = (JavacTaskImpl)tool.getTask(null, null, null, null, null, null);
        JavaCompiler compiler = JavaCompiler.instance(task.getContext());
        try {
            compiler.resolveIdent("T1").complete();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed: unexpected exception while reading class T1");
        }
        try {
            compiler.resolveIdent("T2").complete();
        } catch (BadClassFile e) {
            System.err.println("Passed: expected completion failure " + e.getClass().getName());
            return;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed: unexpected exception while reading class T2");
        }
        throw new RuntimeException("Failed: no error reported");
    }

    // Increase class file cfile's major version by delta
    static void increaseMajor(String cfile, int delta) {
        try {
            RandomAccessFile cls = new RandomAccessFile(
                    new File(System.getProperty("test.classes", "."), cfile), "rw");
            FileChannel fc = cls.getChannel();
            ByteBuffer rbuf = ByteBuffer.allocate(2);
            fc.read(rbuf, 6);
            ByteBuffer wbuf = ByteBuffer.allocate(2);
            wbuf.putShort(0, (short)(rbuf.getShort(0) + delta));
            fc.write(wbuf, 6);
            fc.force(false);
            cls.close();
         } catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("Failed: unexpected exception");
         }
     }
}
