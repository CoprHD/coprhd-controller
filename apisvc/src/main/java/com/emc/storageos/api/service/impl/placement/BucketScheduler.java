/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * StorageScheduler service for Object storage. StorageScheduler is done based on desired
 * class-of-service parameters for the provisioned storage.
 */
public class BucketScheduler {

    public final Logger _log = LoggerFactory.getLogger(BucketScheduler.class);

    private DbClient _dbClient;
    private StorageScheduler _scheduler;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setScheduleUtils(StorageScheduler scheduleUtils) {
        _scheduler = scheduleUtils;
    }

    /**
     * Schedule storage for Object in the varray with the given CoS capabilities.
     * 
     * @param vArray
     * @param vPool
     * @param capabilities
     * @return
     */
    public List<BucketRecommendation> placeBucket(VirtualArray vArray, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        _log.debug("Schedule storage for {} resource(s) of size {}.", capabilities.getResourceCount(), capabilities.getSize());

        // Get all storage pools that match the passed vpool params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        List<StoragePool> candidatePools = _scheduler.getMatchingPools(vArray, vPool, capabilities);

        // Get the recommendations for the candidate pools.
        List<Recommendation> poolRecommendations =
                _scheduler.getRecommendationsForPools(vArray.getId().toString(), candidatePools, capabilities);

        List<BucketRecommendation> recommendations =
                selectMatchingStoragePool(vPool, poolRecommendations);
        // We need to place all the resources. If we can't then
        // log an error and clear the list of recommendations.
        if (recommendations.isEmpty()) {
            _log.error(
                    "Could not find matching pools for virtual array {} & vpool {}",
                    vArray.getId(), vPool.getId());
        }

        return recommendations;
    }

    /**
     * Select the right StorageHADomain matching vpool protocols.
     * 
     * @param vpool
     * @param poolRecommends recommendations after selecting matching storage pools.
     * @return list of Bucket Recommendation
     */
    private List<BucketRecommendation> selectMatchingStoragePool(VirtualPool vpool, List<Recommendation> poolRecommends) {

        List<BucketRecommendation> result = new ArrayList<BucketRecommendation>();
        for (Recommendation recommendation : poolRecommends) {
            BucketRecommendation rec = new BucketRecommendation(recommendation);
            URI storageUri = recommendation.getSourceStorageSystem();
            URI storagePoolUri = recommendation.getSourceStoragePool();

            // Verify if the Storage System is an Object Store
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageUri);
            if (!Type.isObjectStorageSystem(StorageSystem.Type.valueOf(storage.getSystemType()))) {
                continue;
            }

            // Select only those Storage pool whose retention days is more than that of Virtual Pool
            StoragePool pool = _dbClient.queryObject(StoragePool.class, storagePoolUri);
            if (pool.getMaxRetention() == 0 || pool.getMaxRetention() >= vpool.getMaxRetention()) {
                result.add(rec);
                _log.debug("Select matching Object Store Domain : {} # {}", storageUri, storagePoolUri);
            }
        }
        return result;
    }
}
