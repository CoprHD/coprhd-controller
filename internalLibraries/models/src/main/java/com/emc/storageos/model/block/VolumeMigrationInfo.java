/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import java.net.URI;

/**
 * A tuple specifying the data for a migration.
 */
public class VolumeMigrationInfo {

    private URI volume;
    private URI storageSystem;
    private URI virtualPool;

    public VolumeMigrationInfo() {}
            
    public VolumeMigrationInfo(URI volume, URI storageSystem, URI virtualPool) {
        this.volume = volume;
        this.storageSystem = storageSystem;
        this.virtualPool = virtualPool;
    }

    /**
     * This parameter specifies the id of the 
     * volume to be migrated.
     * @valid example: URI of the volume to be migrated 
     */
    @XmlElement(required = true, name = "volume")
    public URI getVolume() {
        return volume;
    }

    public void setVolume(URI volume) {
        this.volume = volume;
    }

    /**
     * This parameter specifies the id of the 
     * storage system to which the volume is to 
     * be migrated.
     * @valid example: URI of the storage system 
     */
    @XmlElement(required = true, name = "storage_system")
    public URI getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
    }

    /**
     * This parameter specifies (optional) the id of the 
     * virtual pool to which the volume is to be migrated.
     * @valid example: URI of the virtual pool 
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
