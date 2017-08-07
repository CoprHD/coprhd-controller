/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.block.export.ExportPathParameters;

/**
 * The migration create zone parameter.
 */
@XmlRootElement(name = "migration_zone_create")
public class MigrationZoneCreateParam {

    private URI targetStorageSystem;
    private URI compute;
    private ExportPathParameters pathParam;
    private URI targetVirtualArray;

    public MigrationZoneCreateParam() {
    }

    public MigrationZoneCreateParam(URI targetStorageSystem, URI compute, ExportPathParameters pathParam) {
        this.targetStorageSystem = targetStorageSystem;
        this.compute = compute;
        this.pathParam = pathParam;
    }

    public MigrationZoneCreateParam(URI targetStorageSystem, URI compute, ExportPathParameters pathParam, URI targetVirtualArray) {
        this.targetStorageSystem = targetStorageSystem;
        this.compute = compute;
        this.pathParam = pathParam;
        this.targetVirtualArray = targetVirtualArray;
    }

    /**
     * The target storage system to whose storage ports
     * the zones needs to be created or deleted.
     */
    @XmlElement(name = "target_storage_system", required = true)
    public URI getTargetStorageSystem() {
        return targetStorageSystem;
    }

    public void setTargetStorageSystem(URI targetStorageSystem) {
        this.targetStorageSystem = targetStorageSystem;
    }

    /**
     * The compute resource involved in migration.
     * It could either be a Host or Cluster.
     */
    @XmlElement(name = "compute", required = false)
    public URI getCompute() {
        return compute;
    }

    public void setCompute(URI compute) {
        this.compute = compute;
    }

    @XmlElement(name = "path_param", required = true)
    public ExportPathParameters getPathParam() {
        return pathParam;
    }

    public void setPathParam(ExportPathParameters pathParam) {
        this.pathParam = pathParam;
    }

    /**
     * The virtual array from where target storage ports to be selected.
     * This is an optional parameter. Virtual array can be specified if
     * target storage ports are not specified.
     */
    @XmlElement(name = "target_virtual_array", required = false)
    public URI getTargetVirtualArray() {
        return targetVirtualArray;
    }

    public void setTargetVirtualArray(URI targetVirtualArray) {
        this.targetVirtualArray = targetVirtualArray;
    }

}
