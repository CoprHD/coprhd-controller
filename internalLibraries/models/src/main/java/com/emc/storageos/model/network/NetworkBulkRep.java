/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.varray.NetworkRestRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_networks")
public class NetworkBulkRep extends BulkRestRep {
    private List<NetworkRestRep> networks;

    /**
     * List of network objects that exist in ViPR. Contains FC
     * and IP networks.
     * 
     */
    @XmlElement(name = "network")
    public List<NetworkRestRep> getNetworks() {
        if (networks == null) {
            networks = new ArrayList<NetworkRestRep>();
        }
        return networks;
    }

    public void setNetworks(List<NetworkRestRep> networks) {
        this.networks = networks;
    }

    public NetworkBulkRep() {
    }

    public NetworkBulkRep(List<NetworkRestRep> networks) {
        this.networks = networks;
    }
}
