/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;

@Cf("RemoteReplicationSet")
public class RemoteReplicationSet extends DataObject {

    private String nativeId;
    private String storageSystemType;
    private String displayName;

    public enum ReplicationRole {
        SOURCE,
        TARGET
    }

    private StringSetMap systemToRolesMap = new StringSetMap();

    public enum ReplicationGranularity {
        SET,
        GROUP,
        PAIR
    }

    private StringSet supportedReplicationGranularity = new StringSet();

    public enum ReplicationMode {
        SYNC,
        ASYNC
    }

    private ReplicationMode replicationMode;

    public enum ReplicationState {
        ACTIVE,
        SYNCHRONIZING,
        PAUSED,
        FAILED_OVER,
        SWAPPED,
        STOPPED
    }

    private ReplicationState replicationState;

    public enum ContainedElementType {
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    private ContainedElementType elementType;

    /*
     * Set of replication groups in this replication set.
     * If ContainedElementType is REPLICATION_GROUP, should be populated by driver.
     * If ContainedElementType is REPLICATIOM_PAIR, driver should leave this element empty.
     */
    //private Set<RemoteReplicationGroup> replicationGroups;


}
