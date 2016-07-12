/*
 * Copyright (c) 2013 EMC Corporation
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
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * ActivePoolMatcher is responsible to check pool activeness, ready state
 * and its registration status.
 * 
 */
public class LongTermRetentionMatcher extends AttributeMatcher {

    private static final Logger _logger = LoggerFactory
            .getLogger(LongTermRetentionMatcher.class);

    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap,
            StringBuffer errorMessage) {
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        Boolean retention = (Boolean) attributeMap.get(Attributes.long_term_retention_policy.toString());
        _logger.info("Long Term Retention Matcher Started : {}", Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (pool.getLongTermRetention().equals(retention)) {
                matchedPools.add(pool);
            }
        }
        _logger.info("Long Term Retention Matcher Ended : {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));

        if (CollectionUtils.isEmpty(matchedPools)) {
            errorMessage.append(String.format("No matching stoarge pool with long term retention %s found. ", retention.toString()));
            _logger.error(errorMessage.toString());
        }
        return matchedPools;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        // Since this is a defaultMatcher, it should always return true.
        return true;
    }
}
