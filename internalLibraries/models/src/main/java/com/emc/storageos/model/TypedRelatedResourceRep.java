/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model;

import java.net.URI;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlAccessorType(XmlAccessType.NONE)
public class TypedRelatedResourceRep extends NamedRelatedResourceRep {
    // This is stored as a string because the XML following the typed value
    @XmlElement(name = "resource_type")
    private String type;

    public TypedRelatedResourceRep() {
    }

    public TypedRelatedResourceRep(URI id, RestLinkRep selfLink, String name, ResourceTypeEnum type) {
        super(id, selfLink, name);
        setType(type);
    }

    /**
     * The type of the resource
     * 
     * @valid none
     */
    @XmlTransient
    public ResourceTypeEnum getType() {
        return ResourceTypeEnum.fromString(type);
    }

    public void setType(ResourceTypeEnum type) {
        this.type = type.getType();
    }
}
