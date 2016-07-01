/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.hmc.model;

public class HMCVersion {

    private String version;

    public HMCVersion(String version) {
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
