/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.vmax.type;

public class VolumeAttributeType extends ParamType {
    private CapacityUnitType capacityUnit;
    private String volume_size;

    public VolumeAttributeType(CapacityUnitType capacityUnit, String volumeSize) {
        this.capacityUnit = capacityUnit;
        this.volume_size = volumeSize;
    }


}
