/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.vpool;

import java.net.URI;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;
import org.codehaus.jackson.annotate.JsonProperty;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class NamedRelatedVirtualPoolRep extends NamedRelatedResourceRep {
    private String virtualPoolType;

    public NamedRelatedVirtualPoolRep() {
    }

    public NamedRelatedVirtualPoolRep(URI id, RestLinkRep selfLink, String name, String virtualPoolType) {
        super(id, selfLink, name);
        this.virtualPoolType = virtualPoolType;
    }

    /**
     * The virtual pool type.
     * 
     * @valid block  = Volume
     * @valid file   = File System
     * @valid object = Object Store
     * 
     * @return The virtual pool type.
     */
    @XmlElement(name = "vpool_type")
    @JsonProperty("vpool_type")
    public String getVirtualPoolType() {
        return virtualPoolType;
    }
    public void setVirtualPoolType(String virtualPoolType) {
        this.virtualPoolType = virtualPoolType;
    }
}
