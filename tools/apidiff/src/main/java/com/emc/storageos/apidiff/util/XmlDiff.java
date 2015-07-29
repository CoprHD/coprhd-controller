/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.apidiff.util;

import org.jdom2.Attribute;
import org.jdom2.Comment;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.util.Iterator;
import java.util.List;

/**
 * Utility class to help decompose, order and compare xml document with Jdom.
 */
public class XmlDiff {

    /**
     * Compares two documents and outputs different part info.
     * <p>
     * 1. ignore sequence 2. ignore element text value 3. ignore xml comment element
     * </p>
     * 
     * @param oldDocument
     *            The old xml document
     * @param newDocument
     *            The new xml document
     * @return
     *         The instance of diff
     */
    public static Pair<String, String> compareXml(Document oldDocument, Document newDocument) {

        Element oldRootElement = oldDocument.getRootElement();
        Element newRootElement = newDocument.getRootElement();

        if (compareElement(oldRootElement, newRootElement)) {
            return null;
        }

        XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat());

        return new Pair<String, String>(xmlOutputter.outputString(oldRootElement),
                xmlOutputter.outputString(newRootElement));
    }

    /**
     * Compares two XML Elements and their children recursively
     * 
     * @param oldElement
     *            The old element to compare
     * @param newElement
     *            The new element to compare
     * @return true if two elements are same, else false
     */
    public static boolean compareElement(Element oldElement, Element newElement) {

        // Check element name
        if (!oldElement.getName().equals(newElement.getName())) {
            return false;
        }

        // Check attributes
        if (!compareAttributes(oldElement.getAttributes(), newElement.getAttributes())) {
            return false;
        }

        // Check children number
        if (oldElement.getChildren().isEmpty() && newElement.getChildren().isEmpty()) {
            return true;
        }

        // Check non leaf element
        Iterator<Element> oldIter = oldElement.getChildren().iterator();
        while (oldIter.hasNext()) {
            Element oldChild = oldIter.next();
            boolean found = false;
            Iterator<Element> newIter = newElement.getChildren().iterator();
            while (newIter.hasNext()) {
                Element newChild = newIter.next();
                if (newChild.getName().equals(oldChild.getName())) {
                    found = compareElement(oldChild, newChild);
                    if (found) {
                        break;
                    }
                }
            }
            if (found) {
                oldIter.remove();
                newIter.remove();
            }
        }

        // Check children
        if (oldElement.getChildren().isEmpty() && newElement.getChildren().isEmpty()) {
            return true;
        } else if (oldElement.getChildren().size() != newElement.getChildren().size()) {
            // Add comments to stand for same tags.
            if (!(oldElement.getContent(0) instanceof Comment)
                    && !(newElement.getContent(0) instanceof Comment)) {
                oldElement.addContent(0, new Comment("..."));
                newElement.addContent(0, new Comment("..."));
            }
        }
        return false;
    }

    /**
     * Compares all attributes of old/new element
     * 
     * @param oldAttributes
     *            The old attribute list to compare
     * @param newAttributes
     *            The new attribute list to compare
     * @return true if two lists are same, else false
     */
    public static boolean compareAttributes(List<Attribute> oldAttributes, List<Attribute> newAttributes) {
        Iterator<Attribute> oldIter = oldAttributes.iterator();
        while (oldIter.hasNext()) {
            Attribute oldAttr = oldIter.next();
            Iterator<Attribute> newIter = newAttributes.iterator();
            while (newIter.hasNext()) {
                Attribute newAttr = newIter.next();
                if (newAttr.getName().equals(oldAttr.getName())) {
                    oldIter.remove();
                    newIter.remove();
                    break;
                }
            }
        }

        return oldAttributes.isEmpty() && newAttributes.isEmpty();
    }
}
