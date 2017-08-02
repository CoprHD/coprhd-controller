/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;

public class GetPortGroupResultType extends GenericResultImplType {

    List<PortGroupType> portGroup;

    /**
     * @return the portGroup
     */
    public List<PortGroupType> getPortGroup() {
        return portGroup;
    }

    /**
     * @param portGroup the portGroup to set
     */
    public void setPortGroup(List<PortGroupType> portGroup) {
        this.portGroup = portGroup;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "GetPortGroupResultType [portGroup=" + portGroup + ", getSuccess()=" + getSuccess() + ", getHttpCode()=" + getHttpCode()
                + ", getMessage()=" + getMessage() + ", isSuccessfulStatus()=" + isSuccessfulStatus() + "]";
    }

}
