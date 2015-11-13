/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.request;

/**
 * Parameters to remove consistency group snapshot
 * 
 */
public class ScaleIORemoveConsistencyGroupSnapshots {
    private String snapGroupId;

    public String getSnapGroupId() {
        return snapGroupId;
    }

    public void setSnapGroupId(String snapGroupId) {
        this.snapGroupId = snapGroupId;
    }

}
