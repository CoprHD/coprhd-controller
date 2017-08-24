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

import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * This matcher is for filtering the storage pool which do not support replication
 * 
 * 
 * @author lakhiv
 * 
 */
public class FileReplicationMatcher extends AttributeMatcher {

    private static final Logger _logger = LoggerFactory.getLogger(FileReplicationMatcher.class);

    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap,
            StringBuffer errorMessage) {
        _logger.info("FileReplicationMatcher Started : {}", Joiner.on("\t").join(getNativeGuidFromPools(pools)));

        Boolean replicationRequired = (Boolean) attributeMap.get(Attributes.file_replication_supported.toString());
        // if replication flag is not true do not filter the pool.
        if (!replicationRequired) {
            return pools;
        }
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();

            if (isReplicationSupportedPool(pool)) {
                matchedPools.add(pool);
            }
        }
        if (CollectionUtils.isEmpty(matchedPools)) {
            errorMessage.append("No matching storage pools found for File replication... ");
            _logger.error(errorMessage.toString());
        }
        _logger.info("FileReplicationMatcher Matcher Ended : {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;

    }

    private boolean isReplicationSupportedPool(StoragePool pool) {
        StorageSystem system = _objectCache.queryObject(StorageSystem.class, pool.getStorageDevice());
        if (null == system.getSupportedReplicationTypes() ||
                system.getSupportedReplicationTypes().isEmpty()) {
            return false;
        }
        // vnxe and unity are unified storage system which support file and block ,
        // while discovery supportedreplicationTypes attribute get populated as block may support it ,
        // Currently file replication is not supported for vnxe and unity.
        if (system.deviceIsType(Type.vnxe) || system.deviceIsType(Type.unity)) {
            return false;
        }
        if (system.getSupportedReplicationTypes().contains("REMOTE") ||
                system.getSupportedReplicationTypes().contains("LOCAL")) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return attributeMap != null && attributeMap.containsKey(Attributes.file_replication_supported.toString());
    }
}
