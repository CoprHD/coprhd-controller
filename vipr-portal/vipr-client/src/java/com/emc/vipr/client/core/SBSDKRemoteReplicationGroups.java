/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Remote Replication Consistency Group resources.
 * <p>
 * Base URL: <tt>/block/remotereplicationgroups</tt>
 *
 * @see RemoteReplicationGroupRestRep
 */
public class SBSDKRemoteReplicationGroups extends ProjectResources<RemoteReplicationGroupRestRep> implements
        TaskResources<RemoteReplicationGroupRestRep> {
    public SBSDKRemoteReplicationGroups(ViPRCoreClient parent, RestClient client) {
        super(parent, client, RemoteReplicationGroupRestRep.class, PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL);
    }

    @Override
    public SBSDKRemoteReplicationGroups withInactive(boolean inactive) {
        return (SBSDKRemoteReplicationGroups) super.withInactive(inactive);
    }

    @Override
    public SBSDKRemoteReplicationGroups withInternal(boolean internal) {
        return (SBSDKRemoteReplicationGroups) super.withInternal(internal);
    }

    @Override
    public Tasks<RemoteReplicationGroupRestRep> getTasks(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Task<RemoteReplicationGroupRestRep> getTask(URI id, URI taskId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected List<RemoteReplicationGroupRestRep> getBulkResources(BulkIdParam input) {
        // TODO Auto-generated method stub
        return null;
    }

}