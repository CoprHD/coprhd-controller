/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Volume creation parameters
 */
@XmlRootElement(name = "host_to_cluster_add")
public class AddHostToClusterParams {

    private URI host;
    private URI cluster;

    public AddHostToClusterParams() {
    }

    public AddHostToClusterParams(URI host, URI cluster) {
        this.host = host;
        this.cluster = cluster;
    }

    /**
     * Host to add to cluster.
     */
    @XmlElement(required = true)
    public URI getHost() {
        return host;
    }

    public void setHost(URI host) {
        this.host = host;
    }

    /**
     * Cluster to be added to.
     */
    @XmlElement(required = true)
    public URI getCluster() {
        return cluster;
    }

    public void setCluster(URI cluster) {
        this.cluster = cluster;
    }

}
