/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.restvmax.vmax.type;

public class RemoveStorageGroupParamType {
    private String[] storageGroupId;
    private boolean force;

    public String[] getStorageGroupId() {
        return storageGroupId;
    }

    public void setStorageGroupId(String[] storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}
