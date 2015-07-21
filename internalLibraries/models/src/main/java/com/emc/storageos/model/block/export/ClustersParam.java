/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * Create parameter for cluster
 */
public class ClustersParam {
    
    private List<URI> clusters;

    public ClustersParam() {}
    
    public ClustersParam(List<URI> clusters) {
        this.clusters = clusters;
    }

    @XmlElementWrapper(required = false)
    @XmlElement(name = "cluster")
    public List<URI> getClusters() {
        if (clusters == null) {
            clusters = new ArrayList<URI>();
        }
        return clusters;
    }

    public void setClusters(List<URI> clusters) {
        this.clusters = clusters;
    }
      
}