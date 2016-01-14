/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * 
 * In object virtual pool, select storage pools that are spread across data centers(VDC)
 * specified in the vpool. Include all pools which have datacenter >= minDatacenters specified 
 *
 */
public class MinDataCenterMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory.getLogger(MinDataCenterMatcher.class);
    
	@Override
	protected boolean isAttributeOn(Map<String, Object> attributeMap) {
		if (attributeMap != null
				&& attributeMap.containsKey(Attributes.min_datacenters.toString())) {
			return true;
		}
		return false;
	}

	@Override
	protected List<StoragePool> matchStoragePoolsWithAttributeOn(
			List<StoragePool> allPools, Map<String, Object> attributeMap) {
		Integer minDataCenters = (Integer) attributeMap.get(Attributes.min_datacenters.toString());
        _logger.info("Pools Matching Minimum Data Centers Started : {}, {}", minDataCenters,
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        Iterator<StoragePool> poolIterator = allPools.iterator();
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(allPools);
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            if (pool.getDataCenters() < minDataCenters) {
            	_logger.info("Ignoring pool {} as Data Centers is less", pool.getNativeGuid());
            	filteredPoolList.remove(pool);
            }
        }
        _logger.info("Pools Matching Minimum Data Centers Ended : {}, {}", minDataCenters,
                Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;
	}

}
