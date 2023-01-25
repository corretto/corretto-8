/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javax.imageio.ImageTypeSpecifier;
import com.sun.imageio.plugins.common.StandardMetadataFormat;

/**
 * A concrete class providing a reusable implementation of the
 * <code>IIOMetadataFormat</code> interface.  In addition, a static
 * instance representing the standard, plug-in neutral
 * <code>javax_imageio_1.0</code> format is provided by the
 * <code>getStandardFormatInstance</code> method.
 *
 * <p> In order to supply localized descriptions of elements and
 * attributes, a <code>ResourceBundle</code> with a base name of
 * <code>this.getClass().getName() + "Resources"</code> should be
 * supplied via the usual mechanism used by
 * <code>ResourceBundle.getBundle</code>.  Briefly, the subclasser
 * supplies one or more additional classes according to a naming
 * convention (by default, the fully-qualified name of the subclass
 * extending <code>IIMetadataFormatImpl</code>, plus the string
 * "Resources", plus the country, language, and variant codes
 * separated by underscores).  At run time, calls to
 * <code>getElementDescription</code> or
 * <code>getAttributeDescription</code> will attempt to load such
 * classes dynamically according to the supplied locale, and will use
 * either the element name, or the element name followed by a '/'
 * character followed by the attribute name as a key.  This key will
 * be supplied to the <code>ResourceBundle</code>'s
 * <code>getString</code> method, and the resulting localized
 * description of the node or attribute is returned.
 *
 * <p> The subclass may supply a different base name for the resource
 * bundles using the <code>setResourceBaseName</code> method.
 *
 * <p> A subclass may choose its own localization mechanism, if so
 * desired, by overriding the supplied implementations of
 * <code>getElementDescription</code> and
 * <code>getAttributeDescription</code>.
 *
 * @see ResourceBundle#getBundle(String,Locale)
 *
 */
public abstract class IIOMetadataFormatImpl implements IIOMetadataFormat {

    /**
     * A <code>String</code> constant containing the standard format
     * name, <code>"javax_imageio_1.0"</code>.
     */
    public static final String standardMetadataFormatName =
        "javax_imageio_1.0";

    private static IIOMetadataFormat standardFormat = null;

    private String resourceBaseName = this.getClass().getName() + "Resources";

    private String rootName;

    // Element name (String) -> Element
    private HashMap elementMap = new HashMap();

    class Element {
        String elementName;

        int childPolicy;
        int minChildren = 0;
        int maxChildren = 0;

        // Child names (Strings)
        List childList = new ArrayList();

        // Parent names (Strings)
        List parentList = new ArrayList();

        // List of attribute names in the order they were added
        List attrList = new ArrayList();
        // Attr name (String) -> Attribute
        Map attrMap = new HashMap();

        ObjectValue objectValue;
    }

    class Attribute {
        String attrName;

        int valueType = VALUE_ARBITRARY;
        int dataType;
        boolean required;
        String defaultValue = null;

        // enumeration
        List enumeratedValues;

        // range
        String minValue;
        String maxValue;

        // list
        int listMinLength;
        int listMaxLength;
    }

    class ObjectValue {
        int valueType = VALUE_NONE;
        Class classType = null;
        Object defaultValue = null;

        // Meaningful only if valueType == VALUE_ENUMERATION
        List enumeratedValues = null;

        // Meaningful only if valueType == VALUE_RANGE
        Comparable minValue = null;
        Comparable maxValue = null;

        // Meaningful only if valueType == VALUE_LIST
        int arrayMinLength = 0;
        int arrayMaxLength = 0;
    }

    /**
     * Constructs a blank <code>IIOMetadataFormatImpl</code> instance,
     * with a given root element name and child policy (other than
     * <code>CHILD_POLICY_REPEAT</code>).  Additional elements, and
     * their attributes and <code>Object</code> reference information
     * may be added using the various <code>add</code> methods.
     *
     * @param rootName the name of the root element.
     * @param childPolicy one of the <code>CHILD_POLICY_*</code> constants,
     * other than <code>CHILD_POLICY_REPEAT</code>.
     *
     * @exception IllegalArgumentException if <code>rootName</code> is
     * <code>null</code>.
     * @exception IllegalArgumentException if <code>childPolicy</code> is
     * not one of the predefined constants.
     */
    public IIOMetadataFormatImpl(String rootName,
                                 int childPolicy) {
        if (rootName == null) {
            throw new IllegalArgumentException("rootName == null!");
        }
        if (childPolicy < CHILD_POLICY_EMPTY ||
            childPolicy > CHILD_POLICY_MAX ||
            childPolicy == CHILD_POLICY_REPEAT) {
            throw new IllegalArgumentException("Invalid value for childPolicy!");
        }

        this.rootName = rootName;

        Element root = new Element();
        root.elementName = rootName;
        root.childPolicy = childPolicy;

        elementMap.put(rootName, root);
    }

