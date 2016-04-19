/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.search;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.net.URI;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class TagNamedRelatedResourceRep extends NamedRelatedResourceRep {
    private String tag;

    /**
     * The tag attached to the resource
     * 
     */
    @XmlElement(name = "tag")
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public TagNamedRelatedResourceRep() {
    }

    public TagNamedRelatedResourceRep(URI id, RestLinkRep selfLink, String name, String tag) {
        super(id, selfLink, name);
        this.tag = tag;
    }

    public TagNamedRelatedResourceRep(URI id, RestLinkRep selfLink, String name) {
        super(id, selfLink, name);
        this.tag = null;
    }
}
