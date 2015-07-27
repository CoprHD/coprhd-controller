/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013-2014 EMC Corporation All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation or is licensed to EMC
 * Corporation from third parties. Use of this software and the intellectual property contained
 * therein is expressly limited to the terms and conditions of the License Agreement under which it
 * is provided by or on behalf of EMC.
 */

package com.emc.storageos.model.host.cluster;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement(name = "cluster_capacity")
public class ClusterCapacityRep {
    private long provisionedCapacityInGB;
    private long freeCapacityInGB;
    /**
     * get total provisioned capacity.
     * 
     * @valid None
     * @return return total provisioned capacity.
     */
    @XmlElement(name = "totalProvisioned_gb")
    public long getProvisionedCapacityInGB() {
        return provisionedCapacityInGB;
    }
    public void setProvisionedCapacityInGB(long provisionedCapacityInGB) {
        this.provisionedCapacityInGB = provisionedCapacityInGB;
    }
    /**
     * gets the total free capacity.
     * 
     * @valid None
     * @return returns the total free capacity.
     */
    @XmlElement(name = "totalFree_gb")
    public long getFreeCapacityInGB() {
        return freeCapacityInGB;
    }

    public void setFreeCapacityInGB(long freeCapacityInGB) {
        this.freeCapacityInGB = freeCapacityInGB;
    }
}
