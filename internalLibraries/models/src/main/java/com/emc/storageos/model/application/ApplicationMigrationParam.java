/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Application Discovery parameter.
 */
@XmlRootElement(name = "application_migration")
public class ApplicationMigrationParam {

    // SRCVP/TGTVPOOL list
    private List<Map<String, String>> srcTGTVPoolPairs;
    private URI storageResourcePoolId;
    private Set<URI> storageArrayPortIds;
    private boolean enableCompression;

    public ApplicationMigrationParam() {
    }

    public ApplicationMigrationParam(List<Map<String, String>> srcTGTVPoolPairs, URI storageResourcePoolId, Set<URI> storageArrayPortIds,
            boolean enableCompression) {
        this.srcTGTVPoolPairs = srcTGTVPoolPairs;
        this.storageResourcePoolId = storageResourcePoolId;
        this.storageArrayPortIds = storageArrayPortIds;
        this.enableCompression = enableCompression;
    }

    /**
     * The Storage Resource Pool on the Target VMAX3 array where the
     * Application will be migrated
     */
    @XmlElement(name = "storage_resource_pool")
    public URI getStorageResourcePoolId() {
        return storageResourcePoolId;
    }


    @XmlElementWrapper(name = "storage_array_port_ids")
    /**
     * A set comprising the unique list of storage arrays ports on the VMAX3 to which the
     * Hosts/Cluster Initiators associated with the Application have to be zoned.
     */
    @XmlElement(name = "storage_array_port_id")
    public Set<URI> getStorageArrayPortIds() {
        return storageArrayPortIds;
    }

    /**
     * The property to determine if compression needs to be set on the VMAX3 array as
     * part of application migration
     */
    @XmlElement(name = "compression")
    public boolean getCompression() {
        return enableCompression;
    }

}
