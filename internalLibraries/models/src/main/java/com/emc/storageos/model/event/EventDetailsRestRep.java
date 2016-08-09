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

/**
 * REST Response representing details for an Event.
 */
@XmlRootElement(name = "event_details")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EventDetailsRestRep extends DataObjectRestRep {

    private String approveDetails;
    private String declineDetails;

    public EventDetailsRestRep() {
    }

    @XmlElement(name = "approve_details")
    public String getApproveDetails() {
        return approveDetails;
    }

    public void setApproveDetails(String approveDetails) {
        this.approveDetails = approveDetails;
    }

    @XmlElement(name = "decline_details")
    public String getDeclineDetails() {
        return declineDetails;
    }

    public void setDeclineDetails(String declineDetails) {
        this.declineDetails = declineDetails;
    }
}
