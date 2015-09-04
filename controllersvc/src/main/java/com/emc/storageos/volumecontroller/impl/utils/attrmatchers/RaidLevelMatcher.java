/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.RaidLevel;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;
import com.google.common.base.Joiner;

public class RaidLevelMatcher extends ConditionalAttributeMatcher {
    private static final Logger _logger = LoggerFactory.getLogger(RaidLevelMatcher.class);

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        boolean isMatcherNeeded = false;

        if (isAutoTieringPolicyOn(attributeMap) && !attributeMap.containsKey(AttributeMatcher.PLACEMENT_MATCHERS)) {
            _logger.info("Skipping RaidLevel matcher, as VMAX FAST Policy is chosen");
            return isMatcherNeeded;
        }

        if (attributeMap != null && attributeMap.get(Attributes.raid_levels.name()) != null) {
            HashSet<String> raidLevels = (HashSet<String>) attributeMap.get(Attributes.raid_levels.name());
            if (!raidLevels.isEmpty()) {
                isMatcherNeeded = true;
            }
        }
        return isMatcherNeeded;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(pools);
        Set<String> raidLevels = null;
        raidLevels = (Set<String>) attributeMap.get(Attributes.raid_levels.toString());
        _logger.info("Pools Matching RaidLevels {} Started:{}", raidLevels,
                Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            StringSet poolSupportedRaidLevels = pool.getSupportedRaidLevels();
            if (null != poolSupportedRaidLevels) {
                _logger.info("Supported Raid Levels {} for Pool {} ", Joiner.on("\t")
                        .join(poolSupportedRaidLevels), pool.getNativeGuid());
            } else {
                _logger.info("Supported Raid Levels Empty for Pool {} ", pool.getNativeGuid());
            }
            // if Pool doesn't have any details on Raid Level, remove that Pool
            if (null == poolSupportedRaidLevels) {
                _logger.info("Ignoring pool {} as it doesn't have raid levels", pool.getNativeGuid());
                filteredPoolList.remove(pool);
                continue;
            }
            Set<String> copies = new HashSet<String>(poolSupportedRaidLevels);
            copies.retainAll(raidLevels);
            if (copies.isEmpty()) {
                _logger.info("Ignoring pool {} as it is not supporting the raid levels.", pool.getNativeGuid());
                filteredPoolList.remove(pool);
            }
        }
        _logger.info("Pools Matching RaidLevels Ended :{}", Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;
    }

    @Override
    public Map<String, Set<String>> getAvailableAttribute(List<StoragePool> neighborhoodPools,
            URI vArrayId) {
        try {
            Map<String, Set<String>> availableAttrMap = new HashMap<String, Set<String>>(1);
            Set<String> availableAttrValues = new HashSet<String>();
            for (StoragePool pool : neighborhoodPools) {
                StringSet raidLevels = pool.getSupportedRaidLevels();
                if (null != raidLevels && !raidLevels.isEmpty()) {
                    for (String raidLevel : raidLevels) {
                        if (RaidLevel.lookup(raidLevel) != null) {
                            availableAttrValues.add(raidLevel);
                        }
                    }
                }
            }
            if (!availableAttrValues.isEmpty()) {
                availableAttrMap.put(Attributes.raid_levels.name(), availableAttrValues);
                return availableAttrMap;
            }
        } catch (Exception e) {
            _logger.error("Exception occurred while getting available attributes using RaidLevelMatcher.", e);
        }
        return Collections.emptyMap();
    }
}
