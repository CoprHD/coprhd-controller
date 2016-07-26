/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Describes consistency remote replication group on device.
 * Group always has one source storage system and one target storage system.
 * Instances of this type are discovered from device.
 */
public class RemoteReplicationGroup {

    // Device nativeId of replication group. Type: Input/Output.
    private String nativeId;

    private String displayName;
    private String replicationSetNativeId;
    private CapabilityInstance replicationMode;
    private RemoteReplicationSet.ReplicationState replicationState;
    /**
     * Defines if group consistency for link operations is enforced
     */
    private boolean isGroupConsistencyEnforced;

    /**
     * Defines types of group elements for which replication link operations are supported.
     */
    private Set<RemoteReplicationSet.ReplicationLinkGranularity> supportedReplicationLinkGranularity = new HashSet<>();

    private String sourceSystemNativeId;
    private String targetSystemNativeId;

    /**
     * Device specific capabilities. For example, device specific replication modes.
     */
    private List<CapabilityInstance> capabilities;




}
