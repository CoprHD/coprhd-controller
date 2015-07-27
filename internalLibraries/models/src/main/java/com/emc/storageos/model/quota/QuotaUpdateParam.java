/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.quota;

/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "quota_update")
public class QuotaUpdateParam {

    private Boolean enable;
    private Long quotaInGb;

    public QuotaUpdateParam() {}
    
    public QuotaUpdateParam(Boolean enable, Long quotaInGb) {
        this.enable = enable;
        this.quotaInGb = quotaInGb;
    }

    /**
     * Enable setting quotas for this resource. 
     * @valid true
     * @valid false
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
     * @valid none
     */
    @XmlElement(name = "quota_gb")
    public Long getQuotaInGb() {
        return quotaInGb;
    }

    public void setQuotaInGb(Long quotaInGb) {
        this.quotaInGb = quotaInGb;
    }
    
}
