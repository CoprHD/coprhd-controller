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
import com.emc.storageos.model.remotereplication.RemoteReplicationPairBulkRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Remote Replication Group resources.
 * <p>
 * Base URL: <tt>/block/remotereplicationpairs</tt>
 *
 * @see RemoteReplicationPairRestRep
 */
public class RemoteReplicationPairs extends BulkExportResources<RemoteReplicationPairRestRep> {

    public RemoteReplicationPairs(ViPRCoreClient parent, RestClient client) {
        super(parent, client, RemoteReplicationPairRestRep.class, PathConstants.BLOCK_REMOTE_REPLICATION_PAIR_URL);
    }

    public RemoteReplicationPairRestRep getRemoteReplicationPairRestRep(String uuid) {
        return client.get(RemoteReplicationPairRestRep.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_PAIR_URL + "/" + uuid);
    }

    public RemoteReplicationPairList listRemoteReplicationPairs() {
        return client.get(RemoteReplicationPairList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_PAIR_URL);
    }

    public RemoteReplicationPairList listRelatedRemoteReplicationPairs(URI elementToFindPairsFor) {        
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.BLOCK_REMOTE_REPLICATION_PAIR_URL);
        uriBuilder.queryParam("storageElement", elementToFindPairsFor);
        return client.getURI(RemoteReplicationPairList.class, uriBuilder.build());
    }

    @Override
    public List<RemoteReplicationPairRestRep> getBulkResources(BulkIdParam input) {
        RemoteReplicationPairBulkRep response = client.post(RemoteReplicationPairBulkRep.class, input, getBulkUrl());
        return defaultList(response.getPairs());
    }
}
