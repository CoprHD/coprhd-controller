/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.pools;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "storage_pools")
public class StoragePoolList {
    private List<NamedRelatedResourceRep> pools;

    public StoragePoolList() {
    }

    public StoragePoolList(List<NamedRelatedResourceRep> pools) {
        this.pools = pools;
    }

    /**
     * List of storage pool
     * 
     * @valid none
     */
    @XmlElement(name = "storage_pool")
    public List<NamedRelatedResourceRep> getPools() {
        if (pools == null) {
            pools = new ArrayList<NamedRelatedResourceRep>();
        }
        return pools;
    }

    public void setPools(List<NamedRelatedResourceRep> pools) {
        this.pools = pools;
    }
}
