/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;

public class GetStorageGroupResultType extends GenericResultImplType {

    // min/max occurs: 0/unbounded
    private List<StorageGroupType> storageGroup;

    /**
     * @return the storageGroup
     */
    public List<StorageGroupType> getStorageGroup() {
        return storageGroup;
    }

    /**
     * @param storageGroup the storageGroup to set
     */
    public void setStorageGroup(List<StorageGroupType> storageGroup) {
        this.storageGroup = storageGroup;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "GetStorageGroupResultType [storageGroup=" + storageGroup + ", getSuccess()=" + getSuccess() + ", getHttpCode()="
                + getHttpCode() + ", getMessage()=" + getMessage() + ", isSuccessfulStatus()=" + isSuccessfulStatus() + "]";
    }

}
