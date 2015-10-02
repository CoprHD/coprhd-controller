/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.cluster;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_clusters")
public class ClusterBulkRep extends BulkRestRep {
    private List<ClusterRestRep> clusters;

    /**
     * Represents a host cluster within ViPR
     * 
     */
    @XmlElement(name = "cluster")
    public List<ClusterRestRep> getClusters() {
        if (clusters == null) {
            clusters = new ArrayList<ClusterRestRep>();
        }
        return clusters;
    }

    public void setClusters(List<ClusterRestRep> cluster) {
        this.clusters = cluster;
    }

    public ClusterBulkRep() {
    }

    public ClusterBulkRep(List<ClusterRestRep> clusters) {
        this.clusters = clusters;
    }
}
