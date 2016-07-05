/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

import java.util.List;

/**
 * Describes consistency replication group on device.
 * Instances of this type are discovered from device.
 */
public class RemoteReplicationGroup {

    // Device nativeId of replication group. Type: Input/Output.
    private String nativeId;

    private String displayName;
    private String replicationSetNativeId;
    private RemoteReplicationSet.ReplicationMode replicationMode;
    private RemoteReplicationSet.ReplicationState replicationState;
    private String sourceSystemNativeId;
    private String targetSystemNativeId;

    /**
     * Device specific capabilities.
     */
    private List<CapabilityInstance> capabilities;




}
