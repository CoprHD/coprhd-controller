/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.event;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * REST Response representing an Event.
 */
@XmlRootElement(name = "event")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EventRestRep extends DataObjectRestRep {

    private String description;
    private NamedRelatedResourceRep resource;
    private String eventStatus;

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

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "resource")
    public NamedRelatedResourceRep getResource() {
        return resource;
    }

    public void setResource(NamedRelatedResourceRep resource) {
        this.resource = resource;
    }

    @XmlElement(name = "event_status")
    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String status) {
        this.eventStatus = status;
    }
}
