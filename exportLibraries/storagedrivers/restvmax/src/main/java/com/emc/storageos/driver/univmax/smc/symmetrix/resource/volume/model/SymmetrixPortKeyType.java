/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.symmetrix.resource.volume.model;

import com.emc.storageos.driver.univmax.smc.basetype.DefaultResponse;

public class SymmetrixPortKeyType extends DefaultResponse {
    private String directorId;
    private String portId;

    public String getDirectorId() {
        return directorId;
    }

    public void setDirectorId(String directorId) {
        this.directorId = directorId;
    }

    public String getPortId() {
        return portId;
    }

    public void setPortId(String portId) {
        this.portId = portId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "SymmetrixPortKeyType [directorId=" + directorId + ", portId=" + portId + "]";
    }

}
