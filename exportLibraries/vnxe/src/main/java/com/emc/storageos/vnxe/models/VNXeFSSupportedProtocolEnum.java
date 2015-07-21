/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

public enum VNXeFSSupportedProtocolEnum {
    NFS(0),
    CIFS(1),
    NFS_CIFS(2);
    
    private final int value;
    private VNXeFSSupportedProtocolEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
