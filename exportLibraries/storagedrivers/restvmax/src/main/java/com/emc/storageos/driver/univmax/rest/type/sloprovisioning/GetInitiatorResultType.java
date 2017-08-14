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
public class GetInitiatorResultType extends GenericResultImplType {

    private List<InitiatorType> initiator;

    /**
     * @return the initiator
     */
    public List<InitiatorType> getInitiator() {
        return initiator;
    }

    /**
     * @param initiator the initiator to set
     */
    public void setInitiator(List<InitiatorType> initiator) {
        this.initiator = initiator;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "GetInitiatorResultType [initiator=" + initiator + ", getSuccess()=" + getSuccess() + ", getHttpCode()=" + getHttpCode()
                + ", getMessage()=" + getMessage() + ", isSuccessfulStatus()=" + isSuccessfulStatus() + "]";
    }

}
