/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.tier;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_storage_tiers")
public class StorageTierBulkRep extends BulkRestRep {
    private List<StorageTierRestRep> storageTiers;

    /**
     * List of storage tiers where a storage tier is a 
     * collection of multiple pools.
     * 
     * @valid none
     */ 
    @XmlElement(name = "storage_tier")
    public List<StorageTierRestRep> getStorageTiers() {
        if (storageTiers == null) {
            storageTiers = new ArrayList<StorageTierRestRep>();
        }
        return storageTiers;
    }

    public void setStorageTiers(List<StorageTierRestRep> storageTiers) {
        this.storageTiers = storageTiers;
    }

    public StorageTierBulkRep() {
    }

    public StorageTierBulkRep(List<StorageTierRestRep> storageTiers) {
        this.storageTiers = storageTiers;
    }
}
