/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.model;

public class HpuxVersion {

    private String version;

    public HpuxVersion(String version) {
        String[] versions = version.split("\\.");
        setVersion(String.format("%s.%s", versions[1], versions[2]));
    }

    @Override
    public String toString() {
        return version;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

}
