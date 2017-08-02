/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;
import com.emc.storageos.driver.univmax.rest.type.common.SymmetrixPortKeyType;

public class CreatePortGroupParamType extends ParamType {

    String portGroupId;
    List<SymmetrixPortKeyType> symmetrixPortKey;

    /**
     * @param portGroupId
     */
    public CreatePortGroupParamType(String portGroupId) {
        super();
        this.portGroupId = portGroupId;
    }

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
     * @return the symmetrixPortKey
     */
    public List<SymmetrixPortKeyType> getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    /**
     * @param symmetrixPortKey the symmetrixPortKey to set
     */
    public void setSymmetrixPortKey(List<SymmetrixPortKeyType> symmetrixPortKey) {
        this.symmetrixPortKey = symmetrixPortKey;
    }

    /**
     * Add port.
     * 
     * @param port
     */
    public void addSymmetrixPortKey(SymmetrixPortKeyType port) {
        if (this.symmetrixPortKey == null) {
            this.symmetrixPortKey = new ArrayList<>();
        }

        this.symmetrixPortKey.add(port);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "CreatePortGroupParamType [portGroupId=" + portGroupId + ", symmetrixPortKey=" + symmetrixPortKey + "]";
    }

}
