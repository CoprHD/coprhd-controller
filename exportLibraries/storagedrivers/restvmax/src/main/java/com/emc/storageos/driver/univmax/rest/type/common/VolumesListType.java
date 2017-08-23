/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

public class VolumesListType {

    // min/max occurs: 0/unbounded
    private VolumeIdType[] volumeId;

    public VolumeIdType[] getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(VolumeIdType[] volumeId) {
        this.volumeId = volumeId;
    }
}
