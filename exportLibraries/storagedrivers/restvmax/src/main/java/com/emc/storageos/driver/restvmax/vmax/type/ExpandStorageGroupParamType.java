/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.restvmax.vmax.type;

public class ExpandStorageGroupParamType extends ParamType{
    private Long num_of_vols;
    private VolumeAttributeType olumeAttribute;
    private Boolean create_new_volumes;

    public Long getNum_of_vols() {
        return num_of_vols;
    }

    public void setNum_of_vols(Long num_of_vols) {
        this.num_of_vols = num_of_vols;
    }

    public VolumeAttributeType getOlumeAttribute() {
        return olumeAttribute;
    }

    public void setOlumeAttribute(VolumeAttributeType olumeAttribute) {
        this.olumeAttribute = olumeAttribute;
    }

    public Boolean getCreate_new_volumes() {
        return create_new_volumes;
    }

    public void setCreate_new_volumes(Boolean create_new_volumes) {
        this.create_new_volumes = create_new_volumes;
    }
}
