/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXUnityQuotaConfig extends VNXeBase {
    private String id;
    private VNXeBase filesystem;
    private VNXeBase treeQuota;
    private Integer defaultHardLimit;
    private Integer defaultSoftLimit;
    private Integer gracePeriod;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public VNXeBase getFilesystem() {
        return filesystem;
    }

    public void setFilesystem(VNXeBase filesystem) {
        this.filesystem = filesystem;
    }

    public VNXeBase getTreeQuota() {
        return treeQuota;
    }

    public void setTreeQuota(VNXeBase treeQuota) {
        this.treeQuota = treeQuota;
    }

    public Integer getDefaultHardLimit() {
        return defaultHardLimit;
    }

    public void setDefaultHardLimit(Integer defaultHardLimit) {
        this.defaultHardLimit = defaultHardLimit;
    }

    public Integer getDefaultSoftLimit() {
        return defaultSoftLimit;
    }

    public void setDefaultSoftLimit(Integer defaultSoftLimit) {
        this.defaultSoftLimit = defaultSoftLimit;
    }

    public Integer getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(Integer gracePeriod) {
        this.gracePeriod = gracePeriod;
    }
}
