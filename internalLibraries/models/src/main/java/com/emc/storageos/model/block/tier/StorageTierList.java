/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.tier;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "storage_tiers")
public class StorageTierList {
    private List<NamedRelatedResourceRep> storageTiers;

    public StorageTierList() {
    }

    public StorageTierList(List<NamedRelatedResourceRep> storageTiers) {
        this.storageTiers = storageTiers;
    }

    /**
     * List of storage tiers where a storage tier is a
     * collection of multiple pools.
     * 
     * @valid none
     */
    @XmlElement(name = "storage_tier")
    public List<NamedRelatedResourceRep> getStorageTiers() {
        if (storageTiers == null) {
            storageTiers = new ArrayList<NamedRelatedResourceRep>();
        }
        return storageTiers;
    }

    public void setStorageTiers(List<NamedRelatedResourceRep> storageTiers) {
        this.storageTiers = storageTiers;
    }
}
