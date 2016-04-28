/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.systems;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "storage_connectivity_list")
public class StorageSystemConnectivityList {

    private List<StorageSystemConnectivityRestRep> connections;

    public StorageSystemConnectivityList() {
    }

    public StorageSystemConnectivityList(
            List<StorageSystemConnectivityRestRep> connections) {
        this.connections = connections;
    }

    /**
     * List of storage systems connected to protection system
     * 
     */
    @XmlElement(name = "storage_connectivity")
    public List<StorageSystemConnectivityRestRep> getConnections() {
        if (connections == null) {
            connections = new ArrayList<StorageSystemConnectivityRestRep>();
        }
        return connections;
    }

    public void setConnections(List<StorageSystemConnectivityRestRep> connections) {
        this.connections = connections;
    }

}