    /**
     * Constructs a blank <code>IIOMetadataFormatImpl</code> instance,
     * with a given root element name and a child policy of
     * <code>CHILD_POLICY_REPEAT</code>.  Additional elements, and
     * their attributes and <code>Object</code> reference information
     * may be added using the various <code>add</code> methods.
     *
     * @param rootName the name of the root element.
     * @param minChildren the minimum number of children of the node.
     * @param maxChildren the maximum number of children of the node.
     *
     * @exception IllegalArgumentException if <code>rootName</code> is
     * <code>null</code>.
     * @exception IllegalArgumentException if <code>minChildren</code>
     * is negative or larger than <code>maxChildren</code>.
     */
    public IIOMetadataFormatImpl(String rootName,
                                 int minChildren,
                                 int maxChildren) {
        if (rootName == null) {
            throw new IllegalArgumentException("rootName == null!");
        }
        if (minChildren < 0) {
            throw new IllegalArgumentException("minChildren < 0!");
        }
        if (minChildren > maxChildren) {
            throw new IllegalArgumentException("minChildren > maxChildren!");
        }

        Element root = new Element();
        root.elementName = rootName;
        root.childPolicy = CHILD_POLICY_REPEAT;
        root.minChildren = minChildren;
        root.maxChildren = maxChildren;

        this.rootName = rootName;
        elementMap.put(rootName, root);
    }

    /**
     * Sets a new base name for locating <code>ResourceBundle</code>s
     * containing descriptions of elements and attributes for this
     * format.
     *
     * <p> Prior to the first time this method is called, the base
     * name will be equal to <code>this.getClass().getName() +
     * "Resources"</code>.
     *
     * @param resourceBaseName a <code>String</code> containing the new
     * base name.
     *
     * @exception IllegalArgumentException if
     * <code>resourceBaseName</code> is <code>null</code>.
     *
     * @see #getResourceBaseName
     */
    protected void setResourceBaseName(String resourceBaseName) {
        if (resourceBaseName == null) {
            throw new IllegalArgumentException("resourceBaseName == null!");
        }
        this.resourceBaseName = resourceBaseName;
    }

    /**
     * Returns the currently set base name for locating
     * <code>ResourceBundle</code>s.
     *
     * @return a <code>String</code> containing the base name.
     *
     * @see #setResourceBaseName
     */
    protected String getResourceBaseName() {
        return resourceBaseName;
    }

    /**
     * Utility method for locating an element.
     *
     * @param mustAppear if <code>true</code>, throw an
     * <code>IllegalArgumentException</code> if no such node exists;
     * if <code>false</code>, just return null.
     */
    private Element getElement(String elementName, boolean mustAppear) {
        if (mustAppear && (elementName == null)) {
            throw new IllegalArgumentException("element name is null!");
        }
        Element element = (Element)elementMap.get(elementName);
        if (mustAppear && (element == null)) {
            throw new IllegalArgumentException("No such element: " +
                                               elementName);
        }
        return element;
    }

    private Element getElement(String elementName) {
        return getElement(elementName, true);
    }

    // Utility method for locating an attribute
    private Attribute getAttribute(String elementName, String attrName) {
        Element element = getElement(elementName);
        Attribute attr = (Attribute)element.attrMap.get(attrName);
        if (attr == null) {
            throw new IllegalArgumentException("No such attribute \"" +
                                               attrName + "\"!");
        }
        return attr;
    }

    // Setup

    /**
     * Adds a new element type to this metadata document format with a
     * child policy other than <code>CHILD_POLICY_REPEAT</code>.
     *
     * @param elementName the name of the new element.
     * @param parentName the name of the element that will be the
     * parent of the new element.
     * @param childPolicy one of the <code>CHILD_POLICY_*</code>
     * constants, other than <code>CHILD_POLICY_REPEAT</code>,
     * indicating the child policy of the new element.
     *
     * @exception IllegalArgumentException if <code>parentName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     * @exception IllegalArgumentException if <code>childPolicy</code>
     * is not one of the predefined constants.
     */
    protected void addElement(String elementName,
                              String parentName,
                              int childPolicy) {
        Element parent = getElement(parentName);
        if (childPolicy < CHILD_POLICY_EMPTY ||
            childPolicy > CHILD_POLICY_MAX ||
            childPolicy == CHILD_POLICY_REPEAT) {
            throw new IllegalArgumentException
                ("Invalid value for childPolicy!");
        }

        Element element = new Element();
        element.elementName = elementName;
        element.childPolicy = childPolicy;

        parent.childList.add(elementName);
        element.parentList.add(parentName);

        elementMap.put(elementName, element);
    }

