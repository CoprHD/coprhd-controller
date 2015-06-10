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
package com.emc.storageos.model.pools;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Class captures lists of URIs for varrays to be assigned/unassigned
 * to/from the storage pool.
 */
public class VirtualArrayAssignmentChanges {

    private VirtualArrayAssignments add;
    private VirtualArrayAssignments remove;

    public VirtualArrayAssignmentChanges() {}
    
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
        return remove != null && remove.getVarrays().size() > 0;
    }

    public boolean hasAdded() {
        return add != null && add.getVarrays().size() > 0;
    }
    
}
