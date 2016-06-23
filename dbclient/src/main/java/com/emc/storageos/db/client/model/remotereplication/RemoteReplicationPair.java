/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;

import java.net.URI;

@Cf("RemoteReplicationPair")
public class RemoteReplicationPair extends DataObject {

    // Device nativeId of replication pair.
    private String nativeId;

    private URI replicationGroup;
    private URI replicationSet;
    private RemoteReplicationSet.ReplicationMode replicationMode;
    private RemoteReplicationSet.ReplicationState replicationState;
    private URI sourceElement;
    private URI targetElement;
}
