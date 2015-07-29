/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

/**
 * The DB schema version
 */
public class DbVersionInfo {
    private String version;

    public String getSchemaVersion() {
        return version;
    }

    public void setSchemaVersion(String version) {
        this.version = version;
    }
}
