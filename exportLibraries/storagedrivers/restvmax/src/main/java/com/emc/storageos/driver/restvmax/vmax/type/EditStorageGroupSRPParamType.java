/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.restvmax.vmax.type;

public class EditStorageGroupSRPParamType extends ParamType{
    private String srpId;

    public String getSrpId() {
        return srpId;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }
}
