/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.io.Serializable;

public class MountPoint implements Serializable {
    private static final long serialVersionUID = 1L;
    private String device;
    private String path;
    private String fsType;
    private String options;

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFsType() {
        return fsType;
    }

    public void setFsType(String fsType) {
        this.fsType = fsType;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String toString() {
        return String.format("%s,%s,%s,%s", device, path, fsType, options);
    }
}
