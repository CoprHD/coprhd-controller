/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.net.URI;

import com.emc.storageos.model.valid.Length;

/**
 * Attributes associated with a bucket, specified during creation.
 * 
 */
@XmlRootElement(name = "bucket_create")
public class BucketParam {

    private String label;
    private String softQuota;
    private String hardQuota;
    private URI vpool;
    private URI varray;
    private String retention;
    private String owner;
    private String path;

    public BucketParam() {
    }

    public BucketParam(String label, String softQuota, String hardQuota, URI vpool, URI varray, String retention) {
        this.label = label;
        this.softQuota = softQuota;
        this.hardQuota = hardQuota;
        this.vpool = vpool;
        this.varray = varray;
        this.retention = retention;
    }

    /**
     * User provided name or label assigned to the Bucket.
     */
    @XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Soft Quota of Bucket in Bytes.
     * 
     * @valid none
     */
    @XmlElement(required = true, name = "soft_quota")
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
    @XmlElement(required = true, name = "hard_quota")
    public String getHardQuota() {
        return hardQuota;
    }

    public void setHardQuota(String hardQuota) {
        this.hardQuota = hardQuota;
    }

    /**
     * URI representing the virtual pool supporting the Bucket.
     * 
     * @valid none
     */
    @XmlElement(required = true)
    public URI getVpool() {
        return vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

    /**
     * URI representing the virtual array containing the Bucket.
     * 
     * @valid none
     */
    @XmlElement(name = "varray", required = true)
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

    /**
     * Retention period for a Bucket
     * 
     * @valid none
     */
    @XmlElement(name = "retention", required = false)
    public String getRetention() {
        return null != retention ? retention : "0";
    }

    public void setRetention(String retention) {
        this.retention = retention;
    }

    @XmlElement(required = false, name = "owner")
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @XmlElement(required = false, name = "path")
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
