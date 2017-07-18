/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.model;

public class GetVolumeResultType extends GenericResultImplType {
    private VolumeType[] volume;

    GetVolumeResultType(Boolean success) {
        super(success);
    }

    public VolumeType[] getVolume() {
        return volume;
    }

    public void setVolume(VolumeType[] volume) {
        this.volume = volume;
    }
}
