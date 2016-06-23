/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import java.net.URI;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;

@Cf("RemoteReplicationSet")
public class RemoteReplicationGroup extends DataObject {

    String nativeId;

    String displayName;

    URI replicationSet;

    @RelationIndex(cf = "RelationIndex", type = RemoteReplicationSet.class)
    @Name("replicationSet")
    public URI getReplicationSet() {
        return replicationSet;
    }


    RemoteReplicationSet.ReplicationMode replicationMode;
    RemoteReplicationSet.ReplicationState replicationState;
}
