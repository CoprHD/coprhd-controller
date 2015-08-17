/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import com.google.gson.Gson;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Class for Isilon event type.
 */

@SuppressWarnings({ "squid:S1206" })
/*
 * Following Jiras raised for tracking, as fix just before release not feasible.
 * Jira COP-32 -Change static Isilon in future, can't change now
 * Jira COP-33 - Change the code for Inappropriate Collection call
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class IsilonEvent {
    @XmlAccessorType(XmlAccessType.FIELD)
    // TODO: This is not right schema. We get specifiers as a Map. If there is no well defined schema, remove this type.
    public static class Specifiers {
        protected String devid;
        protected String job_id;
        protected String job_type;
        protected String lnn;
        protected String phase_num;
        protected String policy;
        protected String progress;
        protected String running_time;
        protected String state;
        protected String val;
    };

    protected String acknowledged_time;
    protected String coalesced_by_id;
    protected String devid;
    protected String end;
    protected String event_type;
    protected String extreme_severity;
    protected String extreme_value;
    protected String id;
    protected String is_coalescing;
    protected String message;
    protected String severity;
    protected String start;
    protected String update_count;
    protected String value;

    // protected Specifiers specifiers;
    protected Map<String, Object> specifiers;  // get it as a Map

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("Event (Instance id: " + id);
        str.append(", Event type: " + event_type);
        str.append(", dev id: " + devid);
        str.append(", start: " + start);
        str.append(", acknowledge time: " + acknowledged_time);
        str.append(", end: " + end);
        str.append(", severity: " + severity);
        str.append(", message: " + message);
        str.append(")");
        return str.toString();
    }

    /**
     * Return JSON String representation of the object
     * 
     * @return
     */
    public String toJSONString() {
        return new Gson().toJson(this);
    }

    /**
     * Get last modified timestamp on this event
     * 
     * @return
     */
    public long getLatestTime() {
        // Only "start" time is available in PAPI for Mavericks 7.0
        return Long.parseLong(start);
    }

    /**
     * Get last modified timestamp in milli seconds on this event
     * 
     * @return
     */
    public long getLatestTimeMilliSeconds() {
        Long seconds = Long.parseLong(start);
        Long milliSeconds = TimeUnit.MILLISECONDS.convert(seconds, TimeUnit.SECONDS);
        return milliSeconds;
    }

    /**
     * Get event id - identifies the type
     * 
     * @return
     */
    public String getEventId() {
        return event_type;
    }

    /**
     * Get event instance id
     * 
     * @return
     */
    public String getInstanceId() {
        return id;
    }

    /**
     * Message from the event
     * 
     * @return
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get devid from event
     */
    public int getDevId() {
        return Integer.parseInt(devid);
    }

    /**
     * Get severity
     * 
     * @return
     */
    public String getSeverity() {
        return severity;
    }

    /**
     * Get specifier info from event as json string
     * 
     * @return
     */
    public String getSpecifiers() {
        // return new Gson().toJson(specifiers, Specifiers.class).toString();
        return specifiers.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof IsilonEvent)) {
            return false;
        }
        IsilonEvent event = (IsilonEvent) o;
        if (this.getInstanceId().equals(event.getInstanceId())) {
            return true;
        } else {
            return false;
        }
    }

}
