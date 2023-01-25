/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

import java.io.ObjectInputStream;
import java.io.SerializablePermission;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import sun.util.logging.PlatformLogger;

import jdk.internal.util.StaticProperty;

/**
 * Filter classes, array lengths, and graph metrics during deserialization.
 * If set on an {@link ObjectInputStream}, the {@link #checkInput checkInput(FilterInfo)}
 * method is called to validate classes, the length of each array,
 * the number of objects being read from the stream, the depth of the graph,
 * and the total number of bytes read from the stream.
 * <p>
 * A filter can be set via {@link ObjectInputStream#setObjectInputFilter setObjectInputFilter}
 * for an individual ObjectInputStream.
 * A filter can be set via {@link Config#setSerialFilter(ObjectInputFilter) Config.setSerialFilter}
 * to affect every {@code ObjectInputStream} that does not otherwise set a filter.
 * <p>
 * A filter determines whether the arguments are {@link Status#ALLOWED ALLOWED}
 * or {@link Status#REJECTED REJECTED} and should return the appropriate status.
 * If the filter cannot determine the status it should return
 * {@link Status#UNDECIDED UNDECIDED}.
 * Filters should be designed for the specific use case and expected types.
 * A filter designed for a particular use may be passed a class that is outside
 * of the scope of the filter. If the purpose of the filter is to black-list classes
 * then it can reject a candidate class that matches and report UNDECIDED for others.
 * A filter may be called with class equals {@code null}, {@code arrayLength} equal -1,
 * the depth, number of references, and stream size and return a status
 * that reflects only one or only some of the values.
 * This allows a filter to specific about the choice it is reporting and
 * to use other filters without forcing either allowed or rejected status.
 *
 * <p>
 * Typically, a custom filter should check if a process-wide filter
 * is configured and defer to it if so. For example,
 * <pre>{@code
 * ObjectInputFilter.Status checkInput(FilterInfo info) {
 *     ObjectInputFilter serialFilter = ObjectInputFilter.Config.getSerialFilter();
 *     if (serialFilter != null) {
 *         ObjectInputFilter.Status status = serialFilter.checkInput(info);
 *         if (status != ObjectInputFilter.Status.UNDECIDED) {
 *             // The process-wide filter overrides this filter
 *             return status;
 *         }
 *     }
 *     if (info.serialClass() != null &&
 *         Remote.class.isAssignableFrom(info.serialClass())) {
 *         return Status.REJECTED;      // Do not allow Remote objects
 *     }
 *     return Status.UNDECIDED;
 * }
 *}</pre>
 * <p>
 * Unless otherwise noted, passing a {@code null} argument to a
 * method in this interface and its nested classes will cause a
 * {@link NullPointerException} to be thrown.
 *
 * @since 8u
 */
@FunctionalInterface
public interface ObjectInputFilter {

    /**
     * Check the class, array length, number of object references, depth,
     * stream size, and other available filtering information.
     * Implementations of this method check the contents of the object graph being created
     * during deserialization. The filter returns {@link Status#ALLOWED Status.ALLOWED},
     * {@link Status#REJECTED Status.REJECTED}, or {@link Status#UNDECIDED Status.UNDECIDED}.
     *
     * @param filterInfo provides information about the current object being deserialized,
     *             if any, and the status of the {@link ObjectInputStream}
     * @return  {@link Status#ALLOWED Status.ALLOWED} if accepted,
     *          {@link Status#REJECTED Status.REJECTED} if rejected,
     *          {@link Status#UNDECIDED Status.UNDECIDED} if undecided.
     */
    Status checkInput(FilterInfo filterInfo);

    /**
     * FilterInfo provides access to information about the current object
     * being deserialized and the status of the {@link ObjectInputStream}.
     * @since 9
     */
    interface FilterInfo {
        /**
         * The class of an object being deserialized.
         * For arrays, it is the array type.
         * For example, the array class name of a 2 dimensional array of strings is
         * "{@code [[Ljava.lang.String;}".
         * To check the array's element type, iteratively use
         * {@link Class#getComponentType() Class.getComponentType} while the result
         * is an array and then check the class.
         * The {@code serialClass is null} in the case where a new object is not being
         * created and to give the filter a chance to check the depth, number of
         * references to existing objects, and the stream size.
         *
         * @return class of an object being deserialized; may be null
         */
        Class<?> serialClass();

        /**
         * The number of array elements when deserializing an array of the class.
         *
         * @return the non-negative number of array elements when deserializing
         * an array of the class, otherwise -1
         */
        long arrayLength();

