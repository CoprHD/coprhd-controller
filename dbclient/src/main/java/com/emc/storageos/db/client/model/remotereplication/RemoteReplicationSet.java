/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;

import java.util.Set;

@Cf("RemoteReplicationSet")
public class RemoteReplicationSet extends DataObject {

    private String nativeId;

    // index this field.
    private String storageSystemType;
    private String displayName;

    public enum ReplicationRole {
        SOURCE,
        TARGET
    }

    private StringSetMap systemToRolesMap = new StringSetMap();

    public enum ReplicationLinkGranularity {
        SET,
        GROUP,
        PAIR
    }

    private StringSet supportedReplicationLinkGranularity = new StringSet();

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

    public enum ElementType {
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    /**
     * Element types supported by this replication set.
     */
    private Set<ElementType> supportedElementTypes;

    /*
     * Set of replication groups in this replication set.
     * If ContainedElementType is REPLICATION_GROUP, should be populated by driver.
     * If ContainedElementType is REPLICATIOM_PAIR, driver should leave this element empty.
     */
    //private Set<RemoteReplicationGroup> replicationGroups;


}
