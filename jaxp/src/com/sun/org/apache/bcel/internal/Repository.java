/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal;


import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.util.*;
import java.io.*;

/**
 * The repository maintains informations about class interdependencies, e.g.,
 * whether a class is a sub-class of another. Delegates actual class loading
 * to SyntheticRepository with current class path by default.
 *
 * @see com.sun.org.apache.bcel.internal.util.Repository
 * @see com.sun.org.apache.bcel.internal.util.SyntheticRepository
 *
 * @author <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class Repository {
  private static com.sun.org.apache.bcel.internal.util.Repository _repository =
    SyntheticRepository.getInstance();

  /** @return currently used repository instance
   */
  public static com.sun.org.apache.bcel.internal.util.Repository getRepository() {
    return _repository;
  }

  /** Set repository instance to be used for class loading
   */
  public static void setRepository(com.sun.org.apache.bcel.internal.util.Repository rep) {
    _repository = rep;
  }

  /** Lookup class somewhere found on your CLASSPATH, or whereever the
   * repository instance looks for it.
   *
   * @return class object for given fully qualified class name, or null
   * if the class could not be found or parsed correctly
   */
  public static JavaClass lookupClass(String class_name) {
    try {
      JavaClass clazz = _repository.findClass(class_name);

      if(clazz == null) {
        return _repository.loadClass(class_name);
      } else {
        return clazz;
      }
    } catch(ClassNotFoundException ex) { return null; }
  }

  /**
   * Try to find class source via getResourceAsStream().
   * @see Class
   * @return JavaClass object for given runtime class
   */
  public static JavaClass lookupClass(Class clazz) {
    try {
      return _repository.loadClass(clazz);
    } catch(ClassNotFoundException ex) { return null; }
  }

  /** Clear the repository.
   */
  public static void clearCache() {
    _repository.clear();
  }

  /**
   * Add clazz to repository if there isn't an equally named class already in there.
   *
   * @return old entry in repository
   */
  public static JavaClass addClass(JavaClass clazz) {
    JavaClass old = _repository.findClass(clazz.getClassName());
    _repository.storeClass(clazz);
    return old;
  }

  /**
   * Remove class with given (fully qualified) name from repository.
   */
  public static void removeClass(String clazz) {
    _repository.removeClass(_repository.findClass(clazz));
  }

  /**
   * Remove given class from repository.
   */
  public static void removeClass(JavaClass clazz) {
    _repository.removeClass(clazz);
  }

  /**
   * @return list of super classes of clazz in ascending order, i.e.,
   * Object is always the last element
   */
  public static JavaClass[] getSuperClasses(JavaClass clazz) {
    return clazz.getSuperClasses();
  }

  /**
   * @return list of super classes of clazz in ascending order, i.e.,
   * Object is always the last element. return "null", if class
   * cannot be found.
   */
  public static JavaClass[] getSuperClasses(String class_name) {
    JavaClass jc = lookupClass(class_name);
    return (jc == null? null : getSuperClasses(jc));
  }

  /**
   * @return all interfaces implemented by class and its super
   * classes and the interfaces that those interfaces extend, and so on.
   * (Some people call this a transitive hull).
   */
  public static JavaClass[] getInterfaces(JavaClass clazz) {
    return clazz.getAllInterfaces();
  }

  /**
   * @return all interfaces implemented by class and its super
   * classes and the interfaces that extend those interfaces, and so on
   */
  public static JavaClass[] getInterfaces(String class_name) {
    return getInterfaces(lookupClass(class_name));
  }

  /**
   * Equivalent to runtime "instanceof" operator.
   * @return true, if clazz is an instance of super_class
   */
  public static boolean instanceOf(JavaClass clazz, JavaClass super_class) {
    return clazz.instanceOf(super_class);
  }

  /**
   * @return true, if clazz is an instance of super_class
   */
  public static boolean instanceOf(String clazz, String super_class) {
    return instanceOf(lookupClass(clazz), lookupClass(super_class));
  }

  /**
   * @return true, if clazz is an instance of super_class
   */
  public static boolean instanceOf(JavaClass clazz, String super_class) {
    return instanceOf(clazz, lookupClass(super_class));
  }

  /**
   * @return true, if clazz is an instance of super_class
   */
  public static boolean instanceOf(String clazz, JavaClass super_class) {
    return instanceOf(lookupClass(clazz), super_class);
  }

  /**
   * @return true, if clazz is an implementation of interface inter
   */
  public static boolean implementationOf(JavaClass clazz, JavaClass inter) {
    return clazz.implementationOf(inter);
  }

  /**
   * @return true, if clazz is an implementation of interface inter
   */
  public static boolean implementationOf(String clazz, String inter) {
    return implementationOf(lookupClass(clazz), lookupClass(inter));
  }

  /**
   * @return true, if clazz is an implementation of interface inter
   */
  public static boolean implementationOf(JavaClass clazz, String inter) {
    return implementationOf(clazz, lookupClass(inter));
  }

  /**
   * @return true, if clazz is an implementation of interface inter
   */
  public static boolean implementationOf(String clazz, JavaClass inter) {
    return implementationOf(lookupClass(clazz), inter);
  }
}
