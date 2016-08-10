/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

/**
 * This class defines capability metadata.
 */
public class CapabilityDefinition {

    /**
     * Supported common capability definitions
     */
    public enum CapabilityUid {
        autoTieringPolicy,    // auto tiering capability
        deduplication,        // de-duplication
        replicationMode,      // replication mode capability
        remoteReplicationAttributes   // remote replication attributes
    }

    // The unique identifier for the capability definition.
    private String uid;
    
    /**
     * Default Constructor
     */
    public CapabilityDefinition(String uid) {
        this.uid = uid;
    }
    
    /**
     * Getter for the unique identifier for the capability definition.
     * 
     * @return The unique identifier for the capability definition.
     */
    public String getId() {
        return uid;
    }
}
