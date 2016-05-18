/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.AttributeMatcher;

/**
 * StorageSystemMatcher is responsible to match the storage systems of Storage
 * Pools.
 * 
 */
public class StorageSystemMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory
            .getLogger(StorageSystemMatcher.class);

    /**
     * Check if the CoS and pools are in the same varray and filter out the pools
     * if they are not in the same varray.
     * 
     * 
     * @param pools
     *            matches neighborhoods of all pools with vpool neighborhoods.
     * @param compareWithObj : vpool
     * @param isMatcherValid : flag to identify whether to run this matcher or not.
     * 
     * 
     * @return list of pools matching if both pool & vpool are part of same varray.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();

        Set<String> systems = (Set<String>) attributeMap.get(Attributes.storage_system
                .toString());

        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (systems.contains(pool.getStorageDevice().toString())) {
                matchedPools.add(pool);
            }
        }
        _logger.info("{} pools are matching with systems after matching.",
                matchedPools.size());
        return matchedPools;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return (null != attributeMap && attributeMap.containsKey(Attributes.storage_system
                .toString()));
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOff(List<StoragePool> pools, Map<String, Object> attributeMap) {
        return pools;
    }
}
