/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.event;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * REST Response representing details for an Event.
 */
@XmlRootElement(name = "event_details")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EventDetailsRestRep {

    private List<String> declineDetails;
    private List<String> approveDetails;

    public EventDetailsRestRep() {
    }

    @XmlElementWrapper(name = "approve_details")
    @XmlElement(name = "approve_detail")
    public List<String> getApproveDetails() {
        return approveDetails;
    }

    public void setApproveDetails(List<String> approveDetails) {
        this.approveDetails = approveDetails;
    }

    @XmlElementWrapper(name = "decline_details")
    @XmlElement(name = "decline_detail")
    public List<String> getDeclineDetails() {
        return declineDetails;
    }

    public void setDeclineDetails(List<String> declineDetails) {
        this.declineDetails = declineDetails;
    }
}

