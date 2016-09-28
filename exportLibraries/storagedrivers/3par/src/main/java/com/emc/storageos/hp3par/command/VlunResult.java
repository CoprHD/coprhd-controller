/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class VlunResult {
    private boolean status;
    private String assignedLun;
    
    public boolean getStatus() {
        return status;
    }
    public void setStatus(boolean status) {
        this.status = status;
    }
    public String getAssignedLun() {
        return assignedLun;
    }
    public void setAssignedLun(String assignedLun) {
        this.assignedLun = assignedLun;
    }
}
