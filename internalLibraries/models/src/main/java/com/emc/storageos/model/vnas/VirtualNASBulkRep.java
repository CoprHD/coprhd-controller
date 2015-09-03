/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vnas;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

/**
 * List of Virtual NAS servers and returned as a bulk response to a REST request.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "bulk_vnas_servers")
public class VirtualNASBulkRep extends BulkRestRep {
    private List<VirtualNASRestRep> vnasServers;

    public VirtualNASBulkRep() {
    }

    public VirtualNASBulkRep(List<VirtualNASRestRep> vnasServers) {
        super();
        this.vnasServers = vnasServers;
    }

    /**
     * List of Virtual NAS Servers. A VNAS Server represents a
     * virtual NAS server of a storage device.
     * 
     * @valid none
     */
    @XmlElement(name = "vnas_server")
    public List<VirtualNASRestRep> getVnasServers() {
        if (vnasServers == null) {
            vnasServers = new ArrayList<VirtualNASRestRep>();
        }
        return vnasServers;
    }

    /**
     * @param vnasServers the vnasServers to set
     */
    public void setVnasServers(List<VirtualNASRestRep> vnasServers) {
        this.vnasServers = vnasServers;
    }

}
