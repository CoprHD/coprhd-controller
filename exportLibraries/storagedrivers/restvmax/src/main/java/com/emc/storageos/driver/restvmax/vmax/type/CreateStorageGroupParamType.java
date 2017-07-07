/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.vmax.type;

public class CreateStorageGroupParamType extends ParamType {
    private String storageGroupId;
    private Boolean create_empty_storage_group;
    private String srpId;
    private SloBasedStorageGroupParamType[] sloBasedStorageGroupParam;

    public CreateStorageGroupParamType(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public CreateStorageGroupParamType setCreateEmptyStorageGroup(boolean createEmptyStorageGroup) {
        this.create_empty_storage_group = createEmptyStorageGroup;
        return this;
    }

    public CreateStorageGroupParamType setSrpId(String srpId) {
        this.srpId = srpId;
        return this;
    }

    public void setSloBasedStorageGroupParam(SloBasedStorageGroupParamType[] sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }
}