        /**
         * The current depth.
         * The depth starts at {@code 1} and increases for each nested object and
         * decrements when each nested object returns.
         *
         * @return the current depth
         */
        long depth();

        /**
         * The current number of object references.
         *
         * @return the non-negative current number of object references
         */
        long references();

        /**
         * The current number of bytes consumed.
         * @implSpec  {@code streamBytes} is implementation specific
         * and may not be directly related to the object in the stream
         * that caused the callback.
         *
         * @return the non-negative current number of bytes consumed
         */
        long streamBytes();
    }

    /**
     * The status of a check on the class, array length, number of references,
     * depth, and stream size.
     *
     * @since 8u
     */
    enum Status {
        /**
         * The status is undecided, not allowed and not rejected.
         */
        UNDECIDED,
        /**
         * The status is allowed.
         */
        ALLOWED,
        /**
         * The status is rejected.
         */
        REJECTED;
    }

    /**
     * A utility class to set and get the process-wide filter or create a filter
     * from a pattern string. If a process-wide filter is set, it will be
     * used for each {@link ObjectInputStream} that does not set its own filter.
     * <p>
     * When setting the filter, it should be stateless and idempotent,
     * reporting the same result when passed the same arguments.
     * <p>
     * The filter is configured using the {@link java.security.Security}
     * property {@code jdk.serialFilter} and can be overridden by
     * the System property {@code jdk.serialFilter}.
     *
     * The syntax is the same as for the {@link #createFilter(String) createFilter} method.
     *
     * @since 8u
     */
    final class Config {
        /* No instances. */
        private Config() {}

        /**
         * Lock object for process-wide filter.
         */
        private final static Object serialFilterLock = new Object();

        /**
         * Debug: Logger
         */
        private final static PlatformLogger configLog;

        /**
         * Logger for debugging.
         */
        static void filterLog(PlatformLogger.Level level, String msg, Object... args) {
            if (configLog != null) {
                if (PlatformLogger.Level.INFO.equals(level)) {
                    configLog.info(msg, args);
                } else if (PlatformLogger.Level.WARNING.equals(level)) {
                    configLog.warning(msg, args);
                } else {
                    configLog.severe(msg, args);
                }
            }
        }

        /**
         * The name for the process-wide deserialization filter.
         * Used as a system property and a java.security.Security property.
         */
        private final static String SERIAL_FILTER_PROPNAME = "jdk.serialFilter";

        /**
         * The process-wide filter; may be null.
         * Lookup the filter in java.security.Security or
         * the system property.
         */
        private final static ObjectInputFilter configuredFilter;

        static {
            configuredFilter = AccessController
                    .doPrivileged((PrivilegedAction<ObjectInputFilter>) () -> {
                        String props = StaticProperty.jdkSerialFilter();
                        if (props == null) {
                            props = Security.getProperty(SERIAL_FILTER_PROPNAME);
                        }
                        if (props != null) {
                            PlatformLogger log = PlatformLogger.getLogger("java.io.serialization");
                            log.info("Creating serialization filter from {0}", props);
                            try {
                                return createFilter(props);
                            } catch (RuntimeException re) {
                                log.warning("Error configuring filter: {0}", re);
                            }
                        }
                        return null;
                    });
            configLog = (configuredFilter != null) ? PlatformLogger.getLogger("java.io.serialization") : null;
        }

        /**
         * Current configured filter.
         */
        private static ObjectInputFilter serialFilter = configuredFilter;

        /**
         * Get the filter for classes being deserialized on the ObjectInputStream.
         *
         * @param inputStream ObjectInputStream from which to get the filter; non-null
         * @throws RuntimeException if the filter rejects
         */
        public static ObjectInputFilter getObjectInputFilter(ObjectInputStream inputStream) {
            Objects.requireNonNull(inputStream, "inputStream");
            return sun.misc.SharedSecrets.getJavaOISAccess().getObjectInputFilter(inputStream);
        }

        /**
         * Set the process-wide filter if it has not already been configured or set.
         *
         * @param inputStream ObjectInputStream on which to set the filter; non-null
         * @param filter the serialization filter to set as the process-wide filter; not null
         * @throws SecurityException if there is security manager and the
         *       {@code SerializablePermission("serialFilter")} is not granted
         * @throws IllegalStateException if the filter has already been set {@code non-null}
         */
        public static void setObjectInputFilter(ObjectInputStream inputStream,
                                                ObjectInputFilter filter) {
            Objects.requireNonNull(inputStream, "inputStream");
            sun.misc.SharedSecrets.getJavaOISAccess().setObjectInputFilter(inputStream, filter);
        }

