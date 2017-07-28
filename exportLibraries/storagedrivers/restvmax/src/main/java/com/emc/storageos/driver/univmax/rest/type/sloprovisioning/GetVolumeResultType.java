/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;

public class GetVolumeResultType extends GenericResultImplType {

    // min/max occurs: 0/unbounded
    private VolumeType[] volume;

    public VolumeType[] getVolume() {
        return volume;
    }
}
