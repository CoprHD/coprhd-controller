/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.quota;


import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "quota_update")
public class QuotaUpdateParam {

    private Boolean enable;
    private Long quotaInGb;

    public QuotaUpdateParam() {
    }

    public QuotaUpdateParam(Boolean enable, Long quotaInGb) {
        this.enable = enable;
        this.quotaInGb = quotaInGb;
    }

    /**
     * Enable setting quotas for this resource.
     * 
     */
    @XmlElement(name = "quota_enabled", required = true)
    public Boolean getEnable() {
        return enable;
    }

    public void setEnable(Boolean enable) {
        this.enable = enable;
    }

    /**
     * Set this quota (in Gb) for this resource.
     * 
     */
    @XmlElement(name = "quota_gb")
    public Long getQuotaInGb() {
        return quotaInGb;
    }

    public void setQuotaInGb(Long quotaInGb) {
        this.quotaInGb = quotaInGb;
    }

}
