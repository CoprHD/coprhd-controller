/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class UseExistingStorageGroupParamType extends ParamType {

    private String storageGroupId;

    /**
     * @return the storageGroupId
     */
    public String getStorageGroupId() {
        return storageGroupId;
    }

    /**
     * @param storageGroupId the storageGroupId to set
     */
    public void setStorageGroupId(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    /**
     * @param storageGroupId
     */
    public UseExistingStorageGroupParamType(String storageGroupId) {
        super();
        this.storageGroupId = storageGroupId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "UseExistingStorageGroupParamType [storageGroupId=" + storageGroupId + "]";
    }

}
