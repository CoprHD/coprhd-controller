/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

/**
 * This class describes operational object for remote link management.
 * All operations on the remote replication link are executed on an instance of this class.
 */
public class RemoteReplicationTarget {

    public static enum ReplicationTargetType {
        REPLICATION_SET,
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    // Replication target type. Type: Input.
    private ReplicationTargetType targetType;

    // Device nativeId of replication target.Depending on target type, this can be nativeId of
    // replication set, replication group or replication pair. Type: Input.
    private String nativeId;
}
