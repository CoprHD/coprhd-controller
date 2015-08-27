/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.block;

public enum IngestionMethodEnum {
    FULL_INGESTION_INCLUDING_BACKEND_VOLUMES("Change one or more volumes from local VPLEX to distributed VPLEX virtual pool"),
    INGEST_ONLY_VIRTUAL_VOLUME("Migrate data from one or more volumes to new virtual pool");
    
    private String description;

    private IngestionMethodEnum(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}
