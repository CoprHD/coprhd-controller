/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.io.Serializable;
import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class StoragePorts implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -7348637714376710785L;
    private List<URI> storagePorts;

    /**
     * List of Storage Ports to be modified.
     * 
     */
    @XmlElementWrapper(name = "storage_ports", required = false)
    @XmlElement(name = "storage_port")
    public List<URI> getStoragePorts() {
        return storagePorts;
    }

    public void setStoragePorts(List<URI> storagePorts) {
        this.storagePorts = storagePorts;
    }

}
