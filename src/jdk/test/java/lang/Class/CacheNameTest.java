import java.lang.annotation.*;

/**
 * SIM issue: JDK-178
 *
 * @test
 * @bug 8187123
 * @summary Verify that Class.getSimpleName and Class.getCanonicalName
 *          behaves the same as before.
 */

public class CacheNameTest {
    static int testSimpleName(Class clazz, String expected) {
        int status = (clazz.getSimpleName().equals(expected))? 0 : 1;

        if (status == 1) {
            System.err.println("Unexpected simple name for " + clazz);
        }
        return status;
    }

    static int testCanonicalName(Class clazz, String expected) {
        int status = (clazz.getCanonicalName().equals(expected))? 0 : 1;

        if (status == 1) {
            System.err.println("Unexpected canonical name for " + clazz);
        }
        return status;
    }

    static int checkNames() {
        int failures = 0;

        // Check simple name
        failures += testSimpleName(CacheNameTest.class, "CacheNameTest");
        failures += testSimpleName(String.class, "String");
        failures += testSimpleName(java.lang.Thread.class, "Thread");
        failures += testSimpleName(java.math.RoundingMode.class, "RoundingMode");
        failures += testSimpleName(java.lang.reflect.Method.class, "Method");
        failures += testSimpleName(Annotation.class, "Annotation");
        failures += testSimpleName(ElementType.class, "ElementType");
        failures += testSimpleName(Retention.class, "Retention");
        failures += testSimpleName(RetentionPolicy.class, "RetentionPolicy");

        // Check canonical name
        failures += testCanonicalName(CacheNameTest.class, "CacheNameTest");
        failures += testCanonicalName(String.class, "java.lang.String");
        failures += testCanonicalName(java.lang.Thread.class, "java.lang.Thread");
        failures += testCanonicalName(java.math.RoundingMode.class, "java.math.RoundingMode");
        failures += testCanonicalName(java.lang.reflect.Method.class, "java.lang.reflect.Method");
        failures += testCanonicalName(Annotation.class, "java.lang.annotation.Annotation");
        failures += testCanonicalName(ElementType.class, "java.lang.annotation.ElementType");
        failures += testCanonicalName(Retention.class, "java.lang.annotation.Retention");
        failures += testCanonicalName(RetentionPolicy.class, "java.lang.annotation.RetentionPolicy");

        return failures;
    }

    public static void main(String argv[]) {
        int failures = 0;

        // First call cache simple names and canonical names
        failures += checkNames();

        // Second call check cached names
        failures += checkNames();

        if (failures > 0) {
            throw new RuntimeException("Unexpected cached name detected.");
        }
    }
}
