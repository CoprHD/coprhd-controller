/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;

import java.io.Serializable;
import java.net.URI;

@SuppressWarnings("serial")
public class RemoteReplicationElement implements Serializable {

    public RemoteReplicationElement(RemoteReplicationSet.ElementType type, URI elementURI) {
        this.type = type;
        this.elementUri = elementURI;
    }

    // Replication target type. Type: Input.
    private RemoteReplicationSet.ElementType type;

    // Uri of the backing object of the element instance. Depending on element type, this can be Uri of
    // replication set, replication group or replication pair. Type: Input.
    private URI elementUri;

    public RemoteReplicationSet.ElementType getType() {
        return type;
    }

    public void setType(RemoteReplicationSet.ElementType type) {
        this.type = type;
    }

    public URI getElementUri() {
        return elementUri;
    }

    public void setElementUri(URI elementUri) {
        this.elementUri = elementUri;
    }
}
