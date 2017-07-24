/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.portgroup;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Create storage port group
 *
 */
@XmlRootElement(name = "storage_port_group_create")
public class StoragePortGroupCreateParam {
    private String name;
    private List<URI> storagePorts;
    private Boolean registered;

    public StoragePortGroupCreateParam() {
        
    }
    
    public StoragePortGroupCreateParam(String name, List<URI> storagePorts) {
        this.name = name;
        this.storagePorts = storagePorts;
        this.registered = true;
    }
    
    @XmlElement(required = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Storage ports members in the port group
     */
    @XmlElementWrapper(name = "storage_ports", required = true)
    @XmlElement(name = "storage_port", required = true)
    public List<URI> getStoragePorts() {
        return storagePorts;
    }

    public void setStoragePorts(List<URI> storagePorts) {
        this.storagePorts = storagePorts;
    }

    /**
     * If the port group is registered when it is created. by default, it is true
     * 
     */
    public Boolean getRegistered() {
        if (registered == null) {
            registered = true;
        }
        return registered;
    }

    public void setRegistered(Boolean registered) {
        this.registered = registered;
    }

}
