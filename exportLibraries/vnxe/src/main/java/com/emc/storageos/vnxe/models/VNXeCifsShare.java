/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeCifsShare extends VNXeBase {
    private String name;
    private String creationTime;
    private Boolean isContinuousAvailabilityEnabled;
    private Boolean isReadOnly;
    private Boolean isEncryptionEnabled;
    private VNXeBase filesystem;
    private String modifiedTime;
    private Boolean isACEEnabled;
    private String path;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(String creationTime) {
        this.creationTime = creationTime;
    }

    public Boolean getIsContinuousAvailabilityEnabled() {
        return isContinuousAvailabilityEnabled;
    }

    public void setIsContinuousAvailabilityEnabled(
            Boolean isContinuousAvailabilityEnabled) {
        this.isContinuousAvailabilityEnabled = isContinuousAvailabilityEnabled;
    }

    public Boolean getIsReadOnly() {
        return isReadOnly;
    }

    public void setIsReadOnly(Boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public Boolean getIsEncryptionEnabled() {
        return isEncryptionEnabled;
    }

    public void setIsEncryptionEnabled(Boolean isEncryptionEnabled) {
        this.isEncryptionEnabled = isEncryptionEnabled;
    }

    public VNXeBase getFilesystem() {
        return filesystem;
    }

    public void setFilesystem(VNXeBase filesystem) {
        this.filesystem = filesystem;
    }

    public String getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(String modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public Boolean getIsACEEnabled() {
        return isACEEnabled;
    }

    public void setIsACEEnabled(Boolean isACEEnabled) {
        this.isACEEnabled = isACEEnabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

}
