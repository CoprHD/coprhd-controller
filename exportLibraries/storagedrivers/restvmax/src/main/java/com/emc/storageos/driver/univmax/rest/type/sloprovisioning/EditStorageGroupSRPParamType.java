/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class EditStorageGroupSRPParamType extends ParamType {

    // min/max occurs: 1/1
    private String srpId;

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }
}
