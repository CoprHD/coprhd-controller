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
 * Specifies the parameters to change the virtual array for a volume.
 */
@Deprecated
@XmlRootElement(name = "volume_varray_change")
public class VirtualArrayChangeParam {

    private URI virtualArray;
    private URI migrationHost;
    private boolean isHostMigration;

    public VirtualArrayChangeParam() {
    }

    public VirtualArrayChangeParam(URI virtualArray) {
        this.virtualArray = virtualArray;
        this.migrationHost = migrationHost;
        this.isHostMigration = isHostMigration;
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
