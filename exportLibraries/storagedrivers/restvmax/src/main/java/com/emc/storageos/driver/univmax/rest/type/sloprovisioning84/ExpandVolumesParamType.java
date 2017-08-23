/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

public class ExpandVolumesParamType {

    // min/max occurs: 0/unbounded
    private SpecificVolumeParamType[] specificVolumeParam;
    // min/max occurs: 0/1
    private AllVolumeParamType allVolumeParam;

    public void setSpecificVolumeParam(SpecificVolumeParamType[] specificVolumeParam) {
        this.specificVolumeParam = specificVolumeParam;
    }

    public void setAllVolumeParam(AllVolumeParamType allVolumeParam) {
        this.allVolumeParam = allVolumeParam;
    }
}
