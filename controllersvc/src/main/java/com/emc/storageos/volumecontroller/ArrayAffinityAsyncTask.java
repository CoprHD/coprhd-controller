/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;

/**
 * Initiator param for adding jobs into ZK queues.
 */
public class ArrayAffinityAsyncTask extends AsyncTask {
    private static final long serialVersionUID = -8866596995946050600L;
    private URI _hostId;
    private List<URI> _systemIds;

    public ArrayAffinityAsyncTask(Class clazz, URI id, List<URI> systemIds, URI hostId, String opId) {
        super(clazz, id, opId, Discovery_Namespaces.ARRAY_AFFINITY.name());
        _hostId = hostId;
        _systemIds = systemIds;
    }

    public URI getHostId() {
        return _hostId;
    }

    public List<URI> getSystemIds() {
        return _systemIds;
    }
}
