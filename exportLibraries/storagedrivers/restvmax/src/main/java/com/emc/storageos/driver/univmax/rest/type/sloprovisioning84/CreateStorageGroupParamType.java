/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ConfigurationManagementParamType;
import com.emc.storageos.driver.univmax.rest.type.common.CreateStorageEmulationType;

public class CreateStorageGroupParamType extends ConfigurationManagementParamType {

    // min/max occurs: 1/1
    private String storageGroupId;
    // min/max occurs: 0/1
    private Boolean create_empty_storage_group;
    // min/max occurs: 0/1
    private String srpId;
    // min/max occurs: 0/unbounded
    private SloBasedStorageGroupParamType[] sloBasedStorageGroupParam;
    // min/max occurs: 1/1
    private CreateStorageEmulationType emulation;

    public void setStorageGroupId(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public void setCreate_empty_storage_group(Boolean create_empty_storage_group) {
        this.create_empty_storage_group = create_empty_storage_group;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    public void setSloBasedStorageGroupParam(SloBasedStorageGroupParamType[] sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }

    public void setEmulation(CreateStorageEmulationType emulation) {
        this.emulation = emulation;
    }
}
