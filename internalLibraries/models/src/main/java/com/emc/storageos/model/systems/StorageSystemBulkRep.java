/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.systems;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_storage_systems")
public class StorageSystemBulkRep extends BulkRestRep {
    private List<StorageSystemRestRep> storageSystems;

    /**
     * List of storage systems
     * 
     */
    @XmlElement(name = "storage_system")
    public List<StorageSystemRestRep> getStorageSystems() {
        if (storageSystems == null) {
            storageSystems = new ArrayList<StorageSystemRestRep>();
        }
        return storageSystems;
    }

    public void setStorageSystems(List<StorageSystemRestRep> storageSystems) {
        this.storageSystems = storageSystems;
    }

    public StorageSystemBulkRep() {
    }

    public StorageSystemBulkRep(List<StorageSystemRestRep> storageSystems) {
        this.storageSystems = storageSystems;
    }
}
