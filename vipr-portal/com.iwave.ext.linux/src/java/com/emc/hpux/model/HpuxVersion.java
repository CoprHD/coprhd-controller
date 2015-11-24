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
        String[] versions = version.split("\\.");
        return String.format("%s.%s", versions[1], versions[2]);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

}
