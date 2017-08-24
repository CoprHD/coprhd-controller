/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ConfigurationManagementParamType;

public class EditVolumeParamType extends ConfigurationManagementParamType {

    // min/max occurs: 0/1
    private EditVolumeActionParamType editVolumeActionParam;

    public void setEditVolumeActionParam(EditVolumeActionParamType editVolumeActionParam) {
        this.editVolumeActionParam = editVolumeActionParam;
    }
}
