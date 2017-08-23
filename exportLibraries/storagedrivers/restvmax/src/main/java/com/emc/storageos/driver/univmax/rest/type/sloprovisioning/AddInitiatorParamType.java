/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class AddInitiatorParamType extends ParamType {

    private List<String> initiator;

    /**
     * @return the initiator
     */
    public List<String> getInitiator() {
        return initiator;
    }

    /**
     * @param initiator the initiator to set
     */
    public void setInitiator(List<String> initiator) {
        this.initiator = initiator;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "AddInitiatorParamType [initiator=" + initiator + "]";
    }

    /**
     * @param initiator
     */
    public AddInitiatorParamType(List<String> initiator) {
        super();
        this.initiator = initiator;
    }

}
