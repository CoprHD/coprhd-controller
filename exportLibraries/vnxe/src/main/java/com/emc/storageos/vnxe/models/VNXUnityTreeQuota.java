/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXUnityTreeQuota extends VNXeBase {
    private String id;
    private VNXeBase filesystem;
    private VNXeBase quotaConfig;
    private String path;
    private String description;
    private Long hardLimit;
    private Long softLimit;
    private Long remainingGracePeriod;
    private Long sizeUsed;

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

    public VNXeBase getQuotaConfig() {
        return quotaConfig;
    }

    public void setQuotaConfig(VNXeBase quotaConfig) {
        this.quotaConfig = quotaConfig;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getHardLimit() {
        return hardLimit;
    }

    public void setHardLimit(Long hardLimit) {
        this.hardLimit = hardLimit;
    }

    public Long getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(Long softLimit) {
        this.softLimit = softLimit;
    }

    public Long getRemainingGracePeriod() {
        return remainingGracePeriod;
    }

    public void setRemainingGracePeriod(Long remainingGracePeriod) {
        this.remainingGracePeriod = remainingGracePeriod;
    }

    public Long getSizeUsed() {
        return sizeUsed;
    }

    public void setSizeUsed(Long sizeUsed) {
        this.sizeUsed = sizeUsed;
    }

    public String getQuotaConfigId() {
        return quotaConfig.getId();
    }

}
