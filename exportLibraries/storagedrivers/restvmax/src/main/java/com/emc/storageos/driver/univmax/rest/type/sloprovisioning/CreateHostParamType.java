/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class CreateHostParamType extends ParamType {
    String hostId;
    List<String> initiatorId;
    private HostFlagsType hostFlags;

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

    /**
     * @return the initiatorId
     */
    public List<String> getInitiatorId() {
        return initiatorId;
    }

    /**
     * @param initiatorId the initiatorId to set
     */
    public void setInitiatorId(List<String> initiatorId) {
        this.initiatorId = initiatorId;
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
     * @param hostId
     */
    public CreateHostParamType(String hostId) {
        super();
        this.hostId = hostId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "CreateHostParamType [hostId=" + hostId + ", initiatorId=" + initiatorId + ", hostFlags=" + hostFlags + "]";
    }

}
