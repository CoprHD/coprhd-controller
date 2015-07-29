/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
 Copyright (c) 2012 EMC Corporation
 All Rights Reserved

 This software contains the intellectual property of EMC Corporation
 or is licensed to EMC Corporation from third parties.  Use of this
 software and the intellectual property contained therein is expressly
 imited to the terms and conditions of the License Agreement under which
 it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa.data.internal;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

public class Event {

    // -- Common properties --

    // urn of tenant resource such as urn:sos:Tenant:123:456:789
    @XmlElement(name = "tenant_id")
    private URI _tenantId;

    // user name or ID of the tenant organization owner
    @XmlElement(name = "user_id")
    private URI _userId;

    // urn of project resource associated to
    @XmlElement(name = "projectId")
    private URI _projectId;

    // class of service for this volume
    @XmlElement(name = "cosId")
    private URI _cos;

    // block, file or object
    @XmlElement(name = "serviceType")
    private String _service;

    // Unique identifier to represent Volume UID of the event
    @XmlElement(name = "resource_id")
    private URI _resourceId;

    // Descriptor that tells about the event
    @XmlElement(name = "description")
    private String _description;

    // type of event occurred
    @XmlElement(name = "event_type")
    private String _eventType;

    // any extra information to provide
    @XmlElement(name = "extensions")
    private String _extensions;

    @XmlElement(name = "time_occurred")
    private String _timeOccurred;

    // event/alert
    @XmlElement(name = "record_type")
    private String _recordType;

    // source of event occurred
    @XmlElement(name = "event_source")
    private String _eventSource;

    // unique event identifier
    @XmlElement(name = "event_id")
    private String _eventId;

    // -- Alert properties --

    // type of alert if this is an alert type
    @XmlElement(name = "alertType")
    private String _alertType;

    // type of severity -- This holds an "enum" representation of "Severity"
    @XmlElement(name = "severity")
    private String _severity;

    // Getters and Setters

    public URI getTenantId() {
        return _tenantId;
    }

    public String getAlertType() {
        return _alertType;
    }

    public URI getCos() {
        return _cos;
    }

    public String getDescription() {
        return _description;
    }

    public String getEventId() {
        return _eventId;
    }

    public String getEventSource() {
        return _eventSource;
    }

    public String getEventType() {
        return _eventType;
    }

    public String getExtensions() {
        return _extensions;
    }

    public URI getProjectId() {
        return _projectId;
    }

    public URI getResourceId() {
        return _resourceId;
    }

    public String getService() {
        return _service;
    }

    public String getSeverity() {
        return _severity;
    }

    public URI getUserId() {
        return _userId;
    }

    public String getTimeOccurred() {
        return _timeOccurred;
    }

    public String getRecordType() {
        return _recordType;
    }

    @Override
    public String toString() {
        return "Event [_alertType=" + _alertType + ", _cos=" + _cos
                + ", _description=" + _description + ", _eventId=" + _eventId
                + ", _eventSource=" + _eventSource + ", _eventType="
                + _eventType + ", _extensions=" + _extensions + ", _projectId="
                + _projectId + ", _recordType=" + _recordType
                + ", _resourceId=" + _resourceId + ", _service=" + _service
                + ", _severity=" + _severity + ", _tenantId=" + _tenantId
                + ", _timeOccurred=" + _timeOccurred + ", _userId=" + _userId
                + "]";
    }

    @XmlRootElement(name = "events")
    public static class EventList {

        @XmlElement(name = "event")
        private List<Event> events;

        /**
         * @return the events
         */
        public List<Event> getEvents() {
            return events;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("EventList [events=");
            builder.append(events);
            builder.append("]");
            return builder.toString();
        }

    }

}
