/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class RemoveStorageGroupParamType extends ParamType {

    // min/max occurs: 0/unbounded
    private String[] storageGroupId;
    // min/max occurs: 0/1
    private Boolean force;

    public void setStorageGroupId(String[] storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }
}
