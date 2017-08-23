/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.CreateStorageEmulationType;
import com.emc.storageos.driver.univmax.rest.type.common.ParamType;
import com.emc.storageos.driver.univmax.rest.type.common.VolumeAttributeType;

public class AddVolumeParamType extends ParamType {

    // min/max occurs: 1/1
    private Long num_of_vols;
    // min/max occurs: 1/1
    private VolumeAttributeType volumeAttribute;
    // min/max occurs: 0/1
    private Boolean create_new_volumes;
    // min/max occurs: 0/1
    private CreateStorageEmulationType emulation;
    // min/max occurs: 0/1
    private VolumeIdentifierType volumeIdentifier;

    public void setNum_of_vols(Long num_of_vols) {
        this.num_of_vols = num_of_vols;
    }

    public void setVolumeAttribute(VolumeAttributeType volumeAttribute) {
        this.volumeAttribute = volumeAttribute;
    }

    public void setCreate_new_volumes(Boolean create_new_volumes) {
        this.create_new_volumes = create_new_volumes;
    }

    public void setEmulation(CreateStorageEmulationType emulation) {
        this.emulation = emulation;
    }

    public void setVolumeIdentifier(VolumeIdentifierType volumeIdentifier) {
        this.volumeIdentifier = volumeIdentifier;
    }
}
