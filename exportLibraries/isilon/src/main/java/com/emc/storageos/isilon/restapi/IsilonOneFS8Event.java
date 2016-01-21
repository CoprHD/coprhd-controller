/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.google.gson.Gson;

/**
 * IsilonOneFS8Event class represents the Isilon event type for ISILON OneFS8.0 array.
 * 
 * @author prasaa9
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class IsilonOneFS8Event {

    @XmlAccessorType(XmlAccessType.FIELD)
    // Class for ISILON event type
    public static class Events {
        protected String devid;
        protected String event;
        protected String id;
        protected String message;
        protected String severity;
        protected String time;
        protected String resolve_time;
        protected String value;

        public static class Specifier {
            protected String client;
            protected String devid;
            protected String error;
            protected String lnn;
            protected String count;
            protected String sec;
            protected String path;
            protected String val;
        };

        // get it as a Map
        protected Map<String, Object> specifier;

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("Event (Instance id: " + id);
            str.append(", Event type: " + event);
            str.append(", dev id: " + devid);
            str.append(", resolve time: " + resolve_time);
            str.append(", severity: " + severity);
            str.append(", message: " + message);
            str.append(")");
            return str.toString();
        }

        /**
         * Get specifier info from event as JSON string
         * 
         * @return
         */
        public Map<String, Object> getSpecifier() {
            return specifier;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof Events)) {
                return false;
            }
            Events event = (Events) o;
            if (this.id.equals(event.id)) {
                return true;
            } else {
                return false;
            }
        }
    }

    // get it as a list of events
    public List<Events> events;

    /**
     * Return JSON String representation of the object
     * 
     * @return
     */
    public String toJSONString() {
        return new Gson().toJson(this);
    }

    /**
     * Return list of ISILON events
     * 
     * @return
     */
    public List<Events> getEvents() {
        return events;
    }
}
