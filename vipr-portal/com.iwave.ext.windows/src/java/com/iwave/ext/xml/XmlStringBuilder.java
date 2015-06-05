/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.xml;

import java.util.LinkedList;

import org.apache.commons.lang.text.StrBuilder;
import com.google.common.collect.Lists;

/**
 * Simple utility class for building XML strings.
 * 
 * @author jonnymiller
 */
public class XmlStringBuilder {
    private StrBuilder sb = new StrBuilder();
    private LinkedList<String> elements = Lists.newLinkedList();
    private boolean tagOpen = false;
    private boolean hasContent = false;

    public XmlStringBuilder start(String name) {
        closePreviousTag();
        elements.push(name);
        sb.append("<").append(name);
        hasContent = false;
        tagOpen = true;
        return this;
    }

    public XmlStringBuilder end() {
        if (elements.isEmpty()) {
            throw new IllegalStateException("No more elements to end");
        }
        String name = elements.pop();
        if (hasContent) {
            sb.append("</").append(name).append(">");
        }
        else {
            sb.append("/>");
        }
        hasContent = true;
        tagOpen = false;
        return this;
    }

    public XmlStringBuilder element(String name, String text) {
        return start(name).text(text).end();
    }

    public XmlStringBuilder element(String name, Object value) {
        return element(name, value != null ? value.toString() : "");
    }

    protected void closePreviousTag() {
        if (tagOpen) {
            sb.append(">");
            hasContent = true;
            tagOpen = false;
        }
    }

    public XmlStringBuilder attr(String name, String value) {
        if (!tagOpen) {
            throw new IllegalStateException("Cannot add attributes after start tag is closed");
        }
        sb.append(" ").append(name).append("=\"");
        XmlUtils.escape(sb, value, true, false);
        sb.append("\"");
        return this;
    }

    public XmlStringBuilder text(String text) {
        closePreviousTag();
        XmlUtils.escape(sb, text, false, false);
        hasContent = true;
        return this;
    }

    public String toString() {
        return sb.toString();
    }
}
