/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import com.oracle.java.testlibrary.*;

/*
 * @test
 * @bug 8038636
 * @library /testlibrary
 * @build Agent
 * @run main ClassFileInstaller Agent
 * @run main Launcher
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:TypeProfileLevel=222 -Xmx1M -XX:ReservedCodeCacheSize=3M Agent
 */
public class Launcher {
    public static void main(String[] args) throws Exception  {

      PrintWriter pw = new PrintWriter("MANIFEST.MF");
      pw.println("Agent-Class: Agent");
      pw.println("Can-Retransform-Classes: true");
      pw.close();

      ProcessBuilder pb = new ProcessBuilder();
      pb.command(new String[] { JDKToolFinder.getJDKTool("jar"), "cmf", "MANIFEST.MF", System.getProperty("test.classes",".") + "/agent.jar", "Agent.class"});
      pb.start().waitFor();
    }
}
