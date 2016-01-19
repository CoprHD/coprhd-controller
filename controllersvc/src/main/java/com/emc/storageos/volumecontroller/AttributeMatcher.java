/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.utils.ObjectLocalCache;

/**
 * AttributeMatcher is base class for all attribute matchers which provides base functionality
 * of identifying the valid matchers and finding the right pools for a given set of attribute matchers.
 * All new attribute matchers should just extend this and implement their own logic to match pools
 * against new attributes.
 */
public abstract class AttributeMatcher {
    /**
     * DbClient reference.
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(AttributeMatcher.class);

    protected ObjectLocalCache _objectCache;

    protected CoordinatorClient _coordinator;

    public static final String VPOOL_MATCHERS = "vpoolMatchers";

    public static final String PLACEMENT_MATCHERS = "placementMatchers";

    public static final String BASIC_PLACEMENT_MATCHERS = "basicPlacementMatchers";

    public static final String CONNECTIVITY_PLACEMENT_MATCHERS = "connectivityMatchers";

    public static enum Attributes {
        vpool_type,
        varrays,
        protocols,
        max_paths,
        paths_per_initiator,
        size,
        fast,
        raid_levels,
        drive_type,
        system_type,
        provisioning_type,
        high_availability_type,
        high_availability_varray,
        high_availability_vpool,
        high_availability_rp,
        metropoint,
        auto_tiering_policy_name,
        thin_volume_preallocation_size,
        storage_system,
        multi_volume_consistency,
        max_native_snapshots,
        max_native_continuous_copies,
        recoverpoint_map,
        thin_volume_preallocation_percentage,
        unique_policy_names,
        remote_copy,
        long_term_retention_policy,
        file_replication_type,
        file_replication_copy_mode,
        file_replication,
        source_storage_system,
        remote_copy_mode,
        min_datacenters,
        quota
    }

    /**
     * This method responsible to run the matchers even if there is CoS attribute on/off.
     * attributeOn => match pools based on the CoS attribute value set.
     * attributeOff => match pools when a CoS attribute value not set.
     * 
     * @param pools : List of pools to match this attribute.
     * @return matchedPools : list of pools matching.
     */
    public List<StoragePool> runMatchStoragePools(List<StoragePool> pools, Map<String, Object> attributeMap) {
        List<StoragePool> matchedPools = null;
        if (isAttributeOn(attributeMap)) {
            matchedPools = matchStoragePoolsWithAttributeOn(pools, attributeMap);
        } else {
            matchedPools = matchStoragePoolsWithAttributeOff(pools, attributeMap);
        }
        return matchedPools;

    }

    /**
     * Returns the StorageSystem object for the given StoragePool.
     * 
     * @param storageSystemMap
     * @param pool
     * @return StorageSystem
     */
    public StorageSystem getStorageSystem(Map<URI, StorageSystem> storageSystemMap,
            StoragePool pool) {
        URI storageSystemURI = pool.getStorageDevice();
        if (storageSystemMap.containsKey(storageSystemURI)) {
            return storageSystemMap.get(storageSystemURI);
        }
        StorageSystem system = _objectCache.queryObject(StorageSystem.class, storageSystemURI);
        storageSystemMap.put(storageSystemURI, system);
        return system;
    }

    /**
     * Match pools if a attribute value is not set.
     * 
     * @param pools : list of pools to match.
     * @param attributeMap : map contains the list of attribute values to match.
     */
    protected List<StoragePool> matchStoragePoolsWithAttributeOff(List<StoragePool> pools, Map<String, Object> attributeMap) {
        return pools;
    }

    /**
     * Check whether attribute value is set or not.
     * Ex. If CoS protocols is set then this method returns true else false.
     * So, Based on this method return value, AttributeMatcher will run.
     * 
     * @param attributeMap : map contains the list of attribute values to match.
     * @return
     */
    protected abstract boolean isAttributeOn(Map<String, Object> attributeMap);

    /**
     * Matches the list of storagePools and returns only the pools
     * which are matching with the attribute.
     * 
     * @param allPools : List of pools to match against each vpool attribute.
     * @param attributeMap : map contains the list of attribute values to match.
     * @return matchedpools:
     */
    protected abstract List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> allPools, Map<String, Object> attributeMap);

    /**
     * For debug purpose, which lists the matched Pool Native Guids
     * 
     * @param pools
     * @return
     */
    protected List<String> getNativeGuidFromPools(List<StoragePool> pools) {
        List<String> poolURIList = new ArrayList<String>();
        for (StoragePool pool : pools) {
            poolURIList.add(pool.getNativeGuid());
        }
        return poolURIList;
    }

    /**
     * set Cache.
     * 
     * @param cache
     */
    public void setObjectCache(ObjectLocalCache cache) {
        _objectCache = cache;
    }

    /**
     * set CoordinatorClient.
     * 
     * @param coordinator
     */
    public void setCoordinatorClient(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Returns a Map contains AttributeName as key and its supported values else empty map.
     * By default this will return empty map and if someone wants
     * to show available attribute & its supported values, they must override this method
     * and return the available attributes..
     * 
     * Ex. In a varray, if any one system supports FAST, then
     * this method returns a map with key as FAST and its policynames as values.
     * ["FAST", [policy1, policy2,policy3...etc.]]
     * 
     * @param neighborhoodPools
     * @param varrayUri virtual array URI
     * 
     * @return
     */
    public Map<String, Set<String>> getAvailableAttribute(List<StoragePool> neighborhoodPools,
            URI varrayUri) {
        return Collections.emptyMap();
    }
}
