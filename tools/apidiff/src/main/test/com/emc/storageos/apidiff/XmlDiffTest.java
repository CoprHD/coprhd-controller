/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.apidiff;

import com.emc.storageos.apidiff.util.XmlDiff;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

/**
 * Tests basic functions of class XmlDiff
 */
public class XmlDiffTest {

    private Document oldDocument;
    private Document newDocument;

    @Before
    public void setUp() throws IOException, JDOMException {
        SAXBuilder saxBuilder = new SAXBuilder();
        saxBuilder.setReuseParser(true);
        String source = "<tag_changes>\n" +
                "  <same>" +
                "    <tag>...</tag>" +
                "  </same>\n" +
                "  <add>" +
                "  </add>\n" +
                "  <!--...more \"add\" elements...-->\n" +
                "  <remove>" +
                "    <tag>...</tag>" +
                "  </remove>\n" +
                "  <!--...more \"remove\" elements...-->\n" +
                "</tag_changes>";
        String dest = "<tag_changes>\n" +
                "  <same>" +
                "    <tag>...</tag>" +
                "  </same>\n" +
                "  <add>" +
                "    <tag>...</tag>" +
                "  </add>\n" +
                "  <!--...more \"add\" elements...-->\n" +
                "  <remove>" +
                "  </remove>\n" +
                "</tag_changes>";
        oldDocument = saxBuilder.build(new StringReader(source));
        newDocument = saxBuilder.build(new StringReader(dest));
    }

    @Test
    public void testSameElement() {
        Element oldElement = oldDocument.getRootElement().getChild("same").clone();
        Element newElement = newDocument.getRootElement().getChild("same").clone();
        boolean ret = XmlDiff.compareElement(oldElement, newElement);
        Assert.assertTrue(ret);
    }

    @Test
    public void testElementAdd() {
        Element oldElement = oldDocument.getRootElement().getChild("add").clone();
        Element newElement = newDocument.getRootElement().getChild("add").clone();
        boolean ret = XmlDiff.compareElement(oldElement, newElement);
        Assert.assertFalse(ret);
        Assert.assertEquals(oldElement.getChildren().size() + 1, newElement.getChildren().size());
    }

    @Test
    public void testElementRemove() {
        Element oldElement = oldDocument.getRootElement().getChild("remove").clone();
        Element newElement = newDocument.getRootElement().getChild("remove").clone();
        boolean ret = XmlDiff.compareElement(oldElement, newElement);
        Assert.assertFalse(ret);
        Assert.assertEquals(oldElement.getChildren().size() - 1, newElement.getChildren().size());
    }
}
