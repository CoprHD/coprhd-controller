/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    public TypedRelatedResourceRep() {}

    public TypedRelatedResourceRep(URI id, RestLinkRep selfLink, String name, ResourceTypeEnum type) {
        super(id, selfLink, name);
        setType(type);
    }

    /**
     * The type of the resource
     * @valid none
     */
    @XmlTransient
    public ResourceTypeEnum getType() {
        return ResourceTypeEnum.fromString(type);
    }
    
    public void setType(ResourceTypeEnum type){
        this.type = type.getType();
    }
}
