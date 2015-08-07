/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.io.Serializable;

public class HBAInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The host ID of the HBA. */
    private int hostId;
    /** The world-wide node name. */
    private String wwnn;
    /** The world-wide port name. */
    private String wwpn;

    public int getHostId() {
        return hostId;
    }

    public void setHostId(int hostId) {
        this.hostId = hostId;
    }

    public String getWwnn() {
        return wwnn;
    }

    public void setWwnn(String wwnn) {
        this.wwnn = wwnn;
    }

    public String getWwpn() {
        return wwpn;
    }

    public void setWwpn(String wwpn) {
        this.wwpn = wwpn;
    }

    public String toString() {
        return String.format("[%s] %s:%s", hostId, wwnn, wwpn);
    }
}
