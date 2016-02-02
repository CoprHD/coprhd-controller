/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.ports;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Class represents a return type that returns the id and self link for a
 * list of storage ports.
 */
@XmlRootElement(name = "storage_ports")
public class StoragePortList {
    private List<NamedRelatedResourceRep> ports;

    public StoragePortList() {
    }

    public StoragePortList(List<NamedRelatedResourceRep> ports) {
        this.ports = ports;
    }

    /**
     * List of Storage ports. A Storage port represents a
     * port of a storage device.
     * 
     */
    @XmlElement(name = "storage_port")
    public List<NamedRelatedResourceRep> getPorts() {
        if (ports == null) {
            ports = new ArrayList<NamedRelatedResourceRep>();
        }
        return ports;
    }

    public void setPorts(List<NamedRelatedResourceRep> ports) {
        this.ports = ports;
    }
}
