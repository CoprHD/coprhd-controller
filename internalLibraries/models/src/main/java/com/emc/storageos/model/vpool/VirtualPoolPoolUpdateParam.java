/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameter to update virtual pool
 */
@XmlRootElement(name = "vpool_pool_update")
public class VirtualPoolPoolUpdateParam {

    private StoragePoolAssignmentChanges storagePoolAssignmentChanges;

    public VirtualPoolPoolUpdateParam() {
    }

    public VirtualPoolPoolUpdateParam(
            StoragePoolAssignmentChanges storagePoolAssignmentChanges) {
        this.storagePoolAssignmentChanges = storagePoolAssignmentChanges;
    }

    /**
     * Changes to the assigned storage pools for a virtual pool.
     * 
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
