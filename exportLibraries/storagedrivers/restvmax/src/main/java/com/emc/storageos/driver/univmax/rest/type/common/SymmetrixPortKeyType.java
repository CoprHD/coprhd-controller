/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

public class SymmetrixPortKeyType extends ParamType {

    // min/max occurs: 1/1
    private String directorId;
    // min/max occurs: 1/1
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

    /**
     * @param directorId
     * @param portId
     */
    public SymmetrixPortKeyType(String directorId, String portId) {
        super();
        this.directorId = directorId;
        this.portId = portId;
    }

    /**
     * 
     */
    public SymmetrixPortKeyType() {
        super();
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
