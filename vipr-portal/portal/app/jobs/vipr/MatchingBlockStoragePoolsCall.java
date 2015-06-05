/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package jobs.vipr;

import java.util.List;

import util.BourneUtil;

import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.CachedResources;

/**
 * Fetches matching storage pools for the given block virtual pool configuration.
 */
public class MatchingBlockStoragePoolsCall extends ViPRListCall<StoragePoolRestRep> {
    private BlockVirtualPoolParam vpool;

    public MatchingBlockStoragePoolsCall(BlockVirtualPoolParam vpool) {
        this(BourneUtil.getViprClient(), vpool);
    }

    public MatchingBlockStoragePoolsCall(ViPRCoreClient client, BlockVirtualPoolParam vpool) {
        this(client, new CachedResources<StoragePoolRestRep>(client.storagePools()), vpool);
    }

    public MatchingBlockStoragePoolsCall(ViPRCoreClient client, CachedResources<StoragePoolRestRep> cache,
            BlockVirtualPoolParam vpool) {
        super(client, cache);
        this.vpool = vpool;
    }

    @Override
    public List<StoragePoolRestRep> call() {
        return getByRefs(client.blockVpools().listMatchingStoragePools(vpool));
    }
}
