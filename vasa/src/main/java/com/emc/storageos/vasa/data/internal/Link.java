/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "link")
public class Link {

    @XmlAttribute(name = "href")
    private String href;

    public String getHref() {
        return href;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Link [href=");
        builder.append(href);
        builder.append("]");
        return builder.toString();
    }

}
