/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.model;

public class RDisk {

    private String devicePath;

    private String rdiskPath;

    private String wwn;

    public RDisk(String devicePath, String rdiskPath, String wwn) {
        super();
        this.devicePath = devicePath;
        this.rdiskPath = rdiskPath;
        this.wwn = wwn;
    }

    public String getDevicePath() {
        return devicePath;
    }

    public void setDevicePath(String path) {
        this.devicePath = path;
    }

    public String getRdiskPath() {
        return rdiskPath;
    }

    public void setRdiskPath(String rdiskPath) {
        this.rdiskPath = rdiskPath;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    @Override
    public String toString() {
        return "RDisk [devicePath=" + devicePath + ", rdiskPath=" + rdiskPath + ", wwn=" + wwn + "]";
    }
}
