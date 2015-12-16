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
        return version;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        String[] versions = version.split("\\.");
        if (versions.length < 2) {
            throw new IllegalArgumentException(version);
        }
        String last = versions[versions.length - 1];
        String secondToLast = versions[versions.length - 2];
        this.version = String.format("%s.%s", secondToLast, last);
    }
}