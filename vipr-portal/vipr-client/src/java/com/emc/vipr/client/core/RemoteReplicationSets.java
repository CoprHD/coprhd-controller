/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Remote Replication Set resources.
 * <p>
 * Base URL: <tt>/block/remotereplicationsets</tt>
 *
 * @see RemoteReplicationGroupRestRep
 */
public class RemoteReplicationSets extends ProjectResources<RemoteReplicationSetRestRep> implements
        TaskResources<RemoteReplicationSetRestRep> {
    public RemoteReplicationSets(ViPRCoreClient parent, RestClient client) {
        super(parent, client, RemoteReplicationSetRestRep.class, PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL);
    }

    @Override
    public Tasks<RemoteReplicationSetRestRep> getTasks(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Task<RemoteReplicationSetRestRep> getTask(URI id, URI taskId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected List<RemoteReplicationSetRestRep> getBulkResources(BulkIdParam input) {
        // TODO Auto-generated method stub
        return null;
    }


}