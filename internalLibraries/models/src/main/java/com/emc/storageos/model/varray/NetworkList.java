/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "networks")
public class NetworkList {
    private List<NamedRelatedResourceRep> networks;

    public NetworkList() {
    }

    public NetworkList(List<NamedRelatedResourceRep> networks) {
        this.networks = networks;
    }

    /**
     * List of network objects that exist in ViPR. Each
     * network contains an id, name, and link.
     * 
     */
    @XmlElement(name = "network")
    public List<NamedRelatedResourceRep> getNetworks() {
        if (networks == null) {
            networks = new ArrayList<NamedRelatedResourceRep>();
        }
        return networks;
    }

    public void setNetworks(List<NamedRelatedResourceRep> networks) {
        this.networks = networks;
    }
}
