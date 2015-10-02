/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * The migration create parameter.
 */
@XmlRootElement(name = "migration_create")
public class MigrationParam {

    private URI volume;
    private URI srcStorageSystem;
    private URI tgtStorageSystem;
    private URI virtualPool;

    public MigrationParam() {
    }

    public MigrationParam(URI volume, URI srcStorageSystem,
            URI tgtStorageSystem, URI virtualPool) {
        this.volume = volume;
        this.srcStorageSystem = srcStorageSystem;
        this.tgtStorageSystem = tgtStorageSystem;
        this.virtualPool = virtualPool;
    }

    /**
     * The id of the VPlex virtual volume to be migrated.
     * 
     */
    @XmlElement(required = true, name = "volume")
    public URI getVolume() {
        return volume;
    }

    public void setVolume(URI volume) {
        this.volume = volume;
    }

    /**
     * The source storage system from which the volume is to be migrated.
     * This identifies the storage system of the backend volume to be
     * migrated.
     * 
     */
    @XmlElement(required = true, name = "source_storage_system")
    public URI getSrcStorageSystem() {
        return srcStorageSystem;
    }

    public void setSrcStorageSystem(URI srcStorageSystem) {
        this.srcStorageSystem = srcStorageSystem;
    }

    /**
     * The target storage system to which the volume is to be migrated.
     * This identifies the storage system on which to create the new
     * backend volume to which the source will be migrated.
     * 
     */
    @XmlElement(required = true, name = "target_storage_system")
    public URI getTgtStorageSystem() {
        return tgtStorageSystem;
    }

    public void setTgtStorageSystem(URI tgtStorageSystem) {
        this.tgtStorageSystem = tgtStorageSystem;
    }

    /**
     * The virtual pool for the volume on the target storage system.
     * 
     */
    @XmlElement(name = "vpool")
    @JsonProperty("vpool")
    public URI getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        this.virtualPool = virtualPool;
    }
}
