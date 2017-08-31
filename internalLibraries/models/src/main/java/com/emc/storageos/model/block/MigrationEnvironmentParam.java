/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * The migration environment parameter.
 */
@XmlRootElement(name = "migration_environment")
public class MigrationEnvironmentParam {

    private URI sourceStorageSystem;
    private URI targetStorageSystem;

    public MigrationEnvironmentParam() {
    }

    public MigrationEnvironmentParam(URI sourceStorageSystem, URI targetStorageSystem) {
        this.sourceStorageSystem = sourceStorageSystem;
        this.targetStorageSystem = targetStorageSystem;
    }

    /**
     * The source storage system from which the migration
     * environment needs to be created or deleted.
     */
    @XmlElement(required = true, name = "source_storage_system")
    public URI getSourceStorageSystem() {
        return sourceStorageSystem;
    }

    public void setSourceStorageSystem(URI sourceStorageSystem) {
        this.sourceStorageSystem = sourceStorageSystem;
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
}
