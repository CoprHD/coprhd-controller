/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;
import com.emc.storageos.driver.univmax.rest.type.common.VolumeAttributeType;

public class SpecificVolumeParamType extends ParamType {

    // min/max occurs: 1/unbounded
    private String[] volumeId;
    // min/max occurs: 1/1
    private VolumeAttributeType volumeAttribute;

    public void setVolumeId(String[] volumeId) {
        this.volumeId = volumeId;
    }

    public void setVolumeAttribute(VolumeAttributeType volumeAttribute) {
        this.volumeAttribute = volumeAttribute;
    }
}
