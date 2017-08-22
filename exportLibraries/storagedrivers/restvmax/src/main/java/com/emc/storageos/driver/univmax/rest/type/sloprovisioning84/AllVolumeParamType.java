/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;
import com.emc.storageos.driver.univmax.rest.type.common.VolumeAttributeType;

public class AllVolumeParamType extends ParamType {

    // min/max occurs: 1/1
    private VolumeAttributeType volumeAttribute;

    public void setVolumeAttribute(VolumeAttributeType volumeAttribute) {
        this.volumeAttribute = volumeAttribute;
    }
}
