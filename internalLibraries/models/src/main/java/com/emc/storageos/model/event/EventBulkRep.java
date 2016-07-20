/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.event;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_events")
public class EventBulkRep extends BulkRestRep {
    private List<EventRestRep> events;

    /**
     * List of event objects that exist in ViPR.
     * 
     */
    @XmlElement(name = "event")
    public List<EventRestRep> getHosts() {
        if (events == null) {
            events = new ArrayList<EventRestRep>();
        }
        return events;
    }

    public void setEvents(List<EventRestRep> events) {
        this.events = events;
    }

    public EventBulkRep() {
    }

    public EventBulkRep(List<EventRestRep> events) {
        this.events = events;
    }

}
