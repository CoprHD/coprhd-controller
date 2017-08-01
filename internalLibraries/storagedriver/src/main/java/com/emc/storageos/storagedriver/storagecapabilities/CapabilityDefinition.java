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
        remoteReplicationAttributes,   // remote replication attributes
        hostIOLimits,  // hostIO limits capability
        volumeCompression //volume compression
    }

    /**
     * Supported common capability categories
     */
    public enum CapabilityCategory {
        dataStorage,    // common data storage capabilities: compression, dedup, hostIOLimits, auto-tiering policies
        storageProtection,   // storage protection category
        storagePerformance,  // storage performance category
        storageConnectivity   // storage connectivity category
    }

    // The unique identifier for the capability definition.
    private String uid;

    // The identifier for the capability category to which this capability belongs
    private String category;
    
    /**
     * Default Constructor
     */
    public CapabilityDefinition(String uid) {
        this.uid = uid;
        category = CapabilityCategory.dataStorage.toString();
    }

    public CapabilityDefinition(String uid, String category) {
        this.uid = uid;
        this.category = category;
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
