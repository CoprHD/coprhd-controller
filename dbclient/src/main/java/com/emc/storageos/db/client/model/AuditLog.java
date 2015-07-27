/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * AuditLog time series data object
 */
@SuppressWarnings("serial")
@XmlRootElement(name = "log")
public class AuditLog extends TimeSeriesSerializer.DataPoint {

    // -- Common properties --

    // product id
    private String _productId;

    // urn of tenant resource such as urn:sos:Tenant:123:456:789
    private URI _tenantId;

    // user name or ID of the tenant organization owner
    private URI _userId;

    // block/file/object/tenant/virtual pool/upgrade services etc.
    private String _serviceType;

    // type of auditlog occurred
    private String _auditType;

    // Descriptor that tells about the auditlog 
    private String _description;

    // operation status
    private String _operationalStatus;

    // unique auditlog identifier
    private String _auditlogId;

    // Getters and Setters
    @SerializationIndex(2)
    @XmlElement(name = "product_id")
    @JsonProperty("product_id")
    public String getProductId() {
        return _productId;
    }
    public void setProductId(String productId) {
        _productId = productId;
    }

    @SerializationIndex(3)
    @XmlElement(name = "tenant_id")
    @JsonProperty("tenant_id")
    public URI getTenantId() {
        return _tenantId;
    }
    public void setTenantId(URI tenantId) {
        _tenantId = tenantId;
    }

    @SerializationIndex(4)
    @XmlElement(name = "user_id")
    @JsonProperty("user_id")
    public URI getUserId() {
        return _userId;
    }
    public void setUserId(URI userId) {
        _userId = userId;
    }

    @SerializationIndex(5)
    @XmlElement(name = "service_type")
    @JsonProperty("service_type")
    public String getServiceType() {
        return _serviceType;
    }
    public void setServiceType(String serviceType) {
        _serviceType = serviceType;
    }

    @SerializationIndex(6)
    @XmlElement(name = "audit_type")
    @JsonProperty("audit_type")
    public String getAuditType() {
        return _auditType;
    }
    public void setAuditType(String auditType) {
        _auditType = auditType;
    }

    @SerializationIndex(7)
    @XmlElement(name = "description")
    @JsonProperty("description")
    public String getDescription() {
        return _description;
    }
    public void setDescription(String description) {
        _description = description;
    }

    @SerializationIndex(8)
    @XmlElement(name = "operational_status")
    @JsonProperty("operational_status")
    public String getOperationalStatus() {
        return _operationalStatus;
    }
    public void setOperationalStatus(String operationalStatus) {
        _operationalStatus = operationalStatus;
    }

    @SerializationIndex(9)
    @XmlElement(name = "auditlog_id")
    @JsonProperty("auditlog_id")
    public String getAuditlogId() {
        return _auditlogId;
    }
    public void setAuditlogId(String auditlogId) {
        _auditlogId = auditlogId;
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

}
