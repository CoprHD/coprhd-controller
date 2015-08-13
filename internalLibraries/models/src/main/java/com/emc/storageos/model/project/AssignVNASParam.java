/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.project;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "assign_vnas")
public class AssignVNASParam {

    /**
     * Returned list of Virtual NAS Servers.
     * 
     * @valid none
     */
    private List<URI> vnasIds;

    public AssignVNASParam() {
    }

    public AssignVNASParam(List<URI> vnasIds) {
        this.vnasIds = vnasIds;
    }

    /**
     * List of Virtual NAS. A Virtual NAS represents a
     * virtual NAS server of a storage device.
     * 
     * @valid none
     */
    public List<URI> getVnasIds() {
        return vnasIds;
    }

    /**
     * @param vnasIds the vnasIds to set
     */
    public void setVnasIds(List<URI> vnasIds) {
        this.vnasIds = vnasIds;
    }

}
