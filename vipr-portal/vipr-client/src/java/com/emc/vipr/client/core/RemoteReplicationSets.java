/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;
import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.block.BlockConsistencyGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
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
        return client.get(RemoteReplicationSetRestRep.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL + "/" + uuid);
	}

    public RemoteReplicationSetList listRemoteReplicationSets() {
		return client.get(RemoteReplicationSetList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL);
	}

    public RemoteReplicationSetList listRemoteReplicationSets(URI varray, URI vpool) {
        return client.get(RemoteReplicationSetList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL +
                "/varray/" + varray + "/vpool/" + vpool);
    }

    public RemoteReplicationSetList listRemoteReplicationSets(URI storageTypeURI) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL +
                "/storage-type/sets");
        if (storageTypeURI != null) {
            uriBuilder.queryParam("storageType", storageTypeURI);
        }
        return client.getURI(RemoteReplicationSetList.class,uriBuilder.build());
    }

    public RemoteReplicationGroupList getGroupsForSet(URI setId) {
        return client.get(RemoteReplicationGroupList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL + "/" + setId + "/groups");
    }

    public BlockConsistencyGroupList listRemoteReplicationSetCGs(URI setId) {
        return client.get(BlockConsistencyGroupList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL + "/" + setId + "/consistency-groups");
    }

    public RemoteReplicationPairList listRemoteReplicationSetPairs(URI setId) {
        return client.get(RemoteReplicationPairList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL + "/" + setId + "/set-pairs");
    }

    public RemoteReplicationPairList listRemoteReplicationPairs(URI setId) {
        return client.get(RemoteReplicationPairList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL + "/" + setId + "/pairs");
    }
}
