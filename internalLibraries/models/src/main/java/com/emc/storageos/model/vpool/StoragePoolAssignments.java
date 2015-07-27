/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
    public StoragePoolAssignments() {}

    public StoragePoolAssignments(Set<String> storagePools) {
        this.storagePools = storagePools;
    }

     /**
     * The list of storage pools to be added to or removed from the virtual pool
     * 
     * @valid none
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
