/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.HashMap;
import java.util.Map;

public enum RaidTypeEnum {
    NONE(0),
    RAID5(1),
    RAID0(2),
    RAID1(3),
    RAID3(4),
    RAID10(7),
    RAID6(10),
    MIXED(12);

    private static final Map<Integer, RaidTypeEnum> raidTypeMap = new HashMap<Integer, RaidTypeEnum>();
    static {
        for (RaidTypeEnum type : RaidTypeEnum.values()) {
            raidTypeMap.put(type.value, type);
        }
    }

    private int value;

    private RaidTypeEnum(int value) {
        this.value = value;
    }

    @org.codehaus.jackson.annotate.JsonValue
    public int getValue() {
        return this.value;
    }

    public static RaidTypeEnum getEnumValue(Integer inValue) {
        return raidTypeMap.get(inValue);
    }

}