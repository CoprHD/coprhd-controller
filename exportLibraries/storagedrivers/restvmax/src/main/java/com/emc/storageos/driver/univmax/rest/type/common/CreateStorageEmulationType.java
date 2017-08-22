/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

public enum  CreateStorageEmulationType {
    FBA("FBA"),
    CELERRA_FBA("CELERRA_FBA"),
    CKD_3390("CKD-3390"),
    CKD_3380("CKD-3380");

    private final String name;

    CreateStorageEmulationType(String s) {
        this.name = s;
    }

    @Override
    public String toString() {
        return getName();
    }

    public String getName() {
        return name;
    }
}
