/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.AttributeMatcher;

/**
 * A matcher that will filter pools that are on a set of excluded storage systems.
 */
public class ExcludeStorageSystemsMatcher extends AttributeMatcher {
    
    // A reference to a logger.
    private static final Logger _logger = LoggerFactory.getLogger(ExcludeStorageSystemsMatcher.class);

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools,
            Map<String, Object> attributeMap, StringBuffer errorMessage) {
        // We only allow pools that are not on the excluded systems.
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        Set<String> excludedSystemIds = null;
        Object attrObj = attributeMap.get(Attributes.exclude_storage_system.toString());
        if (attrObj != null) {
            excludedSystemIds = (Set<String>) attrObj;
            Iterator<StoragePool> poolIterator = pools.iterator();
            while (poolIterator.hasNext()) {
                StoragePool pool = poolIterator.next();
                if (!excludedSystemIds.contains(pool.getStorageDevice().toString())) {
                    matchedPools.add(pool);
                }
            }
        } else {
            // No exclusions, so all match.
            matchedPools.addAll(pools);
        }
        _logger.info("{} Storage pools matching after filtering excluded storage systems.", matchedPools.size());
        
        if (CollectionUtils.isEmpty(matchedPools)) {
            errorMessage.append(String.format("The only matching storage pools are on systems %s, which are not allowed for the request.", 
                    getExcludedSystemInfo(excludedSystemIds)));
            _logger.error(errorMessage.toString());
        }

        return matchedPools;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return ((null != attributeMap) && (attributeMap.containsKey(Attributes.exclude_storage_system.toString())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOff(List<StoragePool> pools, Map<String, Object> attributeMap) {
        return pools;
    }
    
    /**
     * Generates the excluded system info for display in the error message when
     * the matcher eliminates all passed storage pools.
     * 
     * @param excludedSystemIds The Ids of the excluded systems.
     * 
     * @return A string identifying the excluded systems.
     */
    private String getExcludedSystemInfo(Set<String> excludedSystemIds) {
        StringBuilder builder = new StringBuilder();
        for (String excludedSystemId : excludedSystemIds) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            StorageSystem excludedSystem = _objectCache.queryObject(StorageSystem.class, URI.create(excludedSystemId));
            if (excludedSystem != null) {
                builder.append(excludedSystem.forDisplay());
            } else {
                builder.append(excludedSystemId);
            }
        }
        return builder.toString();
    }
}