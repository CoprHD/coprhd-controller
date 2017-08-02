/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

/**
 * @author fengs5
 *
 */
public class StorageGroupSelectionType extends ParamType {
    private UseExistingStorageGroupParamType useExistingStorageGroupParam;
    private CreateStorageGroupParamType createStorageGroupParam;

    /**
     * @return the useExistingStorageGroupParam
     */
    public UseExistingStorageGroupParamType getUseExistingStorageGroupParam() {
        return useExistingStorageGroupParam;
    }

    /**
     * @param useExistingStorageGroupParam the useExistingStorageGroupParam to set
     */
    public void setUseExistingStorageGroupParam(UseExistingStorageGroupParamType useExistingStorageGroupParam) {
        this.useExistingStorageGroupParam = useExistingStorageGroupParam;
    }

    /**
     * @return the createStorageGroupParam
     */
    public CreateStorageGroupParamType getCreateStorageGroupParam() {
        return createStorageGroupParam;
    }

    /**
     * @param createStorageGroupParam the createStorageGroupParam to set
     */
    public void setCreateStorageGroupParam(CreateStorageGroupParamType createStorageGroupParam) {
        this.createStorageGroupParam = createStorageGroupParam;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "StorageGroupSelectionType [useExistingStorageGroupParam=" + useExistingStorageGroupParam + ", createStorageGroupParam="
                + createStorageGroupParam + "]";
    }

}
