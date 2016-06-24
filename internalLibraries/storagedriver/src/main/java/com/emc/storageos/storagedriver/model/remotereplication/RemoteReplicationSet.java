/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class describes set of interconnected storage systems configured for remote replication.
 * The instances of this class are discovered by driver and are read-only from driver management perspective.
 * Any system with SOURCE role can be used to keep source elements, and any system with TARGET role can be used to
 * keep target elements. This should support different remote replication topologies.
 */
public class RemoteReplicationSet {

    private String nativeId;
    private String displayName;

    public enum ReplicationRole {
        SOURCE,
        TARGET
    }

    /**
     * Map of storage systems in the replication set mapped to their roles in the set.
     * The same system can have one or both replication roles.
     */
    private Map<String, Set<ReplicationRole>> systemMap = new HashMap<>();

    public enum ReplicationGranularity {
        SET,
        GROUP,
        PAIR
    }

    private Set<ReplicationGranularity> supportedReplicationGranularity = new HashSet<>();

    public enum ReplicationMode {
        SYNC,
        ASYNC
    }

    private ReplicationMode replicationMode;

    public enum ReplicationState {
        ACTIVE,
        SYNCHRONIZING,
        PAUSED,
        FAILED_OVER,
        SWAPPED,
        STOPPED
    }

    private ReplicationState replicationState;

    public enum SupportedElementType {
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    private Set<SupportedElementType> elementTypes;

    /*
     * Set of replication groups in this replication set.
     * Should be populated only if elementTypes contains REPLICATION_GROUP.
     * If elementTypes has only REPLICATIOM_PAIR, driver should leave this element empty.
     */
    private Set<RemoteReplicationGroup> replicationGroups;



}
