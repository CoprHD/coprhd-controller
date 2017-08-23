/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class SetHostFlagsParamType extends ParamType {

    private HostFlagsType hostFlags;

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
     * @param hostFlags
     */
    public SetHostFlagsParamType(HostFlagsType hostFlags) {
        super();
        this.hostFlags = hostFlags;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "SetHostFlagsParamType [hostFlags=" + hostFlags + "]";
    }

}
