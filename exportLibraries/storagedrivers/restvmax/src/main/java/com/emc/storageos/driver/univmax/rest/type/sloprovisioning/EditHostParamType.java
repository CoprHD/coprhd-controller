/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class EditHostParamType extends ParamType {

    private EditHostActionParamType editHostActionParam;

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "EditHostParamType [editHostActionParam=" + editHostActionParam + "]";
    }

    /**
     * @return the editHostActionParam
     */
    public EditHostActionParamType getEditHostActionParam() {
        return editHostActionParam;
    }

    /**
     * @param editHostActionParam the editHostActionParam to set
     */
    public void setEditHostActionParam(EditHostActionParamType editHostActionParam) {
        this.editHostActionParam = editHostActionParam;
    }

}
