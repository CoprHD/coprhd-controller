/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "varray_connectivity_list")
public class VirtualArrayConnectivityList {

    private List<VirtualArrayConnectivityRestRep> connections;

    public VirtualArrayConnectivityList() {
    }

    public VirtualArrayConnectivityList(
            List<VirtualArrayConnectivityRestRep> connections) {
        this.connections = connections;
    }

    /**
     * A list of connected virtual arrays.
     * 
     * @valid none
     */
    @XmlElement(name = "varray_connectivity")
    public List<VirtualArrayConnectivityRestRep> getConnections() {
        if (connections == null) {
            connections = new ArrayList<VirtualArrayConnectivityRestRep>();
        }
        return connections;
    }

    public void setConnections(List<VirtualArrayConnectivityRestRep> connections) {
        this.connections = connections;
    }

}
