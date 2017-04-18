/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Remote Replication Group resources.
 * <p>
 * Base URL: <tt>/block/remotereplicationgroups</tt>
 *
 * @see RemoteReplicationGroupRestRep
 */
public class RemoteReplicationGroups {

    private RestClient client;

    public RemoteReplicationGroups(RestClient client) {
        this.client = client;
    }

    public RemoteReplicationGroupRestRep getRemoteReplicationGroupsRestRep(String uuid) {
        return client.get(RemoteReplicationGroupRestRep.class, PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL + "/" + uuid);
    }

    public RemoteReplicationGroupList listRemoteReplicationGroups() {
        return client.get(RemoteReplicationGroupList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL);
    }

    public RemoteReplicationGroupList listValidRemoteReplicationGroups() {
        // valid groups are: reachable, have source & target systems
        return client.get(RemoteReplicationGroupList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL + "/valid");
    }

}
