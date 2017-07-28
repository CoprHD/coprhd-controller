/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.VolumeAttributeType;
import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class ExpandStorageGroupParamType extends ParamType {

    // min/max occurs: 1/1
    private Long num_of_vols;
    // min/max occurs: 1/1
    private VolumeAttributeType volumeAttribute;
    // min/max occurs: 0/1
    private Boolean create_new_volumes;

    public Long getNum_of_vols() {
        return num_of_vols;
    }

    public void setNum_of_vols(Long num_of_vols) {
        this.num_of_vols = num_of_vols;
    }

    public VolumeAttributeType getVolumeAttribute() {
        return volumeAttribute;
    }

    public void setVolumeAttribute(VolumeAttributeType volumeAttribute) {
        this.volumeAttribute = volumeAttribute;
    }

    public Boolean getCreate_new_volumes() {
        return create_new_volumes;
    }

    public void setCreate_new_volumes(Boolean create_new_volumes) {
        this.create_new_volumes = create_new_volumes;
    }
}
