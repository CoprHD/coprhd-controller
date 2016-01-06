/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;
import com.google.common.base.Joiner;

/**
 * 
 * ECS does not have concept of thick or thin provisioning. However bucket has hardquota 
 * mapping to ECS max quota. Storage pool free size should be more the requested quota
 * of the bucket
 *
 */
public class BucketQuotaMatcher extends AttributeMatcher {

private static final Logger _logger = LoggerFactory.getLogger(BucketQuotaMatcher.class);
    
    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        if (attributeMap != null
                && attributeMap.containsKey(Attributes.quota.toString())) {
            return true;
        }
        return false;
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> allPools, Map<String, Object> attributeMap) {
        String quota = attributeMap.get(Attributes.quota.toString()).toString();
        Long hardQuota = Long.parseLong(quota);
        //convert to KB
        hardQuota = hardQuota/1024;
        _logger.info("Pools Matching hard quota Started {}, {} :", hardQuota,
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        Iterator<StoragePool> poolIterator = allPools.iterator();
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(allPools);
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (pool.getFreeCapacity() < hardQuota) {
                _logger.info("Ignoring pool {} as Free capacity is less :", pool.getNativeGuid());
                allPools.remove(pool);
            }
        }
        _logger.info("Pools Matching Hard quota Ended {}, {}", hardQuota,
                Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;

    }

}
