/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

public class RemoteReplicationGroup {

    // Device nativeId of replication group. Type: Input/Output.
    String nativeId;

    String displayName;
    String replicationSetNativeId;
    RemoteReplicationSet.ReplicationMode replicationMode;
    RemoteReplicationSet.ReplicationState replicationState;



}
