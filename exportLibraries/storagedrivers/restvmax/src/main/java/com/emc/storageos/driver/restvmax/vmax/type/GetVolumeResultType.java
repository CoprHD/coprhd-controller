/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.restvmax.vmax.type;

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
