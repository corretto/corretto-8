package com.amazon.jdktests.regression;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;

import org.junit.Test;

/**
 * Ensure that java.io.File and java.nio.file.Files both truncate
 * last-modified time to second-level precision.
 *
 * SIM: https://issues.amazon.com/JDK-225
 */
public class MillisecondPrecisionTest {

    private final Instant milliPrecision = Instant.ofEpochMilli(1999L);
    private final Instant secondPrecision = Instant.ofEpochMilli(1000L);

    @Test
    public void lastModified_isAlwaysSecondPrecision() throws IOException {
        File tempFile = Files.createTempFile("MillisecondPrecisionTest", "txt").toFile();
        tempFile.setLastModified(milliPrecision.toEpochMilli());

        long ioTimestamp = tempFile.lastModified();
        long nioTimestamp = Files.getLastModifiedTime(tempFile.toPath()).toMillis();

        assertEquals(secondPrecision.toEpochMilli(), ioTimestamp);
        assertEquals(secondPrecision.toEpochMilli(), nioTimestamp);
        tempFile.delete();
    }
}
