/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.bean;

/**
 * @author fengs5
 *
 */
public enum CreateStorageEmulationType {
    FBA("FBA"),
    CELERRA_FBA("CELERRA_FBA"),
    CKD_3390("CKD-3390"),
    CKD_3380("CKD-3380");

    String value;

    CreateStorageEmulationType(String value) {
        this.value = value;
    }
}
