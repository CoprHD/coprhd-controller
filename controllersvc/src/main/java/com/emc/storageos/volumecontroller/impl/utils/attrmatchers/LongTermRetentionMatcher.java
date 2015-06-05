/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2013. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ActivePoolMatcher is responsible to check pool activeness, ready state
 * and its registration status.
 *
 */
public class LongTermRetentionMatcher extends AttributeMatcher {
    
    private static final Logger _logger = LoggerFactory
            .getLogger(LongTermRetentionMatcher.class);

    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        Boolean retention = (Boolean) attributeMap.get(Attributes.long_term_retention_policy.toString());
        _logger.info("Long Term Retention Matcher Started : {}", Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        Iterator<StoragePool> poolIterator = pools.iterator();
        while(poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (pool.getLongTermRetention().equals(retention)) {
                matchedPools.add(pool);
            }
        }
        _logger.info("Long Term Retention Matcher Ended : {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        // Since this is a defaultMatcher, it should always return true.
        return true;
    }
}
