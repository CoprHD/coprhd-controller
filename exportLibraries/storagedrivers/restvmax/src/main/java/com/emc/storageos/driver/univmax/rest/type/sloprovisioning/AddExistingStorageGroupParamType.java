/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class AddExistingStorageGroupParamType extends ParamType {

    // min/max occurs: 0/unbounded
    private String[] storageGroupId;

    public void setStorageGroupId(String[] storageGroupId) {
        this.storageGroupId = storageGroupId;
    }
}
