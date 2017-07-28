/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class CreateStorageGroupParamType extends ParamType {

    public void setStorageGroupId(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    // min/max occurs: 1/1
    private String storageGroupId;
    // min/max occurs: 0/1
    private Boolean create_empty_storage_group;
    // min/max occurs: 0/1
    private String srpId;
    // min/max occurs: 0/unbounded
    private SloBasedStorageGroupParamType[] sloBasedStorageGroupParam;

    public void setCreateEmptyStorageGroup(Boolean createEmptyStorageGroup) {
        this.create_empty_storage_group = createEmptyStorageGroup;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    public void setSloBasedStorageGroupParam(SloBasedStorageGroupParamType[] sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }
}
