/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.util;

public enum IngestionMethodEnum {
    FULL("Full"),
    VIRTUAL_VOLUMES_ONLY("VirtualVolumesOnly");
    
    private String description;

    private IngestionMethodEnum(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}
