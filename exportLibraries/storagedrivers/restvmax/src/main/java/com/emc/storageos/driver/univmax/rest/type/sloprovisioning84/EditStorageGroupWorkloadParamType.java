/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class EditStorageGroupWorkloadParamType extends ParamType {

    // min/max occurs: 1/1
    private String workloadSelection;

    public void setWorkloadSelection(String workloadSelection) {
        this.workloadSelection = workloadSelection;
    }
}
