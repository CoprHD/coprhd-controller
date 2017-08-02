/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class UseExistingPortGroupParamType extends ParamType {

    private String portGroupId;

    /**
     * @return the portGroupId
     */
    public String getPortGroupId() {
        return portGroupId;
    }

    /**
     * @param portGroupId the portGroupId to set
     */
    public void setPortGroupId(String portGroupId) {
        this.portGroupId = portGroupId;
    }

    /**
     * @param portGroupId
     */
    public UseExistingPortGroupParamType(String portGroupId) {
        super();
        this.portGroupId = portGroupId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "UseExistingPortGroupParamType [portGroupId=" + portGroupId + "]";
    }

}
