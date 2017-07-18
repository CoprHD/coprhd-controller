/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.model;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractResponse;

public class VolumeListResultType extends AbstractResponse {
    private String volumeId;

    public String getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }
}
