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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Parameter to update virtual pool
 */
@XmlRootElement(name = "vpool_pool_update")
public class VirtualPoolPoolUpdateParam {
    
    private StoragePoolAssignmentChanges storagePoolAssignmentChanges;

    public VirtualPoolPoolUpdateParam() {}
    
    public VirtualPoolPoolUpdateParam(
            StoragePoolAssignmentChanges storagePoolAssignmentChanges) {
        this.storagePoolAssignmentChanges = storagePoolAssignmentChanges;
    }

    /**
     * Changes to the assigned storage pools for a virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "assigned_pool_changes")
    public StoragePoolAssignmentChanges getStoragePoolAssignmentChanges() {
        return storagePoolAssignmentChanges;
    }

    public void setStoragePoolAssignmentChanges(
            StoragePoolAssignmentChanges storagePoolAssignmentChanges) {
        this.storagePoolAssignmentChanges = storagePoolAssignmentChanges;
    }
    
}
