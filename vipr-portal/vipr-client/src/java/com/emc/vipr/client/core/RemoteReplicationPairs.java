/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairRestRep;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Remote Replication Group resources.
 * <p>
 * Base URL: <tt>/block/remotereplicationpairs</tt>
 *
 * @see RemoteReplicationPairRestRep
 */
public class RemoteReplicationPairs {

    private RestClient client;

    public RemoteReplicationPairs(RestClient client) {
        this.client = client;
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
}
