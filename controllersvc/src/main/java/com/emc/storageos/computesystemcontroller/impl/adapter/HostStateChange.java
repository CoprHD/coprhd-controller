/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.io.Serializable;
import java.net.URI;
import java.util.Collection;

import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Container for storing the state of host changes during discovery.
 * During host discovery, new and old initiators can be set on the state.
 */
public class HostStateChange implements Serializable {

    protected Host host;
    protected Collection<URI> oldInitiators;
    protected Collection<URI> newInitiators;
    protected URI oldCluster;
    protected URI newCluster;
    protected URI oldDatacenterURI;
    protected URI newDatacenterURI;

    /**
     * Create a new host state
     * 
     * @param target host
     * @param oldClusterURI old cluster id
     * @param oldInitiators list of removed initiators
     * @param addedInitiators list of new initiators
     */
    public HostStateChange(Host target, URI oldClusterURI, URI newClusterURI, Collection<Initiator> oldInitiators,
            Collection<Initiator> addedInitiators, URI oldDatacenterURI, URI newDatacenterURI) {
        this.host = target;
        this.oldInitiators = Sets.newHashSet();
        this.newInitiators = Sets.newHashSet();
        this.oldCluster = oldClusterURI;
        this.newCluster = newClusterURI;
        Collection<URI> oldInitiatorIds = Lists.newArrayList(Collections2.transform(oldInitiators,
                CommonTransformerFunctions.fctnDataObjectToID()));
        Collection<URI> addedInitiatorIds = Lists.newArrayList(Collections2.transform(addedInitiators,
                CommonTransformerFunctions.fctnDataObjectToID()));
        this.oldInitiators.addAll(oldInitiatorIds);
        this.newInitiators.addAll(addedInitiatorIds);
        this.oldDatacenterURI = oldDatacenterURI;
        this.newDatacenterURI = newDatacenterURI;
    }

    /**
     * Create a new host state
     * 
     * @param target host
     * @param oldClusterURI old cluster id
     */
    public HostStateChange(Host target, URI oldClusterURI) {
        this.host = target;
        this.oldInitiators = Sets.newHashSet();
        this.newInitiators = Sets.newHashSet();
        this.oldCluster = oldClusterURI;
    }

    /**
     * Return host
     * 
     * @return host
     */
    public Host getHost() {
        return host;
    }

    /**
     * Returns list of old initiators
     * 
     * @return old initiators
     */
    public Collection<URI> getOldInitiators() {
        return this.oldInitiators;
    }

    /**
     * Returns list of new initiators
     * 
     * @return new initiators
     */
    public Collection<URI> getNewInitiators() {
        return this.newInitiators;
    }

    /**
     * Set list of old initiators
     * 
     * @param oldInitiators
     */
    public void setOldInitiators(Collection<URI> oldInitiators) {
        this.oldInitiators = oldInitiators;
    }

    /**
     * Set list of new initiators
     * 
     * @param newInitiators
     */
    public void setNewInitiators(Collection<URI> newInitiators) {
        this.newInitiators = newInitiators;
    }

    /**
     * Set new cluster
     * 
     * @param new cluster
     */
    public void setNewCluster(URI newCluster) {
        this.newCluster = newCluster;
    }

    /**
     * Set old cluster
     * 
     * @param old cluster
     */
    public void setOldCluster(URI oldCluster) {
        this.oldCluster = oldCluster;
    }

    /**
     * Return old cluster id
     * 
     * @return old cluster id
     */
    public URI getOldCluster() {
        return this.oldCluster;
    }

    /**
     * Return new cluster id
     * 
     * @return new cluster id
     */
    public URI getNewCluster() {
        return this.newCluster;
    }

    /**
     * Return new datacenter id
     * 
     * @return new datacenter id
     */
    public URI getNewDatacenter() {
        return this.newDatacenterURI;
    }

    /**
     * Return old datacenter id
     * 
     * @return old datacenter id
     */
    public URI getOldDatacenter() {
        return this.oldDatacenterURI;
    }

    @Override
    public String toString() {
        return "HostStateChange: [Host: " + this.host.getLabel() + " with cluster: "
                + (this.newCluster == null ? "null" : this.newCluster) + ", OldInitiators: " + this.oldInitiators
                + ", NewInitators: " + this.newInitiators + ", OldCluster: " + this.oldCluster + ", Datacenter: "
                + this.newDatacenterURI + ", Old Datacenter: " + this.oldDatacenterURI + "]";
    }
}