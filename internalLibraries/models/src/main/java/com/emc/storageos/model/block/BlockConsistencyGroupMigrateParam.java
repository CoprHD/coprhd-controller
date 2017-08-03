/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The migration create/initiate parameter.
 */
@XmlRootElement(name = "migration_environment")
public class BlockConsistencyGroupMigrateParam {

    private URI targetStorageSystem;
    
    private URI targetVirtualArray;

    // TODO srp, compression - need to be provided by user?

    public BlockConsistencyGroupMigrateParam() {
    }

    public BlockConsistencyGroupMigrateParam(URI targetStorageSystem, List<URI> targetPorts) {
        this.targetStorageSystem = targetStorageSystem;
    }

    /**
     * The target storage system to which the migration
     * environment needs to be created or deleted.
     */
    @XmlElement(required = true, name = "target_storage_system")
    public URI getTargetStorageSystem() {
        return targetStorageSystem;
    }

    public void setTargetStorageSystem(URI targetStorageSystem) {
        this.targetStorageSystem = targetStorageSystem;
    }

    @XmlElement(required = false, name = "target_virtual_array")
    public URI getTargetVirtualArray() {
        return targetVirtualArray;
    }

    public void setTargetVirtualArray(URI targetVirtualArray) {
        this.targetVirtualArray = targetVirtualArray;
    }

    
}
