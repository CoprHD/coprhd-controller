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

import com.emc.storageos.model.DataObjectRestRep;
import com.google.common.collect.Lists;

/**
 * REST Response representing details for an Event.
 */
@XmlRootElement(name = "event_details")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EventDetailsRestRep extends DataObjectRestRep {

    private List<String> approveDetails;
    private List<String> declineDetails;

    public EventDetailsRestRep() {
    }

    @XmlElementWrapper(name = "approve_details")
    @XmlElement(name = "approve_detail")
    public List<String> getApproveDetails() {
        if (approveDetails == null) {
            return Lists.newArrayList();
        } else {
            return approveDetails;
        }
    }

    public void setApproveDetails(List<String> approveDetails) {
        this.approveDetails = approveDetails;
    }

    @XmlElementWrapper(name = "decline_details")
    @XmlElement(name = "decline_detail")
    public List<String> getDeclineDetails() {
        if (declineDetails == null) {
            return Lists.newArrayList();
        } else {
            return declineDetails;
        }
    }

    public void setDeclineDetails(List<String> declineDetails) {
        this.declineDetails = declineDetails;
    }
}
