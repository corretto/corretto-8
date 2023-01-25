/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

 /*
 * @test
 * @bug 8161266
 * @summary test the FileHandler's new System property
 *  "jdk.internal.FileHandlerLogging.maxLocks" with default value of 100.
 * @library /lib/testlibrary
 * @build jdk.testlibrary.FileUtils
 * @author rpatil
 * @run main/othervm -Djdk.internal.FileHandlerLogging.maxLocks=200 FileHandlerMaxLocksTest
 * @run main/othervm -Djdk.internal.FileHandlerLogging.maxLocks=200ab FileHandlerMaxLocksTest
 */
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import jdk.testlibrary.FileUtils;

public class FileHandlerMaxLocksTest {

    private static final String LOGGER_DIR = "logger-dir";
    private static final String MX_LCK_SYS_PROPERTY =
            "jdk.internal.FileHandlerLogging.maxLocks";

    public static void main(String[] args) throws Exception {
        String maxLocksSet = System.getProperty(MX_LCK_SYS_PROPERTY);
        File loggerDir = createLoggerDir();
        List<FileHandler> fileHandlers = new ArrayList<>();
        try {
            // 200 raises the default limit of 100, we try 102 times
            for (int i = 0; i < 102; i++) {
                fileHandlers.add(new FileHandler(loggerDir.getPath()
                        + File.separator + "test_%u.log"));
            }
        } catch (IOException ie) {
            if (maxLocksSet.equals("200ab")
                    && ie.getMessage().contains("get lock for")) {
                // Ignore: Expected exception while passing bad value- 200ab
            } else {
                throw new RuntimeException("Test Failed: " + ie.getMessage());
            }
        } finally {
            for (FileHandler fh : fileHandlers) {
                fh.close();
            }
            FileUtils.deleteFileTreeWithRetry(Paths.get(loggerDir.getPath()));
        }
    }

    /**
     * Create a writable directory in user directory for the test
     *
     * @return writable directory created that needs to be deleted when done
     * @throws RuntimeException
     */
    private static File createLoggerDir() throws RuntimeException {
        String userDir = System.getProperty("user.dir", ".");
        File loggerDir = new File(userDir, LOGGER_DIR);
        if (!createFile(loggerDir, true)) {
            throw new RuntimeException("Test failed: unable to create"
                    + " writable working directory "
                    + loggerDir.getAbsolutePath());
        }
        // System.out.println("Created Logger Directory: " + loggerDir.getPath());
        return loggerDir;
    }

    /**
     * @param newFile File to be created
     * @param makeDirectory is File to be created is directory
     * @return true if file already exists or creation succeeded
     */
    private static boolean createFile(File newFile, boolean makeDirectory) {
        if (newFile.exists()) {
            return true;
        }
        if (makeDirectory) {
            return newFile.mkdir();
        } else {
            try {
                return newFile.createNewFile();
            } catch (IOException ie) {
                System.err.println("Not able to create file: " + newFile
                        + ", IOException: " + ie.getMessage());
                return false;
            }
        }
    }
}
