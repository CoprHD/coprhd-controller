/*
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
package com.emc.storageos.security.audit;

import java.net.URI;
import java.util.UUID;

import com.emc.storageos.db.client.model.AuditLog;
import com.emc.storageos.services.OperationTypeEnum;

/**
 * It is used to record a Bourne audit log in the database.
 */
public class RecordableAuditLog {

    // product Id
    private String _productId;

    // Id of the tenant associated with the auditlog.
    private URI _tenantId;

    // Id of the user associated with the auditlog.
    private URI _userId;

    // The type of audit log.
    private OperationTypeEnum _auditType;

    // An identifier for the service generating the auditlog.
    private String _serviceType;

    // Time date/time the auditlog occurred.
    private long _timestamp;

    // A description for the auditlog.
    private String _description;

    // OperationalStatusCodes
    private String _operationalStatus;

    // The constant used to generate the auditlog URN used for the auditlog id.
    private static final String AUDITLOG_URN_FORMAT_STR = "urn:storageos:%1$s:%2$s";

    /**
     * Default constructor.
     */
    public RecordableAuditLog() {
    }

    /**
     * Constructor initializes the auditlog info.
     * 
     * @param type
     *            The auditlog type.
     * @param tenantId
     *            The id of the tenant associated with the auditlog.
     * @param userId
     *            The id of the user associated with the auditlog.
     * @param projectId
     *            The id of the project associated with the auditlog.
     * @param cos
     *            The vpool for the auditlog.
     * @param service
     *            The service generating the auditlog.
     * @param resourceId
     *            The id of the resource impacted by the auditlog.
     * @param description
     *            An auditlog description.
     * @param timestamp
     *            The date/time when the auditlog occurred.
     * @param extensions
     *            Any extension data for the auditlog.
     * @param nativeGuid
     *            NativeGuid of the auditlog that can help to find corresponding
     *            resource.
     * @param recordType
     *            recordType of the indication Event or Alert.
     */
    public RecordableAuditLog(String productId,
            URI tenantId,
            URI userId,
            String serviceType,
            OperationTypeEnum auditType,
            long timestamp,
            String description,
            String osStatus) {
        _productId = productId;
        _tenantId = tenantId;
        _userId = userId;
        _serviceType = serviceType;
        _auditType = auditType;
        _timestamp = timestamp;
        _description = description;
        _operationalStatus = osStatus;
    }

    /**
     * {@inheritDoc}
     */
    public String getProductId() {
        return _productId;
    }

    /**
     * Setter for the product id.
     * 
     * @param productId
     *            The product id.
     */
    public void setProductId(String productId) {
        _productId = productId;
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
    public String getServiceType() {
        return _serviceType;
    }

    /**
     * Setter for the service.
     * 
     * @param serviceType
     *            The service.
     */
    public void setServiceType(String serviceType) {
        _serviceType = serviceType;
    }

    /**
     * {@inheritDoc}
     */
    public String getAuditType() {
        if (_auditType != null) {
            return _auditType.name();
        } else {
            return null;
        }
    }

    /**
     * Setter for the auditlog type.
     * 
     * @param auditType
     *            The auditlog type.
     */
    public void setAuditType(OperationTypeEnum auditType) {
        _auditType = auditType;
    }

    /**
     * {@inheritDoc}
     */
    public long getTimestamp() {
        return _timestamp;
    }

    /**
     * Setter for the auditlog timestamp.
     * 
     * @param timestamp
     *            The auditlog date/time.
     */
    public void setTimestamp(long timestamp) {
        _timestamp = timestamp;
    }

    /**
     * {@inheritDoc}
     */
    public String getDescription() {
        return _description;
    }

    /**
     * Setter for the auditlog description.
     * 
     * @param description
     *            The description.
     */
    public void setDescription(String description) {
        _description = description;
    }

    public String getOperationalStatus() {
        return _operationalStatus;
    }

    /**
     * Get Bourne auditlog id
     * 
     * @return The Bourne auditlog id.
     */
    public String getAuditlogId() {
        return getUniqueAuditlogId();
    }

    /**
     * Creates a unique URN for an auditlog.
     * 
     * @return A unique URN for an auditlog.
     */
    public static String getUniqueAuditlogId() {
        URI auditlogURI = URI.create(String.format(AUDITLOG_URN_FORMAT_STR,
                AuditLog.class.getSimpleName(), UUID.randomUUID().toString()));

        return auditlogURI.toASCIIString();
    }
}
