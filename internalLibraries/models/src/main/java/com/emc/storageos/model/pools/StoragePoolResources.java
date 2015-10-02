/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.pools;

import com.emc.storageos.model.TypedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response class captures this list of resources in a storage pool.
 */
@XmlRootElement(name = "storage_resources")
public class StoragePoolResources {

    // A list containing the resources in a storage pool.
    private List<TypedRelatedResourceRep> resources;

    public StoragePoolResources() {
    }

    public StoragePoolResources(List<TypedRelatedResourceRep> resources) {
        this.resources = resources;
    }

    /**
     * List of volumes and file shares created from this storage pool
     * 
     */
    @XmlElement(name = "storage_resource")
    public List<TypedRelatedResourceRep> getResources() {
        if (resources == null) {
            resources = new ArrayList<TypedRelatedResourceRep>();
        }
        return resources;
    }

    public void setResources(List<TypedRelatedResourceRep> resources) {
        this.resources = resources;
    }
}