        /**
         * Returns the process-wide serialization filter or {@code null} if not configured.
         *
         * @return the process-wide serialization filter or {@code null} if not configured
         */
        public static ObjectInputFilter getSerialFilter() {
            synchronized (serialFilterLock) {
                return serialFilter;
            }
        }

        /**
         * Set the process-wide filter if it has not already been configured or set.
         *
         * @param filter the serialization filter to set as the process-wide filter; not null
         * @throws SecurityException if there is security manager and the
         *       {@code SerializablePermission("serialFilter")} is not granted
         * @throws IllegalStateException if the filter has already been set {@code non-null}
         */
        public static void setSerialFilter(ObjectInputFilter filter) {
            Objects.requireNonNull(filter, "filter");
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(new SerializablePermission("serialFilter"));
            }
            synchronized (serialFilterLock) {
                if (serialFilter != null) {
                    throw new IllegalStateException("Serial filter can only be set once");
                }
                serialFilter = filter;
            }
        }

        /**
         * Returns an ObjectInputFilter from a string of patterns.
         * <p>
         * Patterns are separated by ";" (semicolon). Whitespace is significant and
         * is considered part of the pattern.
         * If a pattern includes an equals assignment, "{@code =}" it sets a limit.
         * If a limit appears more than once the last value is used.
         * <ul>
         *     <li>maxdepth={@code value} - the maximum depth of a graph</li>
         *     <li>maxrefs={@code value}  - the maximum number of internal references</li>
         *     <li>maxbytes={@code value} - the maximum number of bytes in the input stream</li>
         *     <li>maxarray={@code value} - the maximum array length allowed</li>
         * </ul>
         * <p>
         * Other patterns match or reject class or package name
         * as returned from {@link Class#getName() Class.getName()}.
         * Note that for arrays the element type is used in the pattern,
         * not the array type.
         * <ul>
         * <li>If the pattern starts with "!", the class is rejected if the remaining pattern is matched;
         *     otherwise the class is allowed if the pattern matches.
         * <li>If the pattern ends with ".**" it matches any class in the package and all subpackages.
         * <li>If the pattern ends with ".*" it matches any class in the package.
         * <li>If the pattern ends with "*", it matches any class with the pattern as a prefix.
         * <li>If the pattern is equal to the class name, it matches.
         * <li>Otherwise, the pattern is not matched.
         * </ul>
         * <p>
         * The resulting filter performs the limit checks and then
         * tries to match the class, if any. If any of the limits are exceeded,
         * the filter returns {@link Status#REJECTED Status.REJECTED}.
         * If the class is an array type, the class to be matched is the element type.
         * Arrays of any number of dimensions are treated the same as the element type.
         * For example, a pattern of "{@code !example.Foo}",
         * rejects creation of any instance or array of {@code example.Foo}.
         * The first pattern that matches, working from left to right, determines
         * the {@link Status#ALLOWED Status.ALLOWED}
         * or {@link Status#REJECTED Status.REJECTED} result.
         * If nothing matches, the result is {@link Status#UNDECIDED Status.UNDECIDED}.
         *
         * @param pattern the pattern string to parse; not null
         * @return a filter to check a class being deserialized; may be null;
         *          {@code null} if no patterns
         * @throws IllegalArgumentException
         *                if a limit is missing the name, or the long value
         *                is not a number or is negative,
         *                or if the package is missing for ".*" and ".**"
         */
        public static ObjectInputFilter createFilter(String pattern) {
            Objects.requireNonNull(pattern, "pattern");
            return Global.createFilter(pattern, true);
        }

        /**
         * Returns an ObjectInputFilter from a string of patterns that
         * checks only the length for arrays, not the component type.
         *
         * @param pattern the pattern string to parse; not null
         * @return a filter to check a class being deserialized;
         *          {@code null} if no patterns
         */
        public static ObjectInputFilter createFilter2(String pattern) {
            Objects.requireNonNull(pattern, "pattern");
            return Global.createFilter(pattern, false);
        }

        /**
         * Implementation of ObjectInputFilter that performs the checks of
         * the process-wide serialization filter. If configured, it will be
         * used for all ObjectInputStreams that do not set their own filters.
         *
         */
        final static class Global implements ObjectInputFilter {
            /**
             * The pattern used to create the filter.
             */
            private final String pattern;
            /**
             * The list of class filters.
             */
            private final List<Function<Class<?>, Status>> filters;
            /**
             * Maximum allowed bytes in the stream.
             */
            private long maxStreamBytes;
            /**
             * Maximum depth of the graph allowed.
             */
            private long maxDepth;
            /**
             * Maximum number of references in a graph.
             */
            private long maxReferences;
            /**
             * Maximum length of any array.
             */
            private long maxArrayLength;
            /**
             * True to check the component type for arrays.
             */
            private final boolean checkComponentType;