    /**
     * Adds a new element type to this metadata document format with a
     * child policy of <code>CHILD_POLICY_REPEAT</code>.
     *
     * @param elementName the name of the new element.
     * @param parentName the name of the element that will be the
     * parent of the new element.
     * @param minChildren the minimum number of children of the node.
     * @param maxChildren the maximum number of children of the node.
     *
     * @exception IllegalArgumentException if <code>parentName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     * @exception IllegalArgumentException if <code>minChildren</code>
     * is negative or larger than <code>maxChildren</code>.
     */
    protected void addElement(String elementName,
                              String parentName,
                              int minChildren,
                              int maxChildren) {
        Element parent = getElement(parentName);
        if (minChildren < 0) {
            throw new IllegalArgumentException("minChildren < 0!");
        }
        if (minChildren > maxChildren) {
            throw new IllegalArgumentException("minChildren > maxChildren!");
        }

        Element element = new Element();
        element.elementName = elementName;
        element.childPolicy = CHILD_POLICY_REPEAT;
        element.minChildren = minChildren;
        element.maxChildren = maxChildren;

        parent.childList.add(elementName);
        element.parentList.add(parentName);

        elementMap.put(elementName, element);
    }

    /**
     * Adds an existing element to the list of legal children for a
     * given parent node type.
     *
     * @param parentName the name of the element that will be the
     * new parent of the element.
     * @param elementName the name of the element to be added as a
     * child.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     * @exception IllegalArgumentException if <code>parentName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     */
    protected void addChildElement(String elementName, String parentName) {
        Element parent = getElement(parentName);
        Element element = getElement(elementName);
        parent.childList.add(elementName);
        element.parentList.add(parentName);
    }

    /**
     * Removes an element from the format.  If no element with the
     * given name was present, nothing happens and no exception is
     * thrown.
     *
     * @param elementName the name of the element to be removed.
     */
    protected void removeElement(String elementName) {
        Element element = getElement(elementName, false);
        if (element != null) {
            Iterator iter = element.parentList.iterator();
            while (iter.hasNext()) {
                String parentName = (String)iter.next();
                Element parent = getElement(parentName, false);
                if (parent != null) {
                    parent.childList.remove(elementName);
                }
            }
            elementMap.remove(elementName);
        }
    }

    /**
     * Adds a new attribute to a previously defined element that may
     * be set to an arbitrary value.
     *
     * @param elementName the name of the element.
     * @param attrName the name of the attribute being added.
     * @param dataType the data type (string format) of the attribute,
     * one of the <code>DATATYPE_*</code> constants.
     * @param required <code>true</code> if the attribute must be present.
     * @param defaultValue the default value for the attribute, or
     * <code>null</code>.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     * @exception IllegalArgumentException if <code>attrName</code> is
     * <code>null</code>.
     * @exception IllegalArgumentException if <code>dataType</code> is
     * not one of the predefined constants.
     */
    protected void addAttribute(String elementName,
                                String attrName,
                                int dataType,
                                boolean required,
                                String defaultValue) {
        Element element = getElement(elementName);
        if (attrName == null) {
            throw new IllegalArgumentException("attrName == null!");
        }
        if (dataType < DATATYPE_STRING || dataType > DATATYPE_DOUBLE) {
            throw new IllegalArgumentException("Invalid value for dataType!");
        }

        Attribute attr = new Attribute();
        attr.attrName = attrName;
        attr.valueType = VALUE_ARBITRARY;
        attr.dataType = dataType;
        attr.required = required;
        attr.defaultValue = defaultValue;

        element.attrList.add(attrName);
        element.attrMap.put(attrName, attr);
    }

    /**
     * Adds a new attribute to a previously defined element that will
     * be defined by a set of enumerated values.
     *
     * @param elementName the name of the element.
     * @param attrName the name of the attribute being added.
     * @param dataType the data type (string format) of the attribute,
     * one of the <code>DATATYPE_*</code> constants.
     * @param required <code>true</code> if the attribute must be present.
     * @param defaultValue the default value for the attribute, or
     * <code>null</code>.
     * @param enumeratedValues a <code>List</code> of
     * <code>String</code>s containing the legal values for the
     * attribute.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     * @exception IllegalArgumentException if <code>attrName</code> is
     * <code>null</code>.
     * @exception IllegalArgumentException if <code>dataType</code> is
     * not one of the predefined constants.
     * @exception IllegalArgumentException if
     * <code>enumeratedValues</code> is <code>null</code>.
     * @exception IllegalArgumentException if
     * <code>enumeratedValues</code> does not contain at least one
     * entry.
     * @exception IllegalArgumentException if
     * <code>enumeratedValues</code> contains an element that is not a
     * <code>String</code> or is <code>null</code>.
     */
    protected void addAttribute(String elementName,
                                String attrName,
                                int dataType,
                                boolean required,
                                String defaultValue,
                                List<String> enumeratedValues) {
        Element element = getElement(elementName);
        if (attrName == null) {
            throw new IllegalArgumentException("attrName == null!");
        }
        if (dataType < DATATYPE_STRING || dataType > DATATYPE_DOUBLE) {
            throw new IllegalArgumentException("Invalid value for dataType!");
        }
        if (enumeratedValues == null) {
            throw new IllegalArgumentException("enumeratedValues == null!");
        }
        if (enumeratedValues.size() == 0) {
            throw new IllegalArgumentException("enumeratedValues is empty!");
        }
        Iterator iter = enumeratedValues.iterator();
        while (iter.hasNext()) {
            Object o = iter.next();
            if (o == null) {
                throw new IllegalArgumentException
                    ("enumeratedValues contains a null!");
            }
            if (!(o instanceof String)) {
                throw new IllegalArgumentException
                    ("enumeratedValues contains a non-String value!");
            }
        }

        Attribute attr = new Attribute();
        attr.attrName = attrName;
        attr.valueType = VALUE_ENUMERATION;
        attr.dataType = dataType;
        attr.required = required;
        attr.defaultValue = defaultValue;
        attr.enumeratedValues = enumeratedValues;

        element.attrList.add(attrName);
        element.attrMap.put(attrName, attr);
    }

