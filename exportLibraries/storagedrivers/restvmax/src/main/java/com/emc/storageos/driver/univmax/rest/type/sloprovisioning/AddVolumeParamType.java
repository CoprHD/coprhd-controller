/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class AddVolumeParamType extends ParamType {

    // min/max occurs: 1/unbounded
    private String[] volumeId;

    public String[] getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(String[] volumeId) {
        this.volumeId = volumeId;
    }
}
