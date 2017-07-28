/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class RemoveStorageGroupParamType extends ParamType {

    // min/max occurs: 0/unbounded
    private String[] storageGroupId;
    // min/max occurs: 0/1
    private Boolean force;

    public String[] getStorageGroupId() {
        return storageGroupId;
    }

    public void setStorageGroupId(String[] storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public Boolean isForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }
}
