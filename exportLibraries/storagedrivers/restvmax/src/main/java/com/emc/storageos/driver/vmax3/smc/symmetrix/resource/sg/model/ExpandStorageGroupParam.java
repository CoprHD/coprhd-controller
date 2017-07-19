/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model;

import com.emc.storageos.driver.vmax3.smc.basetype.DefaultParameter;

public class ExpandStorageGroupParam extends DefaultParameter {
    private AddVolumeParamType addVolumeParam;

    // private addSpecificVolumeParam addSpecificVolumeParam;
    // private expandVolumesParam expandVolumesParam;
    // private addNewStorageGroupParam addNewStorageGroupParam;
    // private addExistingStorageGroupParam addExistingStorageGroupParam;

    /**
     * @return the addVolumeParam
     */
    public AddVolumeParamType getAddVolumeParam() {
        return addVolumeParam;
    }

    /**
     * @param addVolumeParam the addVolumeParam to set
     */
    public void setAddVolumeParam(AddVolumeParamType addVolumeParam) {
        this.addVolumeParam = addVolumeParam;
    }

}
