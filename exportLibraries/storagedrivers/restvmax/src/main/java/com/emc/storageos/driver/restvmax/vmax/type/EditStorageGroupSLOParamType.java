/*
* Copyright (c) 2017 EMC Corporation
* All Rights Reserved
*/

package com.emc.storageos.driver.restvmax.vmax.type;

public class EditStorageGroupSLOParamType extends ParamType{
    private String sloId;

    public String getSloId() {
        return sloId;
    }

    public void setSloId(String sloId) {
        this.sloId = sloId;
    }
}
