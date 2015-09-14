/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import java.util.List;

import util.BourneUtil;

import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.CachedResources;

/**
 * Fetches matching storage pools for the given file virtual pool configuration.
 */
public class MatchingObjectStoragePoolsCall extends ViPRListCall<StoragePoolRestRep> {
    private ObjectVirtualPoolParam vpool;

    public MatchingObjectStoragePoolsCall(ObjectVirtualPoolParam vpool) {
        this(BourneUtil.getViprClient(), vpool);
    }

    public MatchingObjectStoragePoolsCall(ViPRCoreClient client, ObjectVirtualPoolParam vpool) {
        this(client, new CachedResources<StoragePoolRestRep>(client.storagePools()), vpool);
    }

    public MatchingObjectStoragePoolsCall(ViPRCoreClient client, CachedResources<StoragePoolRestRep> cache,
    		ObjectVirtualPoolParam vpool) {
        super(client, cache);
        this.vpool = vpool;
    }

    @Override
    public List<StoragePoolRestRep> call() {
        return getByRefs(client.objectVpools().listMatchingStoragePools(vpool));
    }
}
