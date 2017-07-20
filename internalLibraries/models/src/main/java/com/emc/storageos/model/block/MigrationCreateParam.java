/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * The migration create/initiate parameter.
 */
@XmlRootElement(name = "migration_environment")
public class MigrationCreateParam {

    private URI targetStorageSystem;

    private List<URI> targetPorts;

    // TODO add path param

    public MigrationCreateParam() {
    }

    public MigrationCreateParam(URI targetStorageSystem, List<URI> targetPorts) {
        this.targetStorageSystem = targetStorageSystem;
        this.targetPorts = targetPorts;
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

    @XmlElementWrapper(required = true, name = "target_storage_ports")
    /**
     * List of target storage port ids.
     * Example: list of valid URIs
     */
    @XmlElement(required = true, name = "target_storage_port")
    public List<URI> getTargetPorts() {
        if (targetPorts == null) {
            targetPorts = new ArrayList<URI>();
        }
        return targetPorts;
    }

    public void setTargetPorts(List<URI> targetPorts) {
        this.targetPorts = targetPorts;
    }
}
