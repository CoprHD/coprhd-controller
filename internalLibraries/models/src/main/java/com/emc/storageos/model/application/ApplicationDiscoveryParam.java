/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.Set;

/**
 * The Application Discovery parameter.
 */
@XmlRootElement(name = "application_discovery")
public class ApplicationDiscoveryParam {

    private URI srcStorageSystem;
    private URI tgtStorageSystem;
    private Set<String> applicationGroupNames;
    private Set<URI> volumeIds;
    private Set<URI> hostIds;
    private Set<URI> clusterIds;

    public ApplicationDiscoveryParam() {
    }

    public ApplicationDiscoveryParam(URI srcStorageSystem, URI tgtStorageSystem,
            Set<String> applicationGroupNames, Set<URI> volumeIds, Set<URI> hostIds, Set<URI> clusterIds) {
        this.srcStorageSystem = srcStorageSystem;
        this.tgtStorageSystem = tgtStorageSystem;
        this.applicationGroupNames = applicationGroupNames;
        this.volumeIds = volumeIds;
        this.hostIds = hostIds;
        this.clusterIds = clusterIds;
    }

    /**
     * The source storage system from which the application group is to be migrated.
     * This identifies the storage system of the application group to be
     * migrated.
     * 
     */
    @XmlElement(required = true, name = "source_storage_system")
    public URI getSrcStorageSystem() {
        return srcStorageSystem;
    }

    /**
     * The target storage system to which the application group is to be migrated.
     * This identifies the storage system on which to create the new
     * application group to which the source application group will be migrated.
     * 
     */
    @XmlElement(required = true, name = "target_storage_system")
    public URI getTgtStorageSystem() {
        return tgtStorageSystem;
    }

    @XmlElementWrapper(name = "application_group_names")
    /**
     * A set comprising the unique list of Application Group names that
     * are to be discovered,.
     */
    @XmlElement(name = "application_group_name")
    public Set<String> getApplicationGroupNames() {
        return applicationGroupNames;
    }

    @XmlElementWrapper(name = "volume_ids")
    /**
     * A set comprising the unique list of volumeIds whose associated Application Groups
     * are to be discovered when supplied
     */
    @XmlElement(name = "volume_id")
    public Set<URI> getVolumeIds() {
        return volumeIds;
    }

    @XmlElementWrapper(name = "host_ids")
    /**
     * A set comprising the unique list of hostIds whose associated Application Groups
     * are to be discovered when supplied
     */
    @XmlElement(name = "host_id")
    public Set<URI> getHostIds() {
        return hostIds;
    }

    @XmlElementWrapper(name = "cluster_ids")
    /**
     * A set comprising the unique list of clusterIds whose associated Application Groups
     * are to be discovered when supplied
     */
    @XmlElement(name = "cluster_id")
    public Set<URI> getClusterIds() {
        return clusterIds;
    }

}
