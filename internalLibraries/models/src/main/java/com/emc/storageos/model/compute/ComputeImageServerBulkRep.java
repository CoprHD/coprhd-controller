/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_compute_imageservers")
public class ComputeImageServerBulkRep extends BulkRestRep {
    private List<ComputeImageServerRestRep> computeImageServers;

    public ComputeImageServerBulkRep() {
    }

    public ComputeImageServerBulkRep(List<ComputeImageServerRestRep> computeImageServers) {
        this.computeImageServers = computeImageServers;
    }

    /**
     * List of compute image server objects that exist in ViPR.
     *
     * @valid none
     */
    @XmlElement(name = "compute_imageserver")
    public List<ComputeImageServerRestRep> getComputeImageServers() {
        if (computeImageServers == null) {
            computeImageServers = new ArrayList<ComputeImageServerRestRep>();
        }
        return computeImageServers;
    }

    /**
     * setter for compute image servers
     * @param computeImageServers {@link List} of ComputeImageServerRestRep
     */
    public void setComputeImageServers(List<ComputeImageServerRestRep> computeImageServers) {
        this.computeImageServers = computeImageServers;
    }
}
