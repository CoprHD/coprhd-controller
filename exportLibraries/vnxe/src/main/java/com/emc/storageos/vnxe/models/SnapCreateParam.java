/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class SnapCreateParam extends LunSnapCreateParam {
    private Boolean isReadOnly;
    private Integer filesystemAccessType = null;

    public Boolean getIsReadOnly() {
        return isReadOnly;
    }

    public void setIsReadOnly(Boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public Integer getFilesystemAccessType() {
        return filesystemAccessType;
    }

    public void setFilesystemAccessType(Integer filesystemAccessType) {
        this.filesystemAccessType = filesystemAccessType;
    }

}
