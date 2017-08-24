/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

public class EditVolumeActionParamType {

    // min/max occurs: 0/1
    private FreeVolumeParamType freeVolumeParam;
    // min/max occurs: 0/1
    private ExpandVolumeParamType expandVolumeParam;
    // min/max occurs: 0/1
    private ModifyVolumeIdentifierParamType modifyVolumeIdentifierParam;

    public void setFreeVolumeParam(FreeVolumeParamType freeVolumeParam) {
        this.freeVolumeParam = freeVolumeParam;
    }

    public void setExpandVolumeParam(ExpandVolumeParamType expandVolumeParam) {
        this.expandVolumeParam = expandVolumeParam;
    }

    public void setModifyVolumeIdentifierParam(ModifyVolumeIdentifierParamType modifyVolumeIdentifierParam) {
        this.modifyVolumeIdentifierParam = modifyVolumeIdentifierParam;
    }
}
