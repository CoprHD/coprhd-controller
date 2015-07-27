/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.protection;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.pools.VirtualArrayAssignments;

/**
 * Class captures lists of URIs for varrays to be assigned/unassigned
 * to/from the storage pool.  Special list for virtual array assignments for
 * RP storage pools
 */
@XmlRootElement(name = "varray_assignment_change")
public class RPClusterVirtualArrayAssignmentChanges extends VirtualArrayAssignmentChanges {

	private String clusterId;
	
    public RPClusterVirtualArrayAssignmentChanges() {}
    
    public RPClusterVirtualArrayAssignmentChanges(String clusterId,
    		VirtualArrayAssignments add,
            VirtualArrayAssignments remove) {
        super(add, remove);
    }

    @XmlElement(name = "cluster_id")
    public String getClusterId() {
    	return clusterId;
    }
    
    public void setClusterId(String clusterId) {
    	this.clusterId = clusterId;
    }
}
