/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;

public class ListInitiatorResultType extends GenericResultImplType {

    private int num_of_initiators;
    private List<String> initiatorId;

    /**
     * @return the num_of_initiators
     */
    public int getNum_of_initiators() {
        return num_of_initiators;
    }

    /**
     * @param num_of_initiators the num_of_initiators to set
     */
    public void setNum_of_initiators(int num_of_initiators) {
        this.num_of_initiators = num_of_initiators;
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

}
