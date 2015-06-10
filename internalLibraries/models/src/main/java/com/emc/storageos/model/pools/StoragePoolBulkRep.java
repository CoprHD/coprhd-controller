/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.pools;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_storage_pools")
public class StoragePoolBulkRep extends BulkRestRep {
    private List<StoragePoolRestRep> storagePools;
    /**
     * List of storage pools
     * 
     * @valid none
     */
    @XmlElement(name = "storage_pool")
    public List<StoragePoolRestRep> getStoragePools() {
        if (storagePools == null) {
            storagePools = new ArrayList<StoragePoolRestRep>();
        }
        return storagePools;
    }

    public void setStoragePools(List<StoragePoolRestRep> storagePools) {
        this.storagePools = storagePools;
    }


    public StoragePoolBulkRep() {
    }

    public StoragePoolBulkRep(List<StoragePoolRestRep> storagePools) {
        this.storagePools = storagePools;
    }
}
