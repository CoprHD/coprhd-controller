/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class captures lists of URIs for storage pools to be assigned/unassigned
 * to/from the storage pool.
 */
public class StoragePoolAssignmentChanges {

    private StoragePoolAssignments add;
    private StoragePoolAssignments remove;

    public StoragePoolAssignmentChanges() {
    }

    public StoragePoolAssignmentChanges(StoragePoolAssignments add,
            StoragePoolAssignments remove) {
        this.add = add;
        this.remove = remove;
    }

    /**
     * The list of storage pools to be added to the virtual pool
     * 
     * @valid none
     */
    @XmlElement(name = "add")
    public StoragePoolAssignments getAdd() {
        return add;
    }

    public void setAdd(StoragePoolAssignments add) {
        this.add = add;
    }

    /**
     * The list of storage pools to be removed from the virtual pool
     * 
     * @valid none
     */
    @XmlElement(name = "remove")
    public StoragePoolAssignments getRemove() {
        return remove;
    }

    public void setRemove(StoragePoolAssignments remove) {
        this.remove = remove;
    }

}
