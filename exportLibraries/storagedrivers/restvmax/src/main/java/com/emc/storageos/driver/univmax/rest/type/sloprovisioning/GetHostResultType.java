/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;

/**
 * @author fengs5
 *
 */
public class GetHostResultType extends GenericResultImplType {

    List<HostType> host;

    /**
     * @return the host
     */
    public List<HostType> getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(List<HostType> host) {
        this.host = host;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "GetHostResultType [host=" + host + ", getHttpCode()=" + getHttpCode() + ", getMessage()=" + getMessage()
                + ", isSuccessfulStatus()=" + isSuccessfulStatus() + "]";
    }

}
