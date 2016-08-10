/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.event;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "event_stats")
public class EventStatsRestRep {
    int pending;
    int approved;
    int declined;

    public EventStatsRestRep() {
    };

    public EventStatsRestRep(int pending, int approved, int declined) {
        this.pending = pending;
        this.approved = approved;
        this.declined = declined;
    }

    /** Number of tasks in a pending state */
    @XmlElement(name = "pending")
    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    /** Number of tasks in an approved state */
    @XmlElement(name = "approved")
    public int getApproved() {
        return approved;
    }

    public void setApproved(int approved) {
        this.approved = approved;
    }

    /** Number of tasks in a declined state */
    @XmlElement(name = "declined")
    public int getDeclined() {
        return declined;
    }

    public void setDeclined(int declined) {
        this.declined = declined;
    }
}
