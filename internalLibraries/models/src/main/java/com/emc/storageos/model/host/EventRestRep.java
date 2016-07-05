/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * REST Response representing an Event.
 */
@XmlRootElement(name = "event")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EventRestRep extends DataObjectRestRep {

    private String message;

    private RelatedResourceRep tenant;

    public EventRestRep() {
    }

    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    @XmlElement(name = "message")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
