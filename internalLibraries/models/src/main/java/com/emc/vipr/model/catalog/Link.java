/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "link")
public class Link {

    private String rel;
    private String href;

    public Link() {
    }

    public Link(String rel, String href) {
        this.rel = rel;
        this.href = href;
    }

    public static Link newSelfLink(String href) {
        Link link = new Link("self", href);
        return link;
    }

    @XmlAttribute(name = "rel")
    public String getRel() {
        return rel;
    }

    public void setRel(String value) {
        this.rel = value;
    }

    @XmlAttribute(name = "href")
    public String getHref() {
        return href;
    }

    public void setHref(String value) {
        this.href = value;
    }
}
