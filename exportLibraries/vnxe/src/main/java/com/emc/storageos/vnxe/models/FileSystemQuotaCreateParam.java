/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class FileSystemQuotaCreateParam extends ParamBase {
    private Long hardLimit;
    private Long softLimit;
    private String path;
    private VNXeBase filesystem;

    public VNXeBase getFilesystem() {
        return filesystem;
    }

    public void setFilesystem(String fsId) {
        this.filesystem = new VNXeBase(fsId);
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

}
