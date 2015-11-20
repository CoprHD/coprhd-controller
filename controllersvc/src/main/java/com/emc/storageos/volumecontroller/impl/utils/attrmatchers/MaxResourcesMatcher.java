/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * Filter out the pools which reaches its maximum resources set.
 */
public class MaxResourcesMatcher extends AttributeMatcher {

    private static final Logger _log = LoggerFactory.getLogger(MaxResourcesMatcher.class);

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return true;
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
        _log.info("Pools Matching max resources Started: {}", Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(pools);
        Iterator<StoragePool> poolIterator = pools.iterator();

        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (checkPoolMaximumResourcesApproached(pool, _objectCache.getDbClient(), 0)) {
                filteredPoolList.remove(pool);
            }
        }
        _log.info("Pools Matching max resources Ended: {}", Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;
    }

    /**
     * Check whether pool & its system maximum resources approached or not.
     * 
     * @param pool : Storage Pool
     * @param dbClient : dbClient ref.
     * @return
     */
    public static boolean checkPoolMaximumResourcesApproached(StoragePool pool, DbClient dbClient, Integer resourceCount) {
        // Check whether maximum resources limit reached for pool or not.
        if (pool.getIsResourceLimitSet()) {
            Integer poolResources = getNumResources(pool, dbClient);
            if (pool.getMaxResources() < (poolResources + resourceCount)) {
                _log.info(
                        "Ignoring Storage pool {} since it's approaching Resource limit: {}. ",
                        pool.getNativeGuid(), pool.getMaxResources());
                return true;
            }
        }
        // Check whether maximum resources limit reached for storage system or not
        URI systemId = pool.getStorageDevice();
        StorageSystem system = dbClient.queryObject(StorageSystem.class, systemId);
        if (system.getIsResourceLimitSet()) {
            Integer systemResources = getNumResources(system, dbClient);
            if (system.getMaxResources() < (systemResources + resourceCount)) {
                _log.info(
                        "Ignoring Storage system {} pools since it's approaching Resource limit: {}. ",
                        system.getNativeGuid(), system.getMaxResources());
                return true;
            }
        }

        return false;
    }

    /**
     * Counts and returns the number of resources in a pool
     * 
     * @param pool
     * @param dbClient
     * @return
     */
    public static Integer getNumResources(StoragePool pool, DbClient dbClient) {
        String serviceType = pool.getPoolServiceType();
        Integer count = 0;
        if (StoragePool.PoolServiceType.file.name().equals(serviceType)
                || StoragePool.PoolServiceType.block_file.name().equals(serviceType)) {
            count = count + dbClient.countObjects(FileShare.class, "pool", pool.getId());
        }
        if (StoragePool.PoolServiceType.block.name().equals(serviceType)
                || StoragePool.PoolServiceType.block_file.name().equals(serviceType)) {
            count = count + dbClient.countObjects(Volume.class, "pool", pool.getId());
        }
        // We don't do anything if it's of type object
        return count;
    }

    /**
     * Counts and returns the number of resources in a storage system
     * 
     * @param system
     * @param dbClient
     * @return
     */
    public static Integer getNumResources(StorageSystem system, DbClient dbClient) {

        Integer count = 0;
        if (StorageSystem.Type.isFileStorageSystem(system.getSystemType())) {
            count = count + dbClient.countObjects(FileShare.class, "storageDevice", system.getId());
        }
        if (StorageSystem.Type.isBlockStorageSystem(system.getSystemType())) {
            count = count + dbClient.countObjects(Volume.class, "storageDevice", system.getId());
        }
        return count;
    }

}