            /**
             * Returns an ObjectInputFilter from a string of patterns.
             *
             * @param pattern the pattern string to parse
             * @param checkComponentType true if the filter should check
             *                           the component type of arrays
             * @return a filter to check a class being deserialized; not null
             * @throws IllegalArgumentException if the parameter is malformed
             *                if the pattern is missing the name, the long value
             *                is not a number or is negative.
             */
            static ObjectInputFilter createFilter(String pattern, boolean checkComponentType) {
                Global filter = new Global(pattern, checkComponentType);
                return filter.isEmpty() ? null : filter;
            }

            /**
             * Construct a new filter from the pattern String.
             *
             * @param pattern a pattern string of filters
             * @param checkComponentType true if the filter should check
             *                           the component type of arrays
             * @throws IllegalArgumentException if the pattern is malformed
             */
            private Global(String pattern, boolean checkComponentType) {
                this.pattern = pattern;
                this.checkComponentType = checkComponentType;

                maxArrayLength = Long.MAX_VALUE; // Default values are unlimited
                maxDepth = Long.MAX_VALUE;
                maxReferences = Long.MAX_VALUE;
                maxStreamBytes = Long.MAX_VALUE;

                String[] patterns = pattern.split(";");
                filters = new ArrayList<>(patterns.length);
                for (int i = 0; i < patterns.length; i++) {
                    String p = patterns[i];
                    int nameLen = p.length();
                    if (nameLen == 0) {
                        continue;
                    }
                    if (parseLimit(p)) {
                        // If the pattern contained a limit setting, i.e. type=value
                        continue;
                    }
                    boolean negate = p.charAt(0) == '!';

                    if (p.indexOf('/') >= 0) {
                        throw new IllegalArgumentException("invalid character \"/\" in: \"" + pattern + "\"");
                    }

                    if (p.endsWith("*")) {
                        // Wildcard cases
                        if (p.endsWith(".*")) {
                            // Pattern is a package name with a wildcard
                            final String pkg = p.substring(negate ? 1 : 0, nameLen - 1);
                            if (pkg.length() < 2) {
                                throw new IllegalArgumentException("package missing in: \"" + pattern + "\"");
                            }
                            if (negate) {
                                // A Function that fails if the class starts with the pattern, otherwise don't care
                                filters.add(c -> matchesPackage(c, pkg) ? Status.REJECTED : Status.UNDECIDED);
                            } else {
                                // A Function that succeeds if the class starts with the pattern, otherwise don't care
                                filters.add(c -> matchesPackage(c, pkg) ? Status.ALLOWED : Status.UNDECIDED);
                            }
                        } else if (p.endsWith(".**")) {
                            // Pattern is a package prefix with a double wildcard
                            final String pkgs = p.substring(negate ? 1 : 0, nameLen - 2);
                            if (pkgs.length() < 2) {
                                throw new IllegalArgumentException("package missing in: \"" + pattern + "\"");
                            }
                            if (negate) {
                                // A Function that fails if the class starts with the pattern, otherwise don't care
                                filters.add(c -> c.getName().startsWith(pkgs) ? Status.REJECTED : Status.UNDECIDED);
                            } else {
                                // A Function that succeeds if the class starts with the pattern, otherwise don't care
                                filters.add(c -> c.getName().startsWith(pkgs) ? Status.ALLOWED : Status.UNDECIDED);
                            }
                        } else {
                            // Pattern is a classname (possibly empty) with a trailing wildcard
                            final String className = p.substring(negate ? 1 : 0, nameLen - 1);
                            if (negate) {
                                // A Function that fails if the class starts with the pattern, otherwise don't care
                                filters.add(c -> c.getName().startsWith(className) ? Status.REJECTED : Status.UNDECIDED);
                            } else {
                                // A Function that succeeds if the class starts with the pattern, otherwise don't care
                                filters.add(c -> c.getName().startsWith(className) ? Status.ALLOWED : Status.UNDECIDED);
                            }
                        }
                    } else {
                        final String name = p.substring(negate ? 1 : 0);
                        if (name.isEmpty()) {
                            throw new IllegalArgumentException("class or package missing in: \"" + pattern + "\"");
                        }
                        // Pattern is a class name
                        if (negate) {
                            // A Function that fails if the class equals the pattern, otherwise don't care
                            filters.add(c -> c.getName().equals(name) ? Status.REJECTED : Status.UNDECIDED);
                        } else {
                            // A Function that succeeds if the class equals the pattern, otherwise don't care
                            filters.add(c -> c.getName().equals(name) ? Status.ALLOWED : Status.UNDECIDED);
                        }

                    }
                }
            }

