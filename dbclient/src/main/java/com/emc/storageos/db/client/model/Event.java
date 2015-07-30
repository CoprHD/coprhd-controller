/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Event time series data object
 */
@SuppressWarnings("serial")
@XmlRootElement(name = "event")
public class Event extends TimeSeriesSerializer.DataPoint {

    // -- Common properties --

    // urn of tenant resource such as urn:sos:Tenant:123:456:789
    private URI _tenantId;

    // user name or ID of the tenant organization owner
    private URI _userId;

    // urn of project resource associated to
    private URI _projectId;

    // Virtual Pool for this volume
    private URI _virtualPool;

    // block, file or object
    private String _service;

    // Unique identifier to represent Volume UID of the event
    private URI _resourceId;

    // Descriptor that tells about the event
    private String _description;

    // type of event occurred
    private String _eventType;

    // any extra information to provide
    private String _extensions;

    // unique event identifier
    private String _eventId;

    // unique event identifier
    private String _operationalStatusCodes;

    // unique event identifier
    private String _operationalStatusDescriptions;

    // unique event identifier
    private String _eventSource;

    // -- Alert properties --

    // type of alert if this is an alert type
    private String _alertType;

    // type of severity -- This holds an "enum" representation of "Severity"
    private String _severity;

    // NativeGuid.
    private String _nativeGuid;

    // Type of Indication
    private String _recordType;

    // Getters and Setters
    @SerializationIndex(2)
    @XmlElement(name = "tenant_id")
    @JsonProperty("tenant_id")
    public URI getTenantId() {
        return _tenantId;
    }

    public void setTenantId(URI tenantId) {
        _tenantId = tenantId;
    }

    @SerializationIndex(3)
    @XmlElement(name = "alert_type")
    @JsonProperty("alert_type")
    public String getAlertType() {
        return _alertType;
    }

    public void setAlertType(String alertType) {
        _alertType = alertType;
    }

    @SerializationIndex(4)
    @XmlElement(name = "virtual_pool_id")
    @JsonProperty("virtual_pool_id")
    public URI getVirtualPool() {
        return _virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        _virtualPool = virtualPool;
    }

    @SerializationIndex(5)
    @XmlElement(name = "description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
    }

    @SerializationIndex(6)
    @XmlElement(name = "event_id")
    @JsonProperty("event_id")
    public String getEventId() {
        return _eventId;
    }

    public void setEventId(String eventId) {
        _eventId = eventId;
    }

    @SerializationIndex(7)
    @XmlElement(name = "native_guid")
    @JsonProperty("native_guid")
    public String getNativeGuid() {
        return _nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        _nativeGuid = nativeGuid;
    }

    @SerializationIndex(8)
    @XmlElement(name = "event_type")
    @JsonProperty("event_type")
    public String getEventType() {
        return _eventType;
    }

    public void setEventType(String eventType) {
        _eventType = eventType;
    }

    @SerializationIndex(9)
    @XmlElement(name = "extensions")
    public String getExtensions() {
        return _extensions;
    }

    public void setExtensions(String extensions) {
        _extensions = extensions;
    }

    @SerializationIndex(10)
    @XmlElement(name = "project_id")
    @JsonProperty("project_id")
    public URI getProjectId() {
        return _projectId;
    }

    public void setProjectId(URI projectId) {
        _projectId = projectId;
    }

    @SerializationIndex(11)
    @XmlElement(name = "resource_id")
    @JsonProperty("resource_id")
    public URI getResourceId() {
        return _resourceId;
    }

    public void setResourceId(URI resourceId) {
        _resourceId = resourceId;
    }

    @SerializationIndex(12)
    @XmlElement(name = "service_type")
    @JsonProperty("service_type")
    public String getService() {
        return _service;
    }

    public void setService(String service) {
        _service = service;
    }

    @SerializationIndex(13)
    @XmlElement(name = "severity")
    public String getSeverity() {
        return _severity;
    }

    public void setSeverity(String severity) {
        _severity = severity;
    }

    @SerializationIndex(14)
    @XmlElement(name = "user_id")
    @JsonProperty("user_id")
    public URI getUserId() {
        return _userId;
    }

    public void setUserId(URI userId) {
        _userId = userId;
    }

    @SerializationIndex(15)
    @XmlElement(name = "record_type")
    @JsonProperty("record_type")
    public String getRecordType() {
        return _recordType;
    }

    public void setRecordType(String recordType) {
        _recordType = recordType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlElement(name = "time_occurred")
    @JsonProperty("time_occurred")
    public long getTimeInMillis() {
        return super.getTimeInMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTimeInMillis(long time) {
        super.setTimeInMillis(time);
    }

    /**
     * {@inheritDoc}
     */
    @SerializationIndex(16)
    @XmlElement(name = "operational_status_codes")
    @JsonProperty("operational_status_codes")
    public String getOperationalStatusCodes() {
        return _operationalStatusCodes;
    }

    /**
     * {@inheritDoc}
     */
    public void setOperationalStatusCodes(String operationalStatusCodes) {
        _operationalStatusCodes = operationalStatusCodes;
    }

    /**
     * {@inheritDoc}
     */
    @SerializationIndex(17)
    @XmlElement(name = "operational_status_descriptions")
    @JsonProperty("operational_status_descriptions")
    public String getOperationalStatusDescriptions() {
        return _operationalStatusDescriptions;
    }

    /**
     * {@inheritDoc}
     */
    public void setOperationalStatusDescriptions(
            String operationalStatusDescriptions) {
        _operationalStatusDescriptions = operationalStatusDescriptions;
    }

    /**
     * {@inheritDoc}
     */
    @SerializationIndex(18)
    @XmlElement(name = "event_source")
    @JsonProperty("event_source")
    public String getEventSource() {
        return _eventSource;
    }

    /**
     * {@inheritDoc}
     */
    public void setEventSource(String eventSource) {
        _eventSource = eventSource;
    }

}
