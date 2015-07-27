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

/**
 * Class captures lists of URIs for compute elements to be assigned/unassigned
 * to/from the list.
 */
public class ComputeVirtualPoolAssignmentChanges {
    
    private ComputeVirtualPoolAssignments add;
    private ComputeVirtualPoolAssignments remove;

    public ComputeVirtualPoolAssignmentChanges() {}
    
    public ComputeVirtualPoolAssignmentChanges(ComputeVirtualPoolAssignments add,
            ComputeVirtualPoolAssignments remove) {
        this.add = add;
        this.remove = remove;
    }

    /**
     * The list of compute elements to be added to the compute virtual pool
     * 
     * @valid none
     */
    @XmlElement(name = "add")
    public ComputeVirtualPoolAssignments getAdd() {
        return add;
    }

    public void setAdd(ComputeVirtualPoolAssignments add) {
        this.add = add;
    }

    /**
     * The list of compute elements to be removed from the compute virtual pool
     * 
     * @valid none
     */
    @XmlElement(name = "remove")
    public ComputeVirtualPoolAssignments getRemove() {
        return remove;
    }

    public void setRemove(ComputeVirtualPoolAssignments remove) {
        this.remove = remove;
    }
    
}
