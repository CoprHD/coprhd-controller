/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class ISCSIInfo {
    private Long iSNSPort;

    public Long getiSNSPort() {
        return iSNSPort;
    }

    public void setiSNSPort(Long iSNSPort) {
        this.iSNSPort = iSNSPort;
    }
}
