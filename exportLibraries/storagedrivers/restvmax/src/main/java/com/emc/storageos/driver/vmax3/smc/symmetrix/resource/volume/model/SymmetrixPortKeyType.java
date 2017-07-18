/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.model;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractResponse;

public class SymmetrixPortKeyType extends AbstractResponse {
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
}
