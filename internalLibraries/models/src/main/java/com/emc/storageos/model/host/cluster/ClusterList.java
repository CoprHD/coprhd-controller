/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.cluster;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response for getting a list of tenant clusters
 */
@XmlRootElement(name = "clusters")
public class ClusterList {
    private List<NamedRelatedResourceRep> clusters;

    public ClusterList() {
    }

    public ClusterList(List<NamedRelatedResourceRep> clusters) {
        this.clusters = clusters;
    }

    /**
     * Represents a host cluster within ViPR
     * 
     */
    @XmlElement(name = "cluster")
    public List<NamedRelatedResourceRep> getClusters() {
        if (clusters == null) {
            clusters = new ArrayList<NamedRelatedResourceRep>();
        }
        return clusters;
    }

    public void setClusters(List<NamedRelatedResourceRep> clusters) {
        this.clusters = clusters;
    }
}
