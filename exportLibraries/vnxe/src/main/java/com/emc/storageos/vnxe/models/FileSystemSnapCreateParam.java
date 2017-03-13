/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class FileSystemSnapCreateParam extends ParamBase {
    private VNXeBase storageResource;
    private String name;
    private String description;
    private Boolean isAutoDelete;
    private Boolean isReadOnly;
    private Long retentionDuration;

    public VNXeBase getStorageResource() {
        return storageResource;
    }

    public void setStorageResource(VNXeBase storageResource) {
        this.storageResource = storageResource;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

   public Boolean getIsReadOnly() {
        return isReadOnly;
    }

    public void setIsReadOnly(Boolean readOnly) {
        this.isReadOnly = readOnly;
    }

    public Long getRetentionDuration() {
        return retentionDuration;
    }

    public void setRetentionDuration(Long retentionDuration) {
        this.retentionDuration = retentionDuration;
    }

    public Boolean getIsAutoDelete() {
        return isAutoDelete;
    }

    public void setIsAutoDelete(Boolean isAutoDelete) {
        this.isAutoDelete = isAutoDelete;
    }

}
