/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_compute_vpools")
public class ComputeVirtualPoolBulkRep extends BulkRestRep {
	
    private List<ComputeVirtualPoolRestRep> virtualPools;

    /**
     * List of virtual pools.
     * 
     * @valid none
     */
    @XmlElement(name = "compute_vpool")
    @JsonProperty("compute_vpool")
    public List<ComputeVirtualPoolRestRep> getVirtualPools() {
        if (virtualPools == null) {
            virtualPools = new ArrayList<ComputeVirtualPoolRestRep>();
        }
        return virtualPools;
    }

    public void setVirtualPools(List<ComputeVirtualPoolRestRep> virtualPools) {
        this.virtualPools = virtualPools;
    }

    public ComputeVirtualPoolBulkRep() {
    }

    public ComputeVirtualPoolBulkRep(List<ComputeVirtualPoolRestRep> virtualPools) {
        this.virtualPools = virtualPools;
    }

}
