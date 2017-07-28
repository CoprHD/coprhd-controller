/*
* Copyright (c) 2017 EMC Corporation
* All Rights Reserved
*/

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class EditStorageGroupSLOParamType extends ParamType {

    // min/max occurs: 1/1
    private String sloId;

    public String getSloId() {
        return sloId;
    }

    public void setSloId(String sloId) {
        this.sloId = sloId;
    }
}
