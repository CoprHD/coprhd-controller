/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;

/**
 * Initiator param for adding jobs into ZK queues.
 */
public class ArrayAffinityAsyncTask extends AsyncTask {
    private static final long serialVersionUID = -8866596995946050600L;
    private URI _hostId;

    public ArrayAffinityAsyncTask(Class clazz, URI id, URI hostId, String opId) {
        super(clazz, id, opId, Discovery_Namespaces.ARRAY_AFFINITY.name());
        _hostId = hostId;
    }

    public URI getHostId() {
        return _hostId;
    }
}
