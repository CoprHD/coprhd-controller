/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.map.annotate.JsonRootName;

import java.util.List;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value = "nfs_exports")
public class DDExportList {

    private List<DDExportInfo> exports;

    public List<DDExportInfo> getExports() {
        return exports;
    }

    public void setExports(List<DDExportInfo> exports) {
        this.exports = exports;
    }

}
