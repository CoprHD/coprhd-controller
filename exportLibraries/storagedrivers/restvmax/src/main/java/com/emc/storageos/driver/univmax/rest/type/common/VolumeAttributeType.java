/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

public class VolumeAttributeType extends ParamType {

    // min/max occurs: 1/1
    private CapacityUnitType capacityUnit;
    // min/max occurs: 1/1
    private String volume_size;

    public void setCapacityUnit(CapacityUnitType capacityUnit) {
        this.capacityUnit = capacityUnit;
    }

    public void setVolume_size(String volume_size) {
        this.volume_size = volume_size;
    }
}
