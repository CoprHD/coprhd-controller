/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.pools;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class captures lists of URIs for varrays to be assigned/unassigned
 * to/from the storage pool.
 */
public class VirtualArrayAssignmentChanges {

    private VirtualArrayAssignments add;
    private VirtualArrayAssignments remove;

    public VirtualArrayAssignmentChanges() {
    }

    public VirtualArrayAssignmentChanges(VirtualArrayAssignments add,
            VirtualArrayAssignments remove) {
        this.add = add;
        this.remove = remove;
    }

    // varrays to be assigned.
    @XmlElement(name = "add")
    public VirtualArrayAssignments getAdd() {
        return add;
    }

    public void setAdd(VirtualArrayAssignments add) {
        this.add = add;
    }

    // varrays to be unassigned.
    @XmlElement(name = "remove")
    public VirtualArrayAssignments getRemove() {
        return remove;
    }

    public void setRemove(VirtualArrayAssignments remove) {
        this.remove = remove;
    }

    public boolean hasRemoved() {
        return (remove != null && !remove.getVarrays().isEmpty());
    }

    public boolean hasAdded() {
        return (add != null && !add.getVarrays().isEmpty());
    }

}