    /**
     * Adds a new attribute to a previously defined element that will
     * be defined by a range of values.
     *
     * @param elementName the name of the element.
     * @param attrName the name of the attribute being added.
     * @param dataType the data type (string format) of the attribute,
     * one of the <code>DATATYPE_*</code> constants.
     * @param required <code>true</code> if the attribute must be present.
     * @param defaultValue the default value for the attribute, or
     * <code>null</code>.
     * @param minValue the smallest (inclusive or exclusive depending
     * on the value of <code>minInclusive</code>) legal value for the
     * attribute, as a <code>String</code>.
     * @param maxValue the largest (inclusive or exclusive depending
     * on the value of <code>minInclusive</code>) legal value for the
     * attribute, as a <code>String</code>.
     * @param minInclusive <code>true</code> if <code>minValue</code>
     * is inclusive.
     * @param maxInclusive <code>true</code> if <code>maxValue</code>
     * is inclusive.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     * @exception IllegalArgumentException if <code>attrName</code> is
     * <code>null</code>.
     * @exception IllegalArgumentException if <code>dataType</code> is
     * not one of the predefined constants.
     */
    protected void addAttribute(String elementName,
                                String attrName,
                                int dataType,
                                boolean required,
                                String defaultValue,
                                String minValue,
                                String maxValue,
                                boolean minInclusive,
                                boolean maxInclusive) {
        Element element = getElement(elementName);
        if (attrName == null) {
            throw new IllegalArgumentException("attrName == null!");
        }
        if (dataType < DATATYPE_STRING || dataType > DATATYPE_DOUBLE) {
            throw new IllegalArgumentException("Invalid value for dataType!");
        }

        Attribute attr = new Attribute();
        attr.attrName = attrName;
        attr.valueType = VALUE_RANGE;
        if (minInclusive) {
            attr.valueType |= VALUE_RANGE_MIN_INCLUSIVE_MASK;
        }
        if (maxInclusive) {
            attr.valueType |= VALUE_RANGE_MAX_INCLUSIVE_MASK;
        }
        attr.dataType = dataType;
        attr.required = required;
        attr.defaultValue = defaultValue;
        attr.minValue = minValue;
        attr.maxValue = maxValue;

        element.attrList.add(attrName);
        element.attrMap.put(attrName, attr);
    }

    /**
     * Adds a new attribute to a previously defined element that will
     * be defined by a list of values.
     *
     * @param elementName the name of the element.
     * @param attrName the name of the attribute being added.
     * @param dataType the data type (string format) of the attribute,
     * one of the <code>DATATYPE_*</code> constants.
     * @param required <code>true</code> if the attribute must be present.
     * @param listMinLength the smallest legal number of list items.
     * @param listMaxLength the largest legal number of list items.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     * @exception IllegalArgumentException if <code>attrName</code> is
     * <code>null</code>.
     * @exception IllegalArgumentException if <code>dataType</code> is
     * not one of the predefined constants.
     * @exception IllegalArgumentException if
     * <code>listMinLength</code> is negative or larger than
     * <code>listMaxLength</code>.
     */
    protected void addAttribute(String elementName,
                                String attrName,
                                int dataType,
                                boolean required,
                                int listMinLength,
                                int listMaxLength) {
        Element element = getElement(elementName);
        if (attrName == null) {
            throw new IllegalArgumentException("attrName == null!");
        }
        if (dataType < DATATYPE_STRING || dataType > DATATYPE_DOUBLE) {
            throw new IllegalArgumentException("Invalid value for dataType!");
        }
        if (listMinLength < 0 || listMinLength > listMaxLength) {
            throw new IllegalArgumentException("Invalid list bounds!");
        }

        Attribute attr = new Attribute();
        attr.attrName = attrName;
        attr.valueType = VALUE_LIST;
        attr.dataType = dataType;
        attr.required = required;
        attr.listMinLength = listMinLength;
        attr.listMaxLength = listMaxLength;

        element.attrList.add(attrName);
        element.attrMap.put(attrName, attr);
    }

