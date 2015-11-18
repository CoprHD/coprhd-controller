/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.model;

public class HpuxVersion {

    private String version;

    public HpuxVersion(String version) {
        setVersion(version);
    }

    @Override
    public String toString() {
        return String.format("%s", version);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
