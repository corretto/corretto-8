import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.OutputAnalyzer;

/*
 * @test TestGcLockerEvacFailureThreaded.java
 * @bug 8048556 8137099
 * @summary Ensure that GCLocker does not cause early program termination.
 * @key gc
 * @library /testlibrary
 */
public class TestGcLockerEvacFailureThreaded {
  public static void main(String[] args) throws Exception {
    System.out.println("Beginning test\n");

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                              "-Xmx14m",
                                                              "-XX:MaxGCPauseMillis=10",
                                                              TestGcLocker.class.getName());

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    System.out.println("Output:\n" + output.getOutput());

    output.shouldContain("Threads started");
    output.shouldHaveExitValue(0);
  }

  /**
   * Tests whether GCLocker terminates the application when forced to spin multiple times.
   * We cause a long-running native call during which a GC is invoked by another thread.
   */
  static class TestGcLocker {

    private static int gzipItemLengthBytes = 10000;
    private static int nGzipItems = 1000;
    private static int aliveDataItems = (int) (nGzipItems * 0.7);

    private static int nThreads = 10;
    private static int loopSize = 2000;

    private static List<byte[]> dataToBeGzipped = new ArrayList<>(nGzipItems);

    private static List<byte[]> aliveData = new ArrayList<>(aliveDataItems);

    private static Random randomGenerator = new Random();

    private static volatile boolean cont = true;

    private static void createData(int gzipItemLengthBytes, int nGzipItems) throws IOException {
	for (int gzipDataIndex = 0; gzipDataIndex < nGzipItems; gzipDataIndex++) {
	    ByteBuffer buffer = ByteBuffer.allocate(gzipItemLengthBytes);
	    for (int i = 0; i < gzipItemLengthBytes/4; i++) { // since integer is 4 bytes
	        int randomInt = randomGenerator.nextInt(100);
	        buffer.putInt(randomInt);
	    }
	    byte[] data = buffer.array();
	    dataToBeGzipped.add(data);
	}

	for (int i = 0; i < aliveDataItems; i++) {
	    aliveData.add(new byte[0]);
	}

	for (int gzipDataIndex = 0; gzipDataIndex < nGzipItems; gzipDataIndex++) {
	    native_critical_section(dataToBeGzipped.get(gzipDataIndex));
	}

    }

    public static void runTest(int loopSize) {
	try {
	    int i = 0;
	    while (cont && (i < loopSize)) {
	        i++;
	        try {
	            native_critical_section(dataToBeGzipped.get(i % nGzipItems));
	        } catch (OutOfMemoryError e) {
	            cont = false; //Remove this if you still want to continue after OOME
	            e.printStackTrace();
	        }
	    }

	} catch (IOException e) {
	    cont = true;
	    e.printStackTrace();
	}
    }

    private static void native_critical_section(byte[] data) throws IOException {
	try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
	    gzos.write(data);
	    gzos.finish();
	    byte[] compressedData = bos.toByteArray();
	    int index = randomGenerator.nextInt(aliveDataItems);
	    aliveData.set(index, compressedData);
	}
    }

    public static void main(String[] args) throws InterruptedException, IOException {
	createData(gzipItemLengthBytes, nGzipItems);

	System.gc(); //This will tell us the resident set size

	for (int i = 0; i < nThreads; i++) {
	    Thread t = new Thread() {
	        public void run() {
	            runTest(loopSize);
	        }
	    };
	    t.start();
	}
	System.out.println("Threads started");
    }
  }
}

