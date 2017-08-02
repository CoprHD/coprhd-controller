/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class CreateHostGroupParamType extends ParamType {

    private String hostGroupId;
    List<String> hostId;
    private HostFlagsType hostFlags;

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
     * @return the hostId
     */
    public List<String> getHostId() {
        return hostId;
    }

    /**
     * @param hostId the hostId to set
     */
    public void setHostId(List<String> hostId) {
        this.hostId = hostId;
    }

    /**
     * @return the hostFlags
     */
    public HostFlagsType getHostFlags() {
        return hostFlags;
    }

    /**
     * @param hostFlags the hostFlags to set
     */
    public void setHostFlags(HostFlagsType hostFlags) {
        this.hostFlags = hostFlags;
    }

    /**
     * @param hostGroupId
     */
    public CreateHostGroupParamType(String hostGroupId) {
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
        return "CreateHostGroupParamType [hostGroupId=" + hostGroupId + ", hostId=" + hostId + ", hostFlags=" + hostFlags + "]";
    }

}
