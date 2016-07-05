/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.event;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Response for getting a list of tenant events
 */
@XmlRootElement(name = "events")
public class EventList {
    private List<NamedRelatedResourceRep> events;

    public EventList() {
    }

    public EventList(List<NamedRelatedResourceRep> events) {
        this.events = events;
    }

    /**
     * List of event objects that exist in ViPR. Each
     * event contains an id, name, and link.
     * 
     */
    @XmlElement(name = "event")
    public List<NamedRelatedResourceRep> getEvents() {
        if (events == null) {
            events = new ArrayList<NamedRelatedResourceRep>();
        }
        return events;
    }

    public void setEvents(List<NamedRelatedResourceRep> events) {
        this.events = events;
    }
}