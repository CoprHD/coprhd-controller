/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model;

import com.emc.storageos.driver.univmax.smc.basetype.DefaultParameter;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.CapacityUnitType;

public class VolumeAttributeType extends DefaultParameter {
    private CapacityUnitType capacityUnit;
    private String volume_size;

    public VolumeAttributeType(CapacityUnitType capacityUnit, String volumeSize) {
        this.capacityUnit = capacityUnit;
        this.volume_size = volumeSize;
    }

}
