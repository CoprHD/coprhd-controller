/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class RenameStorageGroupParamType extends ParamType {

    // min/max occurs: 1/1
    private String new_storage_Group_name;

    public void setNew_storage_Group_name(String new_storage_Group_name) {
        this.new_storage_Group_name = new_storage_Group_name;
    }
}
