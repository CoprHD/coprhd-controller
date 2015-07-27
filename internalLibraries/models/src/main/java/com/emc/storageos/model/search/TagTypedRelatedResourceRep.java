/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.search;

import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.TypedRelatedResourceRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.net.URI;

/**
 * Label search result
 */
@XmlAccessorType(XmlAccessType.NONE)
public class TagTypedRelatedResourceRep extends TypedRelatedResourceRep {
    private String tag;

    @XmlElement(name = "tag")
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public TagTypedRelatedResourceRep() {
    }

    public TagTypedRelatedResourceRep(ResourceTypeEnum type, URI id, RestLinkRep selfLink, String name, String tag) {
        super(id, selfLink, name, type);
        this.tag = tag;
    }
}
