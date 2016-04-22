/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Specifies the parameters to change the virtual array
 * for one or more volumes.
 */
@XmlRootElement(name = "volumes_varray_change")
public class MigrateVolumeVirtualArrayParam {

    private List<URI> volumes;
    private URI virtualArray;
    private URI migrationHost;
    private boolean isHostMigration;

    public MigrateVolumeVirtualArrayParam() {
    }

    public MigrateVolumeVirtualArrayParam(List<URI> volumes, URI virtualArray,
            URI migrationHost, boolean isHostMigration) {
        this.volumes = volumes;
        this.virtualArray = virtualArray;
        this.migrationHost = migrationHost;
        this.isHostMigration = isHostMigration;
    }

    @XmlElementWrapper(required = true, name = "volumes")
    /**
     * List of Volume IDs.
     */
    @XmlElement(required = true, name = "volume")
    public List<URI> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<URI>();
        }
        return volumes;
    }

    public void setVolumes(List<URI> volumes) {
        this.volumes = volumes;
    }

    // The new virtual array.
    @XmlElement(required = true, name = "varray")
    @JsonProperty("varray")
    public URI getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
    }

    @XmlElement(required = false, name = "host")
    public URI getMigrationHost() {
        return migrationHost;
    }

    public void setMigrationHost(URI migrationHost) {
        this.migrationHost = migrationHost;
    }

    @XmlElement(required = false, name = "ishostmigration") 
    public boolean getIsHostMigration() {
        return isHostMigration;
    }

    public void setIsHostMigration(boolean isHostMigration) {
        this.isHostMigration = isHostMigration;
    }
}
