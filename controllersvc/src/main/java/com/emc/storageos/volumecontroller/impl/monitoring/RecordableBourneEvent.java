/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.monitoring;

import java.net.URI;
import java.util.UUID;

import com.emc.storageos.db.client.model.Event;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.Severity;

/**
 * RecordableSystemEvent is used to record a Bourne system event in the
 * database.
 */
public class RecordableBourneEvent implements RecordableEvent {

    // Constant defines the event extension for host initiators for volume
    // export/unexport events.
    public static final String INITIATOR_EXTENSION_NAME = "Initiators";

    // Constant defines the event extension for SMB share names of share/unshare
    // events.
    public static final String FS_SHARE_EXTENSION_NAME = "SMB Shares";

    // Constant defines the event extension for host clients for fileshare
    // export/unexport events.
    public static final String FS_CLIENT_EXTENSION_NAME = "Hosts";
    
    // Constant defines the event extension for ACLS of a share
    public static final String FS_ACL_EXTENSION_NAME = "Acls";

    // The constant used to generate the event URN used for the event id.
    private static final String EVENT_URN_FORMAT_STR = "urn:storageos:%1$s:%2$s";

    // The type of Bourne event.
    private String _type;

    // Id of the tenant associated with the event.
    private URI _tenantId;

    // Id of the user associated with the event.
    private URI _userId;

    // Id of the project associated with the event.
    private URI _projectId;

    // The vpool for the event.
    private URI _vpool;

    // An identifier for the service generating the event.
    private String _service;

    // The id of the resource impacted by the event.
    private URI _resourceId;

    // A description for the event.
    private String _description;

    // Time date/time the event occurred.
    private long _timestamp;

    // Extension data for the event.
    private String _extensions;

    // NativeGuid.
    private String _nativeGuid;

    // Type of Indication
    private String _recordType;

    // severity
    private String _severity;

    // source
    private String _source;

    // OperationalStatusCodes
    private String _operationalStatusCodes;

    // OperationalStatusDescriptions
    private String _operationalStatusDescriptions;

    /**
     * Default constructor.
     */
    public RecordableBourneEvent() {
    }

    /**
     * Constructor initializes the event info.
     * 
     * @param type
     *            The event type.
     * @param tenantId
     *            The id of the tenant associated with the event.
     * @param userId
     *            The id of the user associated with the event.
     * @param projectId
     *            The id of the project associated with the event.
     * @param vpool
     *            The virtual pool for the event.
     * @param service
     *            The service generating the event.
     * @param resourceId
     *            The id of the resource impacted by the event.
     * @param description
     *            An event description.
     * @param timestamp
     *            The date/time when the event occurred.
     * @param extensions
     *            Any extension data for the event.
     * @param nativeGuid
     *            NativeGuid of the event that can help to find corresponding
     *            resource.
     * @param recordType
     *            recordType of the indication Event or Alert.
     */
    public RecordableBourneEvent(String type,
            URI tenantId, URI userId, URI projectId, URI vpool, String service,
            URI resourceId, String description, long timestamp,
            String extensions, String nativeGuid, String recordType,
            String source, String osCodes, String osDescs) {
        _type = type;
        _tenantId = tenantId;
        _userId = userId;
        _projectId = projectId;
        _vpool = vpool;
        _service = service;
        _resourceId = resourceId;
        _description = description;
        _timestamp = timestamp;
        _extensions = extensions;
        _nativeGuid = nativeGuid;
        _recordType = recordType;
        _source = source;
        _operationalStatusCodes = osCodes;
        _operationalStatusDescriptions = osDescs;
    }

    /**
     * Creates a unique URN for an event.
     * 
     * @return A unique URN for an event.
     */
    public static String getUniqueEventId() {
        URI eventURI = URI.create(String.format(EVENT_URN_FORMAT_STR,
                Event.class.getSimpleName(), UUID.randomUUID().toString()));

        return eventURI.toASCIIString();
    }

    /**
     * {@inheritDoc}
     */
    public String getType() {
        if (_type != null)
            return _type;
        else
            return null;
    }

    /**
     * Setter for the event type.
     * 
     * @param type
     *            The event type.
     */
    public void setType(String type) {
        _type = type;
    }

    /**
     * {@inheritDoc}
     */
    public URI getTenantId() {
        return _tenantId;
    }

    /**
     * Setter for the tenant id.
     * 
     * @param tenantId
     *            The tenant id.
     */
    public void setTenantId(URI tenantId) {
        _tenantId = tenantId;
    }

    /**
     * {@inheritDoc}
     */
    public URI getUserId() {
        return _userId;
    }

    /**
     * Setter for the user id.
     * 
     * @param userId
     *            The user id.
     */
    public void setUserId(URI userId) {
        _userId = userId;
    }

    /**
     * {@inheritDoc}
     */
    public URI getProjectId() {
        return _projectId;
    }

    /**
     * Setter for the project id.
     * 
     * @param projectId
     *            The project id.
     */
    public void setProjectId(URI projectId) {
        _projectId = projectId;
    }

    /**
     * {@inheritDoc}
     */
    public URI getVirtualPool() {
        return _vpool;
    }

    /**
     * Setter for the vpool.
     * 
     * @param vpool
     *            The VirtualPool.
     */
    public void setVirtualPool(URI vpool) {
        _vpool = vpool;
    }

    /**
     * {@inheritDoc}
     */
    public String getService() {
        return _service;
    }

    /**
     * Setter for the service.
     * 
     * @param service
     *            The service.
     */
    public void setService(String service) {
        _service = service;
    }

    /**
     * {@inheritDoc}
     */
    public URI getResourceId() {
        return _resourceId;
    }

    /**
     * Setter for the resource id.
     * 
     * @param resourceId
     *            The resource id.
     */
    public void setResourceId(URI resourceId) {
        _resourceId = resourceId;
    }

    /**
     * {@inheritDoc}
     */
    public String getDescription() {
        return _description;
    }

    /**
     * Setter for the event description.
     * 
     * @param description
     *            The description.
     */
    public void setDescription(String description) {
        _description = description;
    }

    /**
     * {@inheritDoc}
     */
    public long getTimestamp() {
        return _timestamp;
    }

    /**
     * Setter for the event timestamp.
     * 
     * @param timestamp
     *            The event date/time.
     */
    public void setTimestamp(long timestamp) {
        _timestamp = timestamp;
    }

    /**
     * {@inheritDoc}
     */
    public String getExtensions() {
        return _extensions;
    }

    /**
     * Setter for the event extension data..
     * 
     * @param extensions
     *            The extension data map.
     */
    public void setExtensions(String extensions) {
        _extensions = extensions;
    }

    /**
     * Get severity
     * 
     * @return
     */
    public String getSeverity() {
        return _severity;
    }

    /**
     * Get Bourne event id
     * 
     * @return The Bourne recordable event id.
     */
    public String getEventId() {
        return getUniqueEventId();
    }

    /**
     * Get event source
     * 
     * @return
     */
    public String getSource() {

        return _source;
    }

    /**
     * Get Alert Type
     * 
     * @return
     */
    public String getAlertType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRecordType() {
        return _recordType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNativeGuid() {
        return _nativeGuid;
    }

    @Override
    public String getOperationalStatusDescriptions() {
        return _operationalStatusDescriptions;
    }

    @Override
    public String getOperationalStatusCodes() {
        return _operationalStatusCodes;
    }
}
