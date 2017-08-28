/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The migration create/initiate parameter.
 */
@XmlRootElement(name = "migration_create")
public class MigrationCreateParam  {

    private URI targetStorageSystem;
    private Boolean compressionEnabled = true; // Compression to be enabled by default
    private URI srp;

    public MigrationCreateParam() {
    }

    public MigrationCreateParam(URI targetStorageSystem) {
        this.targetStorageSystem = targetStorageSystem;
    }

    public MigrationCreateParam(URI targetStorageSystem, Boolean compressionEnabled) {
        this.targetStorageSystem = targetStorageSystem;
        this.compressionEnabled = compressionEnabled;
    }

    public MigrationCreateParam(URI targetStorageSystem, Boolean compressionEnabled, URI srp) {
        this.targetStorageSystem = targetStorageSystem;
        this.compressionEnabled = compressionEnabled;
        this.srp = srp;
    }

    /**
     * The target storage system to which the migration
     * environment needs to be created or deleted.
     */
    @XmlElement(name = "target_storage_system", required = true)
    public URI getTargetStorageSystem() {
        return targetStorageSystem;
    }

    public void setTargetStorageSystem(URI targetStorageSystem) {
        this.targetStorageSystem = targetStorageSystem;
    }

    /**
     * Indicates whether compression to be enabled on the target storage group.
     */
    @XmlElement(name = "compression_enabled", required = false)
    public Boolean getCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(Boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    /**
     * The srp pool on the target storage system.
     */
    @XmlElement(name = "srp", required = false)
    public URI getSrp() {
        return srp;
    }

    public void setSrp(URI srp) {
        this.srp = srp;
    }

}
