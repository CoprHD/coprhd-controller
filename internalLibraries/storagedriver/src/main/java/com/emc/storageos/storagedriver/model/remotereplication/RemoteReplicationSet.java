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
 */
public class RemoteReplicationSet {

    private String nativeId;
    private String displayName;

    public static enum ReplicationRole {
        SOURCE,
        TARGET
    }

    private Map<String, Set<ReplicationRole>> systemMap = new HashMap<>();

    public static enum ReplicationGranularity {
        SET,
        GROUP,
        PAIR
    }

    private Set<ReplicationGranularity> supportedReplicationGranularity = new HashSet<>();

    public static enum ReplicationMode {
        SYNC,
        ASYNC
    }

    private ReplicationMode replicationMode;

    public static enum ReplicationState {
        ACTIVE,
        SYNCHRONIZING,
        PAUSED,
        FAILED_OVER,
        SWAPPED,
        STOPPED
    }

    private ReplicationState replicationState;

    public static enum ContainedElementType {
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    private ContainedElementType elementType;

    /*
     * Set of replication groups in this replication set.
     * If ContainedElementType is REPLICATION_GROUP, should be populated by driver.
     * If ContainedElementType is REPLICATIOM_PAIR, driver should leave this element empty.
     */
    private Set<RemoteReplicationGroup> replicationGroups;



}
