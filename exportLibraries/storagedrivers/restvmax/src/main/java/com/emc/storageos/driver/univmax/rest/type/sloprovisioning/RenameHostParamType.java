/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class RenameHostParamType extends ParamType {

    private String new_host_name;

    /**
     * @param new_host_name
     */
    public RenameHostParamType(String new_host_name) {
        super();
        this.new_host_name = new_host_name;
    }

    /**
     * @return the new_host_name
     */
    public String getNew_host_name() {
        return new_host_name;
    }

    /**
     * @param new_host_name the new_host_name to set
     */
    public void setNew_host_name(String new_host_name) {
        this.new_host_name = new_host_name;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "RenameHostParamType [new_host_name=" + new_host_name + "]";
    }

}
