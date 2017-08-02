/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

/**
 * @author fengs5
 *
 */
public class UseExistingHostGroupParamType extends ParamType {

    private String hostGroupId;

    /**
     * @return the hostGroupId
     */
    public String getHostGroupId() {
        return hostGroupId;
    }

    /**
     * @param hostGroupId the hostGroupId to set
     */
    public void setHostGroupId(String hostGroupId) {
        this.hostGroupId = hostGroupId;
    }

    /**
     * @param hostGroupId
     */
    public UseExistingHostGroupParamType(String hostGroupId) {
        super();
        this.hostGroupId = hostGroupId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "UseExistingHostGroupParamType [hostGroupId=" + hostGroupId + "]";
    }

}
