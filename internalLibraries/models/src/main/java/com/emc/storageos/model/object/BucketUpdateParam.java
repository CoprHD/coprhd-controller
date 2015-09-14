/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a bucket, specified during update.
 * 
 */
@XmlRootElement(name = "bucket_update")
public class BucketUpdateParam {

    private String softQuota;
    private String hardQuota;
    private String retention;

    public BucketUpdateParam() {
    }

    public BucketUpdateParam(String softQuota, String hardQuota, String retention) {
        this.softQuota = softQuota;
        this.hardQuota = hardQuota;
        this.retention = retention;
    }

    /**
     * Soft Quota of Bucket in Bytes.
     * 
     * @valid none
     */
    @XmlElement(required = false, name = "soft_quota")
    public String getSoftQuota() {
        return softQuota;
    }

    public void setSoftQuota(String softQuota) {
        this.softQuota = softQuota;
    }

    /**
     * Hard Quota of Bucket in Bytes.
     * 
     * @valid none
     */
    @XmlElement(required = false, name = "hard_quota")
    public String getHardQuota() {
        return hardQuota;
    }

    public void setHardQuota(String hardQuota) {
        this.hardQuota = hardQuota;
    }

    /**
     * Retention period for a Bucket
     * 
     * @valid none
     */
    @XmlElement(name = "retention", required = false)
    public String getRetention() {
        return retention;
    }

    public void setRetention(String retention) {
        this.retention = retention;
    }
}
