/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.bean;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractParameter;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.CapacityUnitType;

public class VolumeAttributeType extends AbstractParameter {
    private CapacityUnitType capacityUnit;
    private String volume_size;

    public VolumeAttributeType(CapacityUnitType capacityUnit, String volumeSize) {
        this.capacityUnit = capacityUnit;
        this.volume_size = volumeSize;
    }

}
