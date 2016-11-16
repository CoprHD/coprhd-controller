/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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

    private final URI id;
    private final Collection<URI> removedInitiators;
    private final Collection<URI> removedHosts;
    private final Collection<URI> removedClusters;
    private final Collection<URI> addedInitiators;
    private final Collection<URI> addedHosts;
    private final Collection<URI> addedClusters;
    private Map<URI, Integer> volumesMap;
    private List<URI> initiators;
    private List<URI> hosts;
    private List<URI> clusters;

    /**
     * Create an export group state with an id.
     * 
     * @param id export group id
     */
    public ExportGroupState(URI id) {
        this.id = id;
        this.addedInitiators = Lists.newArrayList();
        this.removedInitiators = Lists.newArrayList();

        this.addedHosts = Lists.newArrayList();
        this.removedHosts = Lists.newArrayList();

        this.addedClusters = Lists.newArrayList();
        this.removedClusters = Lists.newArrayList();
    }

    /**
     * Gets the state of the export by removing initiators, hosts, and clusters from the state.
     * 
     * @param initiators list of initiators in the export
     * @param hosts list of hosts in the export
     * @param clusters list of clusters in the export
     * @param volumesMap list of volumes in the export
     */
    public void getRemoveDiff(List<URI> initiators, List<URI> hosts, List<URI> clusters,
            Map<URI, Integer> volumesMap) {
        this.initiators = initiators;
        this.hosts = hosts;
        this.clusters = clusters;
        this.volumesMap = volumesMap;
        this.initiators.removeAll(this.removedInitiators);
        this.hosts.removeAll(this.removedHosts);
        this.clusters.removeAll(this.removedClusters);
    }

    /**
     * Gets the state of the export by adding initiators, hosts, and clusters to the state.
     * 
     * @param initiators list of initiators in the export
     * @param hosts list of hosts in the export
     * @param clusters list of clusters in the export
     * @param volumesMap list of volumes in the export
     */
    public void getAddDiff(List<URI> initiators, List<URI> hosts, List<URI> clusters,
            Map<URI, Integer> volumesMap) {
        this.initiators = initiators;
        this.hosts = hosts;
        this.clusters = clusters;
        this.volumesMap = volumesMap;

        // remove what should no longer be in the export group
        this.initiators.removeAll(this.removedInitiators);
        this.hosts.removeAll(this.removedHosts);
        this.clusters.removeAll(this.removedClusters);

        this.initiators.addAll(this.addedInitiators);
        this.hosts.addAll(this.addedHosts);
        this.clusters.addAll(this.addedClusters);
    }

    /**
     * Returns initiators for this export group state
     * 
     * @return list of initiators
     */
    public List<URI> getInitiators() {
        return this.initiators;
    }

    /**
     * Returns hosts for this export group state
     * 
     * @return list of hosts
     */
    public List<URI> getHosts() {
        return this.hosts;
    }

    /**
     * Returns clusters for this export group state
     * 
     * @return list of clusters
     */
    public List<URI> getClusters() {
        return this.clusters;
    }

    /**
     * Returns volumes for this export group state
     * 
     * @return volume map
     */
    public Map<URI, Integer> getVolumesMap() {
        return this.volumesMap;
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
     * Returns true is export group state has additions (new hosts, new clusters, new initiators)
     * 
     * @return true if adds, otherwise false
     */
    public boolean hasAdds() {
        return !addedClusters.isEmpty() || !addedHosts.isEmpty() || !addedInitiators.isEmpty();
    }

    /**
     * Returns true is export group state has removals (removed hosts, removed clusters, removed initiators)
     * 
     * @return true if removed, otherwise false
     */
    public boolean hasRemoves() {
        return !removedClusters.isEmpty() || !removedHosts.isEmpty() || !removedInitiators.isEmpty();
    }

    /**
     * Add initiators to the export group state
     * 
     * @param initiators
     */
    public void addInitiators(Collection<URI> initiators) {
        this.addedInitiators.addAll(initiators);
    }

    /**
     * Remove initiators from the export group state
     * 
     * @param initiators
     */
    public void removeInitiators(Collection<URI> initiators) {
        this.removedInitiators.addAll(initiators);
    }

    /**
     * Add host to the export group state
     * 
     * @param id
     */
    public void addHost(URI id) {
        this.addedHosts.add(id);
    }

    /**
     * Remove host from the export group state
     * 
     * @param id
     */
    public void removeHost(URI id) {
        this.removedHosts.add(id);
    }

    /**
     * Add cluster to the export group state
     * 
     * @param id
     */
    public void addCluster(URI id) {
        this.addedClusters.add(id);
    }

    /**
     * Remove cluster from the export group state
     * 
     * @param id
     */
    public void removeCluster(URI id) {
        this.removedClusters.add(id);
    }

    /**
     * Returns Removed Initiators
     * 
     * @return {@link Collection}{@link URI}
     */
    public Collection<URI> getRemovedInitiators() {
        return removedInitiators;
    }

    /**
     * Returns Removed Hosts
     * 
     * @return {@link Collection}{@link URI}
     */
    public Collection<URI> getRemovedHosts() {
        return removedHosts;
    }

    /**
     * Returns Removed Clusters
     * 
     * @return {@link Collection}{@link URI}
     */
    public Collection<URI> getRemovedClusters() {
        return removedClusters;
    }

    /**
     * Returns Added Initiators
     * 
     * @return {@link Collection}{@link URI}
     */
    public Collection<URI> getAddedInitiators() {
        return addedInitiators;
    }

    /**
     * Returns Added Hosts
     * 
     * @return {@link Collection}{@link URI}
     */
    public Collection<URI> getAddedHosts() {
        return addedHosts;
    }

    /**
     * Returns Added Clusters
     * 
     * @return {@link Collection}{@link URI}
     */
    public Collection<URI> getAddedClusters() {
        return addedClusters;
    }

    @Override
    public String toString() {
        return "ExportGroupState: [ExportId: " + this.id + ", Initiators: " + this.initiators + ", Hosts: "
                + this.hosts + ", Clusters: " + this.clusters + ", Volumes: " + this.volumesMap + ", Added Initiators: "
                + this.addedInitiators + ", Removed Initiators: " + this.removedInitiators + ", Added Hosts: " + this.addedHosts +
                ", Removed Hosts: " + this.removedHosts + ", Added Clusters: " + this.addedClusters + ", Removed Clusters: "
                + this.removedClusters + "]";
    }
}
