/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.restvmax.vmax.type;

public class AddNewStorageGroupParamType {
    private SloBasedStorageGroupParamType[] sloBasedStorageGroupParam;

    public SloBasedStorageGroupParamType[] getSloBasedStorageGroupParam() {
        return sloBasedStorageGroupParam;
    }

    public void setSloBasedStorageGroupParam(SloBasedStorageGroupParamType[] sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }
}
