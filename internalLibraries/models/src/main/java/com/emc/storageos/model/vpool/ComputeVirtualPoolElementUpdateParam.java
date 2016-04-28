/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameter to update compute virtual pool
 */
@XmlRootElement(name = "vpool_element_update")
public class ComputeVirtualPoolElementUpdateParam {

    private ComputeVirtualPoolAssignmentChanges computeVirtualPoolAssignmentChanges;

    public ComputeVirtualPoolElementUpdateParam() {
    }

    public ComputeVirtualPoolElementUpdateParam(
            ComputeVirtualPoolAssignmentChanges computeVirtualPoolAssignmentChanges) {
        this.computeVirtualPoolAssignmentChanges = computeVirtualPoolAssignmentChanges;
    }

    /**
     * Changes to the assigned elements for a compute virtual pool.
     * 
     */
    @XmlElement(name = "assigned_element_changes")
    public ComputeVirtualPoolAssignmentChanges getComputeVirtualPoolAssignmentChanges() {
        return computeVirtualPoolAssignmentChanges;
    }

    public void setComputeVirtualPoolAssignmentChanges(
            ComputeVirtualPoolAssignmentChanges computeVirtualPoolAssignmentChanges) {
        this.computeVirtualPoolAssignmentChanges = computeVirtualPoolAssignmentChanges;
    }

}
