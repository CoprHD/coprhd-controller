/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@XmlRootElement
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class CifsShareCreateForSnapParam extends ParamBase {
    private String path;
    private VNXeBase filesystemSnap;
    private VNXeBase snap;
    private String description;
    private Boolean isReadOnly;
    private Boolean isContinuousAvailabilityEnabled;
    private Boolean isEncryptionEnabled;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public VNXeBase getFilesystemSnap() {
        return filesystemSnap;
    }

    public void setFilesystemSnap(VNXeBase filesystemSnap) {
        this.filesystemSnap = filesystemSnap;
    }

    public VNXeBase getSnap() {
        return snap;
    }

    public void setSnap(VNXeBase snap) {
        this.snap = snap;
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

    public void setIsReadOnly(Boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public Boolean getIsContinuousAvailabilityEnabled() {
        return isContinuousAvailabilityEnabled;
    }

    public void setIsContinuousAvailabilityEnabled(
            Boolean isContinuousAvailabilityEnabled) {
        this.isContinuousAvailabilityEnabled = isContinuousAvailabilityEnabled;
    }

    public Boolean getIsEncryptionEnabled() {
        return isEncryptionEnabled;
    }

    public void setIsEncryptionEnabled(Boolean isEncryptionEnabled) {
        this.isEncryptionEnabled = isEncryptionEnabled;
    }

}
