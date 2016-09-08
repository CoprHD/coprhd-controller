/*
 * Copyright (c) 2016 EMC Corporation
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
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.model.StoragePool;
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
        Set<String> excludeSystems = (Set<String>) attributeMap.get(Attributes.storage_system.toString());
        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (!excludeSystems.contains(pool.getStorageDevice().toString())) {
                matchedPools.add(pool);
            }
        }
        _logger.info("{} Storage pools matching after filtering excluded storage systems.", matchedPools.size());
        
        if (CollectionUtils.isEmpty(matchedPools)) {
            errorMessage.append(String.format("The only matching storage pools are on systems %s, which are not allowed for the request : %s. ", excludeSystems));
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
}