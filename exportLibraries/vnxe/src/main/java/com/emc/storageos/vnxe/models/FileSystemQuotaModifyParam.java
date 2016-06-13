/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class FileSystemQuotaModifyParam extends ParamBase {
    private Long hardLimit;
    private Long softLimit;

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

}
