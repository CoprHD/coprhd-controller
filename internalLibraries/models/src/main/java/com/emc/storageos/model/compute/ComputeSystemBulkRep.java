/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_compute_systems")
public class ComputeSystemBulkRep extends BulkRestRep {
    private List<ComputeSystemRestRep> computeSystems;

    public ComputeSystemBulkRep() {
    }

    public ComputeSystemBulkRep(List<ComputeSystemRestRep> computeSystems) {
        this.computeSystems = computeSystems;
    }

    /**
     * List of compute system objects that exist in ViPR.
     * 
     * @valid none
     */
    @XmlElement(name = "compute_system")
    public List<ComputeSystemRestRep> getComputeSystems() {
        if (computeSystems == null) {
            computeSystems = new ArrayList<ComputeSystemRestRep>();
        }
        return computeSystems;
    }

    public void setComputeSystems(List<ComputeSystemRestRep> computeSystems) {
        this.computeSystems = computeSystems;
    }
}
