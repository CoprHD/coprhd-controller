/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class AddNewStorageGroupParamType extends ParamType {

    // min/max occurs: 1/1
    private String srpId;
    // min/max occurs: 0/unbounded
    private SloBasedStorageGroupParamType[] sloBasedStorageGroupParam;

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    public SloBasedStorageGroupParamType[] getSloBasedStorageGroupParam() {
        return sloBasedStorageGroupParam;
    }

    public void setSloBasedStorageGroupParam(SloBasedStorageGroupParamType[] sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }
}