    /**
     * Adds a new attribute to a previously defined element that will
     * be defined by the enumerated values <code>TRUE</code> and
     * <code>FALSE</code>, with a datatype of
     * <code>DATATYPE_BOOLEAN</code>.
     *
     * @param elementName the name of the element.
     * @param attrName the name of the attribute being added.
     * @param hasDefaultValue <code>true</code> if a default value
     * should be present.
     * @param defaultValue the default value for the attribute as a
     * <code>boolean</code>, ignored if <code>hasDefaultValue</code>
     * is <code>false</code>.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     * @exception IllegalArgumentException if <code>attrName</code> is
     * <code>null</code>.
     */
    protected void addBooleanAttribute(String elementName,
                                       String attrName,
                                       boolean hasDefaultValue,
                                       boolean defaultValue) {
        List values = new ArrayList();
        values.add("TRUE");
        values.add("FALSE");

        String dval = null;
        if (hasDefaultValue) {
            dval = defaultValue ? "TRUE" : "FALSE";
        }
        addAttribute(elementName,
                     attrName,
                     DATATYPE_BOOLEAN,
                     true,
                     dval,
                     values);
    }

    /**
     * Removes an attribute from a previously defined element.  If no
     * attribute with the given name was present in the given element,
     * nothing happens and no exception is thrown.
     *
     * @param elementName the name of the element.
     * @param attrName the name of the attribute being removed.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this format.
     */
    protected void removeAttribute(String elementName, String attrName) {
        Element element = getElement(elementName);
        element.attrList.remove(attrName);
        element.attrMap.remove(attrName);
    }

    /**
     * Allows an <code>Object</code> reference of a given class type
     * to be stored in nodes implementing the named element.  The
     * value of the <code>Object</code> is unconstrained other than by
     * its class type.
     *
     * <p> If an <code>Object</code> reference was previously allowed,
     * the previous settings are overwritten.
     *
     * @param elementName the name of the element.
     * @param classType a <code>Class</code> variable indicating the
     * legal class type for the object value.
     * @param required <code>true</code> if an object value must be present.
     * @param defaultValue the default value for the
     * <code>Object</code> reference, or <code>null</code>.
     * @param <T> the type of the object.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this format.
     */
    protected <T> void addObjectValue(String elementName,
                                      Class<T> classType,
                                      boolean required,
                                      T defaultValue)
    {
        Element element = getElement(elementName);
        ObjectValue obj = new ObjectValue();
        obj.valueType = VALUE_ARBITRARY;
        obj.classType = classType;
        obj.defaultValue = defaultValue;

        element.objectValue = obj;
    }

    /**
     * Allows an <code>Object</code> reference of a given class type
     * to be stored in nodes implementing the named element.  The
     * value of the <code>Object</code> must be one of the values
     * given by <code>enumeratedValues</code>.
     *
     * <p> If an <code>Object</code> reference was previously allowed,
     * the previous settings are overwritten.
     *
     * @param elementName the name of the element.
     * @param classType a <code>Class</code> variable indicating the
     * legal class type for the object value.
     * @param required <code>true</code> if an object value must be present.
     * @param defaultValue the default value for the
     * <code>Object</code> reference, or <code>null</code>.
     * @param enumeratedValues a <code>List</code> of
     * <code>Object</code>s containing the legal values for the
     * object reference.
     * @param <T> the type of the object.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this format.
     * @exception IllegalArgumentException if
     * <code>enumeratedValues</code> is <code>null</code>.
     * @exception IllegalArgumentException if
     * <code>enumeratedValues</code> does not contain at least one
     * entry.
     * @exception IllegalArgumentException if
     * <code>enumeratedValues</code> contains an element that is not
     * an instance of the class type denoted by <code>classType</code>
     * or is <code>null</code>.
     */
    protected <T> void addObjectValue(String elementName,
                                      Class<T> classType,
                                      boolean required,
                                      T defaultValue,
                                      List<? extends T> enumeratedValues)
    {
        Element element = getElement(elementName);
        if (enumeratedValues == null) {
            throw new IllegalArgumentException("enumeratedValues == null!");
        }
        if (enumeratedValues.size() == 0) {
            throw new IllegalArgumentException("enumeratedValues is empty!");
        }
        Iterator iter = enumeratedValues.iterator();
        while (iter.hasNext()) {
            Object o = iter.next();
            if (o == null) {
                throw new IllegalArgumentException("enumeratedValues contains a null!");
            }
            if (!classType.isInstance(o)) {
                throw new IllegalArgumentException("enumeratedValues contains a value not of class classType!");
            }
        }

        ObjectValue obj = new ObjectValue();
        obj.valueType = VALUE_ENUMERATION;
        obj.classType = classType;
        obj.defaultValue = defaultValue;
        obj.enumeratedValues = enumeratedValues;

        element.objectValue = obj;
    }