            /**
             * Returns if this filter has any checks.
             * @return {@code true} if the filter has any checks, {@code false} otherwise
             */
            private boolean isEmpty() {
                return filters.isEmpty() &&
                        maxArrayLength == Long.MAX_VALUE &&
                        maxDepth == Long.MAX_VALUE &&
                        maxReferences == Long.MAX_VALUE &&
                        maxStreamBytes == Long.MAX_VALUE;
            }

            /**
             * Parse out a limit for one of maxarray, maxdepth, maxbytes, maxreferences.
             *
             * @param pattern a string with a type name, '=' and a value
             * @return {@code true} if a limit was parsed, else {@code false}
             * @throws IllegalArgumentException if the pattern is missing
             *                the name, the Long value is not a number or is negative.
             */
            private boolean parseLimit(String pattern) {
                int eqNdx = pattern.indexOf('=');
                if (eqNdx < 0) {
                    // not a limit pattern
                    return false;
                }
                String valueString = pattern.substring(eqNdx + 1);
                if (pattern.startsWith("maxdepth=")) {
                    maxDepth = parseValue(valueString);
                } else if (pattern.startsWith("maxarray=")) {
                    maxArrayLength = parseValue(valueString);
                } else if (pattern.startsWith("maxrefs=")) {
                    maxReferences = parseValue(valueString);
                } else if (pattern.startsWith("maxbytes=")) {
                    maxStreamBytes = parseValue(valueString);
                } else {
                    throw new IllegalArgumentException("unknown limit: " + pattern.substring(0, eqNdx));
                }
                return true;
            }

            /**
             * Parse the value of a limit and check that it is non-negative.
             * @param string inputstring
             * @return the parsed value
             * @throws IllegalArgumentException if parsing the value fails or the value is negative
             */
            private static long parseValue(String string) throws IllegalArgumentException {
                // Parse a Long from after the '=' to the end
                long value = Long.parseLong(string);
                if (value < 0) {
                    throw new IllegalArgumentException("negative limit: " + string);
                }
                return value;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Status checkInput(FilterInfo filterInfo) {
                if (filterInfo.references() < 0
                        || filterInfo.depth() < 0
                        || filterInfo.streamBytes() < 0
                        || filterInfo.references() > maxReferences
                        || filterInfo.depth() > maxDepth
                        || filterInfo.streamBytes() > maxStreamBytes) {
                    return Status.REJECTED;
                }

                Class<?> clazz = filterInfo.serialClass();
                if (clazz != null) {
                    if (clazz.isArray()) {
                        if (filterInfo.arrayLength() >= 0 && filterInfo.arrayLength() > maxArrayLength) {
                            // array length is too big
                            return Status.REJECTED;
                        }
                        if (!checkComponentType) {
                            // As revised; do not check the component type for arrays
                            return Status.UNDECIDED;
                        }
                        do {
                            // Arrays are decided based on the component type
                            clazz = clazz.getComponentType();
                        } while (clazz.isArray());
                    }

                    if (clazz.isPrimitive())  {
                        // Primitive types are undecided; let someone else decide
                        return Status.UNDECIDED;
                    } else {
                        // Find any filter that allowed or rejected the class
                        final Class<?> cl = clazz;
                        Optional<Status> status = filters.stream()
                                .map(f -> f.apply(cl))
                                .filter(p -> p != Status.UNDECIDED)
                                .findFirst();
                        return status.orElse(Status.UNDECIDED);
                    }
                }
                return Status.UNDECIDED;
            }

            /**
             * Returns {@code true} if the class is in the package.
             *
             * @param c   a class
             * @param pkg a package name (including the trailing ".")
             * @return {@code true} if the class is in the package,
             * otherwise {@code false}
             */
            private static boolean matchesPackage(Class<?> c, String pkg) {
                String n = c.getName();
                return n.startsWith(pkg) && n.lastIndexOf('.') == pkg.length() - 1;
            }

            /**
             * Returns the pattern used to create this filter.
             * @return the pattern used to create this filter
             */
            @Override
            public String toString() {
                return pattern;
            }
        }
    }
}
