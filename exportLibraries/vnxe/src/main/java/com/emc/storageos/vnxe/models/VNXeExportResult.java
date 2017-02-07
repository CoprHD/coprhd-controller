/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

public class VNXeExportResult {
    private String lunId;
    private String hostId;
    private int hlu;
    private boolean isNewAccess;

    public String getLunId() {
        return lunId;
    }

    public void setLunId(String lunId) {
        this.lunId = lunId;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public int getHlu() {
        return hlu;
    }

    public void setHlu(int hlu) {
        this.hlu = hlu;
    }

    public boolean isNewAccess() {
        return isNewAccess;
    }

    public void setNewAccess(boolean isNewAccess) {
        this.isNewAccess = isNewAccess;
    }
}
