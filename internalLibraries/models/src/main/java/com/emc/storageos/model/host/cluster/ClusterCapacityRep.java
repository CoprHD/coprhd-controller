/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
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
