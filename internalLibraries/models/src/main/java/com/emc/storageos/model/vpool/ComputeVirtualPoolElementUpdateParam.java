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

/**
 * Parameter to update compute virtual pool
 */
@XmlRootElement(name = "vpool_element_update")
public class ComputeVirtualPoolElementUpdateParam {
    
    private ComputeVirtualPoolAssignmentChanges computeVirtualPoolAssignmentChanges;

    public ComputeVirtualPoolElementUpdateParam() {}
    
    public ComputeVirtualPoolElementUpdateParam(
            ComputeVirtualPoolAssignmentChanges computeVirtualPoolAssignmentChanges) {
        this.computeVirtualPoolAssignmentChanges = computeVirtualPoolAssignmentChanges;
    }

    /**
     * Changes to the assigned elements for a compute virtual pool.
     * 
     * @valid none
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
