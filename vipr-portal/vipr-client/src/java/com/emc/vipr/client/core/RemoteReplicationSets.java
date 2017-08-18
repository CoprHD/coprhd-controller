/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.block.BlockConsistencyGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetBulkRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetList;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Remote Replication Set resources.
 * <p>
 * Base URL: <tt>/block/remote-replication-sets</tt>
 *
 * @see RemoteReplicationGroupRestRep
 */
public class RemoteReplicationSets  extends BulkExportResources<RemoteReplicationSetRestRep> {

    public RemoteReplicationSets(ViPRCoreClient parent, RestClient client) {
        super(parent, client, RemoteReplicationSetRestRep.class, PathConstants.BLOCK_REMOTE_REPLICATION_SET_URL);
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

    public List<RemoteReplicationSetRestRep> getBulkResources(BulkIdParam input) {
        RemoteReplicationSetBulkRep response = client.post(RemoteReplicationSetBulkRep.class, input, getBulkUrl());
        return defaultList(response.getSets());

    }
}
