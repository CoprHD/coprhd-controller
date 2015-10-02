/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.quota;


import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "quota_info")
public class QuotaInfo {

    private boolean enabled;
    private long quotaInGb;
    private long currentCapacityInGb;
    private NamedRelatedResourceRep limitedResource;

    public QuotaInfo() {
    }

    public QuotaInfo(boolean enabled, long quotaInGb, long currentCapacityInGb,
            NamedRelatedResourceRep limitedResource) {
        this.enabled = enabled;
        this.quotaInGb = quotaInGb;
        this.currentCapacityInGb = currentCapacityInGb;
        this.limitedResource = limitedResource;
    }

    /**
     * Indicates whether setting quotas is enabled for this
     * resource.
     * 
     */
    @XmlElement
    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The quota set (in Gb) for this
     * resource.
     * 
     */
    @XmlElement(name = "quota_gb")
    public long getQuotaInGb() {
        return quotaInGb;
    }

    public void setQuotaInGb(long quotaInGb) {
        this.quotaInGb = quotaInGb;
    }

    /**
     * The provisioned quota (in Gb)
     * allocated for this resource.
     * 
     */
    @XmlElement(name = "current_capacity")
    public long getCurrentCapacityInGb() {
        return currentCapacityInGb;
    }

    public void setCurrentCapacityInGb(long currentCapacityInGb) {
        this.currentCapacityInGb = currentCapacityInGb;
    }

    /**
     * The resource information associated with this quota.
     * 
     */
    @XmlElement(name = "limited_resource")
    public NamedRelatedResourceRep getLimitedResource() {
        return limitedResource;
    }

    public void setLimitedResource(NamedRelatedResourceRep limitedResource) {
        this.limitedResource = limitedResource;
    }

}
