/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.vmax.type;

public class CreateStorageGroupParamType extends ParamType {
    private String storageGroupId;
    private boolean create_empty_storage_group;
    private String srpId;
    private SloBasedStorageGroupParamType[] sloBasedStorageGroupParam;

    CreateStorageGroupParamType(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public void setCreateEmptyStorageGroup(boolean createEmptyStorageGroup) {
        this.create_empty_storage_group = createEmptyStorageGroup;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    public void setSloBasedStorageGroupParam(SloBasedStorageGroupParamType[] sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }
}
