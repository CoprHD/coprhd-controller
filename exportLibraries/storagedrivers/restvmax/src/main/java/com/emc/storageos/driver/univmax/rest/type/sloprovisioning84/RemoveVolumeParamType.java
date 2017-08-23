/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class RemoveVolumeParamType extends ParamType {

    // min/max occurs: 1/unbounded
    private String[] volumeId;

    public void setVolumeId(String[] volumeId) {
        this.volumeId = volumeId;
    }
}
