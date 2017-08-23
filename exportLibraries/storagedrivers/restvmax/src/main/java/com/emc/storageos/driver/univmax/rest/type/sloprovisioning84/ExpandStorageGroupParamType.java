/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

public class ExpandStorageGroupParamType {

    // min/max occurs: 0/1
    private AddExistingStorageGroupParamType addExistingStorageGroupParam;
    // min/max occurs: 0/1
    private AddNewStorageGroupParamType addNewStorageGroupParam;
    // min/max occurs: 0/1
    private ExpandVolumesParamType expandVolumesParam;
    // min/max occurs: 0/1
    private AddSpecificVolumeParamType addSpecificVolumeParam;
    // min/max occurs: 0/1
    private AddVolumeParamType addVolumeParam;

    public void setAddExistingStorageGroupParam(AddExistingStorageGroupParamType addExistingStorageGroupParam) {
        this.addExistingStorageGroupParam = addExistingStorageGroupParam;
    }

    public void setAddNewStorageGroupParam(AddNewStorageGroupParamType addNewStorageGroupParam) {
        this.addNewStorageGroupParam = addNewStorageGroupParam;
    }

    public void setExpandVolumesParam(ExpandVolumesParamType expandVolumesParam) {
        this.expandVolumesParam = expandVolumesParam;
    }

    public void setAddSpecificVolumeParam(AddSpecificVolumeParamType addSpecificVolumeParam) {
        this.addSpecificVolumeParam = addSpecificVolumeParam;
    }

    public void setAddVolumeParam(AddVolumeParamType addVolumeParam) {
        this.addVolumeParam = addVolumeParam;
    }
}
