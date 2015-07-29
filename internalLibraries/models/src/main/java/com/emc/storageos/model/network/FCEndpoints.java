/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains a list of the endpoints discovered by the NetworkSystem. An endpoint
 * represents either a connected host port or storage port. Some ISL link ports may
 * also be included by the NetworkSystem.
 * 
 */
@XmlRootElement(name = "fc_endpoints")
public class FCEndpoints {
    private List<FCEndpointRestRep> connections;

    public FCEndpoints() {
    }

    public FCEndpoints(List<FCEndpointRestRep> connections) {
        this.connections = connections;
    }

    /**
     * List of FC (Fibre Channel) endpoints discovered by a network system.
     * 
     * @valid none
     */
    @XmlElement(name = "fc_endpoint")
    public List<FCEndpointRestRep> getConnections() {
        if (connections == null) {
            connections = new ArrayList<FCEndpointRestRep>();
        }
        return connections;
    }

    public void setConnections(List<FCEndpointRestRep> connections) {
        this.connections = connections;
    }
}
