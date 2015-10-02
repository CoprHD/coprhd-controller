/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import org.codehaus.jackson.annotate.JsonProperty;
import javax.xml.bind.annotation.XmlElement;
import java.util.HashSet;
import java.util.Set;

/**
 * Class captures a list of URIs for the storage pools assigned during
 * virtual pool update.
 */
public class StoragePoolAssignments {

    private Set<String> storagePools;

    /**
     * Default Constructor.
     */
    public StoragePoolAssignments() {
    }

    public StoragePoolAssignments(Set<String> storagePools) {
        this.storagePools = storagePools;
    }

    /**
     * The list of storage pools to be added to or removed from the virtual pool
     * 
     */
    @XmlElement(name = "storage_pool")
    @JsonProperty("storage_pool")
    public Set<String> getStoragePools() {
        if (storagePools == null) {
            storagePools = new HashSet<String>();
        }
        return storagePools;
    }

    public void setStoragePools(Set<String> storagePools) {
        this.storagePools = storagePools;
    }

}
