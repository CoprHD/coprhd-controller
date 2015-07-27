/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_network_systems")
public class NetworkSystemBulkRep extends BulkRestRep {
    private List<NetworkSystemRestRep> networkSystems;

    /**
     * List of network system objects that exist in ViPR.
     * @valid none
     */
    @XmlElement(name = "network_system")
    public List<NetworkSystemRestRep> getNetworkSystems() {
        if (networkSystems == null) {
            networkSystems = new ArrayList<NetworkSystemRestRep>();
        }
        return networkSystems;
    }

    public void setNetworkSystems(List<NetworkSystemRestRep> networkSystems) {
        this.networkSystems = networkSystems;
    }

    public NetworkSystemBulkRep() {
    }

    public NetworkSystemBulkRep(List<NetworkSystemRestRep> networkSystems) {
        this.networkSystems = networkSystems;
    }
}
