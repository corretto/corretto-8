/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6397298 6400986 6425592 6449798 6453386 6508401 6498938 6911854 8030049 8038080
 * @summary Tests that getElementsAnnotatedWith works properly.
 * @author  Joseph D. Darcy
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor
 * @compile TestElementsAnnotatedWith.java
 * @compile InheritedAnnotation.java
 * @compile TpAnno.java
 * @compile Anno.java
 * @compile -processor TestElementsAnnotatedWith -proc:only SurfaceAnnotations.java
 * @compile -processor TestElementsAnnotatedWith -proc:only BuriedAnnotations.java
 * @compile -processor TestElementsAnnotatedWith -proc:only Part1.java Part2.java
 * @compile -processor TestElementsAnnotatedWith -proc:only C2.java
 * @compile -processor TestElementsAnnotatedWith -proc:only Foo.java
 * @compile -processor TestElementsAnnotatedWith -proc:only TypeParameterAnnotations.java
 * @compile -processor TestElementsAnnotatedWith -proc:only ParameterAnnotations.java
 * @compile/fail/ref=ErroneousAnnotations.out -processor TestElementsAnnotatedWith -proc:only -XDrawDiagnostics ErroneousAnnotations.java
 * @compile Foo.java
 * @compile/process -processor TestElementsAnnotatedWith -proc:only Foo
 */

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import static javax.lang.model.util.ElementFilter.*;

/**
 * This processor verifies that the information returned by
 * getElementsAnnotatedWith is consistent with the expected results
 * stored in an AnnotatedElementInfo annotation.
 */
@AnnotatedElementInfo(annotationName="java.lang.SuppressWarnings", expectedSize=0, names={})
public class TestElementsAnnotatedWith extends JavacTestingAbstractProcessor {

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnvironment) {
        TypeElement annotatedElementInfoElement =
            elements.getTypeElement("AnnotatedElementInfo");
        Set<? extends Element> resultsMeta = Collections.emptySet();
        Set<? extends Element> resultsBase = Collections.emptySet();

        if (!roundEnvironment.processingOver()) {
            testNonAnnotations(roundEnvironment);

            // Verify AnnotatedElementInfo is present on the first
            // specified type.

            TypeElement firstType = typesIn(roundEnvironment.getRootElements()).iterator().next();

            AnnotatedElementInfo annotatedElementInfo = firstType.getAnnotation(AnnotatedElementInfo.class);

            boolean failed = false;

            if (annotatedElementInfo == null)
                throw new IllegalArgumentException("Missing AnnotatedElementInfo annotation on " +
                                                  firstType);
            else {
                // Verify that the annotation information is as
                // expected.

                Set<String> expectedNames = new HashSet<String>(Arrays.asList(annotatedElementInfo.names()));

                resultsMeta =
                    roundEnvironment.
                    getElementsAnnotatedWith(elements.getTypeElement(annotatedElementInfo.annotationName()));

                if (!resultsMeta.isEmpty())
                    System.err.println("Results: " + resultsMeta);

                if (resultsMeta.size() != annotatedElementInfo.expectedSize()) {
                    failed = true;
                    System.err.printf("Bad number of elements; expected %d, got %d%n",
                                      annotatedElementInfo.expectedSize(), resultsMeta.size());
                } else {
                    for(Element element : resultsMeta) {
                        String simpleName = element.getSimpleName().toString();
                        if (!expectedNames.contains(simpleName) ) {
                            failed = true;
                            System.err.println("Name ``" + simpleName + "'' not expected.");
                        }
                    }
                }
            }

            resultsBase = computeResultsBase(roundEnvironment, annotatedElementInfo.annotationName());

            if (!resultsMeta.equals(resultsBase)) {
                failed = true;
                System.err.println("Base and Meta sets unequal;\n meta: " + resultsMeta +
                                   "\nbase: " + resultsBase);
            }

            if (failed) {
                System.err.println("AnnotatedElementInfo: " + annotatedElementInfo);
                throw new RuntimeException();
            }
        } else {
            // If processing is over without an error, the specified
            // elements should be empty so an empty set should be returned.
            resultsMeta = roundEnvironment.getElementsAnnotatedWith(annotatedElementInfoElement);
            resultsBase = roundEnvironment.getElementsAnnotatedWith(AnnotatedElementInfo.class);
            if (!resultsMeta.isEmpty())
                throw new RuntimeException("Nonempty resultsMeta: " + resultsMeta);
            if (!resultsBase.isEmpty())
                throw new RuntimeException("Nonempty resultsBase: " + resultsBase);

        }
        return true;
    }

    private Set<? extends Element> computeResultsBase(RoundEnvironment roundEnvironment, String name) {
        try {
            return roundEnvironment.
                getElementsAnnotatedWith(Class.forName(name).asSubclass(Annotation.class));
        } catch(ClassNotFoundException cnfe) {
            throw new RuntimeException(cnfe);
        }
    }

    /**
     * Verify non-annotation types result in
     * IllegalArgumentExceptions.
     */
    private void testNonAnnotations(RoundEnvironment roundEnvironment) {
        try {
            Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith((Class)Object.class );
            throw new RuntimeException("Illegal argument exception not thrown");
        } catch(IllegalArgumentException iae) {}

        try {
            Set<? extends Element> elements =
                roundEnvironment.getElementsAnnotatedWith(processingEnv.
                                                          getElementUtils().
                                                          getTypeElement("java.lang.Object") );
            throw new RuntimeException("Illegal argument exception not thrown");
        } catch(IllegalArgumentException iae) {}
    }
}
