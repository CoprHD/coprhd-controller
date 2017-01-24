/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetList;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Remote Replication Set resources.
 * <p>
 * Base URL: <tt>/block/remotereplicationsets</tt>
 *
 * @see RemoteReplicationGroupRestRep
 */
public class RemoteReplicationSets {
    
    private RestClient client;

	public RemoteReplicationSets(RestClient client) {
		this.client = client;
	}

	public RemoteReplicationSetRestRep getRemoteReplicationSetsRestRep(String uuid) {
        return client.get(RemoteReplicationSetRestRep.class, PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL + "/" + uuid);
	}

    public RemoteReplicationSetList listRemoteReplicationSets() {
		return client.get(RemoteReplicationSetList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL);
	}

}