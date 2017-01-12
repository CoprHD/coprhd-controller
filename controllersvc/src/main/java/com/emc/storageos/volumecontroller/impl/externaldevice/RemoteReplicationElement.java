/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import java.io.Serializable;
import java.net.URI;

public class RemoteReplicationElement implements Serializable {

    public RemoteReplicationElement(ReplicationElementType type, URI elementURI) {
        this.type = type;
        this.elementUri = elementURI;
    }

    public static enum ReplicationElementType {
        REPLICATION_SET,
        REPLICATION_GROUP,
        REPLICATION_PAIR
    }

    // Replication target type. Type: Input.
    private ReplicationElementType type;

    // Uri of the backing object of the element instance. Depending on element type, this can be Uri of
    // replication set, replication group or replication pair. Type: Input.
    private URI elementUri;
}
