/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.volumecontroller.ArrayAffinityAsyncTask;

public class ArrayAffinityDiscoverTaskCompleter extends DiscoverTaskCompleter {
    private static final long serialVersionUID = -3464486163426247054L;
    private URI _hostId;

    public ArrayAffinityDiscoverTaskCompleter(ArrayAffinityAsyncTask task, String jobType) {
        super(task, jobType);
        _hostId = task.getHostId();
    }

    public URI getHostId() {
        return _hostId;
    }

    @Override
    final protected void createDefaultOperation(DbClient dbClient) {
        dbClient.createTaskOpStatus(getType(), getId(), getOpId(),
                ResourceOperationTypeEnum.DISCOVER_HOST_ARRAY_AFFINITY);
    }
}
