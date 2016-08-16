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
    private List<URI> _hostIds;
    private List<URI> _systemIds;

    public ArrayAffinityAsyncTask(Class clazz, List<URI> systemIds, List<URI> hostIds, String opId) {
        super(clazz, systemIds.get(0), opId, Discovery_Namespaces.ARRAY_AFFINITY.name());
        _hostIds = hostIds;
        _systemIds = systemIds;
    }

    public List<URI> getHostIds() {
        return _hostIds;
    }

    public List<URI> getSystemIds() {
        return _systemIds;
    }
}
