/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.vmax.type;

public class RemoveVolumeParamType {
    private String[] volumeId;

    public String[] getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(String[] volumeId) {
        this.volumeId = volumeId;
    }
}
