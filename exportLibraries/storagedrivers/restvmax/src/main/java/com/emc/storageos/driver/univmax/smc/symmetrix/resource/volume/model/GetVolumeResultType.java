/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.symmetrix.resource.volume.model;

import com.emc.storageos.driver.univmax.smc.symmetrix.resource.GenericResultImplType;

public class GetVolumeResultType extends GenericResultImplType {
    private VolumeType[] volume;

    public VolumeType[] getVolume() {
        return volume;
    }

    public void setVolume(VolumeType[] volume) {
        this.volume = volume;
    }
}
