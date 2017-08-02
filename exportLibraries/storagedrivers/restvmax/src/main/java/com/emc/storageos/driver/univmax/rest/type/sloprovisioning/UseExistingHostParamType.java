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
public class UseExistingHostParamType extends ParamType {

    private String hostId;

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "UseExistingHostParamType [hostId=" + hostId + "]";
    }

    /**
     * @param hostId
     */
    public UseExistingHostParamType(String hostId) {
        super();
        this.hostId = hostId;
    }

    /**
     * @return the hostId
     */
    public String getHostId() {
        return hostId;
    }

    /**
     * @param hostId the hostId to set
     */
    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

}
