/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import java.net.URI;

public class RemoteReplicationTarget {

    public static enum ReplicationTargetType {
        REPLICATION_SET,
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    // Replication target type. Type: Input.
    private ReplicationTargetType targetType;

    // Uri of the backing object of the target instance. Depending on target type, this can be Uri of
    // replication set, replication group or replication pair. Type: Input.
    private URI nativeId;
}
