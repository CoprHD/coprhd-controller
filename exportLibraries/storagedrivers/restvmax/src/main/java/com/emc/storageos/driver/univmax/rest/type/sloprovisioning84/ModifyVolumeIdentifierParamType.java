/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class ModifyVolumeIdentifierParamType extends ParamType {

    // min/max occurs: 0/1
    private VolumeIdentifierType volumeIdentifier;

    public void setVolumeIdentifier(VolumeIdentifierType volumeIdentifier) {
        this.volumeIdentifier = volumeIdentifier;
    }
}
