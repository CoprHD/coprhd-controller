/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.cluster;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request PUT parameter for cluster update operations.
 */
@XmlRootElement(name = "cluster_update")
public class ClusterUpdateParam extends ClusterParam {

    private String name;
    
    public ClusterUpdateParam() {}
    
    public ClusterUpdateParam(String name) {
        super();
        this.name = name;
    }
    
    public ClusterUpdateParam(URI VcenterDataCenter, URI project,
            String name) {
        super(VcenterDataCenter, project);
        this.name = name;
    }

    /** 
     * The name label for this cluster.  It must be unique to the tenant. 
     * 
     * @valid none
     */
    @XmlElement()
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String findName() {
        return name;
    }
}
