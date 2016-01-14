/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.model;

public class MountPoint {

    private String path;

    private String device;

    private String options;

    public MountPoint(String path, String device, String options) {
        this.path = path;
        this.device = device;
        this.options = options;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    @Override
    public String toString() {
        return "MountPoint [path=" + path + ", device=" + device + ", options=" + options + "]";
    }

}
