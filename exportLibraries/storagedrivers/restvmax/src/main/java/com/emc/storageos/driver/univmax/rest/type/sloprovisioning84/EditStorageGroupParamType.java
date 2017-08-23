/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ConfigurationManagementParamType;

public class EditStorageGroupParamType extends ConfigurationManagementParamType {

    // min/max occurs: 1/1
    EditStorageGroupActionParamType editStorageGroupActionParam;

    public void setEditStorageGroupActionParam(EditStorageGroupActionParamType editStorageGroupActionParam) {
        this.editStorageGroupActionParam = editStorageGroupActionParam;
    }
}
