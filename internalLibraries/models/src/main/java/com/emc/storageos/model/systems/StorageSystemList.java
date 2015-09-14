/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.systems;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "storage_systems")
public class StorageSystemList {
    private List<NamedRelatedResourceRep> storageSystems;

    public StorageSystemList() {
    }

    public StorageSystemList(List<NamedRelatedResourceRep> storageSystems) {
        this.storageSystems = storageSystems;
    }

    /**
     * List of storage system URLs with name
     * 
     * @valid none
     */
    @XmlElement(name = "storage_system")
    public List<NamedRelatedResourceRep> getStorageSystems() {
        if (storageSystems == null) {
            storageSystems = new ArrayList<NamedRelatedResourceRep>();
        }
        return storageSystems;
    }

    public void setStorageSystems(List<NamedRelatedResourceRep> storageSystems) {
        this.storageSystems = storageSystems;
    }
}