    /**
     * Allows an <code>Object</code> reference of a given class type
     * to be stored in nodes implementing the named element.  The
     * value of the <code>Object</code> must be within the range given
     * by <code>minValue</code> and <code>maxValue</code>.
     * Furthermore, the class type must implement the
     * <code>Comparable</code> interface.
     *
     * <p> If an <code>Object</code> reference was previously allowed,
     * the previous settings are overwritten.
     *
     * @param elementName the name of the element.
     * @param classType a <code>Class</code> variable indicating the
     * legal class type for the object value.
     * @param defaultValue the default value for the
     * @param minValue the smallest (inclusive or exclusive depending
     * on the value of <code>minInclusive</code>) legal value for the
     * object value, as a <code>String</code>.
     * @param maxValue the largest (inclusive or exclusive depending
     * on the value of <code>minInclusive</code>) legal value for the
     * object value, as a <code>String</code>.
     * @param minInclusive <code>true</code> if <code>minValue</code>
     * is inclusive.
     * @param maxInclusive <code>true</code> if <code>maxValue</code>
     * is inclusive.
     * @param <T> the type of the object.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this
     * format.
     */
    protected <T extends Object & Comparable<? super T>> void
        addObjectValue(String elementName,
                       Class<T> classType,
                       T defaultValue,
                       Comparable<? super T> minValue,
                       Comparable<? super T> maxValue,
                       boolean minInclusive,
                       boolean maxInclusive)
    {
        Element element = getElement(elementName);
        ObjectValue obj = new ObjectValue();
        obj.valueType = VALUE_RANGE;
        if (minInclusive) {
            obj.valueType |= VALUE_RANGE_MIN_INCLUSIVE_MASK;
        }
        if (maxInclusive) {
            obj.valueType |= VALUE_RANGE_MAX_INCLUSIVE_MASK;
        }
        obj.classType = classType;
        obj.defaultValue = defaultValue;
        obj.minValue = minValue;
        obj.maxValue = maxValue;

        element.objectValue = obj;
    }

    /**
     * Allows an <code>Object</code> reference of a given class type
     * to be stored in nodes implementing the named element.  The
     * value of the <code>Object</code> must an array of objects of
     * class type given by <code>classType</code>, with at least
     * <code>arrayMinLength</code> and at most
     * <code>arrayMaxLength</code> elements.
     *
     * <p> If an <code>Object</code> reference was previously allowed,
     * the previous settings are overwritten.
     *
     * @param elementName the name of the element.
     * @param classType a <code>Class</code> variable indicating the
     * legal class type for the object value.
     * @param arrayMinLength the smallest legal length for the array.
     * @param arrayMaxLength the largest legal length for the array.
     *
     * @exception IllegalArgumentException if <code>elementName</code> is
     * not a legal element name for this format.
     */
    protected void addObjectValue(String elementName,
                                  Class<?> classType,
                                  int arrayMinLength,
                                  int arrayMaxLength) {
        Element element = getElement(elementName);
        ObjectValue obj = new ObjectValue();
        obj.valueType = VALUE_LIST;
        obj.classType = classType;
        obj.arrayMinLength = arrayMinLength;
        obj.arrayMaxLength = arrayMaxLength;

        element.objectValue = obj;
    }

    /**
     * Disallows an <code>Object</code> reference from being stored in
     * nodes implementing the named element.
     *
     * @param elementName the name of the element.
     *
     * @exception IllegalArgumentException if <code>elementName</code> is
     * not a legal element name for this format.
     */
    protected void removeObjectValue(String elementName) {
        Element element = getElement(elementName);
        element.objectValue = null;
    }

    // Utility method

    // Methods from IIOMetadataFormat

    // Root

    public String getRootName() {
        return rootName;
    }

    // Multiplicity

    public abstract boolean canNodeAppear(String elementName,
                                          ImageTypeSpecifier imageType);

    public int getElementMinChildren(String elementName) {
        Element element = getElement(elementName);
        if (element.childPolicy != CHILD_POLICY_REPEAT) {
            throw new IllegalArgumentException("Child policy not CHILD_POLICY_REPEAT!");
        }
        return element.minChildren;
    }

    public int getElementMaxChildren(String elementName) {
        Element element = getElement(elementName);
        if (element.childPolicy != CHILD_POLICY_REPEAT) {
            throw new IllegalArgumentException("Child policy not CHILD_POLICY_REPEAT!");
        }
        return element.maxChildren;
    }

    private String getResource(String key, Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }

        /**
         * If an applet supplies an implementation of IIOMetadataFormat and
         * resource bundles, then the resource bundle will need to be
         * accessed via the applet class loader. So first try the context
         * class loader to locate the resource bundle.
         * If that throws MissingResourceException, then try the
         * system class loader.
         */
        ClassLoader loader = (ClassLoader)
            java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction() {
                   public Object run() {
                       return Thread.currentThread().getContextClassLoader();
                   }
            });

        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle(resourceBaseName,
                                              locale, loader);
        } catch (MissingResourceException mre) {
            try {
                bundle = ResourceBundle.getBundle(resourceBaseName, locale);
            } catch (MissingResourceException mre1) {
                return null;
            }
        }

        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /**
     * Returns a <code>String</code> containing a description of the
     * named element, or <code>null</code>.  The description will be
     * localized for the supplied <code>Locale</code> if possible.
     *
     * <p> The default implementation will first locate a
     * <code>ResourceBundle</code> using the current resource base
     * name set by <code>setResourceBaseName</code> and the supplied
     * <code>Locale</code>, using the fallback mechanism described in
     * the comments for <code>ResourceBundle.getBundle</code>.  If a
     * <code>ResourceBundle</code> is found, the element name will be
     * used as a key to its <code>getString</code> method, and the
     * result returned.  If no <code>ResourceBundle</code> is found,
     * or no such key is present, <code>null</code> will be returned.
     *
     * <p> If <code>locale</code> is <code>null</code>, the current
     * default <code>Locale</code> returned by <code>Locale.getLocale</code>
     * will be used.
     *
     * @param elementName the name of the element.
     * @param locale the <code>Locale</code> for which localization
     * will be attempted.
     *
     * @return the element description.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this format.
     *
     * @see #setResourceBaseName
     */
    public String getElementDescription(String elementName,
                                        Locale locale) {
        Element element = getElement(elementName);
        return getResource(elementName, locale);
    }

    // Children

    public int getChildPolicy(String elementName) {
        Element element = getElement(elementName);
        return element.childPolicy;
    }

    public String[] getChildNames(String elementName) {
        Element element = getElement(elementName);
        if (element.childPolicy == CHILD_POLICY_EMPTY) {
            return null;
        }
        return (String[])element.childList.toArray(new String[0]);
    }

    // Attributes

    public String[] getAttributeNames(String elementName) {
        Element element = getElement(elementName);
        List names = element.attrList;

        String[] result = new String[names.size()];
        return (String[])names.toArray(result);
    }

    public int getAttributeValueType(String elementName, String attrName) {
        Attribute attr = getAttribute(elementName, attrName);
        return attr.valueType;
    }

    public int getAttributeDataType(String elementName, String attrName) {
        Attribute attr = getAttribute(elementName, attrName);
        return attr.dataType;
    }

    public boolean isAttributeRequired(String elementName, String attrName) {
        Attribute attr = getAttribute(elementName, attrName);
        return attr.required;
    }

    public String getAttributeDefaultValue(String elementName,
                                           String attrName) {
        Attribute attr = getAttribute(elementName, attrName);
        return attr.defaultValue;
    }

    public String[] getAttributeEnumerations(String elementName,
                                             String attrName) {
        Attribute attr = getAttribute(elementName, attrName);
        if (attr.valueType != VALUE_ENUMERATION) {
            throw new IllegalArgumentException
                ("Attribute not an enumeration!");
        }

        List values = attr.enumeratedValues;
        Iterator iter = values.iterator();
        String[] result = new String[values.size()];
        return (String[])values.toArray(result);
    }

    public String getAttributeMinValue(String elementName, String attrName) {
        Attribute attr = getAttribute(elementName, attrName);
        if (attr.valueType != VALUE_RANGE &&
            attr.valueType != VALUE_RANGE_MIN_INCLUSIVE &&
            attr.valueType != VALUE_RANGE_MAX_INCLUSIVE &&
            attr.valueType != VALUE_RANGE_MIN_MAX_INCLUSIVE) {
            throw new IllegalArgumentException("Attribute not a range!");
        }

        return attr.minValue;
    }

    public String getAttributeMaxValue(String elementName, String attrName) {
        Attribute attr = getAttribute(elementName, attrName);
        if (attr.valueType != VALUE_RANGE &&
            attr.valueType != VALUE_RANGE_MIN_INCLUSIVE &&
            attr.valueType != VALUE_RANGE_MAX_INCLUSIVE &&
            attr.valueType != VALUE_RANGE_MIN_MAX_INCLUSIVE) {
            throw new IllegalArgumentException("Attribute not a range!");
        }

        return attr.maxValue;
    }

    public int getAttributeListMinLength(String elementName, String attrName) {
        Attribute attr = getAttribute(elementName, attrName);
        if (attr.valueType != VALUE_LIST) {
            throw new IllegalArgumentException("Attribute not a list!");
        }

        return attr.listMinLength;
    }

    public int getAttributeListMaxLength(String elementName, String attrName) {
        Attribute attr = getAttribute(elementName, attrName);
        if (attr.valueType != VALUE_LIST) {
            throw new IllegalArgumentException("Attribute not a list!");
        }

        return attr.listMaxLength;
    }

    /**
     * Returns a <code>String</code> containing a description of the
     * named attribute, or <code>null</code>.  The description will be
     * localized for the supplied <code>Locale</code> if possible.
     *
     * <p> The default implementation will first locate a
     * <code>ResourceBundle</code> using the current resource base
     * name set by <code>setResourceBaseName</code> and the supplied
     * <code>Locale</code>, using the fallback mechanism described in
     * the comments for <code>ResourceBundle.getBundle</code>.  If a
     * <code>ResourceBundle</code> is found, the element name followed
     * by a "/" character followed by the attribute name
     * (<code>elementName + "/" + attrName</code>) will be used as a
     * key to its <code>getString</code> method, and the result
     * returned.  If no <code>ResourceBundle</code> is found, or no
     * such key is present, <code>null</code> will be returned.
     *
     * <p> If <code>locale</code> is <code>null</code>, the current
     * default <code>Locale</code> returned by <code>Locale.getLocale</code>
     * will be used.
     *
     * @param elementName the name of the element.
     * @param attrName the name of the attribute.
     * @param locale the <code>Locale</code> for which localization
     * will be attempted, or <code>null</code>.
     *
     * @return the attribute description.
     *
     * @exception IllegalArgumentException if <code>elementName</code>
     * is <code>null</code>, or is not a legal element name for this format.
     * @exception IllegalArgumentException if <code>attrName</code> is
     * <code>null</code> or is not a legal attribute name for this
     * element.
     *
     * @see #setResourceBaseName
     */
    public String getAttributeDescription(String elementName,
                                          String attrName,
                                          Locale locale) {
        Element element = getElement(elementName);
        if (attrName == null) {
            throw new IllegalArgumentException("attrName == null!");
        }
        Attribute attr = (Attribute)element.attrMap.get(attrName);
        if (attr == null) {
            throw new IllegalArgumentException("No such attribute!");
        }

        String key = elementName + "/" + attrName;
        return getResource(key, locale);
    }

    private ObjectValue getObjectValue(String elementName) {
        Element element = getElement(elementName);
        ObjectValue objv = (ObjectValue)element.objectValue;
        if (objv == null) {
            throw new IllegalArgumentException("No object within element " +
                                               elementName + "!");
        }
        return objv;
    }

    public int getObjectValueType(String elementName) {
        Element element = getElement(elementName);
        ObjectValue objv = (ObjectValue)element.objectValue;
        if (objv == null) {
            return VALUE_NONE;
        }
        return objv.valueType;
    }

    public Class<?> getObjectClass(String elementName) {
        ObjectValue objv = getObjectValue(elementName);
        return objv.classType;
    }

    public Object getObjectDefaultValue(String elementName) {
        ObjectValue objv = getObjectValue(elementName);
        return objv.defaultValue;
    }

    public Object[] getObjectEnumerations(String elementName) {
        ObjectValue objv = getObjectValue(elementName);
        if (objv.valueType != VALUE_ENUMERATION) {
            throw new IllegalArgumentException("Not an enumeration!");
        }
        List vlist = objv.enumeratedValues;
        Object[] values = new Object[vlist.size()];
        return vlist.toArray(values);
    }

    public Comparable<?> getObjectMinValue(String elementName) {
        ObjectValue objv = getObjectValue(elementName);
        if ((objv.valueType & VALUE_RANGE) != VALUE_RANGE) {
            throw new IllegalArgumentException("Not a range!");
        }
        return objv.minValue;
    }

    public Comparable<?> getObjectMaxValue(String elementName) {
        ObjectValue objv = getObjectValue(elementName);
        if ((objv.valueType & VALUE_RANGE) != VALUE_RANGE) {
            throw new IllegalArgumentException("Not a range!");
        }
        return objv.maxValue;
    }

    public int getObjectArrayMinLength(String elementName) {
        ObjectValue objv = getObjectValue(elementName);
        if (objv.valueType != VALUE_LIST) {
            throw new IllegalArgumentException("Not a list!");
        }
        return objv.arrayMinLength;
    }

    public int getObjectArrayMaxLength(String elementName) {
        ObjectValue objv = getObjectValue(elementName);
        if (objv.valueType != VALUE_LIST) {
            throw new IllegalArgumentException("Not a list!");
        }
        return objv.arrayMaxLength;
    }

    // Standard format descriptor

    private synchronized static void createStandardFormat() {
        if (standardFormat == null) {
            standardFormat = new StandardMetadataFormat();
        }
    }

    /**
     * Returns an <code>IIOMetadataFormat</code> object describing the
     * standard, plug-in neutral <code>javax.imageio_1.0</code>
     * metadata document format described in the comment of the
     * <code>javax.imageio.metadata</code> package.
     *
     * @return a predefined <code>IIOMetadataFormat</code> instance.
     */
    public static IIOMetadataFormat getStandardFormatInstance() {
        createStandardFormat();
        return standardFormat;
    }
}
