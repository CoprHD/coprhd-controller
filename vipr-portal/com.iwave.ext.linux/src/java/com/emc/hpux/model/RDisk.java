/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.model;

public class RDisk {

    private String path;

    private String wwn;

    public RDisk(String path, String wwn) {
        super();
        this.path = path;
        this.wwn = wwn;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    @Override
    public String toString() {
        return "RDisk [path=" + path + ", wwn=" + wwn + "]";
    }
}
