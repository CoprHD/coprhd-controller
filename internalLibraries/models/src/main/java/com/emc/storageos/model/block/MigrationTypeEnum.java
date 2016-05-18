/*
 * Copyright (c) 2016 Intel Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.block;

public enum MigrationTypeEnum {
    HOST("Host based data migration"),
    DRIVER("Driver assisted data migration");

    private String description;

    private MigrationTypeEnum(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}
