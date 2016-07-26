/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

import java.util.List;

/**
 * This class describes remote replication pair.
 * The replication pair is defined as a pair of source and target elements, linked by replication link.
 * Example: Source and target elements can be individual volumes.
 */
public class RemoteReplicationPair {

    // Device nativeId of replication pair. Type: Input/Output.
    private String nativeId;

    // Native id of remote replication set for this pair. Type: Input.
    private String replicationSetNativeId;

    /**
     * Native id of existing remote replication group of this pair (if applicable). Type: Input.
     */
    private String replicationGroupNativeId;

    private CapabilityInstance replicationMode;
    private RemoteReplicationSet.ReplicationState replicationState;
    private RemoteReplicationElement sourceElement;
    private RemoteReplicationElement targetElement;

    /**
     * Device specific capabilities.
     */
    private List<CapabilityInstance> capabilities;
}
