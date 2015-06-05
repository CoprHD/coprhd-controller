/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package jobs.vipr;

import java.util.List;

import util.BourneUtil;

import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.CachedResources;

/**
 * Fetches matching storage pools for the given file virtual pool configuration.
 */
public class MatchingFileStoragePoolsCall extends ViPRListCall<StoragePoolRestRep> {
    private FileVirtualPoolParam vpool;

    public MatchingFileStoragePoolsCall(FileVirtualPoolParam vpool) {
        this(BourneUtil.getViprClient(), vpool);
    }

    public MatchingFileStoragePoolsCall(ViPRCoreClient client, FileVirtualPoolParam vpool) {
        this(client, new CachedResources<StoragePoolRestRep>(client.storagePools()), vpool);
    }

    public MatchingFileStoragePoolsCall(ViPRCoreClient client, CachedResources<StoragePoolRestRep> cache,
            FileVirtualPoolParam vpool) {
        super(client, cache);
        this.vpool = vpool;
    }

    @Override
    public List<StoragePoolRestRep> call() {
        return getByRefs(client.fileVpools().listMatchingStoragePools(vpool));
    }
}
