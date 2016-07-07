/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import java.net.URI;

public class RemoteReplicationArgument {

    public static enum ReplicationArgumentType {
        REPLICATION_SET,
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    // Replication target type. Type: Input.
    private ReplicationArgumentType type;

    // Uri of the backing object of the argument instance. Depending on argument type, this can be Uri of
    // replication set, replication group or replication pair. Type: Input.
    private URI argumentUri;
}
