/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "network_systems")
public class NetworkSystemList {
    private List<NamedRelatedResourceRep> systems;

    public NetworkSystemList() {
    }

    public NetworkSystemList(List<NamedRelatedResourceRep> systems) {
        this.systems = systems;
    }

    /**
     * List of network system objects that exist in ViPR. Each
     * network system contains an id, name, and link.
     * 
     * @valid none
     */
    @XmlElement(name = "network_system")
    public List<NamedRelatedResourceRep> getSystems() {
        if (systems == null) {
            systems = new ArrayList<NamedRelatedResourceRep>();
        }
        return systems;
    }

    public void setSystems(List<NamedRelatedResourceRep> systems) {
        this.systems = systems;
    }
}
