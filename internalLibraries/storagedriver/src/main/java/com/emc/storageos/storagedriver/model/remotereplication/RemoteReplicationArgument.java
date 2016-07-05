/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

import java.util.List;

/**
 * This class describes operational object for remote link management.
 * All operations on the remote replication link are executed on instances of this class.
 */
public class RemoteReplicationArgument {

    public static enum ReplicationArgumentType {
        REPLICATION_SET,
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    // Replication argument type. Type: Input.
    private ReplicationArgumentType type;

    /**
     *  Device nativeId of replication argument.Depending on argument type, this can be nativeId of
     *  replication set, replication group or replication pair. Type: Input.
     */
    private String nativeId;

    /**
     * Parent remote replication group native id.
     * Applicable to REPLICATION_PAIR argument.
     */
    private String parentRemoteReplicationGroupId;

    /**
     * Parent remote replication set native id.
     * Applicable to REPLICATION_GROUP and  REPLICATION_PAIR arguments.
     */
    private String parentRemoteReplicationSetId;

    /**
     * Device specific capabilities.
     */
    private List<CapabilityInstance> capabilities;
}
