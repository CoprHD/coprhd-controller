/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class FreeVolumeParamType extends ParamType {

    // min/max occurs: 1/1
    private Boolean free_volume;

    public void setFree_volume(Boolean free_volume) {
        this.free_volume = free_volume;
    }
}
