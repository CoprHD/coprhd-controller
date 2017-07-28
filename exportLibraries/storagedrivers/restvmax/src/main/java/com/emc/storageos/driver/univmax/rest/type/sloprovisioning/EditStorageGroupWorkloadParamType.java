/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

public class EditStorageGroupWorkloadParamType {

    // min/max occurs: 1/1
    private String workloadSelection;

    public String getWorkloadSelection() {
        return workloadSelection;
    }

    public void setWorkloadSelection(String workloadSelection) {
        this.workloadSelection = workloadSelection;
    }
}
