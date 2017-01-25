/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * This matcher is for filtering the storage pool which do not support
 * snapshot scheduling. storage pool copy type is used to check snapshot scheduling
 * 
 * @author lakhiv
 * 
 */
public class FileSnapshotMatcher extends AttributeMatcher {

    private static final Logger _logger = LoggerFactory.getLogger(FileSnapshotMatcher.class);
    private static final String CHECKPOINT_SCHEDULE = "checkpoint_schedule";

    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap,
            StringBuffer errorMessage) {
        _logger.info("FileSnapshotMatcher Started : {}", Joiner.on("\t").join(getNativeGuidFromPools(pools)));

        Boolean schedule = (Boolean) attributeMap.get(Attributes.file_snapshot_supported.toString());
        // if snapshot scheduling is not true do not filter the pool.
        if (!schedule) {
            return pools;
        }
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();

            if (getScheduleSupportFromPool(pool)) {
                matchedPools.add(pool);
            }
        }
        if (CollectionUtils.isEmpty(matchedPools)) {
            errorMessage.append("No matching storage pools found for File snapshot scheduling. ");
            _logger.error(errorMessage.toString());
        }
        _logger.info("FileSnapshotMatcher Matcher Ended : {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;

    }

    private boolean getScheduleSupportFromPool(StoragePool pool) {
        StringSet copyTypes = pool.getSupportedCopyTypes();
        if (copyTypes != null) {
            for (String type : copyTypes) {
                if (type.equalsIgnoreCase(CHECKPOINT_SCHEDULE)) {
                    return true;
                }

            }
        }
        return false;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {

        return attributeMap != null && attributeMap.containsKey(Attributes.file_snapshot_supported.toString());
    }
}
