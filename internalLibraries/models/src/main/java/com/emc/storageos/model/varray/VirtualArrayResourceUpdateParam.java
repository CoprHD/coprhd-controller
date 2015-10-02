/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;

public class VirtualArrayResourceUpdateParam {

    private VirtualArrayAssignmentChanges varrayAssignmentChanges;

    public VirtualArrayResourceUpdateParam() {
    }

    public VirtualArrayResourceUpdateParam(VirtualArrayAssignmentChanges varrayChanges) {
        varrayAssignmentChanges = varrayChanges;
    }

    /**
     * The list of virtual arrays to be added to or removed from the resource.
     * 
     */
    @XmlElement(name = "varray_assignment_changes")
    public VirtualArrayAssignmentChanges getVarrayChanges() {
        return varrayAssignmentChanges;
    }

    public void setVarrayChanges(VirtualArrayAssignmentChanges varrayChanges) {
        varrayAssignmentChanges = varrayChanges;
    }
}
