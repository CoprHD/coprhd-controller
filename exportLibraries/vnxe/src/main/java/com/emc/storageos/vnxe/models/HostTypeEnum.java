/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

public enum HostTypeEnum {
    UNKNOWN(0),
    HOSTMANUAL(1),
    SUBNET(2),
    NETGROUP(3),
    RPA(4),
    HOSTAUTO(5);

    private final int value;

    private HostTypeEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
