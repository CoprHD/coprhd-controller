/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.net.URI;

/**
 * Job for Discover.
 */
public class ArrayAffinityDataCollectionDiscoverJob extends DataCollectionDiscoverJob {
    private static final long serialVersionUID = 97283779984336577L;
    private URI _hostId;

    public ArrayAffinityDataCollectionDiscoverJob(ArrayAffinityDiscoverTaskCompleter completer, String namespace) {
        super(completer, JobOrigin.USER_API, namespace);
        _hostId = completer.getHostId();
    }

    public URI getHostId() {
        return _hostId;
    }
}
