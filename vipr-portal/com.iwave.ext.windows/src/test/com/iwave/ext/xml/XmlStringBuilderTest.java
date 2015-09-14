/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.xml;

import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.junit.Test;

public class XmlStringBuilderTest {
    private static Logger log = Logger.getLogger(XmlStringBuilderTest.class);

    @Test
    public void testSingleNode() {
        XmlStringBuilder sb = new XmlStringBuilder();
        sb.start("node");
        sb.end();
        assertEquals("<node/>", sb.toString());
    }

    @Test
    public void testSingleNodeAttributes() {
        XmlStringBuilder sb = new XmlStringBuilder();
        sb.start("node");
        sb.attr("a", "A");
        sb.attr("b", "B");
        sb.end();
        assertEquals("<node a=\"A\" b=\"B\"/>", sb.toString());
    }

    @Test
    public void testSingleNodeContent() {
        XmlStringBuilder sb = new XmlStringBuilder();
        sb.start("node");
        sb.text("TEXT");
        sb.end();
        assertEquals("<node>TEXT</node>", sb.toString());
    }

    @Test
    public void testSingleNodeAttributesAndContent() {
        XmlStringBuilder sb = new XmlStringBuilder();
        sb.start("node");
        sb.attr("a", "A");
        sb.text("TEXT");
        sb.end();
        assertEquals("<node a=\"A\">TEXT</node>", sb.toString());
    }

    @Test
    public void testMultipleNodes() {
        XmlStringBuilder sb = new XmlStringBuilder();
        sb.start("a");
        sb.start("b").text("B1").end();
        sb.start("b").text("B2").end();
        sb.end();
        assertEquals("<a><b>B1</b><b>B2</b></a>", sb.toString());
    }

    @Test
    public void testInvalidAttrs() {
        XmlStringBuilder sb = new XmlStringBuilder();
        try {
            sb.attr("a", "A");
            fail("Attributes should not be allowed before a start element");
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }

        sb.start("node");
        sb.text("text");
        try {
            sb.attr("a", "A");
            fail("Attributes should not be allowed after outputting text");
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }

        sb.end();
        try {
            sb.attr("a", "A");
            fail("Attributes should not be allowed after closing an element");
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Test
    public void testInvalidState() {
        XmlStringBuilder sb = new XmlStringBuilder();
        try {
            sb.end();
            fail("Should not be able to end an element when none was started");
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }

        sb.start("node");
        sb.end();
        try {
            sb.end();
            fail("Should not be able to end an element when none remain");
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Test
    public void testXmlEntities() {
        XmlStringBuilder sb = new XmlStringBuilder();
        sb.start("node");
        sb.attr("amp", "\"'<&>");
        sb.text("\"'<&>");
        sb.end();

        assertEquals("<node amp=\"&quot;'&lt;&amp;&gt;\">\"'&lt;&amp;&gt;</node>", sb.toString());
    }

    @Test
    public void testElementText() {
        XmlStringBuilder sb = new XmlStringBuilder();
        sb.element("node", "text");
        assertEquals("<node>text</node>", sb.toString());
    }

    @Test
    public void testElementValue() {
        XmlStringBuilder sb = new XmlStringBuilder();
        sb.start("root");
        sb.element("int", 5);
        sb.element("long", ((long) Integer.MAX_VALUE) + 1);
        sb.element("boolean", true);
        sb.element("double", 1.5);
        sb.element("float", 0.5);
        sb.end();

        assertEquals("<root><int>5</int><long>2147483648</long><boolean>true</boolean>"
                + "<double>1.5</double><float>0.5</float></root>", sb.toString());
    }
}
