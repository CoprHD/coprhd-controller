/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.io.Serializable;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

/**
 * Container for storing the state of an export group used during host discovery.
 * Initiators, hosts, and clusters can be added and removed from the state.
 */
public class ExportGroupState implements Serializable {

    private URI id;
    private Collection<URI> initiators;
    private Collection<URI> hosts;
    private Collection<URI> clusters;
    private Map<URI, Integer> volumesMap;

    /**
     * Create an export group state with an id, list of initiators, hosts, clusters, and volumes.
     * 
     * @param id export group id
     * @param initiators list of initiators in the export
     * @param hosts list of hosts in the export
     * @param clusters list of clusters in the export
     * @param volumesMap list of volumes in the export
     */
    public ExportGroupState(URI id, List<URI> initiators, List<URI> hosts, List<URI> clusters, Map<URI, Integer> volumesMap) {
        this.id = id;
        this.initiators = initiators;
        this.hosts = hosts;
        this.clusters = clusters;
        this.volumesMap = volumesMap;
    }

    /**
     * Returns export group id
     * 
     * @return id
     */
    public URI getId() {
        return this.id;
    }

    /**
     * Returns list of initiators
     * 
     * @return initiators
     */
    public Collection<URI> getInitiators() {
        return initiators;
    }

    /**
     * Remove a list of initiators from export's initiators
     * 
     * @param initiators initiators to remove
     */
    public void removeInitiators(List<URI> initiators) {
        if (this.initiators != null) {
            this.initiators.removeAll(initiators);
        }
    }

    /**
     * Remove a collection of initiators from export's initiators
     * 
     * @param initiators initiators to remove
     */
    public void removeInitiators(Collection<URI> initiators) {
        if (this.initiators != null) {
            this.initiators.removeAll(initiators);
        }
    }

    /**
     * Add a collection of initiators to export's initiators
     * 
     * @param initiators initiators to add
     */
    public void addInitiators(Collection<URI> initiators) {
        if (this.initiators == null) {
            this.initiators = Lists.newArrayList();
        }
        this.initiators.addAll(initiators);
    }

    /**
     * Return collection of hosts
     * 
     * @return hosts
     */
    public Collection<URI> getHosts() {
        return hosts;
    }

    /**
     * Remove a host from the export's hosts
     * 
     * @param host host to remove
     */
    public void removeHosts(URI host) {
        if (this.hosts != null) {
            this.hosts.remove(host);
        }
    }

    /**
     * Add a host to the export's hosts
     * 
     * @param host host to add
     */
    public void addHosts(URI host) {
        if (this.hosts == null) {
            this.hosts = Lists.newArrayList();
        }
        this.hosts.add(host);
    }

    /**
     * Returns collection of clusters
     * 
     * @return clusters
     */
    public Collection<URI> getClusters() {
        return clusters;
    }

    /**
     * Remove a cluster from the export's clusters
     * 
     * @param cluster
     */
    public void removeCluster(URI cluster) {
        if (this.clusters != null) {
            this.clusters.remove(cluster);
        }
    }

    /**
     * Returns the volumes in the export
     * 
     * @return volumes
     */
    public Map<URI, Integer> getVolumesMap() {
        return volumesMap;
    }

    public String toString() {
        return "ExportGroupState: [ExportId: " + this.id + ", Initiators: " + this.initiators + ", Hosts: " + this.hosts + ", Clusters: "
                + this.clusters + ", Volumes: " + this.volumesMap + "]";
    }
}
