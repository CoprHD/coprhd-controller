/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

/**
 * This class describes remote replication pair.
 * The replication pair is defined as a pair of source and target elements, linked by replication link.
 * Source and target elements can be consistency groups or individual volumes.
 */
public class RemoteReplicationPair {

    // Device nativeId of replication pair. Type: Input/Output.
    private String nativeId;

    private String replicationGroupNativeId;
    private String replicationSetNativeId;
    private RemoteReplicationSet.ReplicationMode replicationMode;
    private RemoteReplicationSet.ReplicationState replicationState;
    private RemoteReplicationElement sourceElement;
    private RemoteReplicationElement targetElement;
}
