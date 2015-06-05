/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.annotate.JsonDeserialize;

public enum RaidTypeEnum {
    NONE(0),
    RAID5(1),
    RAID0(2),
    RAID1(3),
    RAID3(4),
    RAID10(7),
    RAID6(10),
    MIXED(12);
    
    private static final Map<Integer, RaidTypeEnum> raidTypeMap = new HashMap<Integer,RaidTypeEnum>();
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